package io.legado.app.ui.config.themeConfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.settings.ThemeSettings
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.CompactDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.CompactSwitchSettingItem
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem

@Composable
fun TopBottomBarSettingsSheet(
    show: Boolean,
    theme: ThemeSettings,
    isMiuixEngine: Boolean,
    onDismissRequest: () -> Unit,
    onIntent: (ThemeConfigIntent) -> Unit,
) {
    fun updateTheme(transform: (ThemeSettings) -> ThemeSettings) =
        onIntent(ThemeConfigIntent.UpdateTheme(transform))

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.top_bottom_bar_settings),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!isMiuixEngine) {
                CompactSwitchSettingItem(
                    title = stringResource(R.string.use_flexible_top_bar),
                    checked = theme.useFlexibleTopAppBar,
                    onCheckedChange = { checked ->
                        updateTheme { current -> current.copy(useFlexibleTopAppBar = checked) }
                    },
                )
            }
            CompactDropdownSettingItem(
                title = stringResource(R.string.top_bar_button_style),
                selectedValue = theme.topBarButtonStyle,
                displayEntries = stringArrayResource(R.array.top_bar_button_style),
                entryValues = stringArrayResource(R.array.top_bar_button_style_value),
                onValueChange = { value -> updateTheme { it.copy(topBarButtonStyle = value) } },
            )
            if (theme.topBarButtonStyle != "plain") {
                CompactSwitchSettingItem(
                    title = stringResource(R.string.merge_top_bar_actions),
                    checked = theme.mergeTopBarActions,
                    onCheckedChange = { checked ->
                        updateTheme { current -> current.copy(mergeTopBarActions = checked) }
                    },
                )
            }
            if (theme.enableBlur) {
                SliderSettingItem(
                    title = stringResource(R.string.theme_manage_top_bar_blur_radius),
                    description = stringResource(R.string.theme_config_blur_radius_performance_summary),
                    value = theme.topBarBlurRadius.toFloat(),
                    defaultValue = 24f,
                    valueRange = 0f..30f,
                    onValueChange = { value ->
                        updateTheme { current -> current.copy(topBarBlurRadius = value.toInt()) }
                    },
                )
                SliderSettingItem(
                    title = stringResource(R.string.theme_manage_top_bar_blur_opacity),
                    value = theme.topBarBlurAlpha.toFloat(),
                    defaultValue = 73f,
                    valueRange = 0f..100f,
                    onValueChange = { value ->
                        updateTheme { current -> current.copy(topBarBlurAlpha = value.toInt()) }
                    },
                )
            } else if (!isMiuixEngine) {
                SliderSettingItem(
                    title = stringResource(R.string.top_bar_opacity),
                    description = stringResource(
                        R.string.top_bar_opacity_summary,
                        theme.topBarOpacity
                    ),
                    value = theme.topBarOpacity.toFloat(),
                    defaultValue = 100f,
                    valueRange = 0f..100f,
                    onValueChange = { value ->
                        updateTheme { current -> current.copy(topBarOpacity = value.toInt()) }
                    },
                )
            }
        }
    }
}
