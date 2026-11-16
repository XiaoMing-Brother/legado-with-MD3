package io.legado.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * 全局形状档位。
 *
 * 仓库里所有 Material 组件（按钮、菜单、底部弹层、对话框、输入框、Snackbar、Chip……）的圆角
 * 都取自 `MaterialTheme.shapes`，因此这里是"整机风格"唯一需要改的地方：一旦主题开了
 * 「卡片圆角覆盖」，五档圆角就按同一基准缩放，卡片、弹层、菜单、按钮不再是几套互不相干的弧度。
 *
 * 没开覆盖的主题继续用 M3 默认形状——也就是老用户观感不变。
 */
@Composable
fun appShapes(): Shapes {
    val themeSettings = LocalAppUiConfiguration.current.theme
    if (!themeSettings.overrideBaseCardCornerRadius) return Shapes()
    val base = themeSettings.baseCardCornerRadius.dp
    return Shapes(
        extraSmall = RoundedCornerShape(base * 0.25f),
        small = RoundedCornerShape(base * 0.5f),
        medium = RoundedCornerShape(base * 0.75f),
        large = RoundedCornerShape(base),
        extraLarge = RoundedCornerShape(base * 1.2f),
    )
}
