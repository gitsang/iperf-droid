package com.example.iperfdroid

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.Executors

/**
 * Self-check: verifies that the bundled iperf3 binary can start on the current device and can
 * create its temporary buffer. Running it once after provisioning surfaces SELinux denials,
 * ABI mismatches and read-only temp directories early.
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
                lines.add(context.getString(R.string.selfcheck_binary, bin.absolutePath, bin.length()))
                lines.add(context.getString(R.string.selfcheck_executable, bin.canExecute().toString()))
                lines.add(
                    context.getString(
                        R.string.selfcheck_tmpdir,
                        File(context.cacheDir, "tmp").absolutePath
                    )
                )

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
                lines.add(context.getString(R.string.selfcheck_exit_code, exit))

                val passed = exit == 0 && lines.any { it.contains("iperf3", ignoreCase = true) }
                val summary = if (passed) {
                    context.getString(R.string.selfcheck_passed)
                } else {
                    context.getString(R.string.selfcheck_failed, exit)
                }
                callback(Result(passed, lines, summary))
            } catch (e: Exception) {
                Log.e("SelfCheck", "self check failed", e)
                lines.add(context.getString(R.string.selfcheck_exception_line, e.message ?: e.toString()))
                callback(
                    Result(
                        false,
                        lines,
                        context.getString(R.string.selfcheck_exception, e.message ?: e.toString())
                    )
                )
            }
        }
    }
}
