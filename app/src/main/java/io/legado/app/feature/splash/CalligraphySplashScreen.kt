package io.legado.app.feature.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import io.legado.app.R

/** 与 [R.color.calligraphy_splash_paper]、系统 Splash 背景保持一致，避免图片未就绪时的闪白。 */
private val PaperColor = Color(0xFFF2EDE4)

/**
 * 阅微式开屏：整幅纸纹书法图。诗句与朱印已烘焙进 [R.drawable.calligraphy_splash_portrait]，
 * 不再由 Compose 排版文字。
 */
@Composable
fun CalligraphySplashScreen(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PaperColor),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.calligraphy_splash_portrait),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
        )
    }
}
