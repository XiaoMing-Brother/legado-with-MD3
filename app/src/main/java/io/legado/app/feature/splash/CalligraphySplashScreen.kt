package io.legado.app.feature.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R

/**
 * 内置开屏字体（汇文明朝体 Huiwen Mincho，CC0 1.0，见 assets/licenses）。
 * 打包的是 GB2312 + 内置句子库字形子集；自定义开屏文字中的罕见字会回退系统字体。
 */
private val DefaultSplashFontFamily = FontFamily(Font(R.font.splash_huiwen_mincho))

/** 霞鹜篆书「朙」正篆字形的子集；“朙”是“明”的古字。 */
private val SealFontFamily = FontFamily(Font(R.font.seal_ming))

/** 未设置开屏文字时使用的默认诗句，换行即分列。 */
private const val DEFAULT_SPLASH_TEXT = "欲买桂花同载酒\n终不似少年游"

/** 竖排上限：最多 4 列，每列最多 12 字；显式换行分列，超长单句自动折列。 */
private const val MAX_COLUMNS = 4
private const val MAX_CHARS_PER_COLUMN = 12

/**
 * 阅微式开屏：纯色纸底 + 竖排书法文字 + 朱红落款印。
 *
 * 纸色/墨色/印色分别走 `R.color.calligraphy_splash_paper`、`_ink`、`_seal`，
 * 三者都有 `values-night` 版本，深浅色由资源限定符自动切换，与系统启动窗口同调。
 * 文字来自 [io.legado.app.domain.model.settings.OtherSettings.calligraphySplashText]，可自定义。
 */
@Composable
fun CalligraphySplashScreen(
    modifier: Modifier = Modifier,
    text: String = "",
) {
    val columns = remember(text) { text.toSplashColumns() }
    val paperColor = colorResource(R.color.calligraphy_splash_paper)
    val inkColor = colorResource(R.color.calligraphy_splash_ink)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(paperColor),
        contentAlignment = Alignment.Center,
    ) {
        val totalChars = columns.sumOf { it.size }
        val fontSize = when {
            totalChars > 32 -> 20.sp
            totalChars > 20 -> 24.sp
            totalChars > 10 -> 30.sp
            else -> 36.sp
        }
        Box {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 中文竖排：首列在最右，而 Row 从左往右布局，故倒序排列
                columns.asReversed().forEach { column ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        column.forEach { glyph ->
                            Text(
                                text = glyph,
                                color = inkColor,
                                fontSize = fontSize,
                                lineHeight = fontSize * 1.08f,
                                fontFamily = DefaultSplashFontFamily,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
            // 小印贴近末字右侧，位置和比例参考阅微的启动图。
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-5).dp, y = (-8).dp)
                    .size(width = 14.dp, height = 21.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.calligraphy_seal),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    text = "朙",
                    color = colorResource(R.color.calligraphy_splash_seal_carving),
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    fontFamily = SealFontFamily,
                    softWrap = false,
                )
            }
        }
    }
}

private fun String.toSplashColumns(): List<List<String>> {
    val lines = ifBlank { DEFAULT_SPLASH_TEXT }
        .replace("\r", "")
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    val columns = ArrayList<List<String>>(MAX_COLUMNS)
    for (line in lines) {
        val remainingColumns = MAX_COLUMNS - columns.size
        if (remainingColumns <= 0) break
        // 显式换行仍分列；超长单句在剩余列数内均衡折列，避免被截断或出现孤立单字。
        val glyphs = line.toGlyphs().take(remainingColumns * MAX_CHARS_PER_COLUMN)
        if (glyphs.isEmpty()) continue
        val columnCount = ((glyphs.size + MAX_CHARS_PER_COLUMN - 1) / MAX_CHARS_PER_COLUMN)
            .coerceIn(1, remainingColumns)
        val baseSize = glyphs.size / columnCount
        val extra = glyphs.size % columnCount
        var start = 0
        repeat(columnCount) { index ->
            val size = baseSize + if (index < extra) 1 else 0
            columns += glyphs.subList(start, start + size)
            start += size
        }
    }
    return columns.ifEmpty {
        DEFAULT_SPLASH_TEXT.split('\n').map { line -> line.toGlyphs() }
    }
}

/** 按码点拆分，保证 emoji 等代理对不被拆成半个字符。 */
private fun String.toGlyphs(): List<String> {
    val glyphs = ArrayList<String>(length)
    var index = 0
    while (index < length) {
        val codePoint = codePointAt(index)
        glyphs.add(
            if (Character.isSupplementaryCodePoint(codePoint)) {
                String(Character.toChars(codePoint))
            } else {
                this[index].toString()
            },
        )
        index += Character.charCount(codePoint)
    }
    return glyphs
}
