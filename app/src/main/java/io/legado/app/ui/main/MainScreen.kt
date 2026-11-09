package io.legado.app.ui.main

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import io.legado.app.R
import io.legado.app.feature.explore.ExploreMainRoute
import io.legado.app.ui.main.bookshelf.BookShelfItem
import io.legado.app.ui.main.bookshelf.BookshelfRouteScreen
import io.legado.app.ui.main.home.HomeRouteScreen
import io.legado.app.ui.main.my.MyRouteScreen
import io.legado.app.ui.main.my.PrefClickEvent
import io.legado.app.ui.main.rss.RssRouteScreen
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.pager.rememberPagerFlingPassThroughConnection
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.sendToClip
import io.legado.app.utils.startActivityForBook
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 主界面：所有已启用主页面都在同一个 [HorizontalPager] 里，左右滑动切换，
 * 不再渲染底栏 / 悬浮底栏 / 侧栏。左侧抽屉只作为快捷跳转，通过顶栏汉堡按钮打开。
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalSharedTransitionApi::class,
)
@Composable
fun MainScreen(
    mainUiState: MainUiState,
    onIntent: (MainUiIntent) -> Unit,
    effects: kotlinx.coroutines.flow.Flow<MainEffect>,
    onOpenSettings: () -> Unit,
    onNavigateToSearch: (String?) -> Unit,
    onNavigateToScopedSearch: (String) -> Unit,
    onNavigateToRemoteImport: () -> Unit,
    onNavigateToLocalImport: () -> Unit,
    onNavigateToCache: (Long) -> Unit,
    onNavigateToBookCacheManage: () -> Unit,
    onOpenBookshelfBook: (BookShelfItem, String?) -> Unit,
    onNavigateToBackupSettings: () -> Unit,
    onNavigateToBookInfo: (name: String, author: String, bookUrl: String, origin: String?, coverPath: String?, sharedCoverKey: String?) -> Unit,
    onNavigateToExploreShow: (title: String?, sourceUrl: String, exploreUrl: String?) -> Unit,
    onNavigateToSourceLogin: (type: io.legado.app.ui.login.SourceLoginType, sourceUrl: String) -> Unit,
    onNavigateToBookSourceManage: () -> Unit,
    onNavigateToReplaceRules: () -> Unit,
    onNavigateToBookSourceEdit: (String?) -> Unit,
    onNavigateToRssSourceManage: () -> Unit,
    onNavigateToRssSourceEdit: (String?) -> Unit,
    onNavigateToRssSort: (sourceUrl: String, sortUrl: String?, key: String?) -> Unit,
    onNavigateToRssRead: (
        title: String?,
        origin: String,
        link: String?,
        openUrl: String?,
        startPage: Boolean
    ) -> Unit,
    onNavigateToRssFavorites: () -> Unit,
    onNavigateToRuleSub: () -> Unit,
    onNavigateToReadRecord: () -> Unit,
    onNavigateToReadRecordOverview: () -> Unit,
    onNavigateToHighlightTagRule: () -> Unit,
    onNavigateToDictRule: () -> Unit,
    onNavigateToTxtTocRule: () -> Unit,
    onNavigateToAllBookmark: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onMainDestinationChanged: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(effects, context) {
        effects.collectLatest { effect ->
            when (effect) {
                is MainEffect.OpenUrl -> {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, effect.url.toUri())
                    )
                }

                is MainEffect.CopyUrl -> context.sendToClip(effect.url)
                is MainEffect.StartActivity -> {
                    context.startActivity(Intent(context, effect.destination).apply {
                        effect.configTag?.let { putExtra("configTag", it) }
                    })
                }

                MainEffect.ExitApp -> (context as? ComponentActivity)?.finish()
                MainEffect.NavigateToReadRecord -> onNavigateToReadRecord()
                MainEffect.NavigateToHighlightTagRule -> onNavigateToHighlightTagRule()
                MainEffect.NavigateToAbout -> onNavigateToAbout()
            }
        }
    }

    val pagerDestinations = mainUiState.destinations
    val initialPage = remember(pagerDestinations, mainUiState.defaultHomePage) {
        val index = pagerDestinations.indexOfFirst {
            it.route == mainUiState.defaultHomePage
        }
        if (index != -1) index else 0
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { pagerDestinations.size }
    var lastSettledMainPage by remember(pagerState) { mutableIntStateOf(pagerState.settledPage) }
    val currentOnMainDestinationChanged by rememberUpdatedState(onMainDestinationChanged)
    LaunchedEffect(pagerState.settledPage) {
        if (lastSettledMainPage != pagerState.settledPage) {
            lastSettledMainPage = pagerState.settledPage
            currentOnMainDestinationChanged()
        }
    }
    val pagerNestedScrollConnection = rememberPagerFlingPassThroughConnection(
        state = pagerState,
        orientation = Orientation.Horizontal,
    )
    LaunchedEffect(pagerDestinations) {
        if (pagerDestinations.isNotEmpty() && pagerState.currentPage !in pagerDestinations.indices) {
            pagerState.scrollToPage(pagerDestinations.lastIndex)
        }
    }

    val mainDrawerState = rememberDrawerState(DrawerValue.Closed)
    val openDrawer: () -> Unit = remember(coroutineScope, mainDrawerState) {
        { coroutineScope.launch { mainDrawerState.open() } }
    }
    // 宿主与 NavDisplay 的返回处理器都注册在抽屉之上，事件到不了抽屉内部；把「展开中 +
    // 如何关闭」登记给宿主，宿主才能在返回时先关抽屉而不是出栈或退出应用。
    val drawerHost = LocalMainDrawerHost.current
    val drawerOpen = mainDrawerState.isOpen
    val closeDrawer: () -> Unit = remember(coroutineScope, mainDrawerState) {
        { coroutineScope.launch { mainDrawerState.close() } }
    }
    LaunchedEffect(drawerHost, drawerOpen, closeDrawer) {
        // 首帧同步一次；后续由 SideEffect 在同一帧内完成，避免返回事件抢在登记之前。
        if (drawerHost == null) return@LaunchedEffect
        if (drawerOpen) drawerHost.attach(closeDrawer) else drawerHost.detach()
    }
    SideEffect {
        if (drawerHost == null) return@SideEffect
        if (drawerOpen) drawerHost.attach(closeDrawer) else drawerHost.detach()
    }
    DisposableEffect(drawerHost) {
        onDispose { drawerHost?.detach() }
    }

    CompositionLocalProvider(LocalMainDrawerOpen provides openDrawer) {
        ModalNavigationDrawer(
            drawerState = mainDrawerState,
            // 必须保留手势：M3 的遮罩点击只在 gesturesEnabled 时才调用 drawerState.close()，
            // 关掉手势会连带失去「点抽屉外关闭」。
            gesturesEnabled = true,
            drawerContent = {
                MainNavigationDrawer(
                    destinations = pagerDestinations,
                    selectedIndex = pagerState.currentPage,
                    customIconPath = { destination -> mainUiState.customIconPath(destination) },
                    onSelect = { index ->
                        coroutineScope.launch {
                            mainDrawerState.close()
                            pagerState.animateScrollToPage(index)
                        }
                    },
                )
            },
        ) {
            AppScaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(mainAdaptiveInsets.only(WindowInsetsSides.Horizontal)),
                // 子页面各自拥有顶栏，外壳只负责横向系统栏。底部不再整体让出导航栏：
                // 五页的内容要画到小横条下面，由各页把 navigationBars 的 bottom 作为
                // 滚动内容的末尾留白（见 mainPageBottomInset），否则底部会出现一条空白带。
                contentWindowInsets = WindowInsets(0),
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .consumeWindowInsets(padding)
                ) {
                    HorizontalPager(
                        state = pagerState,
                        pageNestedScrollConnection = pagerNestedScrollConnection,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                with(sharedTransitionScope) {
                                    if (this != null) Modifier.skipToLookaheadSize() else Modifier
                                }
                            ),
                        userScrollEnabled = true,
                        beyondViewportPageCount = 4
                    ) { page ->
                        val destination = pagerDestinations.getOrNull(page) ?: return@HorizontalPager
                        val pageLifecycleOwner = rememberMainPageLifecycleOwner(
                            isActive = page == pagerState.currentPage
                        )
                        CompositionLocalProvider(LocalLifecycleOwner provides pageLifecycleOwner) {
                            when (destination) {
                                MainDestination.Home -> HomeRouteScreen(
                                    onOpenBook = { book ->
                                        context.startActivityForBook(book)
                                    },
                                    onNavigateToBookInfo = { name, author, bookUrl, origin, coverPath, sharedCoverKey ->
                                        onNavigateToBookInfo(
                                            name ?: "",
                                            author ?: "",
                                            bookUrl,
                                            origin,
                                            coverPath,
                                            sharedCoverKey,
                                        )
                                    },
                                    onOpenExploreShow = onNavigateToExploreShow,
                                    onOpenBackupSettings = onNavigateToBackupSettings,
                                    onNavigateToReadRecord = onNavigateToReadRecord,
                                    onNavigateToReadRecordOverview = onNavigateToReadRecordOverview,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                )

                                MainDestination.Bookshelf -> BookshelfRouteScreen(
                                    onBookClick = { book, sharedCoverKey ->
                                        onOpenBookshelfBook(book, sharedCoverKey)
                                    },
                                    onBookLongClick = { book, sharedCoverKey ->
                                        onNavigateToBookInfo(
                                            book.name,
                                            book.author,
                                            book.bookUrl,
                                            book.origin,
                                            book.getDisplayCover(),
                                            sharedCoverKey
                                        )
                                    },
                                    onNavigateToSearch = { query -> onNavigateToSearch(query) },
                                    onNavigateToRemoteImport = onNavigateToRemoteImport,
                                    onNavigateToLocalImport = onNavigateToLocalImport,
                                    onNavigateToCache = onNavigateToCache,
                                    onNavigateToSettings = onOpenSettings,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                )

                                MainDestination.Explore -> ExploreMainRoute(
                                    onOpenExploreShow = onNavigateToExploreShow,
                                    onBookClick = { book, sharedCoverKey ->
                                        onNavigateToBookInfo(
                                            book.name,
                                            book.author,
                                            book.bookUrl,
                                            book.origin,
                                            book.coverUrl,
                                            sharedCoverKey,
                                        )
                                    },
                                    onOpenLogin = { sourceUrl ->
                                        onNavigateToSourceLogin(
                                            io.legado.app.ui.login.SourceLoginType.BookSource,
                                            sourceUrl,
                                        )
                                    },
                                    onOpenEdit = onNavigateToBookSourceEdit,
                                    onOpenSearch = onNavigateToScopedSearch,
                                )

                                MainDestination.Rss -> RssRouteScreen(
                                    onOpenSort = { sourceUrl, sortUrl, key ->
                                        onNavigateToRssSort(sourceUrl, sortUrl, key)
                                    },
                                    onOpenRead = { title, origin, link, openUrl, startPage ->
                                        onNavigateToRssRead(title, origin, link, openUrl, startPage)
                                    },
                                    onOpenFavorites = onNavigateToRssFavorites,
                                    onOpenRuleSub = onNavigateToRuleSub,
                                    onOpenLogin = { sourceUrl ->
                                        onNavigateToSourceLogin(
                                            io.legado.app.ui.login.SourceLoginType.RssSource,
                                            sourceUrl,
                                        )
                                    },
                                    onOpenSourceEdit = onNavigateToRssSourceEdit,
                                    onOpenSourceManage = onNavigateToRssSourceManage,
                                )

                                MainDestination.My -> MyRouteScreen(
                                    onOpenSettings = onOpenSettings,
                                    onNavigate = { event ->
                                        when (event) {
                                            PrefClickEvent.OpenBookCacheManage -> onNavigateToBookCacheManage()
                                            PrefClickEvent.OpenBookSourceManage -> onNavigateToBookSourceManage()
                                            PrefClickEvent.OpenReplaceRules -> onNavigateToReplaceRules()
                                            PrefClickEvent.OpenReadRecord -> onNavigateToReadRecord()
                                            PrefClickEvent.OpenDictRule -> onNavigateToDictRule()
                                            PrefClickEvent.OpenTxtTocRule -> onNavigateToTxtTocRule()
                                            PrefClickEvent.OpenAllBookmark -> onNavigateToAllBookmark()
                                            else -> onIntent(MainUiIntent.HandlePreferenceClick(event))
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberMainPageLifecycleOwner(isActive: Boolean): LifecycleOwner {
    val parentLifecycle = LocalLifecycleOwner.current.lifecycle
    val currentActive by rememberUpdatedState(isActive)
    val owner = remember(parentLifecycle) { MainPageLifecycleOwner() }

    DisposableEffect(parentLifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            owner.update(parentLifecycle.currentState, currentActive)
        }
        parentLifecycle.addObserver(observer)
        owner.update(parentLifecycle.currentState, currentActive)
        onDispose {
            parentLifecycle.removeObserver(observer)
            owner.destroy()
        }
    }

    LaunchedEffect(isActive, parentLifecycle) {
        owner.update(parentLifecycle.currentState, isActive)
    }

    return owner
}

private class MainPageLifecycleOwner : LifecycleOwner {

    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle = registry

    fun update(parentState: Lifecycle.State, isActive: Boolean) {
        registry.currentState = when {
            parentState == Lifecycle.State.DESTROYED -> Lifecycle.State.DESTROYED
            parentState == Lifecycle.State.INITIALIZED -> Lifecycle.State.INITIALIZED
            parentState == Lifecycle.State.CREATED -> Lifecycle.State.CREATED
            isActive -> parentState
            else -> Lifecycle.State.STARTED
        }
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}

@Composable
private fun NavigationIcon(
    destination: MainDestination,
    selected: Boolean,
    customIconPath: String,
    modifier: Modifier = Modifier,
) {
    if (customIconPath.isNotEmpty()) {
        AsyncImage(
            model = customIconPath,
            contentDescription = null,
            modifier = modifier.size(40.dp)
        )
    } else {
        val icon = AppIcons.mainDestination(destination, selected)
        AppIcon(icon, contentDescription = null, modifier = modifier)
    }
}

/** 抽屉只占屏幕左侧一条，不铺满；360dp 机型上留出约 1/5 内容预览。 */
private val DrawerWidth = 288.dp

/**
 * 主导航抽屉。
 *
 * 页面切换本身由 [HorizontalPager] 的左右滑动完成，这里只做快捷跳转：
 * 列出全部已启用主页面，选中后把 pager 滚到对应页。
 */
@Composable
private fun MainNavigationDrawer(
    destinations: List<MainDestination>,
    selectedIndex: Int,
    customIconPath: (MainDestination) -> String,
    onSelect: (Int) -> Unit,
) {
    ModalDrawerSheet(modifier = Modifier.width(DrawerWidth)) {
        Spacer(Modifier.height(24.dp))
        destinations.forEachIndexed { index, destination ->
            MainDrawerDestinationRow(
                destination = destination,
                selected = index == selectedIndex,
                customIconPath = customIconPath(destination),
                onClick = { onSelect(index) },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MainDrawerDestinationRow(
    destination: MainDestination,
    selected: Boolean,
    customIconPath: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavigationIcon(
            destination = destination,
            selected = selected,
            customIconPath = customIconPath,
        )
        Spacer(modifier = Modifier.width(16.dp))
        AppText(
            text = stringResource(destination.labelId),
            style = if (selected) {
                LegadoTheme.typography.titleMediumEmphasized
            } else {
                LegadoTheme.typography.titleMedium
            },
        )
    }
}
