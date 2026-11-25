package top.c8g.iperf_droid

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.Executors

/**
 * Runs the bundled iperf3 executable as a child process. The whole run happens on a thread pool
 * and live output is pushed to the UI through callbacks.
 */
class IperfRunner(private val context: Context) {

    interface Callback {
        fun onLine(line: String)
        fun onFinished(exitCode: Int, elapsedMs: Long)
        fun onError(message: String)
    }

    private val executor = Executors.newSingleThreadExecutor()

    /**
     * Returns the bundled iperf3 executable for the current device ABI.
     *
     * The binary ships as a native library (`jniLibs/<abi>/libiperf3.so`) rather than as an asset,
     * because Android 10+ forbids executing files from the app's own data directory even after
     * chmod +x (the W^X restriction). Files under the native library directory are unpacked by the
     * installer and mounted executable, so they can be exec'd directly.
     *
     * The `.so` suffix is only there to make the packaging tooling treat the file as a library; the
     * content is an ordinary ELF executable, which the loader runs happily.
     */
    fun ensureBinary(): File {
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val candidate = File(nativeDir, LIB_NAME)
        if (candidate.exists() && candidate.length() > 0L) return candidate
        throw IllegalStateException(
            "Bundled iperf3 not found for this ABI: " +
                    "${Build.SUPPORTED_ABIS?.joinToString(",")} (looked in $nativeDir)"
        )
    }

    /** Human-readable path of the bundled binary, for the self-check screen. */
    fun binaryPath(): String = File(context.applicationInfo.nativeLibraryDir, LIB_NAME).absolutePath

    /**
     * Runs one test. [args] looks like "-c 192.168.5.200 -t 10".
     * The returned [Job] can be used to cancel a running test.
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
     * When setting up a data stream iperf3 calls mkstemp() to create a buffer file in a temp
     * directory taken from the TMPDIR / TEMP / TMP environment variables, falling back to a
     * hard-coded "/tmp".
     *
     * Android has no /tmp directory, so mkstemp() returns ENOENT and iperf3 fails with
     * "unable to create a new stream: No such file or directory".
     * TMPDIR therefore has to point at a directory the app can write to.
     */
    private fun childEnv(): Map<String, String> {
        val tmpDir = File(context.cacheDir, "tmp").apply { mkdirs() }
        return tempEnv(tmpDir)
    }

    /** Splits a command-line string into arguments, honouring quotes around arguments with spaces. */
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
        private const val LIB_NAME = "libiperf3.so"

        /**
         * When setting up a data stream iperf3 mkstemp()s a buffer file in a directory taken from
         * TMPDIR / TEMP / TMP. Android has no /tmp, so mkstemp returns ENOENT and iperf3 reports
         * "unable to create a new stream". Set all three variables to cover the different
         * implementation branches.
         */
        fun tempEnv(tmpDir: File): Map<String, String> = mapOf(
            "TMPDIR" to tmpDir.absolutePath,
            "TMP" to tmpDir.absolutePath,
            "TEMP" to tmpDir.absolutePath
        )
    }
}
