package com.github.adamantcheese.chan.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeRectFTest {

    /**
     * -1,-1      1,-1
     *       +--+
     *       |  |
     *       +--+
     *  -1,1      1,1
     * */

    @Test
    fun `test SafeRectF contains points`() {
        val safeRect = SafeRectF(-1f, -1f, 2f, 2f)

        assertTrue(safeRect.contains(0f, 0f))
        assertTrue(safeRect.contains(-1f, -1f))
        assertTrue(safeRect.contains(1f, 1f))
        assertTrue(safeRect.contains(1f, -1f))
        assertTrue(safeRect.contains(-1f, -1f))
    }

    @Test
    fun `test SafeRectF does not contain points`() {
        val safeRect = SafeRectF(-1f, -1f, 2f, 2f)

        assertFalse(safeRect.contains(0f, 5f))
        assertFalse(safeRect.contains(5f, 0f))
    }

}