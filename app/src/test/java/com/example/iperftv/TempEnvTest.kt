package com.example.iperftv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 回归测试：iperf3 建立数据流时会 mkstemp() 一个缓冲区文件，
 * 目录来自 TMPDIR/TEMP/TMP，取不到则硬编码 /tmp —— Android 上不存在该目录，
 * 会报 "unable to create a new stream: No such file or directory"。
 * 因此子进程必须显式带上这三个环境变量。
 */
class TempEnvTest {

    @Test
    fun `子进程环境变量包含TMPDIR`() {
        val dir = File("/data/user/0/com.example.iperftv/cache/tmp")
        val env = IperfRunner.tempEnv(dir)
        assertEquals(dir.absolutePath, env["TMPDIR"])
    }

    @Test
    fun `同时设置TMP与TEMP以覆盖不同实现分支`() {
        val dir = File("/data/user/0/com.example.iperftv/cache/tmp")
        val env = IperfRunner.tempEnv(dir)
        assertEquals(dir.absolutePath, env["TMP"])
        assertEquals(dir.absolutePath, env["TEMP"])
    }

    @Test
    fun `TMPDIR指向应用私有目录而非根目录下的tmp`() {
        val dir = File("/data/user/0/com.example.iperftv/cache/tmp")
        val tmpdir = IperfRunner.tempEnv(dir)["TMPDIR"]!!
        // 路径必须落在应用私有目录内，且不是会被误判的根 /tmp
        assertTrue(tmpdir.endsWith("tmp"))
        assertTrue(tmpdir.contains("cache"))
        // 不能是 Android 上不存在的根 /tmp
        assertTrue(tmpdir != "/tmp")
    }
}
