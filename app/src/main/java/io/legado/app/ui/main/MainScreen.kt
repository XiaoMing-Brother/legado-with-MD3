package io.legado.app.ui.main

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import coil3.compose.AsyncImage
import io.legado.app.R
import io.legado.app.feature.explore.ExploreMainRoute
import io.legado.app.ui.main.bookshelf.BookShelfItem
import io.legado.app.ui.main.bookshelf.BookshelfRouteScreen
import io.legado.app.ui.main.home.HomeRouteScreen
import io.legado.app.ui.main.my.MyRouteScreen
import io.legado.app.ui.main.my.PrefClickEvent
import io.legado.app.ui.main.rss.RssRouteScreen
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalBottomOverlayPadding
import io.legado.app.ui.theme.LocalMiuixBarBackdrop
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.FloatingBottomBar
import io.legado.app.ui.widget.components.FloatingBottomBarItem
import io.legado.app.ui.widget.components.GlassDefaults
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.navigation.AppNavigationBar
import io.legado.app.ui.widget.components.navigation.AppNavigationBarItem
import io.legado.app.ui.widget.components.pager.rememberPagerFlingPassThroughConnection
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.sendToClip
import io.legado.app.utils.startActivityForBook
import top.yukonga.miuix.kmp.blur.layerBackdrop as miuixLayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop as rememberMiuixLayerBackdrop
import ui.feice.mochi.effect.glass.LocalMochiBackdrop
import ui.feice.mochi.effect.glass.mochiBackdropSource
import ui.feice.mochi.effect.glass.rememberMochiBackdrop
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private val MainNavigationTabMinWidth = 76.dp

/**
 * 主界面：所有已启用主页面都在同一个 [HorizontalPager] 里左右滑动切换；
 * 底部使用悬浮导航条切换页面（可配合设置开启液态玻璃）。
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

    var bookshelfScrollToTopRequest by remember { mutableLongStateOf(0L) }
    fun requestBookshelfScrollToTop() {
        bookshelfScrollToTopRequest++
    }

    fun handleMainDestinationClick(index: Int, destination: MainDestination) {
        if (
            destination == MainDestination.Bookshelf &&
            pagerState.currentPage == index &&
            pagerState.targetPage == index
        ) {
            requestBookshelfScrollToTop()
            return
        }
        coroutineScope.launch {
            pagerState.animateScrollToPage(index)
        }
    }

    val destinations = pagerDestinations
    val labelVisibilityMode = mainUiState.labelVisibilityMode
    val isUnlabeled = labelVisibilityMode == "unlabeled"
    val useFloatingBottomBar = mainUiState.showBottomView && mainUiState.useFloatingBottomBar
    val useLiquidGlass = useFloatingBottomBar &&
        mainUiState.useFloatingBottomBarLiquidGlass &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    var floatingFooterHeight by remember { mutableStateOf(0.dp) }
    val alwaysShowLabel = labelVisibilityMode == "labeled"
    val showLabel = !isUnlabeled
    val customSecondaryColor = if (LegadoTheme.isDark) {
        mainUiState.secondaryThemeColorNight.takeIf { it != 0 }
            ?: mainUiState.secondaryThemeColor
    } else {
        mainUiState.secondaryThemeColor
    }
    val floatingBarSurfaceColor = if (mainUiState.deepPersonalizationActive && customSecondaryColor != 0) {
        Color(customSecondaryColor)
    } else {
        LegadoTheme.colorScheme.surface
    }
    val engine = LegadoTheme.composeEngine
    val mochiPageBackdrop = if (engine == ComposeEngine.Mochi && useLiquidGlass) {
        rememberMochiBackdrop(LegadoTheme.colorScheme.background)
    } else {
        null
    }
    val miuixPageBackdrop = if (engine == ComposeEngine.Miuix && useLiquidGlass) {
        rememberMiuixLayerBackdrop {
            drawRect(floatingBarSurfaceColor)
            drawContent()
        }
    } else {
        null
    }
    val floatingBarBackdrop = if (engine == ComposeEngine.Material && useLiquidGlass) {
        rememberLayerBackdrop {
            drawRect(floatingBarSurfaceColor)
            drawContent()
        }
    } else {
        null
    }

    AppScaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(mainAdaptiveInsets.only(WindowInsetsSides.Horizontal)),
        bottomBar = if (mainUiState.showBottomView && !useFloatingBottomBar) {
            {
                AppNavigationBar(
                    showLabel = showLabel,
                    alwaysShowLabel = alwaysShowLabel,
                ) {
                    destinations.forEachIndexed { index, destination ->
                        val selected = pagerState.targetPage == index
                        val customIconPath = mainUiState.customIconPath(destination)
                        val selectedCustomIconPath =
                            mainUiState.selectedCustomIconPath(destination)
                        val destinationLabel = stringResource(destination.labelId)
                        AppNavigationBarItem(
                            modifier = Modifier.semantics(mergeDescendants = true) {
                                contentDescription = destinationLabel
                            },
                            selected = selected,
                            onClick = {
                                handleMainDestinationClick(index, destination)
                            },
                            labelString = stringResource(destination.labelId),
                            iconVector = AppIcons.mainDestination(destination, selected),
                            m3Icon = {
                                NavigationIcon(
                                    destination = destination,
                                    selected = selected,
                                    customIconPath = if (selected) {
                                        selectedCustomIconPath.ifEmpty { customIconPath }
                                    } else {
                                        customIconPath
                                    },
                                )
                            },
                            m3IndicatorColor = GlassDefaults.glassColor(
                                noBlurColor = LegadoTheme.colorScheme.secondaryContainer,
                                blurAlpha = GlassDefaults.ThickBlurAlpha,
                            ),
                            m3ShowLabel = showLabel,
                            m3AlwaysShowLabel = alwaysShowLabel,
                            useCustomIcon =
                                customIconPath.isNotEmpty() || selectedCustomIconPath.isNotEmpty(),
                        )
                    }
                }
            }
        } else null,
        contentWindowInsets = if (useFloatingBottomBar) {
            WindowInsets(0)
        } else {
            mainAdaptiveInsets.only(WindowInsetsSides.Bottom)
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (mochiPageBackdrop != null) {
                            Modifier.mochiBackdropSource(mochiPageBackdrop)
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        when {
                            floatingBarBackdrop != null -> Modifier.layerBackdrop(floatingBarBackdrop)
                            miuixPageBackdrop != null -> Modifier.miuixLayerBackdrop(miuixPageBackdrop)
                            else -> Modifier
                        },
                    ),
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
                    beyondViewportPageCount = 4,
                ) { page ->
                    val destination = destinations.getOrNull(page) ?: return@HorizontalPager
                    val pageLifecycleOwner = rememberMainPageLifecycleOwner(
                        isActive = page == pagerState.currentPage,
                    )
                    CompositionLocalProvider(
                        LocalLifecycleOwner provides pageLifecycleOwner,
                        LocalBottomOverlayPadding provides
                            if (useFloatingBottomBar) floatingFooterHeight + 12.dp else 0.dp,
                    ) {
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
                                    scrollToTopRequest = bookshelfScrollToTopRequest,
                                    onScrollToTopRequestHandled = { handledRequest ->
                                        if (bookshelfScrollToTopRequest == handledRequest) {
                                            bookshelfScrollToTopRequest = 0L
                                        }
                                    },
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

            if (useFloatingBottomBar) {
                CompositionLocalProvider(
                    LocalMochiBackdrop provides mochiPageBackdrop,
                    LocalMiuixBarBackdrop provides miuixPageBackdrop,
                ) {
                    FloatingNavigationContainer(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        onHeightChanged = { floatingFooterHeight = it },
                    ) {
                        val navigationAvailableWidth = maxWidth
                        val normalNavigationWidth =
                            (MainNavigationTabMinWidth * destinations.size + 8.dp)
                                .coerceAtMost(navigationAvailableWidth)
                        val navigationStartInset =
                            (navigationAvailableWidth - normalNavigationWidth) / 2
                        FloatingBottomBar(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .offset(x = navigationStartInset)
                                .width(normalNavigationWidth),
                            selectedIndex = { pagerState.targetPage },
                            onSelected = { index ->
                                destinations.getOrNull(index)?.let { destination ->
                                    handleMainDestinationClick(index, destination)
                                }
                            },
                            onReselected = { index ->
                                destinations.getOrNull(index)?.let { destination ->
                                    handleMainDestinationClick(index, destination)
                                }
                            },
                            backdrop = floatingBarBackdrop,
                            tabsCount = destinations.size,
                            isBlurEnabled = useLiquidGlass,
                            hasCustomIcons = destinations.any { destination ->
                                mainUiState.customIconPath(destination).isNotEmpty() ||
                                    mainUiState.selectedCustomIconPath(destination).isNotEmpty()
                            },
                        ) {
                            destinations.forEachIndexed { index, destination ->
                                val selected = pagerState.targetPage == index
                                val customIconPath = mainUiState.customIconPath(destination)
                                val selectedCustomIconPath =
                                    mainUiState.selectedCustomIconPath(destination)
                                val destinationLabel = stringResource(destination.labelId)
                                FloatingBottomBarItem(
                                    onClick = {
                                        handleMainDestinationClick(index, destination)
                                    },
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = MainNavigationTabMinWidth)
                                        .semantics(mergeDescendants = true) {
                                            contentDescription = destinationLabel
                                        },
                                ) {
                                    NavigationIcon(
                                        destination = destination,
                                        customIconPath = if (selected) {
                                            selectedCustomIconPath.ifEmpty { customIconPath }
                                        } else {
                                            customIconPath
                                        },
                                        selected = selected,
                                    )
                                    if (showLabel && (alwaysShowLabel || selected)) {
                                        AppText(
                                            text = stringResource(destination.labelId),
                                            style = LegadoTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
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
    tint: Color = Color.Unspecified,
) {
    if (customIconPath.isNotEmpty()) {
        AsyncImage(
            model = customIconPath,
            contentDescription = null,
            modifier = modifier.size(40.dp)
        )
    } else {
        val icon = AppIcons.mainDestination(destination, selected)
        AppIcon(icon, contentDescription = null, modifier = modifier, tint = tint)
    }
}
