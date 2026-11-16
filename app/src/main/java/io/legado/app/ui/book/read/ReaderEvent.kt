package io.legado.app.ui.book.read

import io.legado.app.data.entities.BookProgress
import io.legado.app.feature.reader.core.gesture.ReaderTapAction

/**
 * Compose 阅读器向会话层发出的业务意图（Track D 出站）。
 *
 * 画布/手势只产生 [ReaderEvent]；[ReadBookController] 或 [ReadBookViewModel] 再翻译成
 * [ReadBookIntent] 或对 [io.legado.app.model.ReadBook] 的窄口调用（如带回调的进度同步）。
 */
sealed interface ReaderEvent {
    /** 默认阅读菜单（非朗读/非自动翻页/非搜索结果态）。 */
    data object OpenReadingMenu : ReaderEvent
    /** 当前书正在朗读时，中心点击打开朗读相关菜单。 */
    data object OpenReadAloudMenu : ReaderEvent
    /** 自动翻页进行中时打开自动阅读设置。 */
    data object OpenAutoReadMenu : ReaderEvent
    /** 正文搜索命中列表展示中时打开搜索菜单。 */
    data object OpenSearchResultsMenu : ReaderEvent
    data object NextChapter : ReaderEvent
    data object PreviousChapter : ReaderEvent
    data object ReadAloudPreviousParagraph : ReaderEvent
    data object ReadAloudNextParagraph : ReaderEvent
    data object AddBookmark : ReaderEvent
    data object OpenContentEdit : ReaderEvent
    data object ToggleReplace : ReaderEvent
    data object OpenChapterList : ReaderEvent
    data object OpenSearch : ReaderEvent
    data class SyncProgress(
        val onNewProgress: (BookProgress) -> Unit,
        val onUploadSuccess: () -> Unit,
        val onSyncSuccess: () -> Unit,
    ) : ReaderEvent
    data object ToggleReadAloudPlayback : ReaderEvent
    /** 画布可见起点切换朗读（对照阅读菜单/快捷键里的朗读开关）。 */
    data class ToggleReadAloud(
        val visibleChapterIndex: Int? = null,
        val visibleChapterPosition: Int? = null,
        val pageChangedSinceToggle: Boolean = false,
    ) : ReaderEvent
    data object StopReadAloud : ReaderEvent
    /** 展开/收起阅读菜单（对照 [ReadBookIntent.ToggleMenu]）。 */
    data object ToggleReadingMenu : ReaderEvent
    /**
     * Compose 页表章号已变，将 [io.legado.app.model.ReadBook.durChapterIndex] 追到该章
     * （不整页 upContent）。
     */
    data class AlignBookToChapter(val chapterIndex: Int) : ReaderEvent
}

fun interface ReaderEventListener {
    fun onEvent(event: ReaderEvent)
}

/**
 * 九宫格点击动作 → 出站事件。翻页/开菜单由画布手势单独处理，此处映射为 null。
 */
fun ReaderTapAction.toReaderEvent(
    syncProgressCallbacks: ReaderSyncProgressCallbacks? = null,
): ReaderEvent? = when (this) {
    ReaderTapAction.NONE,
    ReaderTapAction.MENU,
    ReaderTapAction.NEXT_PAGE,
    ReaderTapAction.PREVIOUS_PAGE -> null
    ReaderTapAction.NEXT_CHAPTER -> ReaderEvent.NextChapter
    ReaderTapAction.PREVIOUS_CHAPTER -> ReaderEvent.PreviousChapter
    ReaderTapAction.READ_ALOUD_PREVIOUS_PARAGRAPH -> ReaderEvent.ReadAloudPreviousParagraph
    ReaderTapAction.READ_ALOUD_NEXT_PARAGRAPH -> ReaderEvent.ReadAloudNextParagraph
    ReaderTapAction.ADD_BOOKMARK -> ReaderEvent.AddBookmark
    ReaderTapAction.OPEN_CONTENT_EDIT -> ReaderEvent.OpenContentEdit
    ReaderTapAction.TOGGLE_REPLACE -> ReaderEvent.ToggleReplace
    ReaderTapAction.OPEN_CHAPTER_LIST -> ReaderEvent.OpenChapterList
    ReaderTapAction.OPEN_SEARCH -> ReaderEvent.OpenSearch
    ReaderTapAction.SYNC_PROGRESS -> syncProgressCallbacks?.let {
        ReaderEvent.SyncProgress(
            onNewProgress = it.onNewProgress,
            onUploadSuccess = it.onUploadSuccess,
            onSyncSuccess = it.onSyncSuccess,
        )
    }
    ReaderTapAction.TOGGLE_READ_ALOUD_PAUSE -> ReaderEvent.ToggleReadAloudPlayback
}

data class ReaderSyncProgressCallbacks(
    val onNewProgress: (BookProgress) -> Unit,
    val onUploadSuccess: () -> Unit,
    val onSyncSuccess: () -> Unit,
)

/**
 * 画布中心点击（对照旧 View 开菜单）在当前会话态下应发出的菜单事件。
 */
fun resolveComposeCenterMenuEvent(
    isCurrentBookReadAloud: Boolean,
    isAutoPage: Boolean,
    isShowingSearchResult: Boolean,
): ReaderEvent = when {
    isCurrentBookReadAloud -> ReaderEvent.OpenReadAloudMenu
    isAutoPage -> ReaderEvent.OpenAutoReadMenu
    isShowingSearchResult -> ReaderEvent.OpenSearchResultsMenu
    else -> ReaderEvent.OpenReadingMenu
}
