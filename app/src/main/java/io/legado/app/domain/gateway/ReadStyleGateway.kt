package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.ReadStyleState
import io.legado.app.domain.model.settings.ReadBackgroundPalette
import io.legado.app.domain.model.settings.ReadThemePreset
import io.legado.app.domain.model.settings.ReadThemeTarget
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
     * **只写 [target] 那份底色与状态栏图标**：日间写 `bgStr`（并同步墨水屏），夜间写
     * `bgStrNight`，另一模式保持不动。字体颜色、字号、页边距属于用户排版设置，应用主题时不得覆盖。
     * 返回 false 表示主题 id 不存在。主题自带背景图时这里**只**写背景色——图片由
     * [downloadReadThemeBackground] 异步落盘后再经 [setReadThemeBackgroundImage] 补上，
     * 背景色先一步生效，不等下载。
     */
    fun applyReadTheme(themeId: Int, target: ReadThemeTarget): Boolean

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
     * 把已落盘的主题背景图写进排版配置并落盘一次。
     *
     * [target] 决定写哪份：日间写白天与墨水屏，夜间只写 `bgStrNight`，另一模式保持不动。
     * 与 [setCurrentBackgroundImage] 的区别仅在写入范围。
     */
    fun setReadThemeBackgroundImage(path: String, target: ReadThemeTarget)

    /**
     * 把从背景图提取出来的一套配色写进**当前样式**（清墨：「主题编辑器会根据图片提取出一套配色
     * 便于快捷搭配」）。
     *
     * 只改文字相关项：底色仍由背景图提供（[ReadBackgroundPalette.backgroundArgb] 仅用于判定状态栏
     * 图标明暗）。白天与墨水屏写图片取色；夜间槽位是纯色底时按该底色重新反推文字色，避免白天亮图
     * 取出的深色文字压在深色夜间底上看不清。夜间也用这张图时保持三种模式同一套配色。
     */
    fun applyReadBackgroundPalette(palette: ReadBackgroundPalette)

    fun addStyle(): Int
    fun deleteCurrentStyle(): Boolean

    /** 在自建样式区（内置预设那几份之外）内重排；返回是否真的移动了。 */
    fun moveStyle(from: Int, to: Int): Boolean

    /** 批量删除自建样式（下标按删除前算）；返回是否真的删了。 */
    fun deleteStylesAt(indices: List<Int>): Boolean

    /** 把选中样式整块搬到边缘：置顶 = 紧跟预设之后，置底 = 列表末尾；块内保持原相对次序。 */
    fun moveStylesToEdge(indices: List<Int>, toTop: Boolean): Boolean
    fun importCurrentStyle(bytes: ByteArray)
    fun importOrReplaceStyle(bytes: ByteArray): String

    /**
     * 导入为**新增**样式：全部追加到列表末尾，绝不碰任何已有主题（也不改当前选中）。
     *
     * @return 单份导入时返回新样式的下标（调用方据此"立刻应用"）；
     *         批量包返回 null —— 批量必须保持静默，不切换正在使用的主题。
     */
    fun importStylesAsNew(bytes: ByteArray): Int?
    fun exportCurrentStyle(): ByteArray

    /**
     * 批量导出：外层 zip 套若干「单样式包」。按各样式本来的样子导
     * （不混入共享排版的字体项——那套合并语义只属于单份 [exportCurrentStyle]）。
     */
    fun exportStyles(indices: List<Int>): ByteArray

    /**
     * 单份导出指定下标的那份样式（产物 = 批量包里对应那一个小包，格式完全一致）。
     * 下标无效返回 null。用于"只选中 1 个时直接分享单份"。
     */
    fun exportStyleAt(index: Int): ByteArray?
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
