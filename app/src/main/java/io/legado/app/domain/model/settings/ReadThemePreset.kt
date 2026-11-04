package io.legado.app.domain.model.settings

import androidx.annotation.Keep
import androidx.compose.runtime.Stable
import kotlin.math.pow

/**
 * 内置阅读主题（来源：清墨 LightInk 主题商店接口快照，见 `assets/defaultData/readThemes.json`）。
 *
 * `@Keep`：这是 Gson 反序列化的 DTO，字段靠反射写入。Release 开了 R8，靠名字找属性的
 * 反序列化类必须在 proguard-rules.pro 里保留——仓库既有做法是就地标注（见
 * `domain/model/HomepageModels.kt`），不要靠维护 proguard 白名单。
 *
 * 字段与 LightInk 主题编辑器一一对应：
 * - [background] 阅读背景（按钮等背景也复用它）
 * - [foreground] 目录背景，Legado 侧暂无对应的持久化槽位，保留原值以便对照数据源
 * - [content] 正文与标题
 * - [secondary] 其余文字（Legado 侧映射到页眉页脚）
 * - [control] 菜单标记与图标（Legado 侧映射到强调色）
 * - [horizontal] / [top] / [bottom] 边距
 * - [mipmap] 阅读背景图，可为空
 *
 * 颜色是 `#AARRGGBB`。
 */
@Keep
@Stable
data class ReadThemePreset(
    val id: Int,
    val name: String,
    val author: String = "",
    val downloadCount: Int = 0,
    val background: String,
    val foreground: String = "",
    val content: String,
    val secondary: String,
    val control: String,
    val horizontal: Int = 0,
    val top: Int = 0,
    val bottom: Int = 0,
    val mipmap: String? = null,
) {
    /**
     * 底色是否属于深色。
     *
     * 用 `get()` 而不是构造期字段：Gson 反序列化 data class 走反射写字段、不执行构造体，
     * 构造期算出来的值会停在默认值（同 `ReadConfigJsonRoundTripTest` 钉住的那条约束）。
     *
     * 不用接口里的 `dark` 字段：它在全部主题里都是 false，区分不出深浅。
     */
    val isDark: Boolean get() = isDarkBackgroundColor(background)

    /**
     * 底色是否已经深到可以直接当夜间底。
     *
     * 与 [isDark] 分开：`isDark` 按 0.5 相对亮度判状态栏图标反色，米黄、中灰的纸也算「深色」；
     * 夜间模式要求更严（见 [NIGHT_BASE_MAX_LUMINANCE]），不够深的一律派生深色底。
     * 主题自带背景图时也用它决定夜间是否继续用那张图。
     */
    val isNightBase: Boolean
        get() = parseArgbColor(background)
            ?.let { relativeLuminance(it or 0xFF000000.toInt()) < NIGHT_BASE_MAX_LUMINANCE }
            ?: false
}

/**
 * 内置主题落到排版配置上的目标值。
 *
 * 与 `ReadBookConfig.Config` 分开，是为了让「边距钳制」「深浅判定」这些规则能在纯 JVM 单测里
 * 验证——直接改 Config 会经过 `ReadStyleResolver`，需要 Koin 才能跑起来。
 */
@Stable
data class ReadThemeStyle(
    val background: String,
    val content: String,
    val secondary: String,
    val control: String,
    val nightBackground: String,
    val nightContent: String,
    val nightSecondary: String,
    val nightControl: String,
    val paddingHorizontal: Int,
    val paddingTop: Int,
    val paddingBottom: Int,
    val darkStatusIcon: Boolean,
    val darkStatusIconNight: Boolean,
)

/**
 * 解析成可直接写入排版配置的目标值。
 *
 * 白天原样使用主题配色；夜间走 [deriveNightPalette]：底色够深的主题复用原色，浅色主题派生出
 * 一套深底浅字的夜间配色，保证切换日夜时阅读页真的变黑。状态栏图标按各自底色反色。
 */
fun ReadThemePreset.toStyle(): ReadThemeStyle {
    val night = deriveNightPalette(
        backgroundArgb = parseArgbColor(background) ?: FALLBACK_LIGHT_BACKGROUND,
        contentArgb = parseArgbColor(content) ?: FALLBACK_DARK_CONTENT,
        secondaryArgb = parseArgbColor(secondary) ?: FALLBACK_DARK_CONTENT,
        controlArgb = parseArgbColor(control) ?: FALLBACK_DARK_CONTENT,
    )
    return ReadThemeStyle(
        background = background,
        content = content,
        secondary = secondary,
        control = control,
        nightBackground = night.backgroundArgb.toArgbHex(),
        nightContent = night.contentArgb.toArgbHex(),
        nightSecondary = night.secondaryArgb.toArgbHex(),
        nightControl = night.controlArgb.toArgbHex(),
        paddingHorizontal = horizontal.asReadPadding(),
        paddingTop = top.asReadPadding(),
        paddingBottom = bottom.asReadPadding(),
        darkStatusIcon = !isDark,
        darkStatusIconNight = !isDarkBackgroundColor(night.backgroundArgb.toArgbHex()),
    )
}

/** 数据损坏时的兜底：按最常见的浅底深字处理，夜间派生仍是黑底浅字。 */
private const val FALLBACK_LIGHT_BACKGROUND = 0xFFF1F3F4.toInt()
private const val FALLBACK_DARK_CONTENT = 0xFF2D2D33.toInt()

/** LightInk 边距允许为负（数据里最小 -28），Legado 的 padding 直接当 dp 用，负值会算出负的可用宽度。 */
internal fun Int.asReadPadding(): Int = coerceAtLeast(0)

/** 深色判定阈值，与 `io.legado.app.utils.ColorUtils.isColorLight` 的口径一致。 */
private const val DARK_LUMINANCE_THRESHOLD = 0.5

/** 主题商店的图床：数据里 870 条形如 `http://minio.zhendong.ltd/...`，明文会被部分网络环境拦。 */
private const val THEME_ASSET_HOST = "minio.zhendong.ltd"

/**
 * 主题背景图地址的候选顺序。
 *
 * 该图床同时支持 https，先试加密地址是为了绕开明文拦截；失败方回落原始明文地址（清单里
 * `cleartextTrafficPermitted="true"`）。非该图床（如 `cdn-us.imgs.moe`）保持原样，不猜协议。
 */
fun themeMipmapUrlCandidates(url: String): List<String> {
    val plainPrefix = "http://$THEME_ASSET_HOST/"
    return if (url.startsWith(plainPrefix)) {
        listOf("https://${url.removePrefix("http://")}", url)
    } else {
        listOf(url)
    }
}

/** 主题列表缩略图优先用的地址（离线时 Coil 会退化成卡片自带的配色底）。 */
val ReadThemePreset.thumbnailUrl: String?
    get() = mipmap?.let { themeMipmapUrlCandidates(it).first() }

/**
 * 按 WCAG 相对亮度判断底色深浅。
 * 解析失败按浅色处理：主题列表里宁可显示深色文字，也不要因为一条脏数据崩掉。
 */
fun isDarkBackgroundColor(argbHex: String): Boolean {
    val argb = parseArgbColor(argbHex) ?: return false
    return relativeLuminance(argb) < DARK_LUMINANCE_THRESHOLD
}

/** 解析 `#AARRGGBB` / `#RRGGBB` / `#RGB`，失败返回 null。 */
fun parseArgbColor(value: String): Int? {
    val hex = value.removePrefix("#")
    return when (hex.length) {
        8 -> hex.toLongOrNull(16)?.toInt()
        6 -> hex.toLongOrNull(16)?.toInt()?.or(OPAQUE_ALPHA)
        3 -> parseArgbColor(hex.map { "$it$it" }.joinToString(""))
        else -> null
    }
}

private const val OPAQUE_ALPHA = 0xFF000000.toInt()

/** WCAG 相对亮度，0 = 黑，1 = 白。 */
fun relativeLuminance(argb: Int): Double {
    fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel((argb shr 16) and 0xFF) +
        0.7152 * channel((argb shr 8) and 0xFF) +
        0.0722 * channel(argb and 0xFF)
}
