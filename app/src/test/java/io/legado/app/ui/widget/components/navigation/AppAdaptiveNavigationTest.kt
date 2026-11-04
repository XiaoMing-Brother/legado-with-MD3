package io.legado.app.ui.widget.components.navigation

import androidx.compose.ui.unit.IntSize
import androidx.window.core.layout.WindowSizeClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppAdaptiveNavigationTest {
    @Test
    fun autoPanesUse840DpBoundaryInEitherOrientation() {
        assertFalse(shouldUseListDetail("auto", WindowSizeClass(839f, 1200f), portraitPx))
        assertTrue(shouldUseListDetail("auto", WindowSizeClass(840f, 1200f), portraitPx))
        assertTrue(shouldUseListDetail("auto", expandedLandscape, landscapePx))
    }

    @Test
    fun landscapePanesRequireBothWideWindowAndLandscapeOrientation() {
        assertFalse(shouldUseListDetail("landscape", expandedLandscape, portraitPx))
        assertTrue(shouldUseListDetail("landscape", expandedLandscape, landscapePx))
        assertFalse(shouldUseListDetail("landscape", tabletNarrowWindow, landscapePx))
        assertFalse(shouldUseListDetail("landscape", expandedLandscape, IntSize(1000, 1000)))
    }

    @Test
    fun alwaysPanesAllowSmallerWindowsButNeverSqueezeBelowMinimum() {
        assertFalse(shouldUseListDetail("always", WindowSizeClass(743f, 1000f), portraitPx))
        assertTrue(shouldUseListDetail("always", WindowSizeClass(744f, 1000f), portraitPx))
        assertTrue(shouldUseListDetail("always", tabletPortrait, portraitPx))
        assertFalse(shouldUseListDetail("auto", tabletPortrait, portraitPx))
    }

    @Test
    fun disabledPanesStayOffAndUnknownModesUseAuto() {
        assertFalse(shouldUseListDetail("off", expandedLandscape, landscapePx))
        assertTrue(shouldUseListDetail("unknown", expandedLandscape, portraitPx))
        assertFalse(shouldUseListDetail("unknown", tabletNarrowWindow, landscapePx))
        assertEquals(MultiPaneMode.Auto, MultiPaneMode.parse(null))
    }

    // WindowSizeClass 的公开构造接收 dp（float），其余构造是内部/伴随工厂。
    private val tabletNarrowWindow = WindowSizeClass(widthDp = 560f, heightDp = 700f)
    private val tabletPortrait = WindowSizeClass(widthDp = 800f, heightDp = 1200f)
    private val expandedLandscape = WindowSizeClass(widthDp = 1100f, heightDp = 700f)
    private val portraitPx = IntSize(width = 1080, height = 2400)
    private val landscapePx = IntSize(width = 2400, height = 1080)
}
