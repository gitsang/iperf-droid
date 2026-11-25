package top.c8g.iperf_droid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Argument building for live output.
 */
class CommandBuilderTest {

    @Test
    fun `adds forceflush when it is missing`() {
        val result = CommandBuilder.withRealtimeOutput(listOf("-c", "192.168.5.200"))
        assertTrue(result.contains("--forceflush"))
        assertEquals(listOf("-c", "192.168.5.200", "--forceflush"), result)
    }

    @Test
    fun `does not append forceflush twice when the user already set it`() {
        val input = listOf("-c", "192.168.5.200", "--forceflush")
        val result = CommandBuilder.withRealtimeOutput(input)
        assertEquals(input, result)
        assertEquals(1, result.count { it == "--forceflush" })
    }

    @Test
    fun `works with empty arguments`() {
        val result = CommandBuilder.withRealtimeOutput(emptyList())
        assertEquals(listOf("--forceflush"), result)
    }

    @Test
    fun `keeps the original argument order`() {
        val result = CommandBuilder.withRealtimeOutput(listOf("-c", "1.2.3.4", "-t", "10", "-i", "1"))
        assertEquals(listOf("-c", "1.2.3.4", "-t", "10", "-i", "1", "--forceflush"), result)
    }

    @Test
    fun `hasForceFlush recognises the switch`() {
        assertTrue(CommandBuilder.hasForceFlush(listOf("--forceflush")))
        assertFalse(CommandBuilder.hasForceFlush(listOf("-c", "1.2.3.4")))
        // A prefix like "-F" must not be mistaken for the switch
        assertFalse(CommandBuilder.hasForceFlush(listOf("--forceflushx")))
    }

    @Test
    fun `display quotes arguments that contain spaces`() {
        val text = CommandBuilder.display(listOf("-c", "192.168.5.200", "-T", "my net"))
        assertEquals("-c 192.168.5.200 -T \"my net\"", text)
    }

    @Test
    fun `detects JSON mode`() {
        assertTrue(CommandBuilder.isJsonMode(listOf("-c", "1.2.3.4", "-J")))
        assertTrue(CommandBuilder.isJsonMode(listOf("-c", "1.2.3.4", "--json")))
        assertFalse(CommandBuilder.isJsonMode(listOf("-c", "1.2.3.4")))
    }

    @Test
    fun `probe arguments drop the JSON switch and keep the live switch`() {
        val probe = CommandBuilder.probeArgs(listOf("-c", "1.2.3.4", "-J", "-t", "10"))
        assertFalse(probe.contains("-J"))
        assertFalse(probe.contains("--json"))
        assertTrue(probe.contains("--forceflush"))
        assertTrue(probe.contains("1.2.3.4"))
    }
}
