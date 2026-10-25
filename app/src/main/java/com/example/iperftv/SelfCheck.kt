package com.example.iperftv

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.Executors

/**
 * 环境自检：验证内置 iperf3 二进制能否在当前设备上正常启动、能否创建临时缓冲区。
 * 产线装机后先跑一次自检，能提前发现 SELinux 拦截、ABI 不匹配、临时目录不可写等问题。
 */
class SelfCheck(private val context: Context) {

    data class Result(
        val passed: Boolean,
        val lines: List<String>,
        val summary: String
    )

    private val executor = Executors.newSingleThreadExecutor()

    fun run(callback: (Result) -> Unit) {
        executor.execute {
            val lines = mutableListOf<String>()
            try {
                val runner = IperfRunner(context)
                val bin = runner.ensureBinary()
                lines.add("ABI: ${android.os.Build.SUPPORTED_ABIS?.joinToString(",")}")
                lines.add("二进制: ${bin.absolutePath} (${bin.length()} bytes)")
                lines.add("可执行: ${bin.canExecute()}")
                lines.add("临时目录 TMPDIR: ${File(context.cacheDir, "tmp").absolutePath}")

                val process = ProcessBuilder(bin.absolutePath, "--version")
                    .redirectErrorStream(true)
                    .apply {
                        val tmp = File(context.cacheDir, "tmp").apply { mkdirs() }
                        environment()["TMPDIR"] = tmp.absolutePath
                    }
                    .start()

                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        lines.add(line!!)
                    }
                }
                val exit = process.waitFor()
                lines.add("退出码: $exit")

                val passed = exit == 0 && lines.any { it.contains("iperf3", ignoreCase = true) }
                val summary = if (passed) {
                    "自检通过，iperf3 可正常执行"
                } else {
                    "自检失败（退出码 $exit），请检查日志"
                }
                callback(Result(passed, lines, summary))
            } catch (e: Exception) {
                Log.e("SelfCheck", "self check failed", e)
                lines.add("异常: ${e.message ?: e.toString()}")
                callback(Result(false, lines, "自检异常：${e.message ?: e.toString()}"))
            }
        }
    }
}
