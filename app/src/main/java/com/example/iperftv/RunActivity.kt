package com.example.iperftv

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 运行页：实时显示 iperf3 输出，右上角「停止」可中断，右上角「返回首页」回到快捷列表。
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
        // 展示实际执行的完整命令（含自动补上的实时输出开关），便于产线复现
        val effective = CommandBuilder.withRealtimeOutput(ArgsParser.parse(args))
        tvCmd.text = "iperf3 ${CommandBuilder.display(effective)}"

        findViewById<TextView>(R.id.btnStop).setOnClickListener {
            job?.cancel()
            append("--- 已停止 ---")
        }
        findViewById<TextView>(R.id.btnBackHome).setOnClickListener { finish() }

        append("开始执行: iperf3 ${CommandBuilder.display(effective)}")
        job = runner.run(args, this)

        // JSON 模式下 iperf3 中途不输出任何内容，用计时器表明进程仍在运行
        if (CommandBuilder.isJsonMode(effective)) {
            append("提示: JSON 模式结果将在测试结束时一次性输出，期间下方显示运行进度。")
            startJsonProgressTicker()
        }
    }

    override fun onDestroy() {
        stopTicker()
        job?.cancel()
        super.onDestroy()
    }

    /** 返回键先尝试停止正在运行的测试，再按一次才退出。 */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val j = job
            if (j != null && !j.cancelled) {
                j.cancel()
                append("--- 已停止（再按返回退出）---")
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onLine(line: String) {
        // 工作线程收到一行就投递到 UI 线程；append 内部做增量追加，开销很低
        runOnUiThread {
            append(line)
            // 顺带刷新顶部的实时速率条
            IntervalParser.parse(line)?.let { showLiveStatus(it) }
        }
    }

    /** 实时速率状态条：每收到一个 interval 结果就刷新一次。 */
    private fun showLiveStatus(it: IntervalParser.Interval) {
        tvSummary.text = IntervalParser.format(it)
        tvSummary.visibility = android.view.View.VISIBLE
    }

    /**
     * JSON 模式（-J）下 iperf3 会把全部结果累积到测试结束才一次性输出，
     * 中途不会有任何速率行。这时用计时器显示"测试进行中"，
     * 让用户知道进程还活着（而不是卡死）。
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
            append("--- 结束 (exit=$exitCode, 耗时 ${elapsedMs} ms) ---")
            showSummary()
            // 针对已知失败原因给出中文提示
            parser.diagnose(sb.toString())?.let {
                append("--- 提示: $it ---")
            }
        }
    }

    override fun onError(message: String) {
        runOnUiThread {
            append("--- 错误: $message ---")
        }
    }

    /** 若命令带 -J，尝试从输出中提取关键指标。 */
    private fun showSummary() {
        val text = parser.format(parser.parse(sb.toString()))
        if (text.isNotEmpty()) {
            // 结束时用最终汇总覆盖实时速率条
            tvSummary.text = text
            tvSummary.visibility = android.view.View.VISIBLE
        }
    }

    /**
     * 追加一行日志。
     *
     * 直接用 TextView.append 而不是每次重建整段文本：
     * 测试跑几分钟时输出可达上千行，全量重建会导致 UI 卡顿，
     * 反而让"实时"效果变差。
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
