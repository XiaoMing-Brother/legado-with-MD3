package io.legado.app.domain.model.settings

import androidx.compose.runtime.Stable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 由内置主题的白天配色派生出的夜间配色。
 *
 * 主题商店的数据里每个主题只有一套调色板（`dark` 字段全为 false，见 [ReadThemePreset.isDark]），
 * 直接写进夜间槽位会让「切到夜间」和白天一模一样——浅色主题的夜间页就是一张白纸。
 * 这里按底色深浅决定：
 * - 底色已经够深（亮度低于 [NIGHT_BASE_MAX_LUMINANCE]）：夜间直接复用主题原色，主题本来就长这样；
 * - 浅色主题：保留底色的色相、压低明度与饱和度得到夜间底色；正文/页眉页脚/强调色保留各自色相，
 *   反推成与夜间底色满足 WCAG 对比度的浅色。
 *
 * 纯函数，可在 JVM 单测里跑（同 [buildReadBackgroundPalette]）。
 */
@Stable
data class ReadThemeNightPalette(
    val backgroundArgb: Int,
    val contentArgb: Int,
    val secondaryArgb: Int,
    val controlArgb: Int,
)

/**
 * 主题底色低于这个相对亮度就认为它本身已经是夜间底色。
 *
 * 比 [DARK_LUMINANCE_THRESHOLD]（0.5）严格得多：0.5 只用来判「状态栏图标要不要反色」，
 * 而一张米黄/中灰的纸在夜间仍然刺眼，必须重新派生深色底。
 */
internal const val NIGHT_BASE_MAX_LUMINANCE = 0.05

private const val OPAQUE_MASK = 0xFF000000.toInt()

/** 低于这个 alpha 的主题色在夜间不当作「已经配好」：半透明正文压在深色底上会糊成一片。 */
private const val MIN_OPAQUE_ALPHA = 0xE6

private const val NIGHT_BACKGROUND_LIGHTNESS = 0.09f
private const val NIGHT_BACKGROUND_MAX_SATURATION = 0.24f
private const val NIGHT_TEXT_MAX_SATURATION = 0.12f
private const val NIGHT_CONTENT_TARGET_LIGHTNESS = 0.80f
private const val NIGHT_SECONDARY_TARGET_LIGHTNESS = 0.66f
private const val NIGHT_ACCENT_TARGET_LIGHTNESS = 0.72f
private const val NIGHT_ACCENT_MIN_SATURATION = 0.35f
private const val NIGHT_ACCENT_MAX_SATURATION = 0.90f

/** 主题强调色本身接近灰色时不去硬造色相，按正文同款灰阶处理。 */
private const val NIGHT_ACCENT_BASIC_SATURATION = 0.15f

private const val MIN_TEXT_CONTRAST = 4.5
private const val MIN_ACCENT_CONTRAST = 3.0
private const val LIGHTNESS_STEP = 0.02f
private const val MAX_NIGHT_LIGHTNESS = 0.96f

/**
 * 派生夜间配色。
 *
 * 颜色按 `#AARRGGBB` 的 int 传入；解析失败由调用方兜底，这里只处理合法颜色。
 */
internal fun deriveNightPalette(
    backgroundArgb: Int,
    contentArgb: Int,
    secondaryArgb: Int,
    controlArgb: Int,
): ReadThemeNightPalette {
    val dayBackground = backgroundArgb or OPAQUE_MASK
    val nightBackground = if (relativeLuminance(dayBackground) < NIGHT_BASE_MAX_LUMINANCE) {
        dayBackground
    } else {
        val hsl = dayBackground.toHsl()
        hsl.toArgb(
            saturation = min(hsl.saturation, NIGHT_BACKGROUND_MAX_SATURATION),
            lightness = NIGHT_BACKGROUND_LIGHTNESS,
        )
    }
    return deriveNightTextPalette(
        backgroundArgb = nightBackground,
        contentArgb = contentArgb,
        secondaryArgb = secondaryArgb,
        controlArgb = controlArgb,
    )
}

/**
 * 只按给定底色反推夜间文字色，不动底色。
 *
 * 用在「底色是用户自己配的」场景（如从背景图取色后的夜间槽位）：底色是否够深由调用方负责，
 * 这里只保证正文/页眉页脚/强调色在该底色上可读。
 */
internal fun deriveNightTextPalette(
    backgroundArgb: Int,
    contentArgb: Int,
    secondaryArgb: Int,
    controlArgb: Int,
): ReadThemeNightPalette {
    val nightBackground = backgroundArgb or OPAQUE_MASK
    return ReadThemeNightPalette(
        backgroundArgb = nightBackground,
        contentArgb = nightText(contentArgb, nightBackground, NIGHT_CONTENT_TARGET_LIGHTNESS),
        secondaryArgb = nightText(secondaryArgb, nightBackground, NIGHT_SECONDARY_TARGET_LIGHTNESS),
        controlArgb = nightAccent(controlArgb, nightBackground),
    )
}

/**
 * 保留原色相，按目标明度取浅色；仍不达标就继续提亮直到满足 [MIN_TEXT_CONTRAST]。
 *
 * 原文颜色本身够浅且不透明时直接复用——深色主题里那些精心调过的正文色不该被换成通用灰。
 */
private fun nightText(colorArgb: Int, backgroundArgb: Int, targetLightness: Float): Int {
    val opaque = colorArgb or OPAQUE_MASK
    if (isOpaqueEnough(colorArgb) && contrastRatio(opaque, backgroundArgb) >= MIN_TEXT_CONTRAST) {
        return opaque
    }
    val hsl = colorArgb.toHsl()
    val saturation = min(hsl.saturation, NIGHT_TEXT_MAX_SATURATION)
    var lightness = targetLightness
    var candidate = hsl.toArgb(saturation, lightness)
    while (contrastRatio(candidate, backgroundArgb) < MIN_TEXT_CONTRAST &&
        lightness < MAX_NIGHT_LIGHTNESS
    ) {
        lightness += LIGHTNESS_STEP
        candidate = hsl.toArgb(saturation, lightness)
    }
    return candidate
}

/** 强调色只要「看得见」：对比度门槛用 3.0，色相与饱和度尽量保留。 */
private fun nightAccent(colorArgb: Int, backgroundArgb: Int): Int {
    val opaque = colorArgb or OPAQUE_MASK
    if (isOpaqueEnough(colorArgb) && contrastRatio(opaque, backgroundArgb) >= MIN_ACCENT_CONTRAST) {
        return opaque
    }
    val hsl = colorArgb.toHsl()
    val saturation = if (hsl.saturation < NIGHT_ACCENT_BASIC_SATURATION) {
        0f
    } else {
        hsl.saturation.coerceIn(NIGHT_ACCENT_MIN_SATURATION, NIGHT_ACCENT_MAX_SATURATION)
    }
    var lightness = NIGHT_ACCENT_TARGET_LIGHTNESS
    var candidate = hsl.toArgb(saturation, lightness)
    while (contrastRatio(candidate, backgroundArgb) < MIN_ACCENT_CONTRAST &&
        lightness < MAX_NIGHT_LIGHTNESS
    ) {
        lightness += LIGHTNESS_STEP
        candidate = hsl.toArgb(saturation, lightness)
    }
    return candidate
}

private fun isOpaqueEnough(argb: Int): Boolean = (argb ushr 24) >= MIN_OPAQUE_ALPHA

/** 色相 0..360，饱和度/明度 0..1。 */
private data class Hsl(
    val hue: Float,
    val saturation: Float,
    val lightness: Float,
)

private fun Int.toHsl(): Hsl {
    val red = ((this shr 16) and 0xFF) / 255f
    val green = ((this shr 8) and 0xFF) / 255f
    val blue = (this and 0xFF) / 255f
    val maxChannel = max(red, max(green, blue))
    val minChannel = min(red, min(green, blue))
    val delta = maxChannel - minChannel
    val lightness = (maxChannel + minChannel) / 2f
    val saturation = if (delta == 0f) {
        0f
    } else {
        delta / (1f - abs(2f * lightness - 1f))
    }
    val hue = when {
        delta == 0f -> 0f
        maxChannel == red -> 60f * (((green - blue) / delta) % 6f)
        maxChannel == green -> 60f * ((blue - red) / delta + 2f)
        else -> 60f * ((red - green) / delta + 4f)
    }
    return Hsl(
        hue = (hue + 360f) % 360f,
        saturation = saturation.coerceIn(0f, 1f),
        lightness = lightness.coerceIn(0f, 1f),
    )
}

private fun Hsl.toArgb(saturation: Float, lightness: Float): Int {
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)
    val chroma = (1f - abs(2f * l - 1f)) * s
    val section = ((hue % 360f) + 360f) % 360f / 60f
    val second = chroma * (1f - abs(section % 2f - 1f))
    val (red, green, blue) = when {
        section < 1f -> Triple(chroma, second, 0f)
        section < 2f -> Triple(second, chroma, 0f)
        section < 3f -> Triple(0f, chroma, second)
        section < 4f -> Triple(0f, second, chroma)
        section < 5f -> Triple(second, 0f, chroma)
        else -> Triple(chroma, 0f, second)
    }
    val offset = l - chroma / 2f
    return OPAQUE_MASK or
        (channel(red + offset) shl 16) or
        (channel(green + offset) shl 8) or
        channel(blue + offset)
}

private fun channel(value: Float): Int = (value * 255f).roundToInt().coerceIn(0, 255)
