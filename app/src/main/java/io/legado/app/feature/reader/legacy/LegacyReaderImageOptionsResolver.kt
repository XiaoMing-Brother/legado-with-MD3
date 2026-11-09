package io.legado.app.feature.reader.legacy

import io.legado.app.feature.reader.core.layout.ReaderImageLayoutMode
import io.legado.app.feature.reader.core.layout.ReaderImageOptions
import io.legado.app.feature.reader.core.layout.ReaderImageOptionsResolver
import io.legado.app.feature.reader.core.layout.ReaderInlineImagePlaceholder
import io.legado.app.feature.reader.core.layout.ReaderTextAlignment
import io.legado.app.model.ParagraphBubbleRenderer
import io.legado.app.model.ParagraphBubbleSource
import io.legado.app.model.analyzeRule.AnalyzeUrl.Companion.paramPattern
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject

/** Keeps source-URL JSON parsing at the legacy input boundary; the Canvas core receives typed options. */
object LegacyReaderImageOptionsResolver : ReaderImageOptionsResolver {
    /** 与 `Book.imgStyleText` 一致；这里不引入数据层依赖，靠单测钉住取值。 */
    private const val imgStyleText = "TEXT"
    private const val imgStyleTextLowercase = "text"
    /**
     * 与旧 `TextChapterLayout.reviewCharWidth` 同口径：段评图标槽宽 = `袮` 的 advance × 1.5556。
     * `꧁` 的字面 advance 只有 0.3em 左右，直接按它排版会把奇思妙想的气泡缩成一个小点。
     */
    private const val paragraphCommentInlineWidthScale = 1.5556f

    override fun resolve(source: String, htmlParagraph: Boolean): ReaderImageOptions? {
        val values = sourceOptions(source)
        // 段评气泡：尺寸由渲染器给（正方形），一律按「文字嵌入」排版，槽宽沿用旧版段评图标基准。
        // 不能沿用书源里的 style，否则会被当成整图铺满一页。
        // 占位字必须用 CJK 的 `袮`：旧版把气泡源改写成 `bubble://...` 后已不带 `style=TEXT`，
        // 因此走的是 `srcReplaceChar` 分支；`꧁` 在部分字体里只有半个字宽，会让气泡缩成小圆点。
        if (ParagraphBubbleSource.parse(source) != null) {
            return ReaderImageOptions(
                layoutMode = ReaderImageLayoutMode.INLINE,
                horizontalAlignment = ReaderTextAlignment.CENTER,
                action = values?.valueIgnoreCase("pclick")?.takeIf(String::isNotBlank)
                    ?: values?.valueIgnoreCase("click")?.takeIf(String::isNotBlank),
                inlinePlaceholder = ReaderInlineImagePlaceholder.SRC_REPLACE,
                // 软件气泡同样要保留旧版段评图标的 1.5556 倍基准；包内缩放再叠加上去，
                // 否则默认包会缩成一个字宽，开启“接管书源段评图”后反而比原图更小。
                inlineWidthScale = paragraphCommentSlotScale(),
                paragraphCommentIcon = true,
            )
        }
        if (values == null) return null
        val rawStyle = values.valueIgnoreCase("style")
        val style = rawStyle?.uppercase()
        val width = values.valueIgnoreCase("width")
        val paragraphCommentIcon = values.isParagraphCommentIcon()
        return ReaderImageOptions(
            layoutMode = when {
                rawStyle == null -> null
                // 两条旧路径对"文字嵌入"的判据不同：
                // - HTML（`setTypeHtml`）用 `iStyle?.uppercase() == "TEXT"`，不区分大小写；
                // - 纯文本（`getTextChapter`）用字面量 `iStyle == "text" || iStyle == "TEXT"`，
                //   "Text"、"TEXT " 等拼写会走 `setTypeImage` 按整图排版。
                textIsInline(rawStyle, htmlParagraph) -> ReaderImageLayoutMode.INLINE
                // FULL/SINGLE 两条路径都是 `imageStyle?.uppercase()` 比较，不区分大小写。
                style == "FULL" -> ReaderImageLayoutMode.FULL_WIDTH
                style == "SINGLE" -> ReaderImageLayoutMode.SINGLE_PAGE
                else -> ReaderImageLayoutMode.STANDALONE
            },
            requestedWidthPx = width?.takeUnless { it.endsWith('%') }?.toFloatOrNull()
                ?.takeIf { it > 0f },
            requestedWidthFraction = width?.takeIf { it.endsWith('%') }
                ?.dropLast(1)?.toFloatOrNull()?.div(100f)?.takeIf { it > 0f },
            // 旧 `setTypeImage` 的对齐取自"有效样式"：单图 style 存在就只用它（不是 LEFT/RIGHT 即
            // 居中，**不**回落到书级）；缺失时才用书级样式。null = 未指定，由测量层回落书级。
            horizontalAlignment = when (style) {
                "LEFT" -> ReaderTextAlignment.START
                "RIGHT" -> ReaderTextAlignment.END
                else -> if (rawStyle == null) null else ReaderTextAlignment.CENTER
            },
            action = values.valueIgnoreCase("pclick")?.takeIf(String::isNotBlank)
                ?: values.valueIgnoreCase("click")?.takeIf(String::isNotBlank),
            // 占位字只是**纯文本**路径的概念：`"text"` 用 `srcReplaceChar`（袮）、`"TEXT"` 用
            // `reviewChar`（꧁）。旧 HTML 路径的行内图宽来自 ImageSpan 的 advance（没有占位字），
            // 这里取纯文本默认的袮。段评图标例外：旧版给它的槽宽是 `袮` 的 1.5556 倍，不能按
            // `꧁` 自身的 advance 量。
            inlinePlaceholder = if (paragraphCommentIcon) {
                ReaderInlineImagePlaceholder.SRC_REPLACE
            } else if (!htmlParagraph && rawStyle == imgStyleText) {
                ReaderInlineImagePlaceholder.REVIEW
            } else {
                ReaderInlineImagePlaceholder.SRC_REPLACE
            },
            inlineWidthScale = if (paragraphCommentIcon) {
                paragraphCommentSlotScale()
            } else {
                1f
            },
            paragraphCommentIcon = paragraphCommentIcon,
        )
    }

    fun legacyScript(source: String): String? =
        sourceOptions(source)?.valueIgnoreCase("js")?.takeIf(String::isNotBlank)

    /** 该协议需要 ParagraphRule 的规则和变量宿主，不能作为书源 JS 执行。 */
    fun isParagraphRuleAction(action: String): Boolean = action.trimStart().let {
        it.startsWith("paragraphRule:") || it.startsWith("rule:")
    }

    private fun sourceOptions(source: String): Map<String, String>? {
        val separator = paramPattern.find(source) ?: return null
        return GSON.fromJsonObject<Map<String, String>>(
            source.substring(separator.range.last + 1),
        ).getOrNull()
    }

    private fun Map<String, String>.valueIgnoreCase(name: String): String? =
        get(name) ?: entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    private fun Map<String, String>.isParagraphCommentIcon(): Boolean {
        val style = valueIgnoreCase("style")
        if (!style.equals(imgStyleText, ignoreCase = true)) return false
        val type = valueIgnoreCase("type")?.lowercase()
        if (type in paragraphCommentTypes) return true
        val click = listOfNotNull(
            valueIgnoreCase("pclick"),
            valueIgnoreCase("click"),
        ).joinToString(separator = "\n").lowercase()
        return paragraphCommentClickMarkers.any(click::contains)
    }

    private fun textIsInline(rawStyle: String, htmlParagraph: Boolean): Boolean =
        if (htmlParagraph) rawStyle.uppercase() == imgStyleText
        else rawStyle == imgStyleText || rawStyle == imgStyleTextLowercase

    private fun paragraphCommentSlotScale(): Float =
        paragraphCommentInlineWidthScale * ParagraphBubbleRenderer.inlineWidthScale()

    private val paragraphCommentTypes = setOf(
        "qd",
        "fqpl",
        "fanqie",
        "cmt",
        "comment",
        "comments",
        "review",
        "paragraph",
        "paragraphcomment",
    )

    private val paragraphCommentClickMarkers = listOf(
        "showqdcmt(",
        "showfqcmt(",
        "showqmcmt(",
        "showcmt(",
        "showcomment(",
        "showparagraph",
    )
}
