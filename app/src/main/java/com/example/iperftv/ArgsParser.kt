package com.example.iperftv

/**
 * 命令行参数拆分逻辑（纯 Kotlin，无 Android 依赖，便于单元测试）。
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
