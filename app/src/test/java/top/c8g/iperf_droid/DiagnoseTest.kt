package top.c8g.iperf_droid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DiagnoseTest {

    private val parser = ResultParser()

    /** Minimal stand-in for the Android-backed text provider used by the real UI. */
    private val text = object : ResultParser.Text {
        override fun sender(mbitPerSec: Double) = "sender $mbitPerSec"
        override fun receiver(mbitPerSec: Double) = "receiver $mbitPerSec"
        override fun retransmits(count: Int) = "retransmits $count"
        override fun jitter(ms: Double) = "jitter $ms"
        override fun loss(percent: Double) = "loss $percent"
        override fun failed(error: String) = "failed: $error"
        override fun createStream() = "temp directory not writable"
        override fun connect() = "cannot connect"
        override fun listen() = "cannot listen"
        override fun cpuAffinity() = "invalid cpu affinity"
        override fun permission() = "permission denied"
        override fun controlSocket() = "control connection dropped"
    }

    @Test
    fun `detects a stream creation failure and offers a hint`() {
        val out = "iperf3: error - unable to create a new stream: No such file or directory"
        val diagnosis = parser.diagnose(out)
        assertNotNull(diagnosis)
        assertEquals(ResultParser.Diagnosis.CREATE_STREAM, diagnosis)
        assertEquals("temp directory not writable", parser.hint(diagnosis!!, text))
    }

    @Test
    fun `detects a connection failure`() {
        assertEquals(
            ResultParser.Diagnosis.CONNECT,
            parser.diagnose("iperf3: error - unable to connect to server: Connection refused")
        )
    }

    @Test
    fun `detects the control socket dropping mid-test`() {
        val out = "iperf3: error - control socket has closed unexpectedly"
        val diagnosis = parser.diagnose(out)
        assertNotNull(diagnosis)
        assertEquals(ResultParser.Diagnosis.CONTROL_SOCKET, diagnosis)
        assertEquals("control connection dropped", parser.hint(diagnosis!!, text))
    }

    @Test
    fun `detects a control socket read failure`() {
        assertEquals(
            ResultParser.Diagnosis.CONTROL_SOCKET,
            parser.diagnose("iperf3: error - unable to read from the control socket")
        )
    }

    @Test
    fun `normal output produces no hint`() {
        assertNull(parser.diagnose("[00:00:01] 0.00-10.00 sec 100 MBytes 84.2 Mbits/sec"))
    }
}
