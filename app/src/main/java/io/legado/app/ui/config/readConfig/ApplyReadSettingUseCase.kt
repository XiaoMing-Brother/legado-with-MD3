package io.legado.app.ui.config.readConfig

import io.legado.app.constant.EventBus
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ConfigUpdateAction
import io.legado.app.ui.book.read.ReadConfigUpdateBus
import io.legado.app.utils.postEvent

/** Applies runtime reader changes after a setting has entered the effective settings snapshot. */
class ApplyReadSettingUseCase {

    operator fun invoke(intent: ReadConfigIntent) {
        when (intent) {
            is ReadConfigIntent.HideStatusBarChanged,
            is ReadConfigIntent.HideNavigationBarChanged -> {
                ReadConfigUpdateBus.post(
                    setOf(ConfigUpdateAction.UpdateSystemUi, ConfigUpdateAction.UpdateStyle)
                )
            }

            is ReadConfigIntent.ReadMenuBlurAlphaChanged,
            is ReadConfigIntent.ReadSliderModeChanged,
            is ReadConfigIntent.ShowReadTitleAdditionChanged,
            is ReadConfigIntent.ShowMenuIconChanged -> {
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }

            is ReadConfigIntent.TextFullJustifyChanged,
            is ReadConfigIntent.TextBottomJustifyChanged,
            is ReadConfigIntent.UseZhLayoutChanged,
            is ReadConfigIntent.DoubleHorizontalPageChanged -> reloadLayout()

            is ReadConfigIntent.ProgressBarBehaviorChanged -> {
                postEvent(EventBus.UP_SEEK_BAR, true)
            }

            is ReadConfigIntent.PageTouchSlopChanged -> {
                ReadConfigUpdateBus.post(setOf(ConfigUpdateAction.UpdatePageSlopSquare))
            }

            is ReadConfigIntent.NoAnimScrollPageChanged -> ReadBook.renderCallBack?.upPageAnim()
            is ReadConfigIntent.OptimizeRenderChanged -> reloadStyle()

            // useUnderline 进了 RenderStyle 快照，改完必须重建并重绘，否则朗读/搜索
            // 高亮线要等下一次样式变更才生效
            is ReadConfigIntent.UseUnderlineChanged -> {
                ReadConfigUpdateBus.post(
                    setOf(
                        ConfigUpdateAction.UpdateStyle,
                        ConfigUpdateAction.InvalidateTextPage,
                        ConfigUpdateAction.UpdateContent,
                    )
                )
            }
            else -> Unit
        }
    }

    private fun reloadLayout() {
        // 排版参数进了分页快照，必须让活跃阅读器重建环境并重排，不能只 loadContent。
        ReadConfigUpdateBus.post(
            setOf(
                ConfigUpdateAction.UpdateStyle,
                ConfigUpdateAction.UpdateLayout,
                ConfigUpdateAction.ReloadContent,
            )
        )
    }

    private fun reloadStyle() {
        ReadConfigUpdateBus.post(
            setOf(
                ConfigUpdateAction.UpdateStyle,
                ConfigUpdateAction.ReloadContent,
            )
        )
        ReadBook.renderCallBack?.upPageAnim(true)
    }
}
