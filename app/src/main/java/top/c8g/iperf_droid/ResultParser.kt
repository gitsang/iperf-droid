package top.c8g.iperf_droid

import android.util.Log
import org.json.JSONObject

/**
 * Extracts the key metrics from iperf3's JSON output (-J) for a quick pass/fail call.
 */
class ResultParser {

    /**
     * Supplies the localized labels and messages used by [format] and [diagnose].
     * The UI layer implements this on top of Android string resources.
     */
    interface Text {
        fun sender(mbitPerSec: Double): String
        fun receiver(mbitPerSec: Double): String
        fun retransmits(count: Int): String
        fun jitter(ms: Double): String
        fun loss(percent: Double): String
        fun failed(error: String): String

        fun createStream(): String
        fun connect(): String
        fun listen(): String
        fun cpuAffinity(): String
        fun permission(): String
        fun controlSocket(): String
    }

    /** Known iperf3 runtime failures that have a dedicated hint. */
    enum class Diagnosis {
        CREATE_STREAM,
        CONNECT,
        LISTEN,
        CPU_AFFINITY,
        PERMISSION,
        CONTROL_SOCKET
    }

    data class Summary(
        val senderMbps: Double?,
        val receiverMbps: Double?,
        val retransmits: Int?,
        val jitterMs: Double?,
        val lossPercent: Double?,
        val error: String?
    ) {
        val hasData: Boolean
            get() = senderMbps != null || receiverMbps != null || lossPercent != null
    }

    /**
     * Finds the JSON fragment in the output text and parses it. iperf3 -J prints one JSON
     * object, optionally preceded by log lines such as "Connecting to host...".
     */
    fun parse(output: String): Summary {
        val start = output.indexOf('{')
        val end = output.lastIndexOf('}')
        if (start < 0 || end <= start) {
            return Summary(null, null, null, null, null, null)
        }
        val json = output.substring(start, end + 1)
        return try {
            val obj = JSONObject(json)
            val endObj = obj.optJSONObject("end") ?: obj
            val sent = endObj.optJSONObject("sum_sent")
            val recv = endObj.optJSONObject("sum_received")

            var jitter: Double? = null
            var loss: Double? = null
            val udp = endObj.optJSONObject("sum")
            if (udp != null && udp.has("lost_percent")) {
                loss = udp.optDouble("lost_percent", Double.NaN).takeIf { !it.isNaN() }
                jitter = udp.optDouble("jitter_ms", Double.NaN).takeIf { !it.isNaN() }
            }

            Summary(
                senderMbps = sent?.optDouble("bits_per_second", Double.NaN)?.div(1_000_000.0)
                    ?.takeIf { !it.isNaN() },
                receiverMbps = recv?.optDouble("bits_per_second", Double.NaN)?.div(1_000_000.0)
                    ?.takeIf { !it.isNaN() },
                retransmits = sent?.optInt("retransmits", -1)?.takeIf { it >= 0 },
                jitterMs = jitter,
                lossPercent = loss,
                error = extractError(obj)
            )
        } catch (e: Exception) {
            Log.w("ResultParser", "parse failed", e)
            Summary(null, null, null, null, null, e.message)
        }
    }

    /**
     * The shape of iperf3's error field varies across versions: 3.x usually emits a string
     * {"error": "..."}, while some builds emit an object {"error": {"string": "..."}}.
     */
    private fun extractError(obj: JSONObject): String? {
        if (!obj.has("error")) return null
        val raw = obj.opt("error") ?: return null
        return when (raw) {
            is String -> raw.takeIf { it.isNotEmpty() }
            is JSONObject -> raw.optString("string").takeIf { it.isNotEmpty() }
            else -> raw.toString().takeIf { it.isNotEmpty() }
        }
    }

    /**
     * Maps a known iperf3 runtime failure to a diagnosis.
     * Returns null when the output does not match any known problem.
     */
    fun diagnose(output: String): Diagnosis? {
        if (output.contains("unable to create a new stream")) {
            return Diagnosis.CREATE_STREAM
        }
        if (output.contains("unable to connect to server") ||
            output.contains("Connection refused")) {
            return Diagnosis.CONNECT
        }
        if (output.contains("unable to start listener")) {
            return Diagnosis.LISTEN
        }
        if (output.contains("Cannot find CPU")) {
            return Diagnosis.CPU_AFFINITY
        }
        if (output.contains("Permission denied")) {
            return Diagnosis.PERMISSION
        }
        // The client's TCP control connection died mid-test. Most often the server was not an
        // iperf3 server (or a much older/newer one), or something on the path reset the
        // connection. Checked after the connect/listen cases because those print more specific
        // text first.
        if (output.contains("control socket has closed unexpectedly") ||
            output.contains("unable to read from the control socket") ||
            output.contains("unable to write to the control socket")
        ) {
            return Diagnosis.CONTROL_SOCKET
        }
        return null
    }

    /** Renders the localized hint for a [Diagnosis]. */
    fun hint(d: Diagnosis, text: Text): String = when (d) {
        Diagnosis.CREATE_STREAM -> text.createStream()
        Diagnosis.CONNECT -> text.connect()
        Diagnosis.LISTEN -> text.listen()
        Diagnosis.CPU_AFFINITY -> text.cpuAffinity()
        Diagnosis.PERMISSION -> text.permission()
        Diagnosis.CONTROL_SOCKET -> text.controlSocket()
    }

    /** Builds a one-line human-readable summary. */
    fun format(s: Summary, text: Text): String {
        if (s.error != null) return text.failed(s.error)
        if (!s.hasData) return ""
        val sb = StringBuilder()
        s.senderMbps?.let { sb.append(text.sender(it)) }
        s.receiverMbps?.let {
            if (sb.isNotEmpty()) sb.append("  ")
            sb.append(text.receiver(it))
        }
        s.retransmits?.let {
            if (sb.isNotEmpty()) sb.append("  ")
            sb.append(text.retransmits(it))
        }
        s.jitterMs?.let {
            if (sb.isNotEmpty()) sb.append("  ")
            sb.append(text.jitter(it))
        }
        s.lossPercent?.let {
            if (sb.isNotEmpty()) sb.append("  ")
            sb.append(text.loss(it))
        }
        return sb.toString()
    }
}
