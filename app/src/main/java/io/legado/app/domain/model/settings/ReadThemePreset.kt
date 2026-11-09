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
 * 字段与 LightInk 主题编辑器一一对应。Legado 只消费底色（[background]）与背景图
 * （[mipmap]）：字体颜色、字号、边距是用户的排版设置，不随主题覆盖；其余字段保留原值以便
 * 对照数据源。
 * - [background] 阅读背景（按钮等背景也复用它）
 * - [foreground] 目录背景，Legado 侧暂无对应的持久化槽位
 * - [content] 主题正文与标题色（不应用）
 * - [secondary] 主题其余文字色（不应用）
 * - [control] 主题强调色（不应用）
 * - [horizontal] / [top] / [bottom] 主题边距（不应用）
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

    /**
     * 主题是否归入「夜间主题」列表：底色偏深，且正文色比底色亮。
     *
     * 数据里的 `dark` 字段全是 false，只能自己判。只看底色会漏掉 `#ff4a4a4d`（暗）这类
     * 亮度 0.07、没到 [isNightBase] 却明显是深色配白字的主题；只看正文色又会把浅纸上
     * 误配白字的脏数据算进来，所以两者都要满足。解析失败按日间处理。
     */
    val isNightTheme: Boolean
        get() {
            if (!isDark) return false
            val backgroundArgb = parseArgbColor(background) ?: return false
            val contentArgb = parseArgbColor(content) ?: return isNightBase
            return relativeLuminance(contentArgb or 0xFF000000.toInt()) >
                relativeLuminance(backgroundArgb or 0xFF000000.toInt())
        }
}

/**
 * 内置主题落到阅读样式上的目标值。
 *
 * 只含背景与状态栏图标：字体颜色、字号、页边距都属于用户自己的排版设置，应用主题时不得覆盖。
 * 与 `ReadBookConfig.Config` 分开，是为了让「深浅判定」「夜间底色派生」这些规则能在纯 JVM
 * 单测里验证——直接改 Config 会经过 `ReadStyleResolver`，需要 Koin 才能跑起来。
 */
@Stable
data class ReadThemeStyle(
    val background: String,
    val nightBackground: String,
    val darkStatusIcon: Boolean,
    val darkStatusIconNight: Boolean,
)

/**
 * 内置主题的应用目标。
 *
 * 日间与夜间是两份独立背景（`bgStr` / `bgStrNight`）：选日间主题只改日间与墨水屏，
 * 选夜间主题只改夜间，互不覆盖——与清墨的「日间主题 / 夜间主题」两个列表一致。
 */
enum class ReadThemeTarget { Day, Night }

/**
 * 解析成可直接写入样式的背景目标值。
 *
 * 白天原样使用主题底色；夜间走 [deriveNightBackground]：底色够深的主题复用原色，浅色主题
 * 派生出深色底。夜间列表只列 [ReadThemePreset.isNightTheme] 的主题，派生只是兜底；
 * 状态栏图标按各自底色反色。
 */
fun ReadThemePreset.toStyle(): ReadThemeStyle {
    val nightBackgroundArgb = deriveNightBackground(
        parseArgbColor(background) ?: FALLBACK_LIGHT_BACKGROUND
    )
    return ReadThemeStyle(
        background = background,
        nightBackground = nightBackgroundArgb.toArgbHex(),
        darkStatusIcon = !isDark,
        darkStatusIconNight = !isDarkBackgroundColor(nightBackgroundArgb.toArgbHex()),
    )
}

/** 数据损坏时的兜底：按最常见的浅色纸处理，夜间派生仍是深色底。 */
private const val FALLBACK_LIGHT_BACKGROUND = 0xFFF1F3F4.toInt()

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
