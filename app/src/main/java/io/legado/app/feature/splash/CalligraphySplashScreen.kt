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

/** 内置书法字体（Ma Shan Zheng / 马善政毛笔楷书，SIL OFL 1.1，见 assets/licenses）。 */
private val SplashFontFamily = FontFamily(Font(R.font.splash_calligraphy))

/** 霞鹜篆书「朙」正篆字形的子集；“朙”是“明”的古字。 */
private val SealFontFamily = FontFamily(Font(R.font.seal_ming))

/** 未设置开屏文字时使用的默认诗句，换行即分列。 */
private const val DEFAULT_SPLASH_TEXT = "欲买桂花同载酒\n终不似少年游"

/** 竖排上限：最多 4 列，每列最多 12 字，超出截断，避免长文本溢出屏幕。 */
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
                                fontFamily = SplashFontFamily,
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

private fun String.toSplashColumns(): List<List<String>> =
    ifBlank { DEFAULT_SPLASH_TEXT }
        .replace("\r", "")
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .take(MAX_COLUMNS)
        .map { line -> line.toGlyphs().take(MAX_CHARS_PER_COLUMN) }
        .ifEmpty { DEFAULT_SPLASH_TEXT.split('\n').map { line -> line.toGlyphs() } }

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
