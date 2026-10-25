package com.example.iperftv

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnoseTest {

    private val parser = ResultParser()

    @Test
    fun `识别创建流失败并给出提示`() {
        val out = "iperf3: error - unable to create a new stream: No such file or directory"
        val hint = parser.diagnose(out)
        assertNotNull(hint)
        assertTrue(hint!!.contains("临时目录"))
    }

    @Test
    fun `识别连接失败`() {
        assertNotNull(parser.diagnose("iperf3: error - unable to connect to server: Connection refused"))
    }

    @Test
    fun `正常输出不产生提示`() {
        assertNull(parser.diagnose("[00:00:01] 0.00-10.00 sec 100 MBytes 84.2 Mbits/sec"))
    }
}
