package com.example.iperftv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * iperf3 间隔输出行的解析。
 */
class IntervalParserTest {

    @Test
    fun `解析普通TCP间隔行`() {
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
    fun `解析GBytes与Gbits单位`() {
        val line = "[  5]   0.00-1.00   sec  1.00 GBytes  8.59 Gbits/sec"
        val it = IntervalParser.parse(line)!!
        assertEquals(8590.0, it.mbitsPerSec, 0.1)
        assertEquals("G", it.unit)
    }

    @Test
    fun `解析KBytes与Kbits单位`() {
        val line = "[  5]   0.00-1.00   sec  500.0 KBytes  4.10 Mbits/sec"
        val it = IntervalParser.parse(line)!!
        assertEquals(4.10, it.mbitsPerSec, 0.01)
        assertEquals("K", it.unit)
    }

    @Test
    fun `解析UDP行的抖动与丢包`() {
        val line = "[  5]   1.00-2.00   sec    1.25 MBytes   10.5 Mbits/sec  0.123 ms   0.456%   5/1097"
        val it = IntervalParser.parse(line)!!
        assertEquals(10.5, it.mbitsPerSec, 0.01)
        assertEquals(0.123, it.jitterMs!!, 0.0001)
        assertEquals(0.456, it.lossPercent!!, 0.0001)
    }

    @Test
    fun `非间隔行返回null`() {
        assertNull(IntervalParser.parse("Connecting to host 192.168.5.200"))
        assertNull(IntervalParser.parse(""))
        assertNull(IntervalParser.parse("[  5]  10.00 GBytes   8.59 Gbits/sec"))
    }

    @Test
    fun `格式化摘要包含速率与时长`() {
        val line = "[  5]   1.00-2.00   sec   11.9 MBytes    99.9 Mbits/sec"
        val text = IntervalParser.format(IntervalParser.parse(line)!!)
        assertEquals(true, text.contains("99.9"))
        assertEquals(true, text.contains("Mbit/s"))
        assertEquals(true, text.contains("2 秒"))
    }
}
