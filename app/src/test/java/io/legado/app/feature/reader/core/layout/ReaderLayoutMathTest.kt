package io.legado.app.feature.reader.core.layout

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderLayoutMathTest {
    @Test fun surrogatePairFormsOneGlyphCluster() {
        val result = clusterGlyphs("😀", floatArrayOf(20f, 0f))
        assertEquals(listOf("😀"), result.text)
        assertEquals(listOf(20f), result.widthsPx)
    }

    @Test fun calculatesSingleAndDoublePageBounds() {
        assertEquals(
            ReaderContentBounds(1032, 1856, 1056, 1888),
            calculateContentBounds(1080, 1920, 24, 32, 24, 32),
        )
        assertEquals(
            ReaderContentBounds(492, 1856, 516, 1888),
            calculateContentBounds(1080, 1920, 24, 32, 24, 32, 2),
        )
    }

    @Test fun systemBarAvoidanceIsClampedInsteadOfAdded() {
        // 清墨规则：状态栏显示且顶部边距小于状态栏高度时用状态栏高度，不叠加。
        assertEquals(120, readerEdgePadding(userPaddingPx = 16, systemBarPaddingPx = 120))
        assertEquals(200, readerEdgePadding(userPaddingPx = 200, systemBarPaddingPx = 120))
        assertEquals(0, readerEdgePadding(userPaddingPx = 0, systemBarPaddingPx = 0))
        assertEquals(
            "用户把边距清零时，系统栏占位仍然保留（否则正文会顶到状态栏下）",
            120,
            readerEdgePadding(userPaddingPx = 0, systemBarPaddingPx = 120),
        )
    }
}
