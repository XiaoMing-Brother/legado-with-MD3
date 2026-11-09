package io.legado.app.core.ui.morph

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

/**
 * 记录图书封面在全局坐标中的锚点信息，用于无缝 Morph 展开到阅读器、漫画或有声书界面。
 */
@Immutable
data class BookCoverMorphAnchor(
    val bounds: Rect,
    val cornerRadiusPx: Float,
    val bookName: String?,
    val author: String?,
    val coverPath: String?,
    val sourceOrigin: String? = null,
    val bookUrl: String? = null,
    val badgeText: String? = null,
    val showBadgeDot: Boolean = false,
    val leftBottomText: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

object BookCoverMorphAnchors {

    /** 锚点缓存上限：超出后按访问顺序淘汰最久未用的一条。 */
    private const val MaxAnchors = 80

    /**
     * 书架在滚动时每个可见封面每一帧都会 [report] 一次，这里必须是 O(1) 写入：
     * 旧实现每写一条就做一次全表 minBy 找最旧项（表满 80 条后每帧 × 可见封面数次），
     * 是滚动掉帧的一处固定开销。LinkedHashMap(accessOrder) 天然按访问顺序淘汰。
     */
    private val anchors = object :
        LinkedHashMap<String, BookCoverMorphAnchor>(MaxAnchors, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, BookCoverMorphAnchor>
        ): Boolean = size > MaxAnchors
    }

    var activeMorphKey by mutableStateOf<String?>(null)
    var activeMorphState by mutableStateOf<BookMorphState?>(null)

    fun setActiveMorph(key: String?, state: BookMorphState?) {
        activeMorphKey = key
        activeMorphState = state
    }

    fun clearActiveMorph(key: String?) {
        if (key == null || isMorphing(key)) {
            activeMorphKey = null
            activeMorphState = null
        }
    }

    fun clearActiveMorphKey(key: String?) {
        clearActiveMorph(key)
    }

    fun isMorphing(key: String?): Boolean {
        val active = activeMorphKey ?: return false
        if (key.isNullOrBlank()) return false
        if (key == active) return true
        val isDetailActive = isDetailKey(active)
        val isDetailCurrent = isDetailKey(key)
        if (isDetailActive != isDetailCurrent) return false
        val cleanActive = cleanKey(active)
        val cleanCurrent = cleanKey(key)
        return cleanActive == cleanCurrent ||
                cleanActive.endsWith(":$cleanCurrent") ||
                cleanCurrent.endsWith(":$cleanActive")
    }

    private fun isDetailKey(key: String): Boolean =
        key.startsWith("cover:detail:") || key.startsWith("book-info-cover:")

    private fun cleanKey(key: String): String =
        key.removePrefix("cover:shelf:")
            .removePrefix("cover:detail:")
            .removePrefix("book-info-cover:")
            .removePrefix("book-cover:")

    /**
     * 判断指定书源/书架封面当前是否应当隐藏（让位给飞行封面）。
     *
     * 参考播放悬浮胶囊方案：在 graphicsLayer 内动态读取动画进度 progress.value。
     * - 动画进行中 (progress > 0.001f)：源封面隐藏 (alpha = 0f)，避免双封面叠影；
     * - 动画归零收回 (progress <= 0.001f)：源封面在同一绘制帧瞬间显现 (alpha = 1f)，
     *   与飞行封面隐去实现零延迟接力，彻底消除等待 onDispose/popBackStack 导致的空白跳闪。
     */
    fun isOriginCoverHidden(key: String?): Boolean {
        if (!isMorphing(key)) return false
        val state = activeMorphState ?: return true
        return state.progress.value > 0.001f
    }

    fun report(
        key: String?,
        bounds: Rect,
        cornerRadiusPx: Float,
        bookName: String? = null,
        author: String? = null,
        coverPath: String? = null,
        sourceOrigin: String? = null,
        bookUrl: String? = null,
        badgeText: String? = null,
        showBadgeDot: Boolean = false,
        leftBottomText: String? = null,
    ) {
        if (key.isNullOrBlank() || bounds.isEmpty) return
        synchronized(anchors) {
            // 位置与内容都没变就别重写：静止时每次布局回调都会走到这里
            val previous = anchors[key]
            if (previous != null &&
                previous.bounds == bounds &&
                previous.cornerRadiusPx == cornerRadiusPx &&
                previous.bookName == bookName &&
                previous.author == author &&
                previous.coverPath == coverPath &&
                previous.sourceOrigin == sourceOrigin &&
                previous.bookUrl == bookUrl &&
                previous.badgeText == badgeText &&
                previous.showBadgeDot == showBadgeDot &&
                previous.leftBottomText == leftBottomText
            ) {
                return
            }
            anchors[key] = BookCoverMorphAnchor(
                bounds = bounds,
                cornerRadiusPx = cornerRadiusPx,
                bookName = bookName,
                author = author,
                coverPath = coverPath,
                sourceOrigin = sourceOrigin,
                bookUrl = bookUrl,
                badgeText = badgeText,
                showBadgeDot = showBadgeDot,
                leftBottomText = leftBottomText,
            )
        }
    }

    fun get(key: String?): BookCoverMorphAnchor? {
        if (key.isNullOrBlank()) return null
        synchronized(anchors) {
            anchors[key]?.let { return it }
            val isDetail = isDetailKey(key)
            // 兜底策略：只在同一命名空间内允许部分匹配
            return anchors.entries.firstOrNull {
                isDetailKey(it.key) == isDetail &&
                        (it.key.contains(key) || key.contains(it.key))
            }?.value
        }
    }

    fun remove(key: String?) {
        if (key != null) synchronized(anchors) { anchors.remove(key) }
    }
}
