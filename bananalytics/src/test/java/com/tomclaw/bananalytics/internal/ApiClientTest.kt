package com.tomclaw.bananalytics.internal

import com.google.gson.Gson
import com.tomclaw.bananalytics.BananalyticsConfig
import com.tomclaw.bananalytics.api.AnalyticsEvent
import com.tomclaw.bananalytics.api.CrashReport
import com.tomclaw.bananalytics.api.Environment
import com.tomclaw.bananalytics.api.SubmitCrashesRequest
import com.tomclaw.bananalytics.api.SubmitEventsRequest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiClient: ApiClient
    private val gson = Gson()
    private val testApiKey = "bnn_test_api_key"

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
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val config = BananalyticsConfig(
            baseUrl = mockWebServer.url("/").toString(),
            apiKey = testApiKey
        )
        apiClient = ApiClient(config, gson)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // === sendEvents tests ===

    @Test
    fun `sendEvents returns true on successful response`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val request = createEventsRequest()
        val result = apiClient.sendEvents(request)

        assertTrue(result)
    }

    @Test
    fun `sendEvents returns false on server error`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val request = createEventsRequest()
        val result = apiClient.sendEvents(request)

        assertFalse(result)
    }

    @Test
    fun `sendEvents returns false on client error`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(400))

        val request = createEventsRequest()
        val result = apiClient.sendEvents(request)

        assertFalse(result)
    }

    @Test
    fun `sendEvents sends correct endpoint`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val request = createEventsRequest()
        apiClient.sendEvents(request)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/api/v1/events/submit", recordedRequest.path)
    }

    @Test
    fun `sendEvents sends POST request`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val request = createEventsRequest()
        apiClient.sendEvents(request)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
    }

    @Test
    fun `sendEvents includes API key header`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val request = createEventsRequest()
        apiClient.sendEvents(request)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals(testApiKey, recordedRequest.getHeader("X-API-Key"))
    }

    @Test
    fun `sendEvents includes Content-Type header`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val request = createEventsRequest()
        apiClient.sendEvents(request)

        val recordedRequest = mockWebServer.takeRequest()
        val contentType = recordedRequest.getHeader("Content-Type")
        assertTrue(contentType != null && contentType.startsWith("application/json"))
    }

    @Test
    fun `sendEvents sends correct JSON body`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val event = AnalyticsEvent(
            sessionId = "session-123",
            name = "test_event",
            tags = mapOf("key" to "value"),
            fields = mapOf("count" to 42.0),
            time = 1234567890123L
        )
        val request = SubmitEventsRequest(
            sessionId = "session-123",
            environment = testEnvironment,
            events = listOf(event)
        )

        apiClient.sendEvents(request)

        val recordedRequest = mockWebServer.takeRequest()
        val body = recordedRequest.body.readUtf8()

        assertTrue(body.contains("\"session_id\":\"session-123\""))
        assertTrue(body.contains("\"name\":\"test_event\""))
        assertTrue(body.contains("\"key\":\"value\""))
        assertTrue(body.contains("\"count\":42.0"))
    }

    // === sendCrashes tests ===

    @Test
    fun `sendCrashes returns true on successful response`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val request = createCrashesRequest()
        val result = apiClient.sendCrashes(request)

        assertTrue(result)
    }

    @Test
    fun `sendCrashes returns false on server error`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val request = createCrashesRequest()
        val result = apiClient.sendCrashes(request)

        assertFalse(result)
    }

    @Test
    fun `sendCrashes sends correct endpoint`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val request = createCrashesRequest()
        apiClient.sendCrashes(request)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/api/v1/crashes/submit", recordedRequest.path)
    }

    @Test
    fun `sendCrashes sends POST request`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val request = createCrashesRequest()
        apiClient.sendCrashes(request)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
    }

    @Test
    fun `sendCrashes includes API key header`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val request = createCrashesRequest()
        apiClient.sendCrashes(request)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals(testApiKey, recordedRequest.getHeader("X-API-Key"))
    }

    @Test
    fun `sendCrashes sends correct JSON body`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val crash = CrashReport(
            sessionId = "session-456",
            timestamp = 1234567890123L,
            threadName = "main",
            stacktrace = "java.lang.Exception",
            isFatal = true,
            context = mapOf("screen" to "home"),
            breadcrumbs = emptyList()
        )
        val request = SubmitCrashesRequest(
            sessionId = "session-456",
            environment = testEnvironment,
            crashes = listOf(crash)
        )

        apiClient.sendCrashes(request)

        val recordedRequest = mockWebServer.takeRequest()
        val body = recordedRequest.body.readUtf8()

        assertTrue(body.contains("\"session_id\":\"session-456\""))
        assertTrue(body.contains("\"thread\":\"main\""))
        assertTrue(body.contains("\"is_fatal\":true"))
        assertTrue(body.contains("\"screen\":\"home\""))
    }

    // === URL handling tests ===

    @Test
    fun `apiClient handles baseUrl with trailing slash`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val configWithSlash = BananalyticsConfig(
            baseUrl = mockWebServer.url("/").toString() + "/",
            apiKey = testApiKey
        )
        val clientWithSlash = ApiClient(configWithSlash, gson)

        clientWithSlash.sendEvents(createEventsRequest())

        val recordedRequest = mockWebServer.takeRequest()
        // Should not have double slash
        assertFalse(recordedRequest.path!!.contains("//api"))
    }

    @Test
    fun `apiClient handles baseUrl without trailing slash`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val configWithoutSlash = BananalyticsConfig(
            baseUrl = mockWebServer.url("/").toString().trimEnd('/'),
            apiKey = testApiKey
        )
        val clientWithoutSlash = ApiClient(configWithoutSlash, gson)

        clientWithoutSlash.sendEvents(createEventsRequest())

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/api/v1/events/submit", recordedRequest.path)
    }

    // === Helper methods ===

    private fun createEventsRequest() = SubmitEventsRequest(
        sessionId = "test-session",
        environment = testEnvironment,
        events = listOf(
            AnalyticsEvent(
                sessionId = "test-session",
                name = "test_event",
                tags = emptyMap(),
                fields = emptyMap(),
                time = System.currentTimeMillis()
            )
        )
    )

    private fun createCrashesRequest() = SubmitCrashesRequest(
        sessionId = "test-session",
        environment = testEnvironment,
        crashes = listOf(
            CrashReport(
                sessionId = "test-session",
                timestamp = System.currentTimeMillis(),
                threadName = "main",
                stacktrace = "java.lang.Exception: Test",
                isFatal = true,
                context = emptyMap(),
                breadcrumbs = emptyList()
            )
        )
    )
}
