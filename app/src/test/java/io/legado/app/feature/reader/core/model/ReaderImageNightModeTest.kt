package io.legado.app.feature.reader.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 插图夜景处理只在深色页面上生效；档位解析必须保持向后兼容（旧值 / 未知值 → 不处理）。
 */
class ReaderImageNightModeTest {

    private val darkPage = 0xFF121212.toInt()
    private val lightPage = 0xFFF1F3F4.toInt()

    @Test
    fun `未知或缺失的偏好值回落到不处理`() {
        assertEquals(ReaderImageNightMode.NONE, ReaderImageNightMode.fromPref(null))
        assertEquals(ReaderImageNightMode.NONE, ReaderImageNightMode.fromPref(""))
        assertEquals(ReaderImageNightMode.NONE, ReaderImageNightMode.fromPref("weird"))
    }

    @Test
    fun `档位字符串与枚举一一对应`() {
        assertEquals(ReaderImageNightMode.NONE, ReaderImageNightMode.fromPref("0"))
        assertEquals(ReaderImageNightMode.INVERT, ReaderImageNightMode.fromPref("1"))
        assertEquals(ReaderImageNightMode.DIM, ReaderImageNightMode.fromPref("2"))
    }

    @Test
    fun `浅色页面一律按原图绘制`() {
        ReaderImageNightMode.entries.forEach { mode ->
            assertFalse(mode.appliesToBackground(lightPage))
            assertEquals(1f, mode.imageAlpha(lightPage), 0f)
            assertFalse(mode.invertsColors(lightPage))
        }
    }

    @Test
    fun `深色页面按档位分别降亮或反转`() {
        assertFalse(ReaderImageNightMode.NONE.appliesToBackground(darkPage))
        assertEquals(1f, ReaderImageNightMode.NONE.imageAlpha(darkPage), 0f)

        assertTrue(ReaderImageNightMode.DIM.appliesToBackground(darkPage))
        assertTrue(ReaderImageNightMode.DIM.imageAlpha(darkPage) < 1f)
        assertFalse(ReaderImageNightMode.DIM.invertsColors(darkPage))

        assertTrue(ReaderImageNightMode.INVERT.appliesToBackground(darkPage))
        assertTrue(ReaderImageNightMode.INVERT.invertsColors(darkPage))
        assertEquals(1f, ReaderImageNightMode.INVERT.imageAlpha(darkPage), 0f)
    }
}
