package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarScrollBehavior
import io.legado.app.ui.widget.components.topbar.M3GlassScrollBehavior
import io.legado.app.ui.widget.components.topbar.MiuixGlassScrollBehavior
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.PullToRefresh as MiuixPullToRefresh
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState as miuixRememberPullToRefreshState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    topPadding: Dp = 0.dp,
    scrollBehavior: GlassTopAppBarScrollBehavior? = null,
    content: @Composable () -> Unit,
) {
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        // Miuix 的 PullToRefresh 没有 enabled 参数，无法内部关闭手势，
        // 因此只能在 enabled=false 时整体退化成普通容器。
        if (enabled) {
            // Let the miuix library handle gesture/animation, but skip the persistent
            // Refreshing state that blocks all scroll events.
            // localIsRefreshing is true only briefly so the indicator shows then hides,
            // while the actual refresh runs independently.
            var localIsRefreshing by remember { mutableStateOf(false) }

            LaunchedEffect(localIsRefreshing) {
                if (localIsRefreshing) {
                    delay(300)
                    localIsRefreshing = false
                }
            }

            MiuixPullToRefresh(
                isRefreshing = localIsRefreshing,
                onRefresh = {
                    localIsRefreshing = true
                    onRefresh()
                },
                modifier = modifier,
                contentPadding = PaddingValues(top = topPadding + 16.dp),
                pullToRefreshState = miuixRememberPullToRefreshState(),
                topAppBarScrollBehavior = (scrollBehavior as? MiuixGlassScrollBehavior)?.miuixBehavior,
                refreshTexts = listOf(
                    stringResource(R.string.pull_to_refresh),
                    stringResource(R.string.release_to_refresh),
                    stringResource(R.string.refreshing),
                    stringResource(R.string.refreshing)
                )
            ) {
                content()
            }
        } else {
            Box(modifier) {
                content()
            }
        }
    } else {
        val state = rememberPullToRefreshState()
        // 这段判据读的全是滚动状态：顶栏的 heightOffset / contentOffset 与下拉距离每一帧都在变。
        // 直接写在组合里，滚动时 AppPullToRefresh 自身（以及它下面的 PullToRefreshBox 与
        // pullToRefresh 修饰符）每一帧都要重组一次；包进 derivedStateOf 后只有开关真正翻转
        //（贴顶 ↔ 已滚开）时才重组。
        val behavior = scrollBehavior
        val latestEnabled by rememberUpdatedState(enabled)
        val latestRefreshing by rememberUpdatedState(isRefreshing)
        val enabledState = remember(state, behavior) {
            derivedStateOf {
                latestEnabled && (
                    latestRefreshing ||
                        state.distanceFraction > 0f ||
                        behavior == null ||
                        if (behavior is M3GlassScrollBehavior) {
                            val appbarState = behavior.m3Behavior.state
                            (appbarState.heightOffsetLimit == 0f || appbarState.heightOffset >= 0f) &&
                                appbarState.contentOffset >= 0f
                        } else {
                            behavior.collapsedFraction <= 0f
                        }
                    )
            }
        }
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier,
            state = state,
            enabled = enabledState.value,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = state,
                    isRefreshing = isRefreshing,
                    modifier = Modifier
                        .padding(top = topPadding)
                        .align(Alignment.TopCenter),
                )
            }
        ) {
            content()
        }
    }
}
