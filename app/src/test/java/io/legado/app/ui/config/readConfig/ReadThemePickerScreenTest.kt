package io.legado.app.ui.config.readConfig

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import io.legado.app.data.repository.AppShellSettingsRepository
import io.legado.app.data.repository.ThemeSettingsRepository
import io.legado.app.domain.gateway.AppShellSettingsGateway
import io.legado.app.domain.gateway.ThemeSettingsGateway
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.model.settings.ReadStyleItem
import io.legado.app.domain.model.settings.ThemeSettings
import io.legado.app.help.config.AppConfigStore
import io.legado.app.ui.theme.AppTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ReadThemePickerScreenTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        AppConfigStore.init(context)
        stopKoin()
        startKoin {
            modules(module {
                // StyleCard 通过 ReadStyleResolver 读取主题模式（电子墨水/夜间）。
                single<AppShellSettingsGateway> { AppShellSettingsRepository() }
                single<ThemeSettingsGateway> { ThemeSettingsRepository() }
            })
        }
    }

    @After
    fun tearDown() = stopKoin()

    private fun show(
        enableBlur: Boolean,
        readingStyles: ReadThemePickerStyles = ReadThemePickerStyles(),
        onSelectSavedStyle: (Int) -> Unit = {},
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                AppTheme(
                    AppUiConfiguration(theme = ThemeSettings(enableBlur = enableBlur)),
                    applyBackground = false,
                ) {
                    ReadThemePickerScreen(
                        onBackClick = {},
                        onSelectTheme = { _, _ -> },
                        onSelectSavedStyle = onSelectSavedStyle,
                        readingStyles = readingStyles,
                    )
                }
            }
        }
    }

    /**
     * 顶栏毛玻璃只能采样「顶栏背面」的内容。整页必须让网格视口从屏幕顶部开始，
     * 再用 contentPadding 预留顶栏高度；否则内容永远不会经过顶栏背面，模糊是空效果。
     */
    @Test
    fun `blur mode lets the theme grid viewport start behind the top bar`() {
        show(enableBlur = true)

        val grid = compose.onAllNodes(hasScrollToIndexAction()).onFirst()
            .fetchSemanticsNode().boundsInRoot
        assertEquals(0f, grid.top, 0.5f)
        assertTrue(grid.height > 0f)
    }

    @Test
    fun `without blur the grid starts below the top bar`() {
        show(enableBlur = false)

        val grid = compose.onAllNodes(hasScrollToIndexAction()).onFirst()
            .fetchSemanticsNode().boundsInRoot
        assertTrue(grid.top > 0f)
    }

    @Test
    fun `grid keeps scrolling after the header and tabs`() {
        show(enableBlur = true)

        val grid = compose.onAllNodes(hasScrollToIndexAction()).onFirst()
        // 页签固定在顶栏里，内容首项是已保存背景区；网格仍可滚动到最后一项。
        grid.performScrollToIndex(60)
        grid.assertExists()
    }

    @Test
    fun `saved backgrounds are selectable instead of only showing the active one`() {
        val selectedIndex = mutableStateOf(0)
        var clicked = -1
        val styles = ReadThemePickerStyles(
            items = persistentListOf(
                readStyle("第一份背景"),
                readStyle("第二份背景"),
            ),
            selectedIndex = 0,
        )
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                AppTheme(
                    AppUiConfiguration(theme = ThemeSettings(enableBlur = true)),
                    applyBackground = false,
                ) {
                    ReadThemePickerScreen(
                        onBackClick = {},
                        onSelectTheme = { _, _ -> },
                        onSelectSavedStyle = {
                            clicked = it
                            selectedIndex.value = it
                        },
                        readingStyles = styles.copy(selectedIndex = selectedIndex.value),
                    )
                }
            }
        }
        // 顶部卡片只留背景预览，名称作为无障碍描述，不再渲染文字。
        compose.onNodeWithContentDescription("第一份背景").assertIsSelected()
        compose.onNodeWithContentDescription("第二份背景").assertIsNotSelected().performClick()
        compose.runOnIdle { assertEquals(1, clicked) }
        compose.onNodeWithContentDescription("第二份背景").assertIsSelected()
    }

    /**
     * 首屏空间有限：已保存背景区必须是一行（标签 + 小卡片），不能退回“独立标题 + 56×72 卡片”。
     */
    @Test
    fun `saved backgrounds stay a compact single row`() {
        val styles = ReadThemePickerStyles(
            items = persistentListOf(readStyle("第一份背景")),
            selectedIndex = 0,
        )
        show(enableBlur = true, readingStyles = styles)

        val card = compose.onNodeWithContentDescription("第一份背景")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(card.width <= 40f)
        assertTrue(card.height <= 50f)
    }

    private fun readStyle(name: String) = ReadStyleItem(
        id = name,
        name = name,
        bgType = 0,
        bgValue = "#EEEEEE",
        bgTypeNight = 0,
        bgValueNight = "#000000",
        bgTypeEInk = 0,
        bgValueEInk = "#FFFFFF",
        textColor = 0xFF000000.toInt(),
        textColorNight = 0xFFFFFFFF.toInt(),
        textColorEInk = 0xFF000000.toInt(),
    )
}
