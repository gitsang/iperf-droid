package com.example.iperfdroid

/**
 * iperf3 command-line enhancements (pure Kotlin, no Android dependencies, so it is easy to
 * unit test).
 *
 * Background: iperf3 writes its results to stdout. When stdout is a pipe, libc uses full
 * buffering (usually 4KB), while iperf3 only emits a few dozen bytes per interval and does not
 * flush by default. The output therefore accumulates in the buffer and is dumped all at once
 * when the test ends.
 *
 * The corresponding line in the iperf3 source (src/iperf_api.c, end of
 * iperf_print_intermediate) is:
 *     if (test->logfile || test->forceflush)
 *         iflush(test);
 *
 * In other words `--forceflush` makes iperf3 fflush at the end of every interval so the output
 * is visible immediately. The bundled iperf3 3.9 binary is confirmed to support this option.
 */
object CommandBuilder {

    /** Argument appended automatically when live output is required. */
    const val FORCE_FLUSH = "--forceflush"

    /** Versions that already support forceflush (including 3.9 / 3.19). */
    private val FORCE_FLUSH_OPTIONAL = setOf("--forceflush")

    /**
     * Reports whether the given argument list already contains forceflush.
     * Only the `--forceflush` spelling counts (iperf3 long options have no single-letter form).
     */
    fun hasForceFlush(args: List<String>): Boolean {
        return args.any { it in FORCE_FLUSH_OPTIONAL }
    }

    /**
     * Adds the switches needed for live output to the command arguments.
     *
     * @param args raw arguments entered by the user on the config screen
     * @return the final argument list passed to iperf3
     */
    fun withRealtimeOutput(args: List<String>): List<String> {
        if (hasForceFlush(args)) return args
        // --forceflush is a valueless switch, so appending it cannot affect parsing of the rest
        return args + FORCE_FLUSH
    }

    /**
     * Renders an argument list as a command line for display.
     * Arguments containing spaces are quoted to keep the display unambiguous.
     */
    fun display(args: List<String>): String {
        return args.joinToString(" ") { arg ->
            if (arg.isEmpty() || arg.any { it.isWhitespace() }) "\"$arg\"" else arg
        }
    }

    /** JSON output switches. */
    private val JSON_FLAGS = setOf("-J", "--json")

    /**
     * Reports whether the command uses JSON output mode.
     *
     * In JSON mode iperf3 accumulates every result of the run into a single JSON tree and only
     * prints it when the test finishes (see the json_intervals accumulation logic in
     * iperf_api.c), so no output is visible in the meantime.
     */
    fun isJsonMode(args: List<String>): Boolean {
        return args.any { it in JSON_FLAGS }
    }

    /**
     * Builds the arguments for the "live rate probe": the JSON switch is dropped and everything
     * else is kept unchanged.
     *
     * The probe is a second iperf3 process running in parallel purely to obtain rate numbers
     * while the test is running; it does not affect the main command's output or results.
     */
    fun probeArgs(args: List<String>): List<String> {
        val stripped = args.filterNot { it in JSON_FLAGS }
        // The probe does not need to emit JSON itself
        return withRealtimeOutput(stripped)
    }
}
