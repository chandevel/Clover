package com.github.adamantcheese.chan.core

import org.junit.Assert.*
import org.junit.Test

class ExtensionsKtTest {

    @Test
    fun testConvertIntToCharArray() {
        val int = 0x11223344
        val charArray = int.toCharArray()

        assertEquals(0x11, charArray[0].toInt())
        assertEquals(0x22, charArray[1].toInt())
        assertEquals(0x33, charArray[2].toInt())
        assertEquals(0x44, charArray[3].toInt())
    }

    @Test
    fun testCharArrayToInt() {
        val charArray = CharArray(4)
        charArray[0] = 0x11.toChar()
        charArray[1] = 0x22.toChar()
        charArray[2] = 0x33.toChar()
        charArray[3] = 0x44.toChar()

        assertEquals(0x11223344, charArray.toInt())
    }
}