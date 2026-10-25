package com.example.iperftv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArgsParserTest {

    @Test
    fun `解析常用客户端参数`() {
        assertEquals(
            listOf("-c", "192.168.5.200"),
            ArgsParser.parse("-c 192.168.5.200")
        )
    }

    @Test
    fun `解析多参数与额外空格`() {
        assertEquals(
            listOf("-c", "192.168.5.200", "-t", "10", "-J"),
            ArgsParser.parse("  -c   192.168.5.200  -t 10 -J  ")
        )
    }

    @Test
    fun `带引号的参数保留空格`() {
        assertEquals(
            listOf("-c", "192.168.5.200", "--comment", "line one test"),
            ArgsParser.parse("-c 192.168.5.200 --comment \"line one test\"")
        )
    }

    @Test
    fun `空字符串返回空列表`() {
        assertTrue(ArgsParser.parse("   ").isEmpty())
    }
}
