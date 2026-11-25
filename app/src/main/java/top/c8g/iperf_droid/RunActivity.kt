package top.c8g.iperf_droid

import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Run screen: shows iperf3 output live. "Stop" in the top-right corner interrupts the test and
 * "Back to home" in the top-right corner returns to the shortcut list.
 *
 * Rotation is handled in place (android:configChanges in the manifest) instead of letting the
 * activity be recreated. A running iperf3 process is owned by this activity, so recreating it would
 * destroy the process mid-test and the client would report
 * "control socket has closed unexpectedly".
 */
class RunActivity : AppCompatActivity(), IperfRunner.Callback {

    private lateinit var runner: IperfRunner
    private lateinit var tvLog: TextView
    private lateinit var scroll: ScrollView
    private lateinit var tvTitle: TextView
    private lateinit var tvCmd: TextView
    private lateinit var tvSummary: TextView
    private var job: IperfRunner.Job? = null
    private val sb = StringBuilder()
    private val parser = ResultParser()
    private val handler = Handler(Looper.getMainLooper())
    private var ticker: Runnable? = null

    /** Localized labels for the live interval status bar. */
    private val liveText = object : IntervalParser.Text {
        override fun rate(mbitPerSec: Double): String =
            this@RunActivity.getString(R.string.live_rate, mbitPerSec)

        override fun elapsed(seconds: Double): String =
            this@RunActivity.getString(R.string.live_elapsed, seconds)

        override fun jitter(ms: Double): String =
            this@RunActivity.getString(R.string.live_jitter, ms)

        override fun loss(percent: Double): String =
            this@RunActivity.getString(R.string.live_loss, percent)
    }

    /** Localized labels and hints for the JSON summary and the failure diagnosis. */
    private val summaryText = object : ResultParser.Text {
        override fun sender(mbitPerSec: Double): String =
            this@RunActivity.getString(R.string.summary_sender, mbitPerSec)

        override fun receiver(mbitPerSec: Double): String =
            this@RunActivity.getString(R.string.summary_receiver, mbitPerSec)

        override fun retransmits(count: Int): String =
            this@RunActivity.getString(R.string.summary_retransmits, count)

        override fun jitter(ms: Double): String =
            this@RunActivity.getString(R.string.summary_jitter, ms)

        override fun loss(percent: Double): String =
            this@RunActivity.getString(R.string.summary_loss, percent)

        override fun failed(error: String): String =
            this@RunActivity.getString(R.string.summary_failed, error)

        override fun createStream(): String =
            this@RunActivity.getString(R.string.diag_create_stream)

        override fun connect(): String =
            this@RunActivity.getString(R.string.diag_connect)

        override fun listen(): String =
            this@RunActivity.getString(R.string.diag_listen)

        override fun cpuAffinity(): String =
            this@RunActivity.getString(R.string.diag_cpu_affinity)

        override fun permission(): String =
            this@RunActivity.getString(R.string.diag_permission)

        override fun controlSocket(): String =
            this@RunActivity.getString(R.string.diag_control_socket)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_run)

        runner = IperfRunner(this)
        tvLog = findViewById(R.id.tvLog)
        scroll = findViewById(R.id.scrollLog)
        tvTitle = findViewById(R.id.tvRunTitle)
        tvCmd = findViewById(R.id.tvRunCmd)
        tvSummary = findViewById(R.id.tvSummary)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "iperf3"
        val args = intent.getStringExtra(EXTRA_ARGS) ?: ""
        tvTitle.text = title
        // Show the full command that is actually executed (including the automatically added
        // live output switch) so it can be reproduced on a production line
        val effective = CommandBuilder.withRealtimeOutput(ArgsParser.parse(args))
        tvCmd.text = "iperf3 ${CommandBuilder.display(effective)}"

        findViewById<ImageView>(R.id.btnStop).setOnClickListener {
            job?.cancel()
            append(getString(R.string.run_stopped))
        }
        findViewById<ImageView>(R.id.btnBackHome).setOnClickListener { finish() }

        append(getString(R.string.run_start, CommandBuilder.display(effective)))
        job = runner.run(args, this)

        // In JSON mode iperf3 prints nothing while the test runs, so a timer shows that the
        // process is still alive
        if (CommandBuilder.isJsonMode(effective)) {
            append(getString(R.string.run_json_hint))
            startJsonProgressTicker()
        }
    }

    override fun onDestroy() {
        stopTicker()
        job?.cancel()
        super.onDestroy()
    }

    /**
     * Called on rotation because the manifest declares configChanges. The layout is not re-inflated,
     * so re-apply what depends on the new width: the screen margins come from a w600dp resource that
     * changed bucket, and the log has to be scrolled back to the bottom.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    /** The Back key first stops a running test; a second press exits. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val j = job
            if (j != null && !j.cancelled) {
                j.cancel()
                append(getString(R.string.run_stopped_by_back))
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onLine(line: String) {
        // The worker thread hands every line to the UI thread; append() adds incrementally, so
        // the cost is low
        runOnUiThread {
            append(line)
            // Refresh the live rate bar at the top along the way
            IntervalParser.parse(line)?.let { showLiveStatus(it) }
        }
    }

    /** Live rate status bar: refreshed on every interval result. */
    private fun showLiveStatus(it: IntervalParser.Interval) {
        tvSummary.text = IntervalParser.format(it, liveText)
        tvSummary.visibility = android.view.View.VISIBLE
    }

    /**
     * In JSON mode (-J) iperf3 buffers every result and prints it when the test ends, so there is
     * no rate line in between. A timer then shows "test running" so the user knows the process is
     * still alive rather than stuck.
     */
    private fun startJsonProgressTicker() {
        val start = System.currentTimeMillis()
        ticker = object : Runnable {
            override fun run() {
                if (isFinishing || isDestroyed || job?.cancelled == true) return
                val sec = (System.currentTimeMillis() - start) / 1000
                tvSummary.text = getString(R.string.json_progress, sec)
                tvSummary.visibility = android.view.View.VISIBLE
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(ticker!!)
    }

    private fun stopTicker() {
        ticker?.let { handler.removeCallbacks(it) }
        ticker = null
    }

    override fun onFinished(exitCode: Int, elapsedMs: Long) {
        runOnUiThread {
            stopTicker()
            append(getString(R.string.run_finished, exitCode, elapsedMs))
            showSummary()
            // Offer a hint for known failure causes
            parser.diagnose(sb.toString())?.let {
                append(getString(R.string.run_hint, parser.hint(it, summaryText)))
            }
        }
    }

    override fun onError(message: String) {
        runOnUiThread {
            append(getString(R.string.run_error, message))
        }
    }

    /** If the command uses -J, try to extract the key metrics from the output. */
    private fun showSummary() {
        val text = parser.format(parser.parse(sb.toString()), summaryText)
        if (text.isNotEmpty()) {
            // Overwrite the live rate bar with the final summary when the test ends
            tvSummary.text = text
            tvSummary.visibility = android.view.View.VISIBLE
        }
    }

    /**
     * Appends one log line.
     *
     * It calls TextView.append directly instead of rebuilding the whole text every time: a test
     * that runs for minutes can produce thousands of lines, and rebuilding all of it makes the UI
     * stutter, which actually makes the "live" effect worse.
     */
    private fun append(line: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val text = "[$stamp] $line\n"
        sb.append(text)
        tvLog.append(text)
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARGS = "extra_args"
    }
}
