package io.legado.app.ui.book.read

import android.content.Context
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.help.readaloud.cast.CastAssignmentStore
import io.legado.app.help.readaloud.cast.CastAssignmentStore.CastResult
import io.legado.app.help.readaloud.cast.BgmSceneStore
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 多角色分配域：确认（分配 + 更新已有角色状态）/ 创建（新角色身份 = 名字+声音池）/
 * 取消分配，以及 AI 批量分配的成功与取消收尾（同一套「重排当前章 + 朗读队列」，见
 * [refreshAfterCast]）与失败提示。
 *
 * DAO 访问收口在 CastAssignmentStore（架构护栏）；章节重排、关窗与 toast 三条通道
 * 由 VM 以 lambda 注入（`_effects` / `contentProcessDelegate` 只有 VM 能碰）。
 * VM 侧只剩三个意图分支的单行转发。
 */
class ReadAloudCastDelegate(
    private val context: Context,
    private val scope: CoroutineScope,
    private val reloadChapter: () -> Unit,
    private val sendIntent: (ReadBookIntent) -> Unit,
    private val emitToast: (String) -> Unit,
) {

    /** 确认 = 分配这句话 + 更新已有角色状态（永不创建）。 */
    fun confirm(intent: ReadBookIntent.ConfirmRoleCast) {
        val book = ReadBook.book ?: return
        scope.launch(Dispatchers.IO) {
            val result = CastAssignmentStore.confirm(
                bookUrl = book.bookUrl,
                chapterIndex = ReadBook.durChapterIndex,
                quoteOrdinal = intent.ordinal,
                selectedCharacterId = intent.selectedCharacterId,
                characterName = intent.characterName,
                voicePoolLabel = intent.voicePoolLabel,
                voiceId = intent.voiceId,
                voiceEffect = intent.voiceEffect,
            )
            applyResult(result)
        }
    }

    /** 创建 = 新增配音角色并分配给这句话。 */
    fun create(intent: ReadBookIntent.CreateRoleCast) {
        val book = ReadBook.book ?: return
        scope.launch(Dispatchers.IO) {
            val result = CastAssignmentStore.create(
                bookUrl = book.bookUrl,
                chapterIndex = ReadBook.durChapterIndex,
                quoteOrdinal = intent.ordinal,
                characterName = intent.characterName,
                voicePoolLabel = intent.voicePoolLabel,
                voiceId = intent.voiceId,
                voiceEffect = intent.voiceEffect,
            )
            applyResult(result)
        }
    }

    /** cast 系意图统一入口（ReadBookViewModel 行数预算，域内分发在此收口）。 */
    fun onCastIntent(intent: ReadBookIntent) {
        when (intent) {
            is ReadBookIntent.DeleteChapterCastAssignments ->
                deleteChapterAssignments(intent.chapterIndex, intent.alsoScenes)
            is ReadBookIntent.UnassignRoleCast -> unassign(intent.ordinal)
            is ReadBookIntent.SetBgmScene -> setBgmScene(intent)
            is ReadBookIntent.ClearBgmScene -> clearBgmScene(intent.paragraphIndex)
            is ReadBookIntent.UpdateBgmScene -> updateBgmScene(intent)
            is ReadBookIntent.DeleteBgmScene -> deleteBgmScene(intent.paragraphIndex)
            else -> Unit
        }
    }

    fun deleteChapterAssignments(chapterIndex: Int, alsoScenes: Boolean) {
        val book = ReadBook.book ?: return
        scope.launch(Dispatchers.IO) {
            CastAssignmentStore.deleteChapter(book.bookUrl, chapterIndex)
            // 「删除分配」要连这次一起删的东西：勾了配乐场景（或纯场景入口）时，
            // 只删角色会留下场景，表现为「删了但场景还在」
            if (alsoScenes) BgmSceneStore.clearChapter(book.bookUrl, chapterIndex)
            withContext(Dispatchers.Main) {
                reloadChapter()
                refreshAloudCast()
            }
        }
    }

    /** 取消一句话的分配并重排当前章。 */
    fun unassign(ordinal: Int) {
        val book = ReadBook.book ?: return
        scope.launch(Dispatchers.IO) {
            CastAssignmentStore.unassign(book.bookUrl, ReadBook.durChapterIndex, ordinal)
            withContext(Dispatchers.Main) {
                reloadChapter()
                refreshAloudCast()
                sendIntent(ReadBookIntent.DismissSheet)
            }
        }
    }

    /** 确认/创建共用收尾：成功重排+关窗；失败 toast 说明原因并保持悬浮窗打开。 */
    /**
     * 段首配乐：只写 bgm_scene_marks，正文文本一个字符都不动（胶囊是零语义字符的视觉 span），
     * 所以这里同样只需重排当前章，不需要碰朗读内容。
     */
    fun setBgmScene(intent: ReadBookIntent.SetBgmScene) {
        val book = ReadBook.book ?: return
        scope.launch(Dispatchers.IO) {
            if (intent.poolName.isBlank() && intent.trackName.isBlank()) {
                BgmSceneStore.clear(book.bookUrl, ReadBook.durChapterIndex, intent.paragraphIndex)
            } else {
                BgmSceneStore.put(
                    bookUrl = book.bookUrl,
                    chapterIndex = ReadBook.durChapterIndex,
                    ordinal = intent.paragraphIndex,
                    poolName = intent.poolName,
                    trackName = intent.trackName,
                    volume = intent.volume,
                )
            }
            withContext(Dispatchers.Main) {
                reloadChapter()
                emitToast(context.getString(R.string.cast_bgm_scene_saved))
                sendIntent(ReadBookIntent.DismissSheet)
            }
        }
    }

    /** 清除一段的配乐分配并重排当前章。 */
    fun clearBgmScene(paragraphIndex: Int) {
        val book = ReadBook.book ?: return
        scope.launch(Dispatchers.IO) {
            BgmSceneStore.clear(book.bookUrl, ReadBook.durChapterIndex, paragraphIndex)
            withContext(Dispatchers.Main) {
                reloadChapter()
                sendIntent(ReadBookIntent.DismissSheet)
            }
        }
    }

    /**
     * 总览里就地改一段（池/曲目/音量）：写库 + 重排，但不关窗、不弹 toast。
     *
     * 不关窗是因为总览要连着改好几段；重排是池/曲目变了段首胶囊的文字要跟着改（音量不在
     * 胶囊文字里，重排只是顺带，朗读中的配乐轨由 BgmSceneStore.version 自己发现）。
     */
    fun updateBgmScene(intent: ReadBookIntent.UpdateBgmScene) {
        val book = ReadBook.book ?: return
        scope.launch(Dispatchers.IO) {
            if (intent.poolName.isBlank() && intent.trackName.isBlank()) return@launch
            BgmSceneStore.put(
                bookUrl = book.bookUrl,
                chapterIndex = ReadBook.durChapterIndex,
                ordinal = intent.paragraphIndex,
                poolName = intent.poolName,
                trackName = intent.trackName,
                volume = intent.volume,
            )
            withContext(Dispatchers.Main) { reloadChapter() }
        }
    }

    /** 总览里删除一段配乐：重排让胶囊消失，但总览窗口保持打开。 */
    fun deleteBgmScene(paragraphIndex: Int) {
        val book = ReadBook.book ?: return
        scope.launch(Dispatchers.IO) {
            BgmSceneStore.clear(book.bookUrl, ReadBook.durChapterIndex, paragraphIndex)
            withContext(Dispatchers.Main) { reloadChapter() }
        }
    }


    /**
     * 让改动出声：正在朗读时按当前朗读位置重排本章的朗读队列。
     *
     * 只重排队列，不重开服务、不回到章首——正在播的那句仍用旧音色播完，改动从下一句生效。
     * 配乐轨不在这儿管（它自己靠 BgmSceneStore.version 发现改动）。
     */
    private fun refreshAloudCast() {
        if (BaseReadAloudService.isRun) ReadAloud.refreshCastQueue(context)
    }

    private suspend fun applyResult(result: CastResult) {
        if (result == CastResult.OK) {
            withContext(Dispatchers.Main) {
                reloadChapter()
                refreshAloudCast()
                sendIntent(ReadBookIntent.DismissSheet)
            }
            return
        }
        val messageRes = when (result) {
            CastResult.INVALID_NAME -> R.string.cast_invalid_name
            CastResult.NOT_FOUND -> R.string.cast_confirm_not_found
            CastResult.EXISTS -> R.string.cast_create_exists
            CastResult.OK -> return
        }
        val message = context.getString(messageRes)
        withContext(Dispatchers.Main) { emitToast(message) }
    }
}

/** 配乐那一趟的章名前缀：两趟共用一份进度，靠它区分「角色·第 N 章」还是「配乐·第 N 章」。 */
private const val SCENE_PREFIX = "配乐·"

/** 失败明细在悬浮窗里最多留几行（一章一行，再多就把卡片顶到状态栏）。 */
private const val MAX_FAILURE_LINES = 12

/** 失败汇总 toast 的字数上限：toast 太长会被系统截断，剩下的在悬浮窗里逐章看。 */
private const val MAX_TOAST_CHARS = 400
