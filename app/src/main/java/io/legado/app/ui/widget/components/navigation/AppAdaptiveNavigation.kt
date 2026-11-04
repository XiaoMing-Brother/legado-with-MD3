package io.legado.app.ui.widget.components.navigation

import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.IntSize
import androidx.window.core.layout.WindowSizeClass

@Stable
enum class MultiPaneMode(val value: String) {
    Auto("auto"),
    Landscape("landscape"),
    Always("always"),
    Off("off");

    companion object {
        fun parse(raw: String?): MultiPaneMode = entries.firstOrNull { it.value == raw } ?: Auto
    }
}

/**
 * 自动和横屏模式使用 840dp 断点；始终开启允许较小窗口，但保留
 * 360dp 左栏 + 24dp 间隔 + 360dp 右栏的最低空间。方向按当前窗口判断。
 */
fun shouldUseListDetail(
    multiPaneMode: String,
    windowSizeClass: WindowSizeClass,
    windowSize: IntSize,
): Boolean = when (MultiPaneMode.parse(multiPaneMode)) {
    MultiPaneMode.Auto -> windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND
    )

    MultiPaneMode.Landscape -> windowSize.width > windowSize.height &&
            windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

    MultiPaneMode.Always -> windowSizeClass.isWidthAtLeastBreakpoint(744)
    MultiPaneMode.Off -> false
}

