package io.legado.app.ui.config.readConfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.settings.ReadThemeTarget
import io.legado.app.help.DefaultData
import io.legado.app.ui.book.read.sheet.ReadThemePickerContent
import io.legado.app.ui.book.read.sheet.ReadThemeTargetTabs
import io.legado.app.ui.book.read.sheet.StyleCard
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton

/**
 * 设置里的内置主题整页：内容与阅读页弹层共用 [ReadThemePickerContent]。
 *
 * 只在设置入口使用；阅读页仍保留弹层，避免打断阅读上下文。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadThemePickerScreen(
    onBackClick: () -> Unit,
    onSelectTheme: (Int, ReadThemeTarget) -> Unit,
    onSelectSavedStyle: (Int) -> Unit = {},
    appliedThemeIds: ReadThemePickerAppliedThemeIds = ReadThemePickerAppliedThemeIds(),
    readingStyles: ReadThemePickerStyles = ReadThemePickerStyles(),
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    // 整页打开即解析一次（1027 条、约 300KB），与阅读屏热路径无关。
    val themes = remember { DefaultData.readThemes }
    // 页签固定在顶栏，滚动时不会跟着列表一起滑走。
    val initialTarget = if (LegadoTheme.isDark) ReadThemeTarget.Night else ReadThemeTarget.Day
    var targetOrdinal by rememberSaveable {
        mutableStateOf(initialTarget.ordinal)
    }
    val target = ReadThemeTarget.entries[targetOrdinal]
    var showSummary by rememberSaveable { mutableStateOf(false) }
    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.read_theme),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                },
                actions = {
                    TopBarActionButton(
                        onClick = { showSummary = true },
                        imageVector = AppIcons.Info,
                        contentDescription = stringResource(R.string.read_theme_summary_title),
                    )
                },
                bottomContent = {
                    ReadThemeTargetTabs(
                        target = target,
                        onTargetChange = { targetOrdinal = it.ordinal },
                    )
                }
            )
        }
    ) { paddingValues ->
        ReadThemePickerContent(
            themes = themes,
            appliedDayThemeId = appliedThemeIds.day,
            appliedNightThemeId = appliedThemeIds.night,
            onSelectTheme = onSelectTheme,
            modifier = Modifier.fillMaxSize(),
            target = target,
            onTargetChange = { targetOrdinal = it.ordinal },
            gridContentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 24.dp,
            ),
            columns = GridCells.Adaptive(minSize = 112.dp),
            header = {
                SavedReadingBackgrounds(
                    styles = readingStyles,
                    onSelect = onSelectSavedStyle,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            },
        )
    }
    AppAlertDialog(
        show = showSummary,
        onDismissRequest = { showSummary = false },
        title = stringResource(R.string.read_theme_summary_title),
        text = stringResource(R.string.read_theme_summary),
        confirmText = stringResource(R.string.ok),
        onConfirm = { showSummary = false },
    )
}

/**
 * 设置页顶部：横向预览阅读页“背景”里已保存的那几份阅读样式。
 *
 * 首屏空间有限：标题压成行首小标签、卡片只留背景预览（名称进无障碍描述），
 * 整块约 50dp 高；滚动后仍随内容一起让位给主题网格。
 */
@Composable
private fun SavedReadingBackgrounds(
    styles: ReadThemePickerStyles,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (styles.items.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = stringResource(R.string.read_theme_saved_backgrounds),
            style = LegadoTheme.typography.labelSmall,
            color = LegadoTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, end = 8.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(end = 4.dp),
            modifier = Modifier.weight(1f),
        ) {
            itemsIndexed(styles.items, key = { _, item -> item.id }) { index, item ->
                StyleCard(
                    config = item,
                    isSelected = index == styles.selectedIndex,
                    isNightTheme = LegadoTheme.isDark,
                    showName = false,
                    modifier = Modifier
                        .width(36.dp)
                        .height(46.dp),
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}
