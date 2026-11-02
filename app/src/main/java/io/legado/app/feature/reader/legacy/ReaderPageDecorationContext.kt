package io.legado.app.feature.reader.legacy

import io.legado.app.ui.book.read.pageestimate.WholeBookPageState

/**
 * 页眉页脚渲染所需的会话投影，避免 decoration 工厂直接读取全局 [ReadBook] 单例。
 */
data class ReaderPageDecorationContext(
    val bookName: String,
    val chapterCount: Int,
    val isScroll: Boolean,
    val wholeBookPageStateProvider: (chapterIndex: Int, pageIndex: Int) -> WholeBookPageState?,
)
