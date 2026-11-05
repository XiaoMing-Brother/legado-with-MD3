package io.legado.app.ui.config.readConfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.ReadStyleGateway
import io.legado.app.help.DefaultData
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class ReadThemePickerViewModel(
    private val readStyleGateway: ReadStyleGateway,
) : ViewModel() {

    private val _effects = MutableSharedFlow<ReadThemePickerEffect>(extraBufferCapacity = 4)
    val effects = _effects.asSharedFlow()

    /**
     * 应用内置阅读主题：配色、状态栏图标与边距同步写入，自带背景图异步下载后补上。
     *
     * 与阅读页 `ReadStyleDelegate.applyReadTheme` 同一套 gateway 调用，区别只是这里没有渲染器，
     * 不需要发 ConfigUpdate —— 正在阅读的会话订阅 gateway.state 会自行重建快照。
     */
    fun applyTheme(themeId: Int) {
        val theme = DefaultData.readThemes.firstOrNull { it.id == themeId } ?: return
        if (!readStyleGateway.applyReadTheme(themeId)) return
        if (theme.mipmap == null) return
        viewModelScope.launch {
            val path = readStyleGateway.downloadReadThemeBackground(theme)
            if (path == null) {
                _effects.tryEmit(ReadThemePickerEffect.BackgroundFailed)
                return@launch
            }
            readStyleGateway.setReadThemeBackgroundImage(path, useForNight = theme.isNightBase)
        }
    }
}

sealed interface ReadThemePickerEffect {
    /** 配色已应用，但主题自带背景图下载失败。 */
    data object BackgroundFailed : ReadThemePickerEffect
}
