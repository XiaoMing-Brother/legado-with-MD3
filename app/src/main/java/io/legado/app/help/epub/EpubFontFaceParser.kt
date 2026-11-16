package io.legado.app.help.epub

/**
 * 从 CSS 文本里抽取 `@font-face` 声明。
 *
 * 纯字符串解析，不依赖 Android/Jsoup，便于 JVM 单测。只认 `src: url(...)` 的第一项，
 * 并且只保留 ttf/otf：woff/woff2 是压缩格式，`Typeface.Builder` 读不了。
 */
object EpubFontFaceParser {

    data class FontFace(val family: String, val href: String, val extension: String)

    private val blockRegex = Regex("@font-face\\s*\\{([^}]*)\\}", RegexOption.IGNORE_CASE)
    private val familyRegex = Regex("font-family\\s*:\\s*([^;]+)", RegexOption.IGNORE_CASE)
    private val srcRegex = Regex("src\\s*:\\s*([^;}]+)", RegexOption.IGNORE_CASE)
    private val urlRegex = Regex("url\\(\\s*['\"]?([^'\")]+)['\"]?\\s*\\)", RegexOption.IGNORE_CASE)

    fun parse(cssText: String): List<FontFace> {
        if (!cssText.contains("@font-face", ignoreCase = true)) return emptyList()
        return blockRegex.findAll(cssText).mapNotNull { match ->
            val body = match.groupValues[1]
            val family = familyRegex.find(body)?.groupValues?.get(1)
                ?.let(::normalizeFamily)
                ?: return@mapNotNull null
            val src = srcRegex.find(body)?.groupValues?.get(1) ?: return@mapNotNull null
            val href = urlRegex.find(src)?.groupValues?.get(1)?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: return@mapNotNull null
            val extension = href.substringAfterLast('.', "").substringBefore('?').lowercase()
            if (extension != "ttf" && extension != "otf") return@mapNotNull null
            FontFace(family, href, extension)
        }.toList()
    }

    /**
     * `font-family: "My Font", serif` → `my font`。
     *
     * 只取第一个族名：后面的都是回退字体，本来就应该落到正文/系统字体；
     * 纯回退族名返回 null，避免把「没指定字体」当成一次字体声明。
     */
    fun normalizeFamily(raw: String?): String? {
        val first = raw
            ?.substringBefore(',')
            ?.trim()
            ?.trim('"', '\'')
            ?.trim()
            ?.lowercase()
            ?: return null
        return first.takeIf { it.isNotEmpty() && it !in fallbackFamilies }
    }

    private val fallbackFamilies = setOf(
        "serif", "sans-serif", "monospace", "cursive", "fantasy", "system-ui",
        "inherit", "initial", "unset",
    )
}
