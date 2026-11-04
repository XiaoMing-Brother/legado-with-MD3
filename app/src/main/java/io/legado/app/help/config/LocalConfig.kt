package io.legado.app.help.config

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.constant.PreferKey
import io.legado.app.utils.getBoolean
import io.legado.app.utils.putBoolean
import io.legado.app.utils.putLong
import splitties.init.appCtx

@Suppress("ConstPropertyName")
object LocalConfig : SharedPreferences
by appCtx.getSharedPreferences("local", Context.MODE_PRIVATE) {

    private const val versionCodeKey = "appVersionCode"

    // 内置默认数据的当前版本号，随 assets/defaultData 内容变更而递增
    private const val HTTP_TTS_VERSION = 6
    private const val TXT_TOC_RULE_VERSION = 4
    private const val RSS_SOURCE_VERSION = 6
    private const val DICT_RULE_VERSION = 3

    /**
     * 本地密码,用来对需要备份的敏感信息加密,如 webdav 配置等
     *
     * 读写都走 [AppConfigStore] 的内存快照：`App.onCreate` 首行已完成预加载，
     * 这里不再 `runBlocking` 挂起调用线程（旧实现在主线程布局/点击路径上会阻塞）。
     * 写侧沿用 AppConfigStore 的 pending overlay 语义，写完立即能读到新值。
     */
    var password: String?
        get() = AppConfigStore.getString(LocalPreferencesKeys.PASSWORD.name)?.ifEmpty { null }
        set(value) {
            AppConfigStore.putString(LocalPreferencesKeys.PASSWORD.name, value ?: "")
        }

    var lastBackup: Long
        get() = AppConfigStore.getLong(LocalPreferencesKeys.LAST_BACKUP.name) ?: 0L
        set(value) {
            AppConfigStore.putLong(LocalPreferencesKeys.LAST_BACKUP.name, value)
        }

    var privacyPolicyOk: Boolean
        get() = AppConfigStore.getBoolean(LocalPreferencesKeys.PRIVACY_POLICY_OK.name) ?: false
        set(value) {
            AppConfigStore.putBoolean(LocalPreferencesKeys.PRIVACY_POLICY_OK.name, value)
        }

    /** 旧版语言偏好是否已迁移到 AppCompat per-app locales（App.onCreate 一次性迁移） */
    var appLocaleMigrated: Boolean
        get() = getBoolean("appLocaleMigrated")
        set(value) {
            putBoolean("appLocaleMigrated", value)
        }

    val readHelpVersionIsLast: Boolean
        get() = isLastVersion(1, "readHelpVersion", "firstRead")

    val backupHelpVersionIsLast: Boolean
        get() = isLastVersion(1, "backupHelpVersion", "firstBackup")

    val readMenuHelpVersionIsLast: Boolean
        get() = isLastVersion(1, "readMenuHelpVersion", "firstReadMenu")

    val bookSourcesHelpVersionIsLast: Boolean
        get() = isLastVersion(1, "bookSourceHelpVersion", "firstOpenBookSources")

    val webDavBookHelpVersionIsLast: Boolean
        get() = isLastVersion(1, "webDavBookHelpVersion", "firstOpenWebDavBook")

    val ruleHelpVersionIsLast: Boolean
        get() = isLastVersion(1, "ruleHelpVersion")

    // 内置默认数据的导入标记：与下面的 isLastVersion 不同，这里读取不写值。
    // 标记只在 DefaultData 对应导入成功之后由 mark*UpToDate() 提交，
    // 导入失败时不落标记，下次冷启动会重试，避免「读一次就永久消费」。
    val needUpHttpTTS: Boolean
        get() = getInt("httpTtsVersion", 0) < HTTP_TTS_VERSION

    val needUpTxtTocRule: Boolean
        get() = getInt("txtTocRuleVersion", 0) < TXT_TOC_RULE_VERSION

    val needUpRssSources: Boolean
        get() = getInt("rssSourceVersion", 0) < RSS_SOURCE_VERSION

    val needUpDictRule: Boolean
        get() = getInt("needUpDictRule", 0) < DICT_RULE_VERSION

    fun markHttpTTSUpToDate() = markDefaultDataVersion("httpTtsVersion", HTTP_TTS_VERSION)

    fun markTxtTocRuleUpToDate() = markDefaultDataVersion("txtTocRuleVersion", TXT_TOC_RULE_VERSION)

    fun markRssSourcesUpToDate() = markDefaultDataVersion("rssSourceVersion", RSS_SOURCE_VERSION)

    fun markDictRuleUpToDate() = markDefaultDataVersion("needUpDictRule", DICT_RULE_VERSION)

    private fun markDefaultDataVersion(versionKey: String, version: Int) {
        if (getInt(versionKey, 0) < version) {
            edit { putInt(versionKey, version) }
        }
    }

    var versionCode
        get() = getLong(versionCodeKey, 0)
        set(value) {
            edit { putLong(versionCodeKey, value) }
        }

    val isFirstOpenApp: Boolean
        get() {
            val value = getBoolean("firstOpen", true)
            if (value) {
                edit { putBoolean("firstOpen", false) }
            }
            return value
        }

    @Suppress("SameParameterValue")
    private fun isLastVersion(
        lastVersion: Int,
        versionKey: String,
        firstOpenKey: String? = null
    ): Boolean {
        var version = getInt(versionKey, 0)
        if (version == 0 && firstOpenKey != null) {
            if (!getBoolean(firstOpenKey, true)) {
                version = 1
            }
        }
        if (version < lastVersion) {
            edit { putInt(versionKey, lastVersion) }
            return false
        }
        return true
    }

    var bookInfoDeleteAlert: Boolean
        get() = getBoolean("bookInfoDeleteAlert", true)
        set(value) {
            putBoolean("bookInfoDeleteAlert", value)
        }

    var deleteBookOriginal: Boolean
        get() = getBoolean("deleteBookOriginal")
        set(value) {
            putBoolean("deleteBookOriginal", value)
        }

}
