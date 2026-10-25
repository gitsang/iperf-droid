package com.example.iperftv

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.Executors

/**
 * 负责把 assets 里的 iperf3 可执行文件释放到应用私有目录，并以子进程方式运行。
 * 整个执行过程放在线程池里，通过回调把实时输出推给 UI。
 */
class IperfRunner(private val context: Context) {

    interface Callback {
        fun onLine(line: String)
        fun onFinished(exitCode: Int, elapsedMs: Long)
        fun onError(message: String)
    }

    private val executor = Executors.newSingleThreadExecutor()

    /**
     * 释放 assets 中与当前设备 ABI 匹配的 iperf3 到私有目录（首次运行需要，之后复用）。
     * 找不到匹配 ABI 时回退到 arm64-v8a。
     */
    fun ensureBinary(): File {
        val dir = File(context.filesDir, "bin").apply { mkdirs() }
        val target = File(dir, "iperf3")
        val abi = pickAbi()
        val assetPath = "iperf3/$abi/iperf3"
        val marker = File(dir, ".abi")
        val needExtract = !target.exists() || target.length() == 0L ||
                !marker.exists() || marker.readText() != assetPath
        if (needExtract) {
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target.setExecutable(true, false)
            target.setReadable(true, false)
            marker.writeText(assetPath)
        }
        return target
    }

    private fun pickAbi(): String {
        val supported = Build.SUPPORTED_ABIS ?: emptyArray()
        val preferred = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        for (p in preferred) {
            if (supported.contains(p)) return p
        }
        return "arm64-v8a"
    }

    /**
     * 运行一次测试。args 形如 "-c 192.168.5.200 -t 10"。
     * 返回的 Job 可用于取消正在进行的测试。
     */
    fun run(args: String, callback: Callback): Job {
        val job = Job()
        executor.execute {
            val start = System.currentTimeMillis()
            try {
                val bin = ensureBinary()
                val cmd = mutableListOf(bin.absolutePath)
                cmd.addAll(CommandBuilder.withRealtimeOutput(parseArgs(args)))
                Log.i(TAG, "exec: ${cmd.joinToString(" ")}")

                val process = ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .apply { environment().putAll(childEnv()) }
                    .start()

                job.process = process

                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (job.cancelled) break
                        callback.onLine(line!!)
                    }
                }
                val exit = process.waitFor()
                callback.onFinished(exit, System.currentTimeMillis() - start)
            } catch (e: Exception) {
                if (!job.cancelled) {
                    Log.e(TAG, "run failed", e)
                    callback.onError(e.message ?: e.toString())
                }
            }
        }
        return job
    }

    /**
     * iperf3 在建立数据流时会调用 mkstemp() 在临时目录创建一个缓冲区文件，
     * 路径取自环境变量 TMPDIR / TEMP / TMP，取不到则硬编码回落到 "/tmp"。
     *
     * Android 没有 /tmp 目录，mkstemp 会返回 ENOENT，iperf3 随即报
     * "unable to create a new stream: No such file or directory"。
     * 因此必须把 TMPDIR 指向应用自己的可写目录。
     */
    private fun childEnv(): Map<String, String> {
        val tmpDir = File(context.cacheDir, "tmp").apply { mkdirs() }
        return tempEnv(tmpDir)
    }

    /** 把命令行字符串拆成参数数组，支持用引号包裹含空格的参数。 */
    fun parseArgs(args: String): List<String> = ArgsParser.parse(args)

    class Job {
        @Volatile var cancelled: Boolean = false
        @Volatile var process: Process? = null

        fun cancel() {
            cancelled = true
            process?.destroy()
        }
    }

    companion object {
        private const val TAG = "IperfRunner"

        /**
         * iperf3 建立数据流时会 mkstemp() 一个缓冲区文件，目录取自 TMPDIR / TEMP / TMP。
         * Android 无 /tmp，mkstemp 返回 ENOENT → "unable to create a new stream"。
         * 三个变量都设上，覆盖不同分支实现。
         */
        fun tempEnv(tmpDir: File): Map<String, String> = mapOf(
            "TMPDIR" to tmpDir.absolutePath,
            "TMP" to tmpDir.absolutePath,
            "TEMP" to tmpDir.absolutePath
        )
    }
}
