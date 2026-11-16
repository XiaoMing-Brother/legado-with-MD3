package io.legado.app.ui.widget.components.card

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.text.AnimatedTextLine
import io.legado.app.ui.widget.components.text.AppText


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TextCard(
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    backgroundColor: Color? = null,
    contentColor: Color? = null,
    cornerRadius: Dp = 8.dp,
    horizontalPadding: Dp = 8.dp,
    verticalPadding: Dp = 4.dp,
    iconSize: Dp = 14.dp,
    spacing: Dp = 4.dp,
    textStyle: TextStyle = LegadoTheme.typography.labelSmallEmphasized,
    border: BorderStroke? = null,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    /**
     * 文本变化时是否播放滑动动画。
     *
     * 列表里（书架卡片角标）关掉：`AnimatedContent` 会为内容套一层 graphicsLayer，
     * 每一项一两个标签就是一两层 RenderNode，滚动时是纯绘制开销，而角标文字基本不变。
     */
    animateText: Boolean = true,
) {
    val defaultBackground = LegadoTheme.colorScheme.surfaceContainer
    val defaultContent = LegadoTheme.colorScheme.onSurface

    val finalBackgroundColor = backgroundColor ?: defaultBackground
    val finalContentColor = contentColor ?: defaultContent

    // 不经过 Surface：这里只画一块圆角底色，内容（文字/图标）不会被裁掉，
    // 每个 Surface 都是独立 RenderNode（书架列表里每个标签一块，实测每层约 0.2ms）。
    // 圆角仍遵循与 NormalCard 一致的全局覆盖设置。
    val themeSettings = LocalAppUiConfiguration.current.theme
    val resolvedCornerRadius = if (themeSettings.overrideBaseCardCornerRadius) {
        themeSettings.baseCardCornerRadius.dp
    } else {
        cornerRadius
    }
    val shape = RoundedCornerShape(resolvedCornerRadius)

    Row(
        modifier = modifier
            .background(finalBackgroundColor, shape)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .then(
                if (onClick != null) {
                    Modifier.clip(shape).clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically
    ) {

        if (icon != null) {
            AppIcon(
                imageVector = icon,
                contentDescription = null,
                tint = finalContentColor,
                modifier = Modifier.size(iconSize)
            )
        }

        if (icon != null && text != null)
            Spacer(modifier = Modifier.width(spacing))

        text?.let {
            if (animateText) {
                AnimatedTextLine(
                    text = it,
                    style = textStyle,
                    color = finalContentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                AppText(
                    text = it,
                    style = textStyle,
                    color = finalContentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

