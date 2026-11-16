package io.legado.app.help.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ePub 内嵌字体的两个边界：`@font-face` 只认 ttf/otf，族名归一化后能与 CSS 里的
 * `font-family` 对应上。两者错了的表现都是「字体静默不生效」，所以单独钉住。
 */
class EpubFontFaceParserTest {

    @Test
    fun `抽取 ttf 与 otf 字体并去掉引号`() {
        val css = """
            @font-face { font-family: "Source Han Serif"; src: url(fonts/serif.ttf) format("truetype"); }
            @font-face { font-family: 'Kai'; src: url('../fonts/kai.otf'); }
        """.trimIndent()

        assertEquals(
            listOf(
                // 族名统一小写：登记表与 CSS 匹配都按归一化后的名字
                EpubFontFaceParser.FontFace("source han serif", "fonts/serif.ttf", "ttf"),
                EpubFontFaceParser.FontFace("kai", "../fonts/kai.otf", "otf"),
            ),
            EpubFontFaceParser.parse(css),
        )
    }

    @Test
    fun `woff 字体不登记`() {
        val css = "@font-face { font-family: Web; src: url(fonts/web.woff2) format('woff2'); }"

        assertTrue(EpubFontFaceParser.parse(css).isEmpty())
    }

    @Test
    fun `没有 font-face 时不解析`() {
        assertTrue(EpubFontFaceParser.parse("p { font-family: serif; }").isEmpty())
    }

    @Test
    fun `族名取第一个并排除系统回退名`() {
        assertEquals("my font", EpubFontFaceParser.normalizeFamily("'My Font', serif"))
        assertEquals("kai", EpubFontFaceParser.normalizeFamily(" Kai "))
        assertNull(EpubFontFaceParser.normalizeFamily("serif, kai"))
        assertNull(EpubFontFaceParser.normalizeFamily(""))
        assertNull(EpubFontFaceParser.normalizeFamily(null))
    }

    @Test
    fun `非令牌字符串不会被当成字体路径`() {
        assertNull(EpubFontRegistry.fontPathFor(null))
        assertNull(EpubFontRegistry.fontPathFor(""))
        assertNull(EpubFontRegistry.fontPathFor("sans-serif"))
        assertNull(EpubFontRegistry.fontPathFor("epubfont:../../etc/passwd"))
    }
}
