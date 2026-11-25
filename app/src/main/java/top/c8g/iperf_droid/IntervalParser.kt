package top.c8g.iperf_droid

/**
 * Parses the live rate from an iperf3 text output line.
 *
 * iperf3 prints one line per interval, for example:
 *   [  5]   1.00-2.00   sec  11.9 MBytes   99.9 Mbits/sec
 *   [  5]   2.00-3.00   sec  11.9 MBytes   99.9 Mbits/sec
 * UDP mode also carries jitter and loss:
 *   [  5]   1.00-2.00   sec  1.00 MBytes   8.39 Mbits/sec  0.000 ms    0.000%   0/0
 *
 * Pure Kotlin so it can be unit tested without an Android runtime.
 */
object IntervalParser {

    /**
     * Supplies the localized labels used by [format].
     * The UI layer implements this on top of Android string resources.
     */
    interface Text {
        fun rate(mbitPerSec: Double): String
        fun elapsed(seconds: Double): String
        fun jitter(ms: Double): String
        fun loss(percent: Double): String
    }

    data class Interval(
        val fromSec: Double,
        val toSec: Double,
        val mbitsPerSec: Double,
        val bytes: Double,
        val unit: String,
        val jitterMs: Double?,
        val lossPercent: Double?
    ) {
        /** Seconds covered by this interval. */
        val elapsed: Double get() = toSec - fromSec
    }

    /**
     * Parses one iperf3 interval report line, or returns null when the line does not match.
     */
    fun parse(line: String): Interval? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null

        // Match the time range: 1.00-2.00 or 0.00-1.00
        val rangeRegex = Regex("""(\d+\.\d+)\s*-\s*(\d+\.\d+)\s+sec""")
        val range = rangeRegex.find(trimmed) ?: return null
        val from = range.groupValues[1].toDoubleOrNull() ?: return null
        val to = range.groupValues[2].toDoubleOrNull() ?: return null

        // Match the transferred volume: 11.9 MBytes / 1.00 GBytes / 500 KBytes
        val bytesRegex = Regex("""(\d+(?:\.\d+)?)\s+([KMGT]?)Bytes""")
        val bytesMatch = bytesRegex.find(trimmed)
        val bytes = bytesMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val unit = bytesMatch?.groupValues?.get(2) ?: ""

        // Match the rate: 99.9 Mbits/sec
        val rateRegex = Regex("""(\d+(?:\.\d+)?)\s+([KMGT]?)bits/sec""")
        val rateMatch = rateRegex.find(trimmed) ?: return null
        val rateValue = rateMatch.groupValues[1].toDoubleOrNull() ?: return null
        val rateUnit = rateMatch.groupValues[2]

        // Normalize everything to Mbit/s
        val mbits = when (rateUnit) {
            "K" -> rateValue / 1000.0
            "G" -> rateValue * 1000.0
            "T" -> rateValue * 1000000.0
            else -> rateValue
        }

        // Jitter and loss, present in UDP mode only
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
     * Builds the compact one-line rate summary shown in the run screen's live status bar.
     * All words come from [text] so the output follows the app language.
     */
    fun format(it: Interval, text: Text): String {
        val sb = StringBuilder()
        sb.append(text.rate(it.mbitsPerSec))
        sb.append("  ·  ").append(text.elapsed(it.toSec))
        it.jitterMs?.let { sb.append("  ·  ").append(text.jitter(it)) }
        it.lossPercent?.let { sb.append("  ·  ").append(text.loss(it)) }
        return sb.toString()
    }
}
