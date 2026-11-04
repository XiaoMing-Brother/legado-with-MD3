package io.legado.app.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainDrawerHostStateTest {

    @Test
    fun `attached drawer is open and closes through the host`() {
        val host = MainDrawerHostState()
        var closes = 0

        host.attach { closes++ }

        assertTrue(host.isOpen)
        host.requestClose()
        assertEquals(1, closes)
    }

    @Test
    fun `detached drawer swallows back instead of popping the route`() {
        val host = MainDrawerHostState()
        var closes = 0
        host.attach { closes++ }

        host.detach()
        host.requestClose()

        assertFalse(host.isOpen)
        assertEquals(0, closes)
    }

    @Test
    fun `reopening swaps the close action without leaving stale state`() {
        val host = MainDrawerHostState()
        var first = 0
        var second = 0

        host.attach { first++ }
        host.attach { second++ }
        host.requestClose()

        assertEquals(0, first)
        assertEquals(1, second)
    }
}
