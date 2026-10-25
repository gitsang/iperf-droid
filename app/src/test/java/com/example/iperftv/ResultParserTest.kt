package com.example.iperftv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultParserTest {

    private val parser = ResultParser()

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
    fun `解析TCP结果`() {
        val s = parser.parse(tcpJson)
        assertEquals(1006.63296, s.senderMbps!!, 0.001)
        assertEquals(998.244352, s.receiverMbps!!, 0.001)
        assertEquals(3, s.retransmits)
        assertTrue(s.hasData)
    }

    @Test
    fun `摘要文本包含收发速率`() {
        val text = parser.format(parser.parse(tcpJson))
        assertTrue(text.contains("发送"))
        assertTrue(text.contains("接收"))
        assertTrue(text.contains("重传"))
    }

    @Test
    fun `无JSON输出时不报错`() {
        val s = parser.parse("Connecting to host 192.168.5.200\niperf3: error - unable to connect")
        assertTrue(!s.hasData)
    }

    @Test
    fun `错误字段被提取`() {
        val s = parser.parse("""{"error": "unable to connect to server: Connection refused"}""")
        assertEquals("unable to connect to server: Connection refused", s.error)
    }
}
