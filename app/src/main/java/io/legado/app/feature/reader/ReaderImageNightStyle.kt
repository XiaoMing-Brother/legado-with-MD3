package io.legado.app.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.toArgb
import io.legado.app.feature.reader.core.model.ReaderImageNightMode
import io.legado.app.feature.reader.core.model.imageAlpha
import io.legado.app.feature.reader.core.model.invertsColors

/**
 * 插图夜景绘制的两个参数：降低亮度走 alpha（把图叠到页面底色上），反转走 ColorFilter。
 *
 * 只做一层值对象，让「用什么方式画插图」与「画到哪一页」解耦；取色判定留在
 * [ReaderImageNightMode] 的纯函数里，便于 JVM 单测。
 */
@Immutable
internal data class ReaderImageNightStyle(
    val alpha: Float = 1f,
    val colorFilter: ColorFilter? = null,
)

internal val DefaultReaderImageNightStyle = ReaderImageNightStyle()

/** 背景色变化（切日夜主题）或设置变化时才重建，绘制期零分配。 */
@Composable
internal fun rememberReaderImageNightStyle(
    mode: ReaderImageNightMode,
    background: Color,
): ReaderImageNightStyle = remember(mode, background) {
    val backgroundArgb = background.toArgb()
    ReaderImageNightStyle(
        alpha = mode.imageAlpha(backgroundArgb),
        colorFilter = if (mode.invertsColors(backgroundArgb)) ReaderImageInvertFilter else null,
    )
}

private val ReaderImageInvertFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
)
