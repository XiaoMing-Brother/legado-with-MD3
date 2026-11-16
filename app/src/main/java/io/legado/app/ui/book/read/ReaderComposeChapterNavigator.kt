package io.legado.app.ui.book.read

/**
 * Compose 阅读器在页窗口已跨章、但 [io.legado.app.model.ReadBook] 仍停在旧章时的章索引对齐。
 *
 * 对照 View 阅读器里 `TextPageFactory` 翻页触发的 `moveToNextChapter`/`moveToPrevChapter`：
 * Canvas 路径由 [ReadBookController.completeComposePageTurn] 先改页表，再在这里把书内游标追到页表章号。
 * 不刷新整页内容（`upContent = false`），由控制器随后的 `publishDirectReaderWindow` 承接。
 */
object ReaderComposeChapterNavigator {

    fun advanceToChapter(
        targetChapterIndex: Int,
        currentChapterIndex: () -> Int,
        moveToNextChapter: () -> Boolean,
        moveToPrevChapter: () -> Boolean,
    ): Boolean {
        while (currentChapterIndex() < targetChapterIndex) {
            if (!moveToNextChapter()) break
        }
        while (currentChapterIndex() > targetChapterIndex) {
            if (!moveToPrevChapter()) break
        }
        return currentChapterIndex() == targetChapterIndex
    }
}
