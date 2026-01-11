package com.tomclaw.bananalytics.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Collections

class EventFileComparatorTest {

    private val comparator = EventFileComparator()

    // === getFileNameTime tests ===

    @Test
    fun `getFileNameTime extracts time from valid event file name`() {
        val fileName = "1234567890123-abc123.event"

        val time = getFileNameTime(fileName)

        assertEquals(1234567890123L, time)
    }

    @Test
    fun `getFileNameTime extracts time from crash file name`() {
        val fileName = "1234567890123-fatal.crash"

        val time = getFileNameTime(fileName)

        assertEquals(1234567890123L, time)
    }

    @Test
    fun `getFileNameTime returns 0 for invalid file name without dash`() {
        val fileName = "noDashHere.event"

        val time = getFileNameTime(fileName)

        assertEquals(0L, time)
    }

    @Test
    fun `getFileNameTime returns 0 for file name with non-numeric prefix`() {
        val fileName = "notANumber-abc123.event"

        val time = getFileNameTime(fileName)

        assertEquals(0L, time)
    }

    @Test
    fun `getFileNameTime handles empty file name`() {
        val fileName = ""

        val time = getFileNameTime(fileName)

        assertEquals(0L, time)
    }

    @Test
    fun `getFileNameTime handles file name starting with dash`() {
        val fileName = "-12345.event"

        val time = getFileNameTime(fileName)

        assertEquals(0L, time)
    }

    @Test
    fun `getFileNameTime handles very large timestamp`() {
        val fileName = "9999999999999-hash.event"

        val time = getFileNameTime(fileName)

        assertEquals(9999999999999L, time)
    }

    // === EventFileComparator tests ===

    @Test
    fun `compare returns negative when first file is earlier`() {
        val file1 = File("/path/1000-abc.event")
        val file2 = File("/path/2000-def.event")

        val result = comparator.compare(file1, file2)

        assertTrue(result < 0)
    }

    @Test
    fun `compare returns positive when first file is later`() {
        val file1 = File("/path/3000-abc.event")
        val file2 = File("/path/2000-def.event")

        val result = comparator.compare(file1, file2)

        assertTrue(result > 0)
    }

    @Test
    fun `compare returns zero when files have same timestamp`() {
        val file1 = File("/path/1000-abc.event")
        val file2 = File("/path/1000-def.event")

        val result = comparator.compare(file1, file2)

        assertEquals(0, result)
    }

    @Test
    fun `sorting files orders by timestamp ascending`() {
        val files = mutableListOf(
            File("/path/3000-c.event"),
            File("/path/1000-a.event"),
            File("/path/2000-b.event"),
            File("/path/5000-e.event"),
            File("/path/4000-d.event")
        )

        Collections.sort(files, comparator)

        assertEquals("1000-a.event", files[0].name)
        assertEquals("2000-b.event", files[1].name)
        assertEquals("3000-c.event", files[2].name)
        assertEquals("4000-d.event", files[3].name)
        assertEquals("5000-e.event", files[4].name)
    }

    @Test
    fun `sorting handles mixed valid and invalid file names`() {
        val files = mutableListOf(
            File("/path/2000-b.event"),
            File("/path/invalid.event"),
            File("/path/1000-a.event")
        )

        Collections.sort(files, comparator)

        // Invalid files (time=0) should come first
        assertEquals("invalid.event", files[0].name)
        assertEquals("1000-a.event", files[1].name)
        assertEquals("2000-b.event", files[2].name)
    }

    @Test
    fun `sorting handles empty list`() {
        val files = mutableListOf<File>()

        Collections.sort(files, comparator)

        assertTrue(files.isEmpty())
    }

    @Test
    fun `sorting handles single file`() {
        val files = mutableListOf(File("/path/1000-a.event"))

        Collections.sort(files, comparator)

        assertEquals(1, files.size)
        assertEquals("1000-a.event", files[0].name)
    }

    @Test
    fun `sorting with real timestamps maintains chronological order`() {
        val now = System.currentTimeMillis()
        val files = mutableListOf(
            File("/path/${now + 2000}-c.event"),
            File("/path/${now}-a.event"),
            File("/path/${now + 1000}-b.event")
        )

        Collections.sort(files, comparator)

        assertTrue(files[0].name.endsWith("-a.event"))
        assertTrue(files[1].name.endsWith("-b.event"))
        assertTrue(files[2].name.endsWith("-c.event"))
    }
}
