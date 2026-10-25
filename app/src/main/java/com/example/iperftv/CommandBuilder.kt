package com.example.iperftv

/**
 * iperf3 命令行增强策略（纯 Kotlin，无 Android 依赖，便于单元测试）。
 *
 * 背景：iperf3 把结果写到 stdout。当 stdout 是管道时，libc 使用全缓冲（通常 4KB），
 * 而 iperf3 每个 interval 只输出几十字节，默认不 flush，
 * 于是输出会一直攒在缓冲区里，直到测试结束才一次性吐出来。
 *
 * iperf3 源码里对应的是这一行（src/iperf_api.c，iperf_print_intermediate 末尾）：
 *     if (test->logfile || test->forceflush)
 *         iflush(test);
 *
 * 也就是说 `--forceflush` 会让 iperf3 在每个 interval 结束时主动 fflush，
 * 输出立刻可见。已确认打包的 iperf3 3.9 二进制支持该选项。
 */
object CommandBuilder {

    /** 需要实时输出时自动追加的参数 */
    const val FORCE_FLUSH = "--forceflush"

    /** 已支持 forceflush 的版本以上（含 3.9 / 3.19） */
    private val FORCE_FLUSH_OPTIONAL = setOf("--forceflush")

    /**
     * 判断给定参数列表是否已经包含 forceflush。
     * 兼容写成 `--forceflush` 的形式（iperf3 的长选项没有单字母短选项）。
     */
    fun hasForceFlush(args: List<String>): Boolean {
        return args.any { it in FORCE_FLUSH_OPTIONAL }
    }

    /**
     * 为命令参数补上实时输出所需的开关。
     *
     * @param args 用户在配置页填写的原始参数
     * @return 最终传给 iperf3 的参数列表
     */
    fun withRealtimeOutput(args: List<String>): List<String> {
        if (hasForceFlush(args)) return args
        // --forceflush 是无值开关，放在末尾不会影响其他参数的解析
        return args + FORCE_FLUSH
    }

    /**
     * 把参数列表渲染成用于展示的命令行文本。
     * 参数中的空格会被引号包裹，避免展示时产生歧义。
     */
    fun display(args: List<String>): String {
        return args.joinToString(" ") { arg ->
            if (arg.isEmpty() || arg.any { it.isWhitespace() }) "\"$arg\"" else arg
        }
    }

    /** JSON 输出开关 */
    private val JSON_FLAGS = setOf("-J", "--json")

    /**
     * 判断命令是否使用 JSON 输出模式。
     *
     * JSON 模式下 iperf3 会把整个测试期间的所有结果累积进一个 JSON 树，
     * 直到测试结束才一次性打印（见 iperf_api.c 中 json_intervals 的累积逻辑），
     * 因此中途看不到任何输出。
     */
    fun isJsonMode(args: List<String>): Boolean {
        return args.any { it in JSON_FLAGS }
    }

    /**
     * 生成用于「实时速率探针」的参数：去掉 JSON 开关，其余保持不变。
     *
     * 探针是并行跑的第二个 iperf3 进程，只为在中途拿到速率数字，
     * 不影响主命令的输出与结果。
     */
    fun probeArgs(args: List<String>): List<String> {
        val stripped = args.filterNot { it in JSON_FLAGS }
        // 探针不需要自己再输出 JSON
        return withRealtimeOutput(stripped)
    }
}
