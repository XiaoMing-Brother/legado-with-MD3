package io.legado.app.feature.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/**
 * 冷启动时在主界面之上展示书法开屏；结束后回调 [onFinished]（用于收起系统 SplashScreen）。
 */
@Composable
fun CalligraphySplashOverlay(
    onFinished: () -> Unit,
) {
    var visible by remember { mutableStateOf(true) }
    var dismissed by remember { mutableStateOf(false) }

    fun dismiss() {
        if (dismissed) return
        dismissed = true
        visible = false
    }

    LaunchedEffect(Unit) {
        delay(2_400)
        dismiss()
    }

    LaunchedEffect(visible) {
        if (!visible) {
            delay(450)
            onFinished()
        }
    }

    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(animationSpec = tween(420)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = ::dismiss,
                ),
        ) {
            CalligraphySplashScreen()
        }
    }
}
