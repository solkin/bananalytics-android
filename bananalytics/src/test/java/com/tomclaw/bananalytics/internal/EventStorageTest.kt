package com.tomclaw.bananalytics.internal

import com.google.gson.Gson
import com.tomclaw.bananalytics.api.AnalyticsEvent
import com.tomclaw.bananalytics.api.Breadcrumb
import com.tomclaw.bananalytics.api.CrashReport
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class EventStorageTest {

    private lateinit var tempDir: File
    private lateinit var storage: EventStorage
    private val gson = Gson()

    @Before
    fun setUp() {
        tempDir = createTempDir("bananalytics_test")
        storage = EventStorage(tempDir, gson)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // === Event Tests ===

    @Test
    fun `writeEvent creates file in events directory`() {
        val event = createTestEvent("test_event")

        val file = storage.writeEvent(event)

        assertTrue(file.exists())
        assertTrue(file.absolutePath.contains("bananalytics/events"))
    }

    @Test
    fun `readEvent returns correct event data`() {
        val originalEvent = createTestEvent(
            name = "purchase",
            tags = mapOf("item" to "sword", "category" to "weapons"),
            fields = mapOf("price" to 99.99, "quantity" to 2.0)
        )
        val file = storage.writeEvent(originalEvent)

        val readEvent = storage.readEvent(file)

        assertNotNull(readEvent)
        assertEquals(originalEvent.sessionId, readEvent!!.sessionId)
        assertEquals(originalEvent.name, readEvent.name)
        assertEquals(originalEvent.tags, readEvent.tags)
        assertEquals(originalEvent.fields, readEvent.fields)
        assertEquals(originalEvent.time, readEvent.time)
    }

    @Test
    fun `listEventFiles returns all event files`() {
        storage.writeEvent(createTestEvent("event1"))
        storage.writeEvent(createTestEvent("event2"))
        storage.writeEvent(createTestEvent("event3"))

        val files = storage.listEventFiles()

        assertEquals(3, files.size)
    }

    @Test
    fun `listEventFiles returns empty list when no events`() {
        val files = storage.listEventFiles()

        assertTrue(files.isEmpty())
    }

    @Test
    fun `readEvent returns null for corrupted file`() {
        val eventsDir = File(tempDir, "bananalytics/events").apply { mkdirs() }
        val corruptedFile = File(eventsDir, "corrupted.event")
        corruptedFile.writeText("not valid json")

        val result = storage.readEvent(corruptedFile)

        assertNull(result)
    }

    @Test
    fun `readEvent returns null for non-existent file`() {
        val nonExistentFile = File(tempDir, "bananalytics/events/does_not_exist.event")

        val result = storage.readEvent(nonExistentFile)

        assertNull(result)
    }

    // === Crash Tests ===

    @Test
    fun `writeCrashSync creates file in crashes directory`() {
        val crash = createTestCrash(isFatal = true)

        storage.writeCrashSync(crash)

        val files = storage.listCrashFiles()
        assertEquals(1, files.size)
        assertTrue(files[0].absolutePath.contains("bananalytics/crashes"))
    }

    @Test
    fun `readCrash returns correct crash data`() {
        val originalCrash = createTestCrash(
            isFatal = true,
            context = mapOf("screen" to "main", "userId" to "123"),
            breadcrumbs = listOf(
                Breadcrumb(1000L, "Clicked button", "user_action"),
                Breadcrumb(2000L, "API call started", "network")
            )
        )
        storage.writeCrashSync(originalCrash)

        val files = storage.listCrashFiles()
        val readCrash = storage.readCrash(files[0])

        assertNotNull(readCrash)
        assertEquals(originalCrash.sessionId, readCrash!!.sessionId)
        assertEquals(originalCrash.timestamp, readCrash.timestamp)
        assertEquals(originalCrash.threadName, readCrash.threadName)
        assertEquals(originalCrash.stacktrace, readCrash.stacktrace)
        assertEquals(originalCrash.isFatal, readCrash.isFatal)
        assertEquals(originalCrash.context, readCrash.context)
        assertEquals(originalCrash.breadcrumbs.size, readCrash.breadcrumbs.size)
    }

    @Test
    fun `listCrashFiles returns empty list when no crashes`() {
        val files = storage.listCrashFiles()

        assertTrue(files.isEmpty())
    }

    @Test
    fun `crash file name contains fatal marker for fatal crash`() {
        val fatalCrash = createTestCrash(isFatal = true)
        storage.writeCrashSync(fatalCrash)

        val files = storage.listCrashFiles()

        assertTrue(files[0].name.contains("fatal"))
    }

    @Test
    fun `crash file name contains exception marker for non-fatal crash`() {
        val nonFatalCrash = createTestCrash(isFatal = false)
        storage.writeCrashSync(nonFatalCrash)

        val files = storage.listCrashFiles()

        assertTrue(files[0].name.contains("exception"))
    }

    @Test
    fun `readCrash returns null for corrupted file`() {
        val crashesDir = File(tempDir, "bananalytics/crashes").apply { mkdirs() }
        val corruptedFile = File(crashesDir, "corrupted.crash")
        corruptedFile.writeText("invalid json content")

        val result = storage.readCrash(corruptedFile)

        assertNull(result)
    }

    // === Delete Tests ===

    @Test
    fun `deleteFiles removes specified files`() {
        val file1 = storage.writeEvent(createTestEvent("event1"))
        val file2 = storage.writeEvent(createTestEvent("event2"))
        val file3 = storage.writeEvent(createTestEvent("event3"))

        storage.deleteFiles(listOf(file1, file3))

        val remainingFiles = storage.listEventFiles()
        assertEquals(1, remainingFiles.size)
        assertEquals(file2.name, remainingFiles[0].name)
    }

    @Test
    fun `deleteFiles handles empty list gracefully`() {
        storage.writeEvent(createTestEvent("event1"))

        storage.deleteFiles(emptyList())

        val files = storage.listEventFiles()
        assertEquals(1, files.size)
    }

    // === File Naming Tests ===

    @Test
    fun `event file name contains timestamp`() {
        val event = createTestEvent("test_event", time = 1234567890123L)
        val file = storage.writeEvent(event)

        assertTrue(file.name.startsWith("1234567890123"))
    }

    @Test
    fun `event file has correct extension`() {
        val event = createTestEvent("test_event")
        val file = storage.writeEvent(event)

        assertTrue(file.name.endsWith(".event"))
    }

    @Test
    fun `crash file has correct extension`() {
        val crash = createTestCrash()
        storage.writeCrashSync(crash)

        val files = storage.listCrashFiles()
        assertTrue(files[0].name.endsWith(".crash"))
    }

    // === Helper Methods ===

    private fun createTestEvent(
        name: String,
        sessionId: String = "test-session-id",
        tags: Map<String, String> = emptyMap(),
        fields: Map<String, Double> = emptyMap(),
        time: Long = System.currentTimeMillis()
    ) = AnalyticsEvent(
        sessionId = sessionId,
        name = name,
        tags = tags,
        fields = fields,
        time = time
    )

    private fun createTestCrash(
        isFatal: Boolean = true,
        sessionId: String = "test-session-id",
        timestamp: Long = System.currentTimeMillis(),
        threadName: String = "main",
        stacktrace: String = "java.lang.NullPointerException\n\tat Test.test(Test.kt:1)",
        context: Map<String, String> = emptyMap(),
        breadcrumbs: List<Breadcrumb> = emptyList()
    ) = CrashReport(
        sessionId = sessionId,
        timestamp = timestamp,
        threadName = threadName,
        stacktrace = stacktrace,
        isFatal = isFatal,
        context = context,
        breadcrumbs = breadcrumbs
    )
}
