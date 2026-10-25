package com.example.iperftv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 实时输出相关的参数构建逻辑。
 */
class CommandBuilderTest {

    @Test
    fun `未指定forceflush时自动补上`() {
        val result = CommandBuilder.withRealtimeOutput(listOf("-c", "192.168.5.200"))
        assertTrue(result.contains("--forceflush"))
        assertEquals(listOf("-c", "192.168.5.200", "--forceflush"), result)
    }

    @Test
    fun `用户已指定forceflush时不重复追加`() {
        val input = listOf("-c", "192.168.5.200", "--forceflush")
        val result = CommandBuilder.withRealtimeOutput(input)
        assertEquals(input, result)
        assertEquals(1, result.count { it == "--forceflush" })
    }

    @Test
    fun `空参数也能正常工作`() {
        val result = CommandBuilder.withRealtimeOutput(emptyList())
        assertEquals(listOf("--forceflush"), result)
    }

    @Test
    fun `原有参数顺序不变`() {
        val result = CommandBuilder.withRealtimeOutput(listOf("-c", "1.2.3.4", "-t", "10", "-i", "1"))
        assertEquals(listOf("-c", "1.2.3.4", "-t", "10", "-i", "1", "--forceflush"), result)
    }

    @Test
    fun `hasForceFlush能识别`() {
        assertTrue(CommandBuilder.hasForceFlush(listOf("--forceflush")))
        assertFalse(CommandBuilder.hasForceFlush(listOf("-c", "1.2.3.4")))
        // 不能被 -F 之类的前缀误判
        assertFalse(CommandBuilder.hasForceFlush(listOf("--forceflushx")))
    }

    @Test
    fun `展示命令对含空格的参数加引号`() {
        val text = CommandBuilder.display(listOf("-c", "192.168.5.200", "-T", "my net"))
        assertEquals("-c 192.168.5.200 -T \"my net\"", text)
    }

    @Test
    fun `识别JSON模式`() {
        assertTrue(CommandBuilder.isJsonMode(listOf("-c", "1.2.3.4", "-J")))
        assertTrue(CommandBuilder.isJsonMode(listOf("-c", "1.2.3.4", "--json")))
        assertFalse(CommandBuilder.isJsonMode(listOf("-c", "1.2.3.4")))
    }

    @Test
    fun `探针参数去掉JSON开关并保留实时开关`() {
        val probe = CommandBuilder.probeArgs(listOf("-c", "1.2.3.4", "-J", "-t", "10"))
        assertFalse(probe.contains("-J"))
        assertFalse(probe.contains("--json"))
        assertTrue(probe.contains("--forceflush"))
        assertTrue(probe.contains("1.2.3.4"))
    }
}
