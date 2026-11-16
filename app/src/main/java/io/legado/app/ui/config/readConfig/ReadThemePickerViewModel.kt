package io.legado.app.ui.config.readConfig

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.gateway.ReadStyleGateway
import io.legado.app.domain.model.settings.ReadStyleItem
import io.legado.app.domain.model.settings.ReadThemeTarget
import io.legado.app.help.DefaultData
import io.legado.app.ui.book.read.ConfigUpdate
import io.legado.app.ui.book.read.ReadConfigUpdateBus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReadThemePickerViewModel(
    private val readStyleGateway: ReadStyleGateway,
    private val readSettingsGateway: ReadSettingsGateway,
) : ViewModel() {

    private val _effects = MutableSharedFlow<ReadThemePickerEffect>(extraBufferCapacity = 4)
    val effects = _effects.asSharedFlow()

    /**
     * 当前生效样式的日间/夜间内置主题 id，用于在设置页标出「使用中」。
     *
     * 同时订阅排版网关与阅读设置：前者提供主题 id，后者提供当前选中的样式下标（切换样式
     * 只写设置，不会让排版网关重新发布状态）。列表下标越界时按未设置处理。
     */
    val appliedThemeIds: StateFlow<ReadThemePickerAppliedThemeIds> =
        combine(readStyleGateway.state, readSettingsGateway.settings) { state, settings ->
            val item = state.items.getOrNull(settings.readStyleSelect)
            ReadThemePickerAppliedThemeIds(
                day = item?.readThemeIdDay,
                night = item?.readThemeIdNight,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReadThemePickerAppliedThemeIds(),
        )

    /** 已保存的阅读背景（阅读页“背景”里那几份样式），用于设置页顶部横向预览。 */
    val readingStyles: StateFlow<ReadThemePickerStyles> =
        combine(readStyleGateway.state, readSettingsGateway.settings) { state, settings ->
            ReadThemePickerStyles(
                items = state.items.toImmutableList(),
                selectedIndex = settings.readStyleSelect,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReadThemePickerStyles(),
        )

    /**
     * 应用内置阅读主题：只写 [target] 那份背景色与状态栏图标，自带背景图异步下载后补上。
     *
     * 与阅读页 `ReadStyleDelegate.applyReadTheme` 同一套 gateway 调用，区别只是这里没有渲染器，
     * 不需要发 ConfigUpdate —— 正在阅读的会话订阅 gateway.state 会自行重建快照。
     */
    fun applyTheme(themeId: Int, target: ReadThemeTarget) {
        val theme = DefaultData.readThemes.firstOrNull { it.id == themeId } ?: return
        if (!readStyleGateway.applyReadTheme(themeId, target)) return
        if (theme.mipmap == null) return
        viewModelScope.launch {
            val path = readStyleGateway.downloadReadThemeBackground(theme)
            if (path == null) {
                _effects.tryEmit(ReadThemePickerEffect.BackgroundFailed)
                return@launch
            }
            readStyleGateway.setReadThemeBackgroundImage(path, target)
        }
    }

    /**
     * 把已保存的阅读背景设为当前样式。
     *
     * 与阅读页「背景」里点选样式同一条路径：先写 `readStyleSelect`，再通知活跃阅读器重载，
     * 否则设置页改完要等下次进书才生效。下标越界（样式被并发删除）时忽略。
     */
    fun selectStyle(index: Int) {
        if (index !in readStyleGateway.currentState.items.indices) return
        viewModelScope.launch {
            readSettingsGateway.update { settings -> settings.copy(readStyleSelect = index) }
            ReadConfigUpdateBus.post(ConfigUpdate.StyleSelect(index).actions)
        }
    }
}

data class ReadThemePickerAppliedThemeIds(
    val day: Int? = null,
    val night: Int? = null,
)

@Stable
data class ReadThemePickerStyles(
    val items: ImmutableList<ReadStyleItem> = persistentListOf(),
    val selectedIndex: Int = 0,
)

sealed interface ReadThemePickerEffect {
    /** 配色已应用，但主题自带背景图下载失败。 */
    data object BackgroundFailed : ReadThemePickerEffect
}
