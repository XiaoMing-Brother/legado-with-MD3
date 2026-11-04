package io.legado.app.help.epub

import io.legado.app.constant.AppLog
import io.legado.app.utils.MD5Utils
import splitties.init.appCtx
import java.io.File

/**
 * ePub 内嵌字体（`@font-face`）的落盘与令牌解析。
 *
 * 阅读器排版只认 [io.legado.app.feature.reader.core.model.ReaderTextStyle.fontFamily] 这个字符串，
 * 而 CSS 里的族名既不唯一（两本书都可能叫 `F0`）也不带文件信息。因此这里把字体文件抽到
 * 应用私有目录，用内容 MD5 生成一个令牌 `epubfont:<md5>.<ext>`：
 * - 令牌只由字体内容决定，不同书用同一个字体文件时自然共用一份；
 * - 同名族不会跨书串味，因为族名到令牌的映射留在各自的 `EpubFile` 里；
 * - `loadTypeface` 看到 `epubfont:` 前缀就直接按令牌取文件，不依赖任何全局族名表。
 *
 * 只接受 `.ttf` / `.otf`：woff/woff2 是压缩字体，Android `Typeface.Builder` 读不了，
 * 登记了也只会静默回落，反而把用户选定的正文/标题字体顶掉。
 */
object EpubFontRegistry {

    const val TOKEN_PREFIX = "epubfont:"

    private const val folderName = "epub_fonts"

    /**
     * 写入字体文件并返回令牌。字体内容为空、扩展名不支持或写盘失败时返回 null。
     */
    fun register(data: ByteArray, extension: String): String? {
        val ext = extension.lowercase().takeIf { it == "ttf" || it == "otf" } ?: return null
        if (data.isEmpty()) return null
        val hash = MD5Utils.md5Encode(data.inputStream())
        val token = TOKEN_PREFIX + hash + "." + ext
        if (fontPathFor(token) != null) return token
        return runCatching {
            val dir = File(appCtx.filesDir, folderName)
            if (!dir.isDirectory && !dir.mkdirs()) return null
            val target = File(dir, hash + "." + ext)
            if (!target.isFile) target.writeBytes(data)
            token
        }.onFailure { AppLog.put("ePub 字体落盘失败: $token", it) }.getOrNull()
    }

    /** 令牌对应的字体文件路径；不是令牌或文件已被清理时返回 null。 */
    fun fontPathFor(family: String?): String? {
        val name = family?.takeIf { it.startsWith(TOKEN_PREFIX) }
            ?.removePrefix(TOKEN_PREFIX)
            ?: return null
        // 令牌里的文件名来自 MD5 + 白名单扩展名，不接受任何路径分隔符
        if (name.contains('/') || name.contains('\\') || name.contains("..")) return null
        return File(File(appCtx.filesDir, folderName), name)
            .takeIf { it.isFile }?.absolutePath
    }
}
