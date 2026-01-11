package com.tomclaw.bananalytics.internal

import com.google.gson.Gson
import com.tomclaw.bananalytics.api.BreadcrumbCategory
import com.tomclaw.bananalytics.api.Environment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class CrashHandlerTest {

    private lateinit var tempDir: java.io.File
    private lateinit var storage: EventStorage
    private lateinit var breadcrumbBuffer: BreadcrumbBuffer
    private lateinit var crashHandler: CrashHandler
    private val testSessionId = "test-session-123"
    private val testEnvironment = Environment(
        packageName = "com.test.app",
        appVersion = 1,
        appVersionName = "1.0.0",
        deviceId = "device-123",
        osVersion = 30,
        manufacturer = "Google",
        model = "Pixel 5",
        country = "US",
        language = "en"
    )

    @Before
    fun setUp() {
        tempDir = createTempDir("bananalytics_test")
        storage = EventStorage(tempDir, Gson())
        breadcrumbBuffer = BreadcrumbBuffer()
        crashHandler = CrashHandler(
            sessionId = testSessionId,
            storage = storage,
            breadcrumbBuffer = breadcrumbBuffer,
            environmentProvider = { testEnvironment }
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `uncaughtException saves crash to storage`() {
        val thread = Thread.currentThread()
        val exception = RuntimeException("Test exception")

        crashHandler.uncaughtException(thread, exception)

        val crashFiles = storage.listCrashFiles()
        assertEquals(1, crashFiles.size)
    }

    @Test
    fun `uncaughtException saves crash with correct session id`() {
        val thread = Thread.currentThread()
        val exception = RuntimeException("Test exception")

        crashHandler.uncaughtException(thread, exception)

        val crashFiles = storage.listCrashFiles()
        val crash = storage.readCrash(crashFiles[0])

        assertNotNull(crash)
        assertEquals(testSessionId, crash!!.sessionId)
    }

    @Test
    fun `uncaughtException saves crash with thread name`() {
        val testThread = Thread("TestThread")
        val exception = RuntimeException("Test exception")

        crashHandler.uncaughtException(testThread, exception)

        val crashFiles = storage.listCrashFiles()
        val crash = storage.readCrash(crashFiles[0])

        assertEquals("TestThread", crash!!.threadName)
    }

    @Test
    fun `uncaughtException saves crash as fatal`() {
        val thread = Thread.currentThread()
        val exception = RuntimeException("Test exception")

        crashHandler.uncaughtException(thread, exception)

        val crashFiles = storage.listCrashFiles()
        val crash = storage.readCrash(crashFiles[0])

        assertTrue(crash!!.isFatal)
    }

    @Test
    fun `uncaughtException saves stacktrace`() {
        val thread = Thread.currentThread()
        val exception = RuntimeException("Test exception message")

        crashHandler.uncaughtException(thread, exception)

        val crashFiles = storage.listCrashFiles()
        val crash = storage.readCrash(crashFiles[0])

        assertTrue(crash!!.stacktrace.contains("RuntimeException"))
        assertTrue(crash.stacktrace.contains("Test exception message"))
    }

    @Test
    fun `uncaughtException includes breadcrumbs in crash report`() {
        breadcrumbBuffer.add("User clicked button", BreadcrumbCategory.USER_ACTION)
        breadcrumbBuffer.add("Navigation to settings", BreadcrumbCategory.NAVIGATION)

        val thread = Thread.currentThread()
        val exception = RuntimeException("Test exception")

        crashHandler.uncaughtException(thread, exception)

        val crashFiles = storage.listCrashFiles()
        val crash = storage.readCrash(crashFiles[0])

        assertEquals(2, crash!!.breadcrumbs.size)
        assertEquals("User clicked button", crash.breadcrumbs[0].message)
        assertEquals("Navigation to settings", crash.breadcrumbs[1].message)
    }

    @Test
    fun `uncaughtException handles nested exceptions`() {
        val thread = Thread.currentThread()
        val cause = IllegalStateException("Root cause")
        val exception = RuntimeException("Wrapper exception", cause)

        crashHandler.uncaughtException(thread, exception)

        val crashFiles = storage.listCrashFiles()
        val crash = storage.readCrash(crashFiles[0])

        assertTrue(crash!!.stacktrace.contains("RuntimeException"))
        assertTrue(crash.stacktrace.contains("IllegalStateException"))
        assertTrue(crash.stacktrace.contains("Root cause"))
    }

    @Test
    fun `uncaughtException forwards to default handler`() {
        val mockDefaultHandler: Thread.UncaughtExceptionHandler = mock()
        Thread.setDefaultUncaughtExceptionHandler(mockDefaultHandler)

        // Re-create crash handler to pick up the mock default handler
        val handlerWithDefault = CrashHandler(
            sessionId = testSessionId,
            storage = storage,
            breadcrumbBuffer = breadcrumbBuffer,
            environmentProvider = { testEnvironment }
        )

        val thread = Thread.currentThread()
        val exception = RuntimeException("Test exception")

        handlerWithDefault.uncaughtException(thread, exception)

        verify(mockDefaultHandler).uncaughtException(thread, exception)
    }

    @Test
    fun `uncaughtException saves crash even when breadcrumbBuffer is empty`() {
        val thread = Thread.currentThread()
        val exception = RuntimeException("Test exception")

        crashHandler.uncaughtException(thread, exception)

        val crashFiles = storage.listCrashFiles()
        val crash = storage.readCrash(crashFiles[0])

        assertNotNull(crash)
        assertTrue(crash!!.breadcrumbs.isEmpty())
    }

    @Test
    fun `uncaughtException does not throw on storage failure`() {
        // Create a storage that will fail by using a read-only directory
        val readOnlyDir = createTempDir("readonly_test")
        readOnlyDir.setWritable(false)
        val failingStorage = EventStorage(readOnlyDir, Gson())

        val handlerWithFailingStorage = CrashHandler(
            sessionId = testSessionId,
            storage = failingStorage,
            breadcrumbBuffer = breadcrumbBuffer,
            environmentProvider = { testEnvironment }
        )

        val thread = Thread.currentThread()
        val exception = RuntimeException("Test exception")

        // Should not throw
        handlerWithFailingStorage.uncaughtException(thread, exception)

        // Cleanup
        readOnlyDir.setWritable(true)
        readOnlyDir.deleteRecursively()
    }

    @Test
    fun `crash timestamp is recorded correctly`() {
        val beforeCrash = System.currentTimeMillis()

        val thread = Thread.currentThread()
        val exception = RuntimeException("Test exception")
        crashHandler.uncaughtException(thread, exception)

        val afterCrash = System.currentTimeMillis()

        val crashFiles = storage.listCrashFiles()
        val crash = storage.readCrash(crashFiles[0])

        assertTrue(crash!!.timestamp >= beforeCrash)
        assertTrue(crash.timestamp <= afterCrash)
    }
}
