package io.legado.app.domain.model.settings

import androidx.compose.runtime.Stable

/**
 * 从阅读背景图里抽出来的一套配色（清墨：「主题编辑器会根据图片提取出一套配色便于快捷搭配」）。
 *
 * 与 Android 解耦：取色本身在 `ReaderBackgroundPaletteExtractor` 里做，这里只负责「这几个色块
 * 该当底色、正文色、页眉页脚色还是强调色」——纯函数，可在 JVM 单测里验证。
 */
@Stable
data class ReadBackgroundPalette(
    /** 页面底色：背景图里占比最大的那种颜色，最接近「这张图的整体色调」。 */
    val backgroundArgb: Int,
    /** 正文颜色：与底色对比度最高的候选，保证可读。 */
    val contentArgb: Int,
    /** 页眉页脚颜色：取自柔和色块，比正文浅/暗一档，不抢正文。 */
    val secondaryArgb: Int,
    /** 强调色（菜单标记、图标）：取自饱和度最高的色块。 */
    val controlArgb: Int,
) {
    /** 底色偏深时状态栏图标要取浅色。 */
    val darkStatusIcon: Boolean get() = !isDarkBackgroundColor(backgroundArgb.toArgbHex())
}

/** 取色器的输出单元：一个色块及其像素占比。 */
data class ReadPaletteSwatch(
    val argb: Int,
    val population: Int,
)

/** WCAG 正文对比度阈值。低于它就不当成可用正文色，改用纯黑/纯白兜底。 */
private const val MIN_CONTENT_CONTRAST = 4.5

/**
 * 页眉页脚色的对比度下限。
 *
 * 用 WCAG 的「大字号/非正文」标准 3.0，而不是正文的 4.5：题头页码是辅助信息，卡着正文阈值会把
 * 背景图里那些「比底色深一档的中灰」全部筛掉，结果页眉页脚只能回落到正文色，失去分层的意义。
 */
private const val MIN_SECONDARY_CONTRAST = 3.0

/** 强调色的对比度下限：菜单图标只要「看得见」即可，不必达到正文标准。 */
private const val MIN_CONTROL_CONTRAST = 2.0

private const val OPAQUE_BLACK = 0xFF000000.toInt()
private const val OPAQUE_WHITE = 0xFFFFFFFF.toInt()

/**
 * 从色块列表推导一套可直接写进排版配置的配色。
 *
 * 规则与取舍：
 * - 底色取占比最大的色块；它可能偏深也可能偏浅，后面的正文/页眉页脚都按它的亮度反推。
 * - 正文色取「与底色对比度最高的非背景色块」；全部不达标时回落到纯黑或纯白。背景图往往只有
 *   相近的几种颜色，硬用某个中间色块当正文会直接变成看不清的文字。
 * - 页眉页脚色从 **通过对比度** 的候选里挑最接近底色亮度的那一个：它是辅助信息，太跳会抢正文。
 * - 强调色从所有色块里挑饱和度最高且对比度达标的；没有就沿用正文色。
 *
 * 空列表返回 null，调用方据此提示「这张图取不出配色」。
 */
fun buildReadBackgroundPalette(swatches: List<ReadPaletteSwatch>): ReadBackgroundPalette? {
    if (swatches.isEmpty()) return null
    val opaque = swatches.filter { (it.argb ushr 24) and 0xFF >= 200 }
    val usable = opaque.ifEmpty { swatches }
    val background = usable.maxByOrNull { it.population } ?: return null

    val backgroundLuminance = relativeLuminance(background.argb)
    val readable = usable
        .filter { it.argb != background.argb }
        .filter { contrastRatio(it.argb, background.argb) >= MIN_CONTENT_CONTRAST }
    val fallbackContent = if (backgroundLuminance < 0.5) OPAQUE_WHITE else OPAQUE_BLACK
    val content = readable
        .maxByOrNull { contrastRatio(it.argb, background.argb) }
        ?.argb
        ?: fallbackContent

    val secondary = usable
        .filter { it.argb != background.argb && it.argb != content }
        .filter { contrastRatio(it.argb, background.argb) >= MIN_SECONDARY_CONTRAST }
        .minByOrNull { kotlin.math.abs(relativeLuminance(it.argb) - backgroundLuminance) }
        ?.argb
        ?: content

    val control = usable
        .filter { contrastRatio(it.argb, background.argb) >= MIN_CONTROL_CONTRAST }
        .maxByOrNull { saturationOf(it.argb) }
        ?.argb
        ?: content

    return ReadBackgroundPalette(
        backgroundArgb = background.argb,
        contentArgb = content,
        secondaryArgb = secondary,
        controlArgb = control,
    )
}

/** WCAG 对比度（1..21）。 */
fun contrastRatio(foregroundArgb: Int, backgroundArgb: Int): Double {
    val first = relativeLuminance(foregroundArgb)
    val second = relativeLuminance(backgroundArgb)
    val lighter = maxOf(first, second)
    val darker = minOf(first, second)
    return (lighter + 0.05) / (darker + 0.05)
}

/** 大致饱和度，0..1：取 Max-Min 通道差。用于挑强调色。 */
fun saturationOf(argb: Int): Double {
    val red = (argb shr 16) and 0xFF
    val green = (argb shr 8) and 0xFF
    val blue = argb and 0xFF
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    return if (max == 0) 0.0 else (max - min).toDouble() / max
}

/**
 * 写回排版配置时用的 `#AARRGGBB`。
 *
 * 与项目里 `Int.hexString` 的落盘格式保持一致（配置里的颜色字符串本来就带 alpha），
 * 取色结果是不透明的，alpha 位补齐为 `FF`。
 */
fun Int.toArgbHex(): String =
    "#" + (this or OPAQUE_BLACK).toUInt().toString(16).padStart(8, '0')
