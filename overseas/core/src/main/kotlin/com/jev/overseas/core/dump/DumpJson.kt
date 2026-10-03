package com.jev.overseas.core.dump

/**
 * Minimal JSON encoder for [ScreenDump]. Hand-written so the dump format has no
 * Android or third-party dependency and can be tested on the JVM.
 *
 * One node per line, so a dump diffs cleanly against a later recording.
 */
object DumpJson {

    fun encode(dump: ScreenDump): String {
        val sb = StringBuilder(64 * 1024)
        sb.append("{\n\"meta\": ")
        value(sb, dump.meta)
        sb.append(",\n\"nodes\": [\n")
        dump.nodes.forEachIndexed { i, node ->
            value(sb, nodeMap(node))
            if (i < dump.nodes.size - 1) sb.append(',')
            sb.append('\n')
        }
        sb.append("]\n}\n")
        return sb.toString()
    }

    private fun nodeMap(n: NodeRecord): Map<String, Any?> = linkedMapOf(
        "i" to n.index,
        "parent" to n.parent,
        "depth" to n.depth,
        "childIndex" to n.childIndex,
        "class" to n.className,
        "package" to n.packageName,
        "id" to n.viewId,
        "text" to n.text,
        "textLength" to n.textLength,
        "desc" to n.contentDescription,
        "descLength" to n.contentDescriptionLength,
        "hint" to n.hint,
        "stateDescription" to n.stateDescription,
        "paneTitle" to n.paneTitle,
        "tooltip" to n.tooltip,
        "bounds" to n.bounds,
        "flags" to n.flags,
        "actions" to n.actions,
        "childCount" to n.childCount,
        "missingChildren" to n.missingChildren,
        "collection" to n.collection,
        "collectionItem" to n.collectionItem,
        "drawingOrder" to n.drawingOrder,
        "uniqueId" to n.uniqueId,
        "extraKeys" to n.extraKeys,
        "inScrollable" to n.inScrollable,
    )

    private fun value(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> string(sb, v)
            is Boolean -> sb.append(v.toString())
            is Int, is Long -> sb.append(v.toString())
            is Float, is Double -> {
                val d = (v as Number).toDouble()
                if (d.isNaN() || d.isInfinite()) sb.append("null") else sb.append(d.toString())
            }
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, item) in v) {
                    if (!first) sb.append(", ")
                    first = false
                    string(sb, k.toString())
                    sb.append(": ")
                    value(sb, item)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (item in v) {
                    if (!first) sb.append(", ")
                    first = false
                    value(sb, item)
                }
                sb.append(']')
            }
            else -> string(sb, v.toString())
        }
    }

    private fun string(sb: StringBuilder, s: String) {
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (ch < ' ' || ch == ' ' || ch == ' ') {
                    sb.append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    sb.append(ch)
                }
            }
        }
        sb.append('"')
    }
}
