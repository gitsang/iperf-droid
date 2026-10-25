package com.example.iperftv

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * 从 iperf3 的 JSON 输出（-J）中提取关键指标，便于产线快速判定。
 */
class ResultParser {

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
     * 从输出文本中找出 JSON 片段并解析。iperf3 -J 会输出一个 JSON 对象，
     * 前面可能夹杂 "Connecting to host..." 之类的日志。
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
     * iperf3 的 error 字段在不同版本里形态不同：
     * 3.x 常见为字符串 {"error": "..."}，也有版本为对象 {"error": {"string": "..."}}。
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
     * 针对常见 iperf3 运行期错误给出中文排查提示。
     * 返回 null 表示没有匹配到已知问题。
     */
    fun diagnose(output: String): String? {
        if (output.contains("unable to create a new stream")) {
            return "iperf3 无法创建数据缓冲区（临时目录不可写）。\n" +
                    "应用已自动设置 TMPDIR，若仍出现请执行「配置 → 环境自检」查看详情。"
        }
        if (output.contains("unable to connect to server") ||
            output.contains("Connection refused")) {
            return "无法连接到服务器，请确认目标 IP 上的 iperf3 服务已启动（iperf3 -s）。"
        }
        if (output.contains("unable to start listener")) {
            return "无法监听端口，可能是端口被占用或缺少权限。"
        }
        if (output.contains("Cannot find CPU")) {
            return "指定的 CPU 亲和性参数在当前设备上无效。"
        }
        if (output.contains("Permission denied")) {
            return "权限被拒绝，可能是系统安全策略限制了应用执行二进制。"
        }
        return null
    }

    /** 生成一行人类可读的摘要。 */
    fun format(s: Summary): String {
        if (s.error != null) return "失败: ${s.error}"
        if (!s.hasData) return ""
        val sb = StringBuilder()
        s.senderMbps?.let { sb.append(String.format("发送 %.2f Mbit/s", it)) }
        s.receiverMbps?.let {
            if (sb.isNotEmpty()) sb.append("  ")
            sb.append(String.format("接收 %.2f Mbit/s", it))
        }
        s.retransmits?.let {
            if (sb.isNotEmpty()) sb.append("  ")
            sb.append("重传 $it")
        }
        s.jitterMs?.let {
            if (sb.isNotEmpty()) sb.append("  ")
            sb.append(String.format("抖动 %.2f ms", it))
        }
        s.lossPercent?.let {
            if (sb.isNotEmpty()) sb.append("  ")
            sb.append(String.format("丢包 %.2f%%", it))
        }
        return sb.toString()
    }
}
