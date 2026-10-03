package com.jev.overseas.core.dump

import com.jev.overseas.core.json.MiniJson

/** Reads a ScreenDump file written by [DumpJson]. */
object DumpReader {

    class DumpFormatException(message: String) : IllegalArgumentException(message)

    fun read(json: String): ScreenDump {
        val root = MiniJson.parseObject(json)
        @Suppress("UNCHECKED_CAST")
        val meta = root["meta"] as? Map<String, Any?> ?: throw DumpFormatException("Missing meta")
        val schema = meta["schema"]
        if (schema != ScreenDumpFormat.SCHEMA) throw DumpFormatException("Unsupported schema: $schema")
        val nodes = (root["nodes"] as? List<*> ?: throw DumpFormatException("Missing nodes")).map { node(it) }
        return ScreenDump(meta, nodes)
    }

    private fun node(raw: Any?): NodeRecord {
        @Suppress("UNCHECKED_CAST")
        val m = raw as? Map<String, Any?> ?: throw DumpFormatException("Node is not an object")
        fun int(key: String): Int = (m[key] as? Number)?.toInt() ?: throw DumpFormatException("Node field '$key' is not a number")
        fun str(key: String): String? = m[key] as? String
        fun ints(key: String): List<Int>? = (m[key] as? List<*>)?.map { (it as Number).toInt() }
        fun strs(key: String): List<String> = (m[key] as? List<*>)?.map { it as String } ?: emptyList()
        return NodeRecord(
            index = int("i"),
            parent = int("parent"),
            depth = int("depth"),
            childIndex = int("childIndex"),
            className = str("class"),
            packageName = str("package"),
            viewId = str("id"),
            text = str("text"),
            textLength = int("textLength"),
            contentDescription = str("desc"),
            contentDescriptionLength = int("descLength"),
            hint = str("hint"),
            stateDescription = str("stateDescription"),
            paneTitle = str("paneTitle"),
            tooltip = str("tooltip"),
            bounds = ints("bounds") ?: throw DumpFormatException("Node has no bounds"),
            flags = strs("flags"),
            actions = strs("actions"),
            childCount = int("childCount"),
            missingChildren = int("missingChildren"),
            collection = ints("collection"),
            collectionItem = ints("collectionItem"),
            drawingOrder = int("drawingOrder"),
            uniqueId = str("uniqueId"),
            extraKeys = strs("extraKeys"),
            inScrollable = m["inScrollable"] as? Boolean ?: false,
        )
    }
}
