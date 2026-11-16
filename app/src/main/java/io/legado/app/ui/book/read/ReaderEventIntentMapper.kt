package io.legado.app.ui.book.read

import io.legado.app.model.ReadBook

/**
 * 将 [ReaderEvent] 落到 [ReadBookIntent] 或带副作用的 [ReadBook] 窄口 API。
 */
object ReaderEventIntentMapper {

    fun dispatch(
        event: ReaderEvent,
        onIntent: (ReadBookIntent) -> Unit,
    ) {
        when (event) {
            ReaderEvent.OpenReadingMenu -> onIntent(ReadBookIntent.ShowMenu)
            ReaderEvent.OpenReadAloudMenu -> onIntent(ReadBookIntent.ReadAloudAction)
            ReaderEvent.OpenAutoReadMenu ->
                onIntent(ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.AutoRead))
            ReaderEvent.OpenSearchResultsMenu -> onIntent(ReadBookIntent.ShowSearchMenu)
            ReaderEvent.NextChapter -> onIntent(ReadBookIntent.NextChapter)
            ReaderEvent.PreviousChapter -> onIntent(ReadBookIntent.PrevChapter)
            ReaderEvent.ReadAloudPreviousParagraph -> onIntent(ReadBookIntent.ReadAloudPrevParagraph)
            ReaderEvent.ReadAloudNextParagraph -> onIntent(ReadBookIntent.ReadAloudNextParagraph)
            ReaderEvent.AddBookmark -> onIntent(ReadBookIntent.AddBookmark)
            ReaderEvent.OpenContentEdit -> onIntent(ReadBookIntent.OpenContentEdit)
            ReaderEvent.ToggleReplace -> onIntent(ReadBookIntent.MenuEnableReplace)
            ReaderEvent.OpenChapterList -> onIntent(ReadBookIntent.OpenChapterList)
            ReaderEvent.OpenSearch -> onIntent(ReadBookIntent.OpenSearch(null))
            ReaderEvent.ToggleReadAloudPlayback -> onIntent(ReadBookIntent.ToggleReadAloudPlayback)
            is ReaderEvent.ToggleReadAloud -> onIntent(
                ReadBookIntent.ToggleReaderReadAloud(
                    visibleChapterIndex = event.visibleChapterIndex,
                    visibleChapterPosition = event.visibleChapterPosition,
                    pageChangedSinceToggle = event.pageChangedSinceToggle,
                ),
            )
            ReaderEvent.StopReadAloud -> onIntent(ReadBookIntent.ReadAloudStop)
            ReaderEvent.ToggleReadingMenu -> onIntent(ReadBookIntent.ToggleMenu)
            is ReaderEvent.AlignBookToChapter ->
                onIntent(ReadBookIntent.ComposeTurnPage(event.chapterIndex))
            is ReaderEvent.SyncProgress -> ReadBook.syncProgress(
                newProgressAction = event.onNewProgress,
                uploadSuccessAction = event.onUploadSuccess,
                syncSuccessAction = event.onSyncSuccess,
            )
        }
    }
}
