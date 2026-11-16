package io.legado.app.help.readaloud.cast

import android.content.Context
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import kotlinx.coroutines.sync.Mutex
import splitties.init.appCtx

/**
 * 角色记忆的归属账本。
 *
 * 原先挂在 `AiCastPresetStore` 上；AI 功能整体移除后，这里只保留 `CastMemoryMirror`
 * 需要的部分：写入互斥、按稳定档案 id 记录"哪些字段由用户编辑过"。
 * 存储键沿用 `ai_cast` / `aiIdentityOwnershipV2|bookUrl`，老用户的账本不丢。
 */
internal object CastMemoryStore {

    internal val memoryMutex = Mutex()

    internal fun identityOwnership(bookUrl: String): Map<String, CastMemoryMirror.IdentityOwnership> =
        GSON.fromJsonArray<CastMemoryMirror.IdentityOwnership>(
            prefs().getString(ledgerKey(bookUrl), null)
        ).getOrNull().orEmpty().associateBy { it.profileId }

    internal fun saveIdentityOwnership(
        bookUrl: String,
        ledger: Map<String, CastMemoryMirror.IdentityOwnership>,
    ) {
        prefs().edit().putString(ledgerKey(bookUrl), GSON.toJson(ledger.values.toList())).apply()
    }

    internal fun markProfileEdited(bookUrl: String, profileId: String, identityEdited: Boolean) {
        val ledger = identityOwnership(bookUrl).toMutableMap()
        val previous = ledger[profileId] ?: CastMemoryMirror.IdentityOwnership(profileId)
        ledger[profileId] = previous.copy(
            aliases = if (identityEdited) emptyList() else previous.aliases,
            summary = if (identityEdited) "" else previous.summary,
            userEdited = true,
        )
        saveIdentityOwnership(bookUrl, ledger)
    }

    private fun ledgerKey(bookUrl: String) = "aiIdentityOwnershipV2|$bookUrl"

    private fun prefs() = appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val PREFS = "ai_cast"
}
