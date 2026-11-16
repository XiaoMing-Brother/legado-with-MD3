package io.legado.app.domain.model.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 从背景图取色的角色分配规则。
 *
 * 这段逻辑决定「这张图配什么文字色」，错了的失败模式是**正文看不见**（浅底浅字），
 * 所以这里钉住对比度兜底与各角色的取值来源。取色本身（Palette 调用）在 Android 侧，
 * 不在本测试范围内。
 */
class ReadBackgroundPaletteTest {

    @Test
    fun `浅色背景取深色正文与强调色`() {
        val palette = buildReadBackgroundPalette(
            listOf(
                ReadPaletteSwatch(argb = 0xFFF2F2F0.toInt(), population = 900),
                ReadPaletteSwatch(argb = 0xFF2B2B2B.toInt(), population = 100),
                ReadPaletteSwatch(argb = 0xFFB3261E.toInt(), population = 30),
            )
        )

        assertNotNull(palette)
        assertEquals(0xFFF2F2F0.toInt(), palette!!.backgroundArgb)
        assertEquals("正文取对比度最高的色块", 0xFF2B2B2B.toInt(), palette.contentArgb)
        // darkStatusIcon 的语义是「用深色（黑）状态栏图标」，与 ReadThemeStyle.darkStatusIcon = !isDark 一致。
        assertTrue("浅底要深色状态栏图标", palette.darkStatusIcon)
        assertTrue(
            "强调色应挑饱和度最高的色块（红 > 灰）",
            palette.controlArgb == 0xFFB3261E.toInt(),
        )
        assertTrue(contrastRatio(palette.contentArgb, palette.backgroundArgb) >= 4.5)
    }

    @Test
    fun `深色背景取浅色正文并提示浅色状态栏图标`() {
        val palette = buildReadBackgroundPalette(
            listOf(
                ReadPaletteSwatch(argb = 0xFF101418.toInt(), population = 800),
                ReadPaletteSwatch(argb = 0xFFE8E8E8.toInt(), population = 120),
            )
        )

        assertNotNull(palette)
        // 深色底 -> 状态栏图标取浅色，即 darkStatusIcon（深色图标）为 false。
        assertFalse(palette!!.darkStatusIcon)
        assertEquals(0xFFE8E8E8.toInt(), palette.contentArgb)
        assertEquals(
            "没有别的可读色块时，页眉页脚色回落到正文色",
            palette.contentArgb,
            palette.secondaryArgb,
        )
    }

    @Test
    fun `色块全都不可读时正文色回落到纯黑或纯白`() {
        val lightBackground = buildReadBackgroundPalette(
            listOf(ReadPaletteSwatch(argb = 0xFFEFEFEF.toInt(), population = 500))
        )
        assertEquals(
            "浅底配不出可读色块时正文用纯黑，不能原地取一个灰",
            0xFF000000.toInt(),
            lightBackground!!.contentArgb,
        )

        val darkBackground = buildReadBackgroundPalette(
            listOf(ReadPaletteSwatch(argb = 0xFF101010.toInt(), population = 500))
        )
        assertEquals(0xFFFFFFFF.toInt(), darkBackground!!.contentArgb)
    }

    @Test
    fun `页眉页脚色取可读色块里最接近底色亮度的那一个`() {
        val palette = buildReadBackgroundPalette(
            listOf(
                ReadPaletteSwatch(argb = 0xFFFFFFFF.toInt(), population = 1000),
                ReadPaletteSwatch(argb = 0xFF000000.toInt(), population = 200),
                ReadPaletteSwatch(argb = 0xFF777777.toInt(), population = 50),
            )
        )

        assertEquals("正文取对比度最高的纯黑", 0xFF000000.toInt(), palette!!.contentArgb)
        assertEquals("页眉页脚取更靠近底色的中灰", 0xFF777777.toInt(), palette.secondaryArgb)
    }

    @Test
    fun `透明色块不参与底色与正文选择`() {
        val palette = buildReadBackgroundPalette(
            listOf(
                ReadPaletteSwatch(argb = 0x00000000, population = 5000),
                ReadPaletteSwatch(argb = 0xFFF7F7F7.toInt(), population = 100),
                ReadPaletteSwatch(argb = 0xFF222222.toInt(), population = 20),
            )
        )

        assertEquals(
            "大面积透明像素只是画布遗留，不能当页面底色",
            0xFFF7F7F7.toInt(),
            palette!!.backgroundArgb,
        )
    }

    @Test
    fun `没有色块时返回 null 让调用方提示`() {
        assertNull(buildReadBackgroundPalette(emptyList()))
    }

    @Test
    fun `写回配置的颜色格式是不透明 ARGB`() {
        assertEquals("#ff2b2b2b", 0xFF2B2B2B.toInt().toArgbHex())
        assertEquals("#ffffffff", 0xFFFFFFFF.toInt().toArgbHex())
    }
}
