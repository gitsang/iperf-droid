package top.c8g.iperf_droid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Parsing of iperf3 interval output lines.
 */
class IntervalParserTest {

    /** English-locale stand-in for the Android-backed text provider. */
    private val text = object : IntervalParser.Text {
        override fun rate(mbitPerSec: Double) =
            String.format(Locale.US, "Live %.1f Mbit/s", mbitPerSec)

        override fun elapsed(seconds: Double) =
            String.format(Locale.US, "%.0f s elapsed", seconds)

        override fun jitter(ms: Double) = String.format(Locale.US, "jitter %.2f ms", ms)

        override fun loss(percent: Double) = String.format(Locale.US, "loss %.1f%%", percent)
    }

    @Test
    fun `parses a plain TCP interval line`() {
        val line = "[  5]   1.00-2.00   sec   11.9 MBytes    99.9 Mbits/sec"
        val it = IntervalParser.parse(line)
        assertNotNull(it)
        assertEquals(1.0, it!!.fromSec, 0.001)
        assertEquals(2.0, it.toSec, 0.001)
        assertEquals(99.9, it.mbitsPerSec, 0.01)
        assertEquals(11.9, it.bytes, 0.01)
        assertEquals("M", it.unit)
    }

    @Test
    fun `parses GBytes and Gbits units`() {
        val line = "[  5]   0.00-1.00   sec  1.00 GBytes  8.59 Gbits/sec"
        val it = IntervalParser.parse(line)!!
        assertEquals(8590.0, it.mbitsPerSec, 0.1)
        assertEquals("G", it.unit)
    }

    @Test
    fun `parses KBytes and Kbits units`() {
        val line = "[  5]   0.00-1.00   sec  500.0 KBytes  4.10 Mbits/sec"
        val it = IntervalParser.parse(line)!!
        assertEquals(4.10, it.mbitsPerSec, 0.01)
        assertEquals("K", it.unit)
    }

    @Test
    fun `parses jitter and loss on a UDP line`() {
        val line = "[  5]   1.00-2.00   sec    1.25 MBytes   10.5 Mbits/sec  0.123 ms   0.456%   5/1097"
        val it = IntervalParser.parse(line)!!
        assertEquals(10.5, it.mbitsPerSec, 0.01)
        assertEquals(0.123, it.jitterMs!!, 0.0001)
        assertEquals(0.456, it.lossPercent!!, 0.0001)
    }

    @Test
    fun `returns null for lines that are not intervals`() {
        assertNull(IntervalParser.parse("Connecting to host 192.168.5.200"))
        assertNull(IntervalParser.parse(""))
        assertNull(IntervalParser.parse("[  5]  10.00 GBytes   8.59 Gbits/sec"))
    }

    @Test
    fun `formatted summary contains the rate and the duration`() {
        val line = "[  5]   1.00-2.00   sec   11.9 MBytes    99.9 Mbits/sec"
        val summary = IntervalParser.format(IntervalParser.parse(line)!!, text)
        assertTrue(summary.contains("99.9"))
        assertTrue(summary.contains("Mbit/s"))
        assertTrue(summary.contains("2 s elapsed"))
    }
}
