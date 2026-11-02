package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.ReadStyleState
import io.legado.app.domain.model.settings.ReadBackgroundPalette
import io.legado.app.domain.model.settings.ReadThemePreset
import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream

interface ReadStyleGateway {
    val currentState: ReadStyleState
    val state: StateFlow<ReadStyleState>

    fun refresh()
    fun save()

    /**
     * 日夜 / 墨水屏模式切换后调用。
     *
     * 排版值本身没变，但**解析后的生效值**变了（`curTextColor()`/`curUnderlineColor()`/
     * `curTextShadowColor()` 等都按当前模式取值），订阅方必须重新派生快照。
     */
    fun notifyModeChanged()
    fun updateCurrentStyle(mutation: ReadStyleMutation)

    /**
     * 排版时发现当前字体文件已不存在——清空字体设置并落盘，下次排版回落到系统字体。
     */
    fun clearMissingTextFont()
    fun applyPreset(index: Int): Boolean

    /**
     * 应用一个内置阅读主题（清墨 LightInk 主题商店快照，见 `assets/defaultData/readThemes.json`）。
     *
     * 一次写入底色、正文/页眉页脚色、强调色与边距，并落盘；返回 false 表示主题 id 不存在。
     * 主题自带背景图时这里**只**写配色——图片由 [downloadReadThemeBackground] 异步落盘后
     * 再经 [setReadThemeBackgroundImage] 补上，配色先一步生效，不等下载。
     */
    fun applyReadTheme(themeId: Int): Boolean

    /**
     * 把主题背景图下载到本地 bg 目录，返回绝对路径；全部候选地址都失败时返回 null。
     *
     * 纯下载，不碰排版配置——写配置由 [setReadThemeBackgroundImage] 负责，两步分开
     * 才能保证下载失败时配色照旧生效。
     *
     * 主题商店的图床在数据里多为 `http://`，候选顺序见 `themeMipmapUrlCandidates`。
     */
    suspend fun downloadReadThemeBackground(theme: ReadThemePreset): String?

    /**
     * 把已落盘的主题背景图写进**三种阅读模式**并落盘一次。
     *
     * 与 [applyReadTheme] 写三份配色对称：主题只有一张图，只写当前模式会导致
     * 「白天有图、切到夜间变纯色」。与 [setCurrentBackgroundImage] 的区别仅在写入范围。
     */
    fun setReadThemeBackgroundImage(path: String)

    /**
     * 把从背景图提取出来的一套配色写进**当前样式**（清墨：「主题编辑器会根据图片提取出一套配色
     * 便于快捷搭配」）。
     *
     * 只改文字相关项：底色仍由背景图提供（[ReadBackgroundPalette.backgroundArgb] 仅用于判定状态栏
     * 图标明暗）。三种阅读模式写同一套——用户是照着「这张图」挑的色，切到夜间变成另一套反而错位。
     */
    fun applyReadBackgroundPalette(palette: ReadBackgroundPalette)

    fun addStyle(): Int
    fun deleteCurrentStyle(): Boolean
    fun importCurrentStyle(bytes: ByteArray)
    fun importOrReplaceStyle(bytes: ByteArray): String
    fun exportCurrentStyle(): ByteArray
    fun saveBackgroundImage(inputStream: InputStream, displayName: String?): String
    fun setCurrentBackgroundImage(path: String)
    fun setCurrentBackgroundImageForMode(path: String, isNight: Boolean)
    fun exportConfigsJson(): String
    fun exportShareConfigJson(): String

    /** 备份要把所有排版引用到的背景图一起带走。 */
    fun allBackgroundImagePaths(): List<String>

    /** 清掉没有任何排版引用的背景图，以及导入导出留下的缓存。 */
    fun clearUnusedBackgrounds()
}
