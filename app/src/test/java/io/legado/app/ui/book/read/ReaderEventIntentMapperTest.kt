package io.legado.app.ui.book.read

import io.legado.app.feature.reader.core.gesture.ReaderTapAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderEventIntentMapperTest {

    @Test
    fun `tap actions map to reader events except page and menu`() {
        assertNull(ReaderTapAction.NEXT_PAGE.toReaderEvent())
        assertNull(ReaderTapAction.MENU.toReaderEvent())
        assertEquals(ReaderEvent.NextChapter, ReaderTapAction.NEXT_CHAPTER.toReaderEvent())
        assertEquals(
            ReaderEvent.ToggleReadAloudPlayback,
            ReaderTapAction.TOGGLE_READ_ALOUD_PAUSE.toReaderEvent(),
        )
    }

    @Test
    fun `sync progress tap requires callbacks`() {
        assertNull(ReaderTapAction.SYNC_PROGRESS.toReaderEvent())
        val callbacks = ReaderSyncProgressCallbacks(
            onNewProgress = {},
            onUploadSuccess = {},
            onSyncSuccess = {},
        )
        assertTrue(ReaderTapAction.SYNC_PROGRESS.toReaderEvent(callbacks) is ReaderEvent.SyncProgress)
    }

    @Test
    fun `mapper turns navigation events into intents`() {
        val intents = mutableListOf<ReadBookIntent>()
        ReaderEventIntentMapper.dispatch(ReaderEvent.NextChapter) { intents += it }
        ReaderEventIntentMapper.dispatch(ReaderEvent.ToggleReadAloudPlayback) { intents += it }
        assertTrue(intents.any { it is ReadBookIntent.NextChapter })
        assertTrue(intents.any { it is ReadBookIntent.ToggleReadAloudPlayback })
    }

    @Test
    fun `center menu resolver picks session specific menu events`() {
        assertEquals(
            ReaderEvent.OpenReadAloudMenu,
            resolveComposeCenterMenuEvent(
                isCurrentBookReadAloud = true,
                isAutoPage = false,
                isShowingSearchResult = false,
            ),
        )
        assertEquals(
            ReaderEvent.OpenAutoReadMenu,
            resolveComposeCenterMenuEvent(
                isCurrentBookReadAloud = false,
                isAutoPage = true,
                isShowingSearchResult = false,
            ),
        )
        assertEquals(
            ReaderEvent.OpenSearchResultsMenu,
            resolveComposeCenterMenuEvent(
                isCurrentBookReadAloud = false,
                isAutoPage = false,
                isShowingSearchResult = true,
            ),
        )
        assertEquals(
            ReaderEvent.OpenReadingMenu,
            resolveComposeCenterMenuEvent(
                isCurrentBookReadAloud = false,
                isAutoPage = false,
                isShowingSearchResult = false,
            ),
        )
    }

    @Test
    fun `mapper maps menu events to intents`() {
        val intents = mutableListOf<ReadBookIntent>()
        val dispatch = { event: ReaderEvent ->
            ReaderEventIntentMapper.dispatch(event) { intents += it }
        }
        dispatch(ReaderEvent.OpenReadAloudMenu)
        dispatch(ReaderEvent.OpenAutoReadMenu)
        dispatch(ReaderEvent.OpenSearchResultsMenu)
        assertTrue(intents.any { it is ReadBookIntent.ReadAloudAction })
        assertTrue(intents.any { it is ReadBookIntent.OpenReadMenuRoute })
        assertTrue(intents.any { it is ReadBookIntent.ShowSearchMenu })
    }

    @Test
    fun `mapper maps session control events`() {
        val intents = mutableListOf<ReadBookIntent>()
        ReaderEventIntentMapper.dispatch(
            ReaderEvent.ToggleReadAloud(pageChangedSinceToggle = true),
            { intents += it },
        )
        ReaderEventIntentMapper.dispatch(ReaderEvent.StopReadAloud) { intents += it }
        ReaderEventIntentMapper.dispatch(ReaderEvent.ToggleReadingMenu) { intents += it }
        ReaderEventIntentMapper.dispatch(ReaderEvent.AlignBookToChapter(3)) { intents += it }
        assertTrue(intents.any { it is ReadBookIntent.ToggleReaderReadAloud })
        assertTrue(intents.any { it is ReadBookIntent.ReadAloudStop })
        assertTrue(intents.any { it is ReadBookIntent.ToggleMenu })
        assertTrue(intents.any { it is ReadBookIntent.ComposeTurnPage })
    }
}
