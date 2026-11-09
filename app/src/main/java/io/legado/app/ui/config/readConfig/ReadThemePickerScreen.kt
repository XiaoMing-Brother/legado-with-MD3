package io.legado.app.ui.config.readConfig

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.settings.ReadThemeTarget
import io.legado.app.help.DefaultData
import io.legado.app.ui.book.read.sheet.ReadThemePickerContent
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
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
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    // 整页打开即解析一次（1027 条、约 300KB），与阅读屏热路径无关。
    val themes = remember { DefaultData.readThemes }
    // 日间/夜间各自记这次打开选过哪个，仅用于列表勾选反馈。
    var appliedDayThemeId by rememberSaveable { mutableStateOf<Int?>(null) }
    var appliedNightThemeId by rememberSaveable { mutableStateOf<Int?>(null) }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.read_theme),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    adaptiveContentPadding(
                        top = paddingValues.calculateTopPadding(),
                        bottom = 0.dp,
                    )
                ),
        ) {
            ReadThemePickerContent(
                themes = themes,
                appliedDayThemeId = appliedDayThemeId,
                appliedNightThemeId = appliedNightThemeId,
                onSelectTheme = { themeId, target ->
                    when (target) {
                        ReadThemeTarget.Day -> appliedDayThemeId = themeId
                        ReadThemeTarget.Night -> appliedNightThemeId = themeId
                    }
                    onSelectTheme(themeId, target)
                },
                modifier = Modifier.fillMaxSize(),
                gridModifier = Modifier.weight(1f),
                gridContentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = paddingValues.calculateBottomPadding() + 24.dp,
                ),
                columns = GridCells.Adaptive(minSize = 112.dp),
            )
        }
    }
}
