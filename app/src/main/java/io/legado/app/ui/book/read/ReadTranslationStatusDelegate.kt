package io.legado.app.ui.book.read

import io.legado.app.data.entities.Book
import io.legado.app.model.translation.TranslationChapterKey
import io.legado.app.model.translation.TranslationChapterStatus
import io.legado.app.model.translation.TranslationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch

/**
 * 阅读页的章节翻译状态观察。
 *
 * 原先挂在 `ReadAiDelegate` 上；AI 域删除后翻译（只剩 Google）与 AI 无关，
 * 这块单独留在阅读器里，避免阅读页失去翻译进度显示。
 */
class ReadTranslationStatusDelegate(
    private val scope: CoroutineScope,
    private val host: Host,
) {

    interface Host {
        val currentTranslationStatus: TranslationChapterStatus

        fun updateTranslationStatus(status: TranslationChapterStatus)
    }

    private var translationStatusJob: Job? = null
    private var observedTranslationKey: TranslationChapterKey? = null

    /**
     * 观察当前章节的翻译任务状态。返回当前状态供阅读态快照同步取用；
     * 同一章节已在观察中时直接复用，不重建订阅。
     */
    fun observeChapterTranslation(book: Book?, chapterIndex: Int): TranslationChapterStatus {
        val key = book
            ?.takeIf { it.getTranslationMode() }
            ?.let { TranslationChapterKey(it.bookUrl, chapterIndex) }
        if (key == observedTranslationKey && translationStatusJob?.isActive == true) {
            return host.currentTranslationStatus
        }

        translationStatusJob?.cancel()
        val taskFlow = key?.let {
            TranslationManager.getChapterTaskStateFlow(it.bookUrl, it.chapterIndex)
        }
        if (taskFlow == null) {
            observedTranslationKey = null
            return TranslationChapterStatus.Idle
        }

        observedTranslationKey = key
        translationStatusJob = scope.launch {
            taskFlow.takeWhile { taskState ->
                if (observedTranslationKey == taskState.key) {
                    host.updateTranslationStatus(taskState.status)
                }
                taskState.status == TranslationChapterStatus.Translating ||
                        taskState.status == TranslationChapterStatus.Thinking
            }.collect {}
            if (observedTranslationKey == key) observedTranslationKey = null
        }
        return taskFlow.value.status
    }
}
