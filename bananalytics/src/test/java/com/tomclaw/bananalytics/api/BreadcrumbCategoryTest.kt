package com.tomclaw.bananalytics.api

import org.junit.Assert.assertEquals
import org.junit.Test

class BreadcrumbCategoryTest {

    @Test
    fun `NAVIGATION toApiValue returns lowercase`() {
        val result = BreadcrumbCategory.NAVIGATION.toApiValue()

        assertEquals("navigation", result)
    }

    @Test
    fun `USER_ACTION toApiValue returns lowercase with underscore`() {
        val result = BreadcrumbCategory.USER_ACTION.toApiValue()

        assertEquals("user_action", result)
    }

    @Test
    fun `NETWORK toApiValue returns lowercase`() {
        val result = BreadcrumbCategory.NETWORK.toApiValue()

        assertEquals("network", result)
    }

    @Test
    fun `ERROR toApiValue returns lowercase`() {
        val result = BreadcrumbCategory.ERROR.toApiValue()

        assertEquals("error", result)
    }

    @Test
    fun `CUSTOM toApiValue returns lowercase`() {
        val result = BreadcrumbCategory.CUSTOM.toApiValue()

        assertEquals("custom", result)
    }

    @Test
    fun `all categories have unique API values`() {
        val apiValues = BreadcrumbCategory.entries.map { it.toApiValue() }
        val uniqueValues = apiValues.toSet()

        assertEquals(apiValues.size, uniqueValues.size)
    }

    @Test
    fun `all categories have non-empty API values`() {
        BreadcrumbCategory.entries.forEach { category ->
            val apiValue = category.toApiValue()
            assert(apiValue.isNotEmpty()) { "Category $category has empty API value" }
        }
    }
}
