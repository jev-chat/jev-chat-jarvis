package com.jev.overseas.core.dump

/**
 * One accessibility node, as recorded by the probe. Plain data with no Android
 * types, so redaction, the test-chat guard and JSON encoding run in JVM tests.
 *
 * Free-text fields ([text], [contentDescription], [hint], [stateDescription],
 * [paneTitle], [tooltip]) are null after redaction; [textLength] and
 * [contentDescriptionLength] always hold the original lengths.
 */
data class NodeRecord(
    val index: Int,
    val parent: Int,
    val depth: Int,
    val childIndex: Int,
    val className: String?,
    val packageName: String?,
    val viewId: String?,
    val text: String?,
    val textLength: Int,
    val contentDescription: String?,
    val contentDescriptionLength: Int,
    val hint: String?,
    val stateDescription: String?,
    val paneTitle: String?,
    val tooltip: String?,
    /** left, top, right, bottom in screen pixels. */
    val bounds: List<Int>,
    val flags: List<String>,
    val actions: List<String>,
    val childCount: Int,
    val missingChildren: Int,
    /** rowCount, columnCount; null when the node is not a collection. */
    val collection: List<Int>?,
    /** rowIndex, rowSpan, columnIndex, columnSpan; null when not a collection item. */
    val collectionItem: List<Int>?,
    val drawingOrder: Int,
    val uniqueId: String?,
    val extraKeys: List<String>,
    /** True when the node or one of its ancestors is scrollable. */
    val inScrollable: Boolean,
) {
    val isEditable: Boolean get() = "editable" in flags
    val isPassword: Boolean get() = "password" in flags
    val isScrollable: Boolean get() = "scrollable" in flags
}

enum class DumpMode { FULL, STRUCTURE }

/** Header values are strings, numbers, booleans, lists or nested maps. */
data class ScreenDump(
    val meta: Map<String, Any?>,
    val nodes: List<NodeRecord>,
)

object ScreenDumpFormat {
    const val SCHEMA = "jev.screendump/1"
}
