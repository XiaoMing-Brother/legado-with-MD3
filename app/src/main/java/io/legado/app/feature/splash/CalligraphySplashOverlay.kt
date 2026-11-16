package io.legado.app.feature.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.zIndex
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import io.legado.app.R
import kotlinx.coroutines.delay

/**
 * 冷启动时在主界面之上展示书法开屏；结束后回调 [onFinished]（用于收起系统 SplashScreen）。
 *
 * [onFirstFrame] 在诗句真正画上屏幕后回调一次：系统启动窗口要等它才淡出，
 * 否则纸色窗口先消失、诗句晚几百毫秒才出现，就是启动时的“延迟感”。
 */
@Composable
fun CalligraphySplashOverlay(
    onFinished: () -> Unit,
    text: String = "",
    onFirstFrame: () -> Unit = {},
) {
    var visible by remember { mutableStateOf(true) }
    var dismissed by remember { mutableStateOf(false) }

    fun dismiss() {
        if (dismissed) return
        dismissed = true
        visible = false
    }

    // 字形要等字体加载完才画得出来，两帧后再放行系统启动窗口。
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        onFirstFrame()
    }

    LaunchedEffect(Unit) {
        delay(1_500)
        dismiss()
    }

    LaunchedEffect(visible) {
        if (!visible) {
            delay(320)
            onFinished()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(10_000f),
    ) {
        AnimatedVisibility(
            visible = visible,
            exit = fadeOut(animationSpec = tween(280)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colorResource(R.color.calligraphy_splash_paper))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = ::dismiss,
                    ),
            ) {
                CalligraphySplashScreen(text = text)
            }
        }
    }
}
