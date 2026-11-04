package io.legado.app.feature.splash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.widget.components.text.AppText
import kotlin.random.Random

private val PaperColor = Color(0xFFF2EDE4)
private val InkColor = Color(0xFF1C1A17)
private val SealRed = Color(0xFFB71C1C)

/**
 * 阅微式开屏：纸纹底 + 竖排书法 + 朱印。文案见 [R.string.calligraphy_splash_line1/2]。
 */
@Composable
fun CalligraphySplashScreen(
    modifier: Modifier = Modifier,
) {
    val line1 = stringResource(R.string.calligraphy_splash_line1)
    val line2 = stringResource(R.string.calligraphy_splash_line2)
    val sealText = stringResource(R.string.calligraphy_splash_seal)
    val speckleSeed = remember { Random(0x4C656761646FL) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        PaperTextureBackground(seed = speckleSeed)
        Row(
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VerticalCalligraphyColumn(line2)
            VerticalCalligraphyColumn(line1)
        }
        CalligraphySeal(
            text = sealText,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 52.dp, y = 96.dp),
        )
    }
}

@Composable
private fun PaperTextureBackground(seed: Random) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(PaperColor)
        drawPaperSpeckles(seed)
    }
}

private fun DrawScope.drawPaperSpeckles(seed: Random) {
    val random = Random(seed.nextLong())
    repeat(900) {
        val x = random.nextFloat() * size.width
        val y = random.nextFloat() * size.height
        val alpha = 0.02f + random.nextFloat() * 0.06f
        val radius = 0.6f + random.nextFloat() * 1.8f
        drawCircle(
            color = Color(0xFF3E3A33).copy(alpha = alpha),
            radius = radius,
            center = Offset(x, y),
        )
    }
}

@Composable
private fun VerticalCalligraphyColumn(text: String) {
    val style = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        color = InkColor,
        textAlign = TextAlign.Center,
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        text.filter { !it.isWhitespace() && it != '，' && it != '。' }.forEach { ch ->
            AppText(text = ch.toString(), style = style)
        }
    }
}

@Composable
private fun CalligraphySeal(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(color = SealRed.copy(alpha = 0.92f), radius = size.minDimension / 2f)
        }
        AppText(
            text = text,
            style = TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
            ),
        )
    }
}
