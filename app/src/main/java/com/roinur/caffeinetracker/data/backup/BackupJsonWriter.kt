package com.roinur.caffeinetracker.data.backup

import org.json.JSONArray
import org.json.JSONObject
import java.io.Reader
import java.io.Writer

/** Bounded JSON output, shared by normal export and cloud hashing/encoding. */
internal object BackupJsonWriter {
    private fun escape(character: Char): String? = when (character) {
        '"' -> "\\\""
        '\\' -> "\\\\"
        '\b' -> "\\b"
        '\u000c' -> "\\f"
        '\n' -> "\\n"
        '\r' -> "\\r"
        '\t' -> "\\t"
        else -> if (character < ' ') "\\u${character.code.toString(16).padStart(4, '0')}" else null
    }

    fun quoted(text: String, destination: Writer) {
        destination.write('"'.code)
        var start = 0
        for (index in text.indices) {
            val escaped = escape(text[index]) ?: continue
            destination.write(text, start, index - start)
            destination.write(escaped)
            start = index + 1
        }
        destination.write(text, start, text.length - start)
        destination.write('"'.code)
    }

    fun quoted(source: Reader, destination: Writer) {
        destination.write('"'.code)
        val buffer = CharArray(8192)
        while (true) {
            val length = source.read(buffer)
            if (length < 0) break
            var start = 0
            for (index in 0 until length) {
                val escaped = escape(buffer[index]) ?: continue
                destination.write(buffer, start, index - start)
                destination.write(escaped)
                start = index + 1
            }
            destination.write(buffer, start, length - start)
        }
        destination.write('"'.code)
    }

    fun write(root: Any?, destination: Writer, prettyPrint: Boolean = false,
              sortKeys: Boolean = false, excludedRootKeys: Set<String> = emptySet()) {
        fun indent(depth: Int) { repeat(depth * 2) { destination.write(' '.code) } }
        fun value(item: Any?, depth: Int) {
            when (item) {
                null, JSONObject.NULL -> destination.write("null")
                is JSONObject -> {
                    destination.write('{'.code)
                    val keys = if (sortKeys) item.keys().asSequence().toList().sorted().iterator() else item.keys()
                    var first = true
                    while (keys.hasNext()) {
                        val key = keys.next()
                        if (depth == 0 && key in excludedRootKeys) continue
                        if (!first) destination.write(','.code)
                        if (prettyPrint) { destination.write('\n'.code); indent(depth + 1) }
                        quoted(key, destination)
                        destination.write(if (prettyPrint) ": " else ":")
                        value(item.get(key), depth + 1)
                        first = false
                    }
                    if (!first && prettyPrint) { destination.write('\n'.code); indent(depth) }
                    destination.write('}'.code)
                }
                is JSONArray -> {
                    destination.write('['.code)
                    for (index in 0 until item.length()) {
                        if (index > 0) destination.write(','.code)
                        if (prettyPrint) { destination.write('\n'.code); indent(depth + 1) }
                        value(item.get(index), depth + 1)
                    }
                    if (item.length() > 0 && prettyPrint) { destination.write('\n'.code); indent(depth) }
                    destination.write(']'.code)
                }
                is Number -> destination.write(JSONObject.numberToString(item))
                is Boolean -> destination.write(item.toString())
                else -> quoted(item.toString(), destination)
            }
        }
        value(root, 0)
    }
}
