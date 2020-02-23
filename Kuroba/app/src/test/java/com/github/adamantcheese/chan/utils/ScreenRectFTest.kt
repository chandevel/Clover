package com.github.adamantcheese.chan.utils

import com.github.adamantcheese.chan.features.gesture_editor.ScreenRectF
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenRectFTest {

    /**
     * -1,-1      1,-1
     *       +--+
     *       |  |
     *       +--+
     *  -1,1      1,1
     * */

    @Test
    fun `test ScreenRectF contains points`() {
        val screenRectF = ScreenRectF(-1f, -1f, 2f, 2f, 2f, 2f)

        assertTrue(screenRectF.contains(0f, 0f))
        assertTrue(screenRectF.contains(-1f, -1f))
        assertTrue(screenRectF.contains(1f, 1f))
        assertTrue(screenRectF.contains(1f, -1f))
        assertTrue(screenRectF.contains(-1f, -1f))
    }

    @Test
    fun `test ScreenRectF does not contain points`() {
        val screenRectF = ScreenRectF(-1f, -1f, 2f, 2f, 2f, 2f)

        assertFalse(screenRectF.contains(0f, 5f))
        assertFalse(screenRectF.contains(5f, 0f))
    }

}