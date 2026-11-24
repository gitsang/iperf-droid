package com.example.iperfdroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArgsParserTest {

    @Test
    fun `parses common client arguments`() {
        assertEquals(
            listOf("-c", "192.168.5.200"),
            ArgsParser.parse("-c 192.168.5.200")
        )
    }

    @Test
    fun `parses multiple arguments and extra whitespace`() {
        assertEquals(
            listOf("-c", "192.168.5.200", "-t", "10", "-J"),
            ArgsParser.parse("  -c   192.168.5.200  -t 10 -J  ")
        )
    }

    @Test
    fun `quoted arguments keep their spaces`() {
        assertEquals(
            listOf("-c", "192.168.5.200", "--comment", "line one test"),
            ArgsParser.parse("-c 192.168.5.200 --comment \"line one test\"")
        )
    }

    @Test
    fun `blank input returns an empty list`() {
        assertTrue(ArgsParser.parse("   ").isEmpty())
    }
}
