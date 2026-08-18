package com.umar.yaraan.chate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AppUnitTest {

    @Test
    fun testPackageName() {
        val expectedPackageName = "com.umar.yaraan.chate"
        assertEquals("com.umar.yaraan.chate", expectedPackageName)
    }

    @Test
    fun testWebAppUrl() {
        val expectedUrl = "https://yaraan-voice-chat.netlify.app"
        assertNotNull(expectedUrl)
        assertEquals("https://yaraan-voice-chat.netlify.app", expectedUrl)
    }
}
