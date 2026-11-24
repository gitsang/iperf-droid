package com.example.iperfdroid

/**
 * Command-line argument splitting (pure Kotlin, no Android dependencies, so it is easy to unit
 * test).
 */
object ArgsParser {

    fun parse(args: String): List<String> {
        val result = mutableListOf<String>()
        val cur = StringBuilder()
        var quote: Char? = null
        for (ch in args) {
            when {
                quote != null && ch == quote -> quote = null
                quote == null && (ch == '"' || ch == '\'') -> quote = ch
                quote == null && ch.isWhitespace() -> {
                    if (cur.isNotEmpty()) {
                        result.add(cur.toString())
                        cur.clear()
                    }
                }
                else -> cur.append(ch)
            }
        }
        if (cur.isNotEmpty()) result.add(cur.toString())
        return result
    }
}
