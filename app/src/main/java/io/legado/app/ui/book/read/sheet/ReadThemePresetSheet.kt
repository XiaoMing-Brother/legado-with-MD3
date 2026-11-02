package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.legado.app.R
import io.legado.app.domain.model.settings.ReadThemePreset
import io.legado.app.domain.model.settings.parseArgbColor
import io.legado.app.domain.model.settings.thumbnailUrl
import io.legado.app.help.DefaultData
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText

/**
 * 内置阅读主题选择（清墨 LightInk 主题商店快照，1027 条）。
 *
 * 列表里只加载**远程缩略图**：主题自带背景图有 966 条、体积远大于缩略图，逐条下载到本地
 * 既慢又占空间。真正落盘发生在用户点选主题之后（`ReadStyleDelegate.applyReadTheme`）。
 * 离线或图床不可达时卡片自带的配色底仍然可见，列表不会因为下载失败而残缺。
 *
 * 数据已按下载量降序排列，因此这里不排序、只过滤；同名主题靠 id 区分。
 */
@Composable
fun ReadThemePresetSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onSelectTheme: (Int) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // 只记「这次打开选过哪个」，用于列表内的选中反馈；持久状态在排版配置里，不在这。
    var appliedThemeId by remember(show) { mutableStateOf<Int?>(null) }
    // 本 composable 一直挂在阅读屏上（show 控制显隐），所以这里按 show 取值：
    // DefaultData.readThemes 是 300KB 资源的首次解析，不该算进开书那条热路径。
    val themes = remember(show) {
        if (show) DefaultData.readThemes else emptyList()
    }
    val filtered = remember(query, themes) {
        val keyword = query.trim()
        if (keyword.isEmpty()) {
            themes
        } else {
            // Gson 反射写字段、不走构造体，缺字段的引用类型字段会留 null 而不是声明默认值
            // （见 ReadThemePresetTest「缺 mipmap 的主题只有配色」），所以这里统一 orEmpty()。
            themes.filter { theme ->
                theme.name.orEmpty().contains(keyword, ignoreCase = true) ||
                    theme.author.orEmpty().contains(keyword, ignoreCase = true)
            }
        }
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.read_theme),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AppText(
                text = stringResource(R.string.read_theme_summary),
                style = LegadoTheme.typography.labelSmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            SearchBar(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.search_placeholder),
                autoFocus = false,
            )
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        text = stringResource(R.string.empty),
                        style = LegadoTheme.typography.bodyMedium,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                ) {
                    items(filtered, key = { it.id }) { theme ->
                        ReadThemeCard(
                            theme = theme,
                            isApplied = theme.id == appliedThemeId,
                            onClick = {
                                appliedThemeId = theme.id
                                onSelectTheme(theme.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadThemeCard(
    theme: ReadThemePreset,
    isApplied: Boolean,
    onClick: () -> Unit,
) {
    // 数据里有全透明底色（`#00000000`）与脏值，解析不出来就回落到主题容器色，别画成透明块。
    val background = theme.background.toComposeColor(LegadoTheme.colorScheme.surfaceContainer)
    val content = theme.content.toComposeColor(LegadoTheme.colorScheme.onSurface)
    val secondary = theme.secondary.toComposeColor(LegadoTheme.colorScheme.onSurfaceVariant)
    val control = theme.control.toComposeColor(LegadoTheme.colorScheme.primary)
    // 目录/菜单底色。数据里 1027 条全都有值，但没有能落盘的槽位，因此只在预览里体现。
    val directory = theme.foreground.toComposeColor(background)
    val thumbnail = theme.thumbnailUrl
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(
                width = if (isApplied) 2.dp else 1.dp,
                color = if (isApplied) {
                    LegadoTheme.colorScheme.primary
                } else {
                    content.copy(alpha = 0.12f)
                },
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(background),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnail != null) {
                // 加载失败时不覆盖配色底：主题本来就有底色，露出它比给错误占位符更好看。
                AsyncImage(
                    model = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // 纯配色主题没有缩略图，用它的调色板自己当预览，比只写一句「纯配色」好挑。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    listOf(content, control, secondary, directory, background).forEach { swatch ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(swatch)
                        )
                    }
                }
            }
            // 目录底色（foreground）是主题里唯一没有落盘槽位的字段，缩略图里也不一定体现得出来，
            // 因此在卡片底部补一条窄色带，保证这个字段在选主题时可见。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .align(Alignment.BottomCenter)
                    .background(directory),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(control)
            )
            Spacer(Modifier.width(4.dp))
            AppText(
                text = theme.name.orEmpty(),
                style = LegadoTheme.typography.labelMediumEmphasized,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        AppText(
            text = theme.author.orEmpty()
                .ifBlank { stringResource(R.string.read_theme_download_count, theme.downloadCount) },
            style = LegadoTheme.typography.labelSmall,
            color = secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 数据里的颜色是 `#AARRGGBB`，解析失败时回落到主题色。 */
private fun String.toComposeColor(fallback: Color): Color =
    parseArgbColor(this)?.let(::Color) ?: fallback
