package io.legado.app.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton

/**
 * 打开主界面导航抽屉。抽屉是纯滑动导航之外的快捷跳转入口，由 [MainScreen] 提供。
 *
 * 页面（如书架顶栏）只判断「有没有这个能力」来决定是否显示汉堡按钮，不需要知道
 * 当前处于哪一页；没有抽屉可开时不画任何按钮。
 */
val LocalMainDrawerOpen = compositionLocalOf<(() -> Unit)?> { null }

/**
 * 主界面抽屉在宿主侧的开关状态。
 *
 * 抽屉自己无法抢在 NavDisplay 前面处理返回：宿主与 NavDisplay 的返回处理器都注册在
 * 抽屉之上，事件不会落到抽屉内部。因此由 [MainScreen] 把「当前是否展开 + 如何关闭」
 * 登记到这里，宿主在 NavDisplay 之后注册的 BackHandler 才能在抽屉展开时优先关闭抽屉，
 * 而不是让 NavDisplay 出栈甚至退出应用。
 */
@Stable
class MainDrawerHostState {
    private var closeAction by mutableStateOf<(() -> Unit)?>(null)

    val isOpen: Boolean get() = closeAction != null

    fun attach(close: () -> Unit) {
        closeAction = close
    }

    fun detach() {
        closeAction = null
    }

    fun requestClose() {
        closeAction?.invoke()
    }
}

val LocalMainDrawerHost = staticCompositionLocalOf<MainDrawerHostState?> { null }

/** 主页面顶栏的导航按钮：有抽屉就显示汉堡，否则什么都不画。 */
@Composable
fun MainDrawerNavigationIcon() {
    val openDrawer = LocalMainDrawerOpen.current ?: return
    TopBarNavigationButton(
        onClick = openDrawer,
        imageVector = Icons.Default.Menu,
        contentDescription = stringResource(R.string.menu),
    )
}
