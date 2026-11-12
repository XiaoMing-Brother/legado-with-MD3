package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.legado.app.R
import io.legado.app.domain.model.settings.ReadThemePreset
import io.legado.app.domain.model.settings.ReadThemeTarget
import io.legado.app.domain.model.settings.parseArgbColor
import io.legado.app.domain.model.settings.thumbnailUrl
import io.legado.app.help.DefaultData
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.tabRow.AppTabRow
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
    onSelectTheme: (Int, ReadThemeTarget) -> Unit,
    initialDayThemeId: Int? = null,
    initialNightThemeId: Int? = null,
) {
    // 本 composable 一直挂在阅读屏上（show 控制显隐），所以这里按 show 取值：
    // DefaultData.readThemes 是 300KB 资源的首次解析，不该算进开书那条热路径。
    val themes = remember(show) {
        if (show) DefaultData.readThemes else emptyList()
    }
    // 日间与夜间各记「这次打开选过哪个」，用于列表内的选中反馈；持久状态在排版配置里，不在这。
    var appliedDayThemeId by remember(show, initialDayThemeId) {
        mutableStateOf(initialDayThemeId)
    }
    var appliedNightThemeId by remember(show, initialNightThemeId) {
        mutableStateOf(initialNightThemeId)
    }
    val initialTarget = if (LegadoTheme.isDark) ReadThemeTarget.Night else ReadThemeTarget.Day
    var target by remember(show) {
        mutableStateOf(
            initialTarget
        )
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.read_theme),
    ) {
        ReadThemePickerContent(
            themes = themes,
            target = target,
            appliedDayThemeId = appliedDayThemeId,
            appliedNightThemeId = appliedNightThemeId,
            modifier = Modifier.fillMaxWidth(),
            onTargetChange = { target = it },
            onSelectTheme = { themeId, target ->
                when (target) {
                    ReadThemeTarget.Day -> appliedDayThemeId = themeId
                    ReadThemeTarget.Night -> appliedNightThemeId = themeId
                }
                onSelectTheme(themeId, target)
            },
            gridModifier = Modifier.height(360.dp),
            gridContentPadding = PaddingValues(vertical = 8.dp),
        )
    }
}

/**
 * 内置主题选择内容：日间/夜间切换 + 卡片网格。
 *
 * 阅读页弹层与设置里的整页共用这一块；[themes] 由调用方决定何时解析（弹层按 `show` 惰性取，
 * 整页直接取），避免把 300KB 资源的解析算进阅读屏热路径。
 *
 * 日间列表只放浅色主题，夜间列表只放 [ReadThemePreset.isNightTheme] 的深底浅字主题；默认停留的
 * 列表跟随当前阅读模式（夜间打开就落在夜间主题）。两个列表各自记自己的已选主题。
 */
@Composable
fun ReadThemePickerContent(
    themes: List<ReadThemePreset>,
    onSelectTheme: (Int, ReadThemeTarget) -> Unit,
    target: ReadThemeTarget,
    onTargetChange: (ReadThemeTarget) -> Unit,
    modifier: Modifier = Modifier,
    appliedDayThemeId: Int? = null,
    appliedNightThemeId: Int? = null,
    gridModifier: Modifier = Modifier,
    gridContentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    columns: GridCells = GridCells.Fixed(3),
    header: (@Composable () -> Unit)? = null,
) {
    // 只在切换列表时过滤一次：1027 条里夜间主题不到 5%，其余都归日间。
    val visibleThemes = remember(themes, target) {
        themes.filter { theme ->
            when (target) {
                ReadThemeTarget.Day -> !theme.isNightTheme
                ReadThemeTarget.Night -> theme.isNightTheme
            }
        }
    }
    val appliedThemeId = when (target) {
        ReadThemeTarget.Day -> appliedDayThemeId
        ReadThemeTarget.Night -> appliedNightThemeId
    }
    if (header != null) {
        // 整页模式：头部也进入网格，整块内容共用一个滚动容器，才能从顶栏背面经过。
        val gridState = rememberLazyGridState()
        // 切换日/夜间后列表内容不同，回到顶部避免停在上一个列表的滚动位置。
        LaunchedEffect(target) {
            gridState.scrollToItem(0)
        }
        LazyVerticalGrid(
            columns = columns,
            state = gridState,
            contentPadding = gridContentPadding,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier.fillMaxSize(),
        ) {
            // 整页把说明收进顶栏的信息弹窗，首屏只留已保存背景与主题网格。
            item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                header()
            }
            ReadThemeGridItems(
                themes = visibleThemes,
                appliedThemeId = appliedThemeId,
                onSelectTheme = onSelectTheme,
                target = target,
            )
        }
    } else {
        // 弹层模式：页签固定在网格上方，不随卡片滚动。
        Column(modifier = modifier.fillMaxWidth()) {
            AppText(
                text = stringResource(R.string.read_theme_summary),
                style = LegadoTheme.typography.labelSmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            ReadThemeTargetTabs(
                target = target,
                onTargetChange = onTargetChange,
            )
            LazyVerticalGrid(
                columns = columns,
                contentPadding = gridContentPadding,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = gridModifier.fillMaxWidth(),
            ) {
                ReadThemeGridItems(
                    themes = visibleThemes,
                    appliedThemeId = appliedThemeId,
                    onSelectTheme = onSelectTheme,
                    target = target,
                )
            }
        }
    }
}

/** 主题卡片区；空列表时占满整行显示提示。 */
private fun LazyGridScope.ReadThemeGridItems(
    themes: List<ReadThemePreset>,
    appliedThemeId: Int?,
    onSelectTheme: (Int, ReadThemeTarget) -> Unit,
    target: ReadThemeTarget,
) {
    if (themes.isEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
            ReadThemeEmpty()
        }
    } else {
        items(themes, key = { it.id }) { theme ->
            ReadThemeCard(
                theme = theme,
                isApplied = theme.id == appliedThemeId,
                onClick = { onSelectTheme(theme.id, target) },
            )
        }
    }
}

/** 日间/夜间页签；设置整页放进顶栏底部，阅读弹层固定在网格上方。 */
@Composable
fun ReadThemeTargetTabs(
    target: ReadThemeTarget,
    onTargetChange: (ReadThemeTarget) -> Unit,
) {
    val targets = remember { listOf(ReadThemeTarget.Day, ReadThemeTarget.Night) }
    AppTabRow(
        tabTitles = listOf(
            stringResource(R.string.read_theme_day),
            stringResource(R.string.read_theme_night),
        ),
        selectedTabIndex = targets.indexOf(target),
        onTabSelected = { onTargetChange(targets[it]) },
        isScrollable = false,
    )
}

@Composable
private fun ReadThemeEmpty() {
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
}

@Composable
private fun ReadThemeCard(
    theme: ReadThemePreset,
    isApplied: Boolean,
    onClick: () -> Unit,
) {
    // 数据里有全透明底色（`#00000000`）与脏值，解析不出来就回落到主题容器色，别画成透明块。
    val background = theme.background.toComposeColor(LegadoTheme.colorScheme.surfaceContainer)
        .let { if (it.alpha == 0f) LegadoTheme.colorScheme.surfaceContainer else it }
    val content = theme.content.toComposeColor(LegadoTheme.colorScheme.onSurface)
    val secondary = theme.secondary.toComposeColor(LegadoTheme.colorScheme.onSurfaceVariant)
    val control = theme.control.toComposeColor(LegadoTheme.colorScheme.primary)
    // 目录/菜单底色。数据里 1027 条全都有值，但没有能落盘的槽位，只用于无图主题的配色展示。
    val directory = theme.foreground.toComposeColor(background)
    val thumbnail = theme.thumbnailUrl
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
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
            .selectable(selected = isApplied, onClick = onClick),
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
            // 纯配色主题没有缩略图，用整卡五色竖条当预览，避免方形框里只放一小条色块。
            Row(modifier = Modifier.fillMaxSize()) {
                listOf(content, control, secondary, directory, background).forEach { swatch ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(swatch)
                    )
                }
            }
        }

        // 用主题底色做底部渐变，保证 content/secondary 在背景图上仍可读。
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.44f)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to background.copy(alpha = 0.82f),
                        1f to background.copy(alpha = 0.97f),
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
        ) {
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
                if (isApplied) {
                    AppText(
                        text = stringResource(R.string.read_theme_in_use),
                        style = LegadoTheme.typography.labelSmall,
                        color = control,
                        maxLines = 1,
                    )
                }
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

        if (isApplied) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(LegadoTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = LegadoTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** 数据里的颜色是 `#AARRGGBB`，解析失败时回落到主题色。 */
private fun String.toComposeColor(fallback: Color): Color =
    parseArgbColor(this)?.let(::Color) ?: fallback
