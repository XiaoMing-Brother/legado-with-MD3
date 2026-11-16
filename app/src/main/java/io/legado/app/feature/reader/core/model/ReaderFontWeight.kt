package io.legado.app.feature.reader.core.model

/**
 * 字重配置的口径（0=常规 / 1=加粗 / 2=细 / 100..900=直接值）。
 *
 * 正文、标题、页眉页脚共用这一份：预览与最终排版必须解释同一套取值，
 * 否则同一次「粗体」在弹层里看着是粗的、正文渲染却是常规。
 */
fun resolveReaderFontWeight(configured: Int): Int = when (configured) {
    1 -> 900
    2 -> 300
    in 100..900 -> configured
    else -> 400
}
