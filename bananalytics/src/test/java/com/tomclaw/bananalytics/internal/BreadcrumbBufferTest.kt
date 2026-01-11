package com.tomclaw.bananalytics.internal

import com.tomclaw.bananalytics.api.BreadcrumbCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BreadcrumbBufferTest {

    private lateinit var buffer: BreadcrumbBuffer

    @Before
    fun setUp() {
        buffer = BreadcrumbBuffer(maxSize = 5)
    }

    @Test
    fun `snapshot returns empty list when buffer is empty`() {
        val result = buffer.snapshot()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `add single breadcrumb and retrieve via snapshot`() {
        buffer.add("Test message", BreadcrumbCategory.CUSTOM)

        val result = buffer.snapshot()

        assertEquals(1, result.size)
        assertEquals("Test message", result[0].message)
        assertEquals("custom", result[0].category)
    }

    @Test
    fun `add multiple breadcrumbs preserves order`() {
        buffer.add("First", BreadcrumbCategory.NAVIGATION)
        buffer.add("Second", BreadcrumbCategory.USER_ACTION)
        buffer.add("Third", BreadcrumbCategory.NETWORK)

        val result = buffer.snapshot()

        assertEquals(3, result.size)
        assertEquals("First", result[0].message)
        assertEquals("Second", result[1].message)
        assertEquals("Third", result[2].message)
    }

    @Test
    fun `buffer respects max size and removes oldest items`() {
        buffer.add("One", BreadcrumbCategory.CUSTOM)
        buffer.add("Two", BreadcrumbCategory.CUSTOM)
        buffer.add("Three", BreadcrumbCategory.CUSTOM)
        buffer.add("Four", BreadcrumbCategory.CUSTOM)
        buffer.add("Five", BreadcrumbCategory.CUSTOM)
        buffer.add("Six", BreadcrumbCategory.CUSTOM) // Should push out "One"

        val result = buffer.snapshot()

        assertEquals(5, result.size)
        assertEquals("Two", result[0].message) // "One" was removed
        assertEquals("Six", result[4].message)
    }

    @Test
    fun `clear removes all breadcrumbs`() {
        buffer.add("First", BreadcrumbCategory.CUSTOM)
        buffer.add("Second", BreadcrumbCategory.CUSTOM)

        buffer.clear()

        val result = buffer.snapshot()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `snapshot returns copy of data`() {
        buffer.add("Original", BreadcrumbCategory.CUSTOM)

        val snapshot1 = buffer.snapshot()
        buffer.add("New", BreadcrumbCategory.CUSTOM)
        val snapshot2 = buffer.snapshot()

        assertEquals(1, snapshot1.size)
        assertEquals(2, snapshot2.size)
    }

    @Test
    fun `breadcrumb categories are correctly converted`() {
        buffer.add("nav", BreadcrumbCategory.NAVIGATION)
        buffer.add("action", BreadcrumbCategory.USER_ACTION)
        buffer.add("net", BreadcrumbCategory.NETWORK)
        buffer.add("err", BreadcrumbCategory.ERROR)
        buffer.add("custom", BreadcrumbCategory.CUSTOM)

        val result = buffer.snapshot()

        assertEquals("navigation", result[0].category)
        assertEquals("user_action", result[1].category)
        assertEquals("network", result[2].category)
        assertEquals("error", result[3].category)
        assertEquals("custom", result[4].category)
    }

    @Test
    fun `breadcrumb has timestamp`() {
        val beforeAdd = System.currentTimeMillis()
        buffer.add("Test", BreadcrumbCategory.CUSTOM)
        val afterAdd = System.currentTimeMillis()

        val result = buffer.snapshot()

        assertTrue(result[0].timestamp >= beforeAdd)
        assertTrue(result[0].timestamp <= afterAdd)
    }

    @Test
    fun `default max size is 50`() {
        val defaultBuffer = BreadcrumbBuffer()

        repeat(55) { i ->
            defaultBuffer.add("Message $i", BreadcrumbCategory.CUSTOM)
        }

        val result = defaultBuffer.snapshot()
        assertEquals(50, result.size)
        assertEquals("Message 5", result[0].message) // First 5 were removed
    }
}
