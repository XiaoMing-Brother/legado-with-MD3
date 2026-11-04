package io.legado.app.feature.reader.core.model

/**
 * 阅读器插图在深色页面上的处理方式（对齐「阅微」的插图优化：色彩反转 / 降低亮度）。
 *
 * 只在深色页面生效：用户选择的是「夜里怎么处理插图」，白天一律按原图绘制。
 * 取值与 `PreferKey.imageNightMode` 落盘的字符串一一对应，与 `clickImgWay` 这类
 * 字符串设置保持同一种形态，避免为单个开关引入枚举序列化。
 */
enum class ReaderImageNightMode {
    NONE,
    INVERT,
    DIM;

    companion object {
        fun fromPref(value: String?): ReaderImageNightMode = when (value) {
            "1" -> INVERT
            "2" -> DIM
            else -> NONE
        }
    }
}

/** 「降低亮度」时前景图叠加的不透明度：越低越贴近页面底色。 */
private const val DIM_ALPHA = 0.55f

/** 深色页面判定：按 sRGB 相对亮度的常用近似权重，阈值取中值。 */
internal fun isDarkReaderBackground(backgroundArgb: Int): Boolean {
    val red = (backgroundArgb shr 16) and 0xFF
    val green = (backgroundArgb shr 8) and 0xFF
    val blue = backgroundArgb and 0xFF
    val luminance = (0.2126f * red + 0.7152f * green + 0.0722f * blue) / 255f
    return luminance < 0.5f
}

internal fun ReaderImageNightMode.appliesToBackground(backgroundArgb: Int): Boolean =
    this != ReaderImageNightMode.NONE && isDarkReaderBackground(backgroundArgb)

/** 绘制插图时使用的不透明度。 */
internal fun ReaderImageNightMode.imageAlpha(backgroundArgb: Int): Float =
    if (this == ReaderImageNightMode.DIM && appliesToBackground(backgroundArgb)) DIM_ALPHA else 1f

/** 是否需要给插图套反色矩阵。 */
internal fun ReaderImageNightMode.invertsColors(backgroundArgb: Int): Boolean =
    this == ReaderImageNightMode.INVERT && appliesToBackground(backgroundArgb)
