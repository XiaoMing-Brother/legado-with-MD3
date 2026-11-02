package io.legado.app.ui.book.read

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderComposeChapterNavigatorTest {

    @Test
    fun `advances forward until target chapter`() {
        var chapter = 2
        val ok = ReaderComposeChapterNavigator.advanceToChapter(
            targetChapterIndex = 5,
            currentChapterIndex = { chapter },
            moveToNextChapter = {
                chapter++
                true
            },
            moveToPrevChapter = { false },
        )
        assertTrue(ok)
        assertTrue(chapter == 5)
    }

    @Test
    fun `advances backward until target chapter`() {
        var chapter = 8
        val ok = ReaderComposeChapterNavigator.advanceToChapter(
            targetChapterIndex = 3,
            currentChapterIndex = { chapter },
            moveToNextChapter = { false },
            moveToPrevChapter = {
                chapter--
                true
            },
        )
        assertTrue(ok)
        assertTrue(chapter == 3)
    }

    @Test
    fun `returns false when forward move stalls before target`() {
        var chapter = 1
        val ok = ReaderComposeChapterNavigator.advanceToChapter(
            targetChapterIndex = 4,
            currentChapterIndex = { chapter },
            moveToNextChapter = {
                if (chapter >= 2) {
                    false
                } else {
                chapter++
                true
                }
            },
            moveToPrevChapter = { false },
        )
        assertFalse(ok)
        assertTrue(chapter == 2)
    }
}
