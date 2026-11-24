package com.example.iperfdroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regression test: when iperf3 sets up a data stream it mkstemp()s a buffer file,
 * taking the directory from TMPDIR/TEMP/TMP and falling back to a hardcoded /tmp.
 * That directory does not exist on Android, so it fails with
 * "unable to create a new stream: No such file or directory".
 * The child process therefore has to be given all three variables explicitly.
 */
class TempEnvTest {

    @Test
    fun `child process environment contains TMPDIR`() {
        val dir = File("/data/user/0/com.example.iperfdroid/cache/tmp")
        val env = IperfRunner.tempEnv(dir)
        assertEquals(dir.absolutePath, env["TMPDIR"])
    }

    @Test
    fun `sets both TMP and TEMP to cover the different implementation branches`() {
        val dir = File("/data/user/0/com.example.iperfdroid/cache/tmp")
        val env = IperfRunner.tempEnv(dir)
        assertEquals(dir.absolutePath, env["TMP"])
        assertEquals(dir.absolutePath, env["TEMP"])
    }

    @Test
    fun `TMPDIR points at the app-private directory rather than the root tmp`() {
        val dir = File("/data/user/0/com.example.iperfdroid/cache/tmp")
        val tmpdir = IperfRunner.tempEnv(dir)["TMPDIR"]!!
        // The path must fall inside the app-private directory, not the misleading root /tmp
        assertTrue(tmpdir.endsWith("tmp"))
        assertTrue(tmpdir.contains("cache"))
        // It must not be the root /tmp, which does not exist on Android
        assertTrue(tmpdir != "/tmp")
    }
}
