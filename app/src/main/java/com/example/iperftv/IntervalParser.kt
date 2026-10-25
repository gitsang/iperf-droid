package com.example.iperftv

/**
 * 从 iperf3 的文本输出行中解析实时速率。
 *
 * iperf3 每个 interval 输出一行，形如：
 *   [  5]   1.00-2.00   sec  11.9 MBytes   99.9 Mbits/sec
 *   [  5]   2.00-3.00   sec  11.9 MBytes   99.9 Mbits/sec
 * UDP 模式还会带抖动和丢包：
 *   [  5]   1.00-2.00   sec  1.00 MBytes   8.39 Mbits/sec  0.000 ms    0.000%   0/0
 *
 * 纯 Kotlin 实现，便于单元测试。
 */
object IntervalParser {

    data class Interval(
        val fromSec: Double,
        val toSec: Double,
        val mbitsPerSec: Double,
        val bytes: Double,
        val unit: String,
        val jitterMs: Double?,
        val lossPercent: Double?
    ) {
        /** 已进行的秒数 */
        val elapsed: Double get() = toSec - fromSec
    }

    /**
     * 解析一行 iperf3 间隔报告，失败返回 null。
     */
    fun parse(line: String): Interval? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null

        // 匹配时间区间：1.00-2.00 或 0.00-1.00
        val rangeRegex = Regex("""(\d+\.\d+)\s*-\s*(\d+\.\d+)\s+sec""")
        val range = rangeRegex.find(trimmed) ?: return null
        val from = range.groupValues[1].toDoubleOrNull() ?: return null
        val to = range.groupValues[2].toDoubleOrNull() ?: return null

        // 匹配传输量：11.9 MBytes / 1.00 GBytes / 500 KBytes
        val bytesRegex = Regex("""(\d+(?:\.\d+)?)\s+([KMGT]?)Bytes""")
        val bytesMatch = bytesRegex.find(trimmed)
        val bytes = bytesMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val unit = bytesMatch?.groupValues?.get(2) ?: ""

        // 匹配速率：99.9 Mbits/sec
        val rateRegex = Regex("""(\d+(?:\.\d+)?)\s+([KMGT]?)bits/sec""")
        val rateMatch = rateRegex.find(trimmed) ?: return null
        val rateValue = rateMatch.groupValues[1].toDoubleOrNull() ?: return null
        val rateUnit = rateMatch.groupValues[2]

        // 统一换算成 Mbit/s
        val mbits = when (rateUnit) {
            "K" -> rateValue / 1000.0
            "G" -> rateValue * 1000.0
            "T" -> rateValue * 1000000.0
            else -> rateValue
        }

        // UDP 才有的抖动与丢包
        val jitterRegex = Regex("""(\d+(?:\.\d+)?)\s+ms""")
        val jitter = jitterRegex.find(trimmed)?.groupValues?.get(1)?.toDoubleOrNull()

        val lossRegex = Regex("""(\d+(?:\.\d+)?)\s*%""")
        val loss = lossRegex.find(trimmed)?.groupValues?.get(1)?.toDoubleOrNull()

        return Interval(
            fromSec = from,
            toSec = to,
            mbitsPerSec = mbits,
            bytes = bytes,
            unit = unit,
            jitterMs = jitter,
            lossPercent = loss
        )
    }

    /**
     * 生成一行精简的中文速率摘要，用于运行页顶部的实时状态条。
     */
    fun format(it: Interval): String {
        val sb = StringBuilder()
        sb.append(String.format("实时 %.1f Mbit/s", it.mbitsPerSec))
        sb.append(String.format("  ·  已测 %.0f 秒", it.toSec))
        it.jitterMs?.let { sb.append(String.format("  ·  抖动 %.2f ms", it)) }
        it.lossPercent?.let { sb.append(String.format("  ·  丢包 %.1f%%", it)) }
        return sb.toString()
    }
}
