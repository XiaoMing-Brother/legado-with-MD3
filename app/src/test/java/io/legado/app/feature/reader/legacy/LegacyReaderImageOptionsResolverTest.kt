package io.legado.app.feature.reader.legacy

import io.legado.app.feature.reader.core.layout.ReaderImageLayoutMode
import io.legado.app.feature.reader.core.layout.ReaderInlineImagePlaceholder
import io.legado.app.feature.reader.core.layout.ReaderTextAlignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class LegacyReaderImageOptionsResolverTest {
    @Test
    fun keepsLegacyJsFallbackAndIdentifiesActionsThatRequireTheParagraphRuleHost() {
        assertEquals("legacy()", LegacyReaderImageOptionsResolver.legacyScript("image,{\"JS\":\"legacy()\"}"))
        assertNull(LegacyReaderImageOptionsResolver.legacyScript("image,{\"js\":\"\"}"))
        assertTrue(LegacyReaderImageOptionsResolver.isParagraphRuleAction("paragraphRule:rule:123\nrun()"))
        assertTrue(LegacyReaderImageOptionsResolver.isParagraphRuleAction("rule:123:run()"))
        assertFalse(LegacyReaderImageOptionsResolver.isParagraphRuleAction("java.showBrowser('https://example')"))
    }

    @Test
    fun recognizesCaseInsensitiveKeysAndPrioritizesPclickLikeTheReferenceReader() {
        val options = LegacyReaderImageOptionsResolver.resolve(
            "image,{\"STYLE\":\"full\",\"Width\":\"50%\",\"Click\":\"ordinary()\",\"PCliCk\":\"paragraph()\"}",
            htmlParagraph = false,
        )!!
        assertEquals(ReaderImageLayoutMode.FULL_WIDTH, options.layoutMode)
        assertEquals(0.5f, options.requestedWidthFraction!!, 0f)
        assertEquals("paragraph()", options.action)
        assertEquals(
            "ordinary()",
            LegacyReaderImageOptionsResolver.resolve(
                "image,{\"pclick\":\"\",\"CLICK\":\"ordinary()\"}", false,
            )!!.action,
        )
    }

    /**
     * 奇思妙想把段评入口画成 800×800 的 data-URI SVG，书源只写 `style=TEXT`。
     * 旧 `TextChapterLayout` 对段评图标的槽宽是 `袮` 的 1.5556 倍；按 `꧁` 自身 advance
     * 排版会让气泡缩成一个小点。
     */
    @Test
    fun paragraphCommentSvgUsesWiderInlineSlot() {
        val source = "data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=," +
            """{"style":"TEXT","type":"qd","click":"showQdCmt('1','2','3','4')"}"""

        val options = LegacyReaderImageOptionsResolver.resolve(source, htmlParagraph = false)!!

        assertEquals(ReaderImageLayoutMode.INLINE, options.layoutMode)
        assertEquals(ReaderInlineImagePlaceholder.SRC_REPLACE, options.inlinePlaceholder)
        assertEquals(1.5556f, options.inlineWidthScale, 0f)
        assertTrue(options.paragraphCommentIcon)
        assertEquals("showQdCmt('1','2','3','4')", options.action)
    }

    @Test
    fun ordinaryInlineTextImageKeepsLegacyOneCharacterSlot() {
        val options = LegacyReaderImageOptionsResolver.resolve(
            "data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=,{\"style\":\"TEXT\"}",
            htmlParagraph = false,
        )!!

        assertEquals(ReaderImageLayoutMode.INLINE, options.layoutMode)
        assertEquals(1f, options.inlineWidthScale, 0f)
        assertFalse(options.paragraphCommentIcon)
    }

    /**
     * 软件气泡的源会被改写成 `bubble://...`，旧版此时按无 style 的小图处理，占位字是 `袮`
     * 而不是 `꧁`。用 `꧁` 会让气泡在缺字字体上只剩半个字宽，看起来像一个小圆点。
     */
    @Test
    fun softwareBubbleUsesCjkPlaceholderAndExpandedTouchTarget() {
        val options = LegacyReaderImageOptionsResolver.resolve(
            "dp:5",
            htmlParagraph = false,
        )!!

        assertEquals(ReaderImageLayoutMode.INLINE, options.layoutMode)
        assertEquals(ReaderInlineImagePlaceholder.SRC_REPLACE, options.inlinePlaceholder)
        assertEquals(1.5556f, options.inlineWidthScale, 0f)
        assertTrue(options.paragraphCommentIcon)
    }

    /** 奇思妙想 HTML 段落里的番茄段评图标：type 是 `fq`，只能靠 click 里的函数名识别。 */
    @Test
    fun fanqieCommentSvgInHtmlParagraphUsesCommentIconLayout() {
        val source = "data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=," +
            """{"style":"TEXT","type":"fq","click":"showFqCmt('1','2','3','4','5')"}"""

        val options = LegacyReaderImageOptionsResolver.resolve(source, htmlParagraph = true)!!

        assertEquals(ReaderImageLayoutMode.INLINE, options.layoutMode)
        assertEquals(ReaderInlineImagePlaceholder.SRC_REPLACE, options.inlinePlaceholder)
        assertEquals(1.5556f, options.inlineWidthScale, 0f)
        assertTrue(options.paragraphCommentIcon)
    }

    @Test
    fun parsesTypedImageUrlOptions() {
        val options = LegacyReaderImageOptionsResolver.resolve(
            "https://example/image, {\"style\":\"right\",\"width\":\"37.5%\",\"click\":\"open(\\\"x\\\")\"}",
            htmlParagraph = false,
        )!!
        assertEquals(ReaderImageLayoutMode.STANDALONE, options.layoutMode)
        assertEquals(ReaderTextAlignment.END, options.horizontalAlignment)
        assertEquals(.375f, options.requestedWidthFraction!!, 0f)
        assertEquals("open(\"x\")", options.action)
    }

    @Test
    fun parsesAbsoluteWidthAndKnownModesCaseInsensitively() {
        val options = LegacyReaderImageOptionsResolver
            .resolve("x,{\"style\":\"full\",\"width\":\"123\"}", htmlParagraph = false)!!
        assertEquals(ReaderImageLayoutMode.FULL_WIDTH, options.layoutMode)
        assertEquals(123f, options.requestedWidthPx!!, 0f)
    }

    /** 旧纯文本路径的 `iStyle == "text" || iStyle == "TEXT"` 是精确匹配，迁移必须保留。 */
    @Test
    fun onlyExactLegacyTextSpellingsBecomeInlineImages() {
        fun layoutMode(style: String, htmlParagraph: Boolean = false) =
            LegacyReaderImageOptionsResolver
                .resolve("x,{\"style\":\"$style\"}", htmlParagraph)!!.layoutMode

        assertEquals(ReaderImageLayoutMode.INLINE, layoutMode("text"))
        assertEquals(ReaderImageLayoutMode.INLINE, layoutMode("TEXT"))
        // 混合大小写/带空格的拼写在旧 `setTypeImage` 里按整图排版（不区分大小写的前提不成立）。
        assertEquals(ReaderImageLayoutMode.STANDALONE, layoutMode("Text"))
        assertEquals(ReaderImageLayoutMode.STANDALONE, layoutMode("tExT"))
        assertEquals(ReaderImageLayoutMode.STANDALONE, layoutMode("text "))
        assertEquals(ReaderImageLayoutMode.STANDALONE, layoutMode("Text "))

        // 旧 HTML 路径（`setTypeHtml`）用的是 `iStyle?.uppercase()`，不区分大小写……
        assertEquals(ReaderImageLayoutMode.INLINE, layoutMode("Text", htmlParagraph = true))
        assertEquals(ReaderImageLayoutMode.INLINE, layoutMode("tExT", htmlParagraph = true))
        // ……但带空格的拼写 uppercase 之后也对不上 "TEXT"。
        assertEquals(ReaderImageLayoutMode.STANDALONE, layoutMode("text ", htmlParagraph = true))
        assertEquals(ReaderImageLayoutMode.STANDALONE, layoutMode("Text ", htmlParagraph = true))
    }

    /**
     * 旧 `TextChapterLayout` 按拼写选占位字：`"text"` → `srcReplaceChar`（袮），
     * `"TEXT"` → `reviewChar`（꧁）。两个字的 advance 不同，行内图的宽也随之不同。
     */
    @Test
    fun exactTextSpellingPicksItsLegacyPlaceholderGlyph() {
        fun placeholder(style: String, htmlParagraph: Boolean = false) =
            LegacyReaderImageOptionsResolver
                .resolve("x,{\"style\":\"$style\"}", htmlParagraph)!!.inlinePlaceholder

        assertEquals(ReaderInlineImagePlaceholder.SRC_REPLACE, placeholder("text"))
        assertEquals(ReaderInlineImagePlaceholder.REVIEW, placeholder("TEXT"))
        // 非文字嵌入时占位字不参与排版（书级 TEXT 走默认的袮）。
        assertEquals(ReaderInlineImagePlaceholder.SRC_REPLACE, placeholder("full"))
        assertEquals(ReaderInlineImagePlaceholder.SRC_REPLACE, placeholder("Text"))
        // 旧 HTML 路径没有占位字（行内宽来自 ImageSpan），统一取纯文本默认的袮。
        assertEquals(
            ReaderInlineImagePlaceholder.SRC_REPLACE,
            placeholder("TEXT", htmlParagraph = true),
        )
        // 没有 style（布局模式回落到书级设置，书级 TEXT 旧版也用袮）时保持旧默认。
        assertEquals(
            ReaderInlineImagePlaceholder.SRC_REPLACE,
            LegacyReaderImageOptionsResolver
                .resolve("x,{\"click\":\"go()\"}", htmlParagraph = false)!!.inlinePlaceholder,
        )
    }

    /**
     * 旧 `setTypeImage` 的对齐取自"有效样式"：单图 style 存在就只用它（不是 LEFT/RIGHT 即居中，
     * 不回落到书级）；只有 style 缺失时 `horizontalAlignment` 才是 null（未指定，由测量层回落书级）。
     */
    @Test
    fun alignmentIsUnspecifiedOnlyWhenTheImageStyleIsAbsent() {
        fun alignment(source: String) = LegacyReaderImageOptionsResolver
            .resolve(source, htmlParagraph = false)!!.horizontalAlignment

        assertEquals(ReaderTextAlignment.START, alignment("x,{\"style\":\"left\"}"))
        assertEquals(ReaderTextAlignment.END, alignment("x,{\"style\":\"right\"}"))
        assertEquals(ReaderTextAlignment.CENTER, alignment("x,{\"style\":\"full\"}"))
        assertNull(alignment("x,{\"click\":\"go()\"}"))
    }

    @Test fun malformedOrAbsentOptionsDoNotInventOverrides() {
        assertNull(LegacyReaderImageOptionsResolver.resolve("x", htmlParagraph = false))
        assertNull(LegacyReaderImageOptionsResolver.resolve("x,{bad}", htmlParagraph = false))
        assertNull(LegacyReaderImageOptionsResolver.resolve("x", htmlParagraph = true))
        assertNull(LegacyReaderImageOptionsResolver.resolve("x,{bad}", htmlParagraph = true))
    }
}
