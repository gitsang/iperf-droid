package top.c8g.iperf_droid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultParserTest {

    private val parser = ResultParser()

    /** English stand-in for the Android-backed Text provider (tests run on the JVM). */
    private val text = object : ResultParser.Text {
        override fun sender(mbitPerSec: Double) = "Sender %.2f Mbit/s".format(mbitPerSec)
        override fun receiver(mbitPerSec: Double) = "Receiver %.2f Mbit/s".format(mbitPerSec)
        override fun retransmits(count: Int) = "Retransmits $count"
        override fun jitter(ms: Double) = "Jitter %.2f ms".format(ms)
        override fun loss(percent: Double) = "Loss %.2f%%".format(percent)
        override fun failed(error: String) = "Failed: $error"
        override fun createStream() = "temp directory not writable"
        override fun connect() = "cannot connect"
        override fun listen() = "cannot listen"
        override fun cpuAffinity() = "invalid cpu affinity"
        override fun permission() = "permission denied"
    }

    private val tcpJson = """
        {
          "start": {"connected": [{"address": "192.168.5.200", "port": 5201}]},
          "end": {
            "sum_sent": {"bytes": 125829120, "bits_per_second": 1006632960.0, "retransmits": 3},
            "sum_received": {"bytes": 125829120, "bits_per_second": 998244352.0}
          }
        }
    """.trimIndent()

    @Test
    fun `parses a TCP result`() {
        val s = parser.parse(tcpJson)
        assertEquals(1006.63296, s.senderMbps!!, 0.001)
        assertEquals(998.244352, s.receiverMbps!!, 0.001)
        assertEquals(3, s.retransmits)
        assertTrue(s.hasData)
    }

    @Test
    fun `summary text contains sender receiver and retransmit figures`() {
        val summary = parser.format(parser.parse(tcpJson), text)
        assertTrue(summary.contains("Sender"))
        assertTrue(summary.contains("Receiver"))
        assertTrue(summary.contains("Retransmits"))
    }

    @Test
    fun `missing JSON output does not throw`() {
        val s = parser.parse("Connecting to host 192.168.5.200\niperf3: error - unable to connect")
        assertTrue(!s.hasData)
    }

    @Test
    fun `error field is extracted`() {
        val s = parser.parse("""{"error": "unable to connect to server: Connection refused"}""")
        assertEquals("unable to connect to server: Connection refused", s.error)
    }
}
