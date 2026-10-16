package com.example.pfptodo

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates deep-link task id extraction (device/instrumented; Uri needs the
 * Android runtime).
 */
@RunWith(AndroidJUnit4::class)
class MainActivityDeepLinkTest {

    @Test
    fun pathStyleDeepLinkExtractsTaskId() {
        assertEquals(42L, extractTaskId(Uri.parse("wtodo://task/42")))
        assertEquals(42L, extractTaskId(Uri.parse("pishitodo://task/42")))
    }

    @Test
    fun queryStyleDeepLinkExtractsTaskId() {
        assertEquals(7L, extractTaskId(Uri.parse("wtodo://task?id=7")))
        assertEquals(7L, extractTaskId(Uri.parse("pishitodo://task?id=7")))
    }

    @Test
    fun invalidIdIsRejected() {
        assertNull(extractTaskId(Uri.parse("wtodo://task/abc")))
        assertNull(extractTaskId(Uri.parse("wtodo://task/0")))
        assertNull(extractTaskId(Uri.parse("wtodo://task/-1")))
    }

    @Test
    fun missingIdIsRejected() {
        assertNull(extractTaskId(Uri.parse("wtodo://task")))
        assertNull(extractTaskId(Uri.parse("wtodo://")))
    }
}
