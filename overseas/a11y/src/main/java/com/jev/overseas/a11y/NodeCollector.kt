package com.jev.overseas.a11y

import android.content.res.Resources
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.jev.overseas.core.dump.NodeRecord

/**
 * Walks one window's accessibility tree and copies each node into a
 * [NodeRecord]. Read-only: it never calls performAction.
 */
object NodeCollector {

    const val MAX_NODES = 8000

    data class Result(val nodes: List<NodeRecord>, val truncated: Boolean)

    private class Pending(
        val node: AccessibilityNodeInfo,
        val parent: Int,
        val depth: Int,
        val childIndex: Int,
        val inScrollable: Boolean,
    )

    fun collect(root: AccessibilityNodeInfo, res: Resources): Result {
        val out = ArrayList<NodeRecord>(1024)
        val stack = ArrayDeque<Pending>()
        stack.addLast(Pending(root, parent = -1, depth = 0, childIndex = 0, inScrollable = false))
        var truncated = false
        while (stack.isNotEmpty()) {
            if (out.size >= MAX_NODES) { truncated = true; break }
            val p = stack.removeLast()
            val node = p.node
            val index = out.size
            val inScrollable = p.inScrollable || node.isScrollable

            val children = ArrayList<AccessibilityNodeInfo?>(node.childCount)
            for (i in 0 until node.childCount) children.add(runCatching { node.getChild(i) }.getOrNull())

            out.add(record(node, index, p, inScrollable, children.count { it == null }, res))

            // Push in reverse so children come out in document order.
            for (i in children.indices.reversed()) {
                val child = children[i] ?: continue
                stack.addLast(Pending(child, index, p.depth + 1, i, inScrollable))
            }
        }
        return Result(out, truncated)
    }

    private fun record(
        node: AccessibilityNodeInfo,
        index: Int,
        p: Pending,
        inScrollable: Boolean,
        missingChildren: Int,
        res: Resources,
    ): NodeRecord {
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        val collection = node.collectionInfo?.let { listOf(it.rowCount, it.columnCount) }
        val item = node.collectionItemInfo?.let {
            listOf(it.rowIndex, it.rowSpan, it.columnIndex, it.columnSpan)
        }
        return NodeRecord(
            index = index,
            parent = p.parent,
            depth = p.depth,
            childIndex = p.childIndex,
            className = node.className?.toString(),
            packageName = node.packageName?.toString(),
            viewId = node.viewIdResourceName,
            text = text,
            textLength = text?.length ?: 0,
            contentDescription = desc,
            contentDescriptionLength = desc?.length ?: 0,
            hint = node.hintText?.toString(),
            stateDescription = node.stateDescription?.toString(),
            paneTitle = node.paneTitle?.toString(),
            tooltip = node.tooltipText?.toString(),
            bounds = listOf(bounds.left, bounds.top, bounds.right, bounds.bottom),
            flags = flags(node),
            actions = node.actionList.map { actionName(it, res) },
            childCount = node.childCount,
            missingChildren = missingChildren,
            collection = collection,
            collectionItem = item,
            drawingOrder = node.drawingOrder,
            uniqueId = if (Build.VERSION.SDK_INT >= 33) node.uniqueId else null,
            extraKeys = runCatching { node.extras.keySet().sorted() }.getOrDefault(emptyList()) +
                node.availableExtraData.map { "available:$it" },
            inScrollable = inScrollable,
        )
    }

    private fun flags(n: AccessibilityNodeInfo): List<String> {
        val f = ArrayList<String>(8)
        if (n.isVisibleToUser) f.add("visible")
        if (n.isEnabled) f.add("enabled")
        if (n.isClickable) f.add("clickable")
        if (n.isLongClickable) f.add("longClickable")
        if (n.isScrollable) f.add("scrollable")
        if (n.isEditable) f.add("editable")
        if (n.isPassword) f.add("password")
        if (n.isFocusable) f.add("focusable")
        if (n.isFocused) f.add("focused")
        if (n.isAccessibilityFocused) f.add("accessibilityFocused")
        if (n.isSelected) f.add("selected")
        if (n.isCheckable) f.add("checkable")
        if (n.isChecked) f.add("checked")
        if (n.isHeading) f.add("heading")
        if (n.isImportantForAccessibility) f.add("important")
        if (n.isScreenReaderFocusable) f.add("screenReaderFocusable")
        if (n.isMultiLine) f.add("multiLine")
        if (n.isContextClickable) f.add("contextClickable")
        if (n.isDismissable) f.add("dismissable")
        if (n.isShowingHintText) f.add("showingHint")
        return f
    }

    private val standardActions = mapOf(
        AccessibilityNodeInfo.ACTION_FOCUS to "FOCUS",
        AccessibilityNodeInfo.ACTION_CLEAR_FOCUS to "CLEAR_FOCUS",
        AccessibilityNodeInfo.ACTION_SELECT to "SELECT",
        AccessibilityNodeInfo.ACTION_CLEAR_SELECTION to "CLEAR_SELECTION",
        AccessibilityNodeInfo.ACTION_CLICK to "CLICK",
        AccessibilityNodeInfo.ACTION_LONG_CLICK to "LONG_CLICK",
        AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS to "ACCESSIBILITY_FOCUS",
        AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS to "CLEAR_ACCESSIBILITY_FOCUS",
        AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY to "NEXT_AT_MOVEMENT_GRANULARITY",
        AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY to "PREVIOUS_AT_MOVEMENT_GRANULARITY",
        AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT to "NEXT_HTML_ELEMENT",
        AccessibilityNodeInfo.ACTION_PREVIOUS_HTML_ELEMENT to "PREVIOUS_HTML_ELEMENT",
        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD to "SCROLL_FORWARD",
        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD to "SCROLL_BACKWARD",
        AccessibilityNodeInfo.ACTION_COPY to "COPY",
        AccessibilityNodeInfo.ACTION_PASTE to "PASTE",
        AccessibilityNodeInfo.ACTION_CUT to "CUT",
        AccessibilityNodeInfo.ACTION_SET_SELECTION to "SET_SELECTION",
        AccessibilityNodeInfo.ACTION_EXPAND to "EXPAND",
        AccessibilityNodeInfo.ACTION_COLLAPSE to "COLLAPSE",
        AccessibilityNodeInfo.ACTION_DISMISS to "DISMISS",
        AccessibilityNodeInfo.ACTION_SET_TEXT to "SET_TEXT",
    )

    /** Standard name, framework resource name (scrollUp, showOnScreen…), or the app's own label. */
    private fun actionName(action: AccessibilityNodeInfo.AccessibilityAction, res: Resources): String {
        standardActions[action.id]?.let { return it }
        val framework = runCatching { res.getResourceEntryName(action.id) }.getOrNull()
        if (framework != null) return framework
        return "custom:${action.id}:${action.label ?: ""}"
    }
}
