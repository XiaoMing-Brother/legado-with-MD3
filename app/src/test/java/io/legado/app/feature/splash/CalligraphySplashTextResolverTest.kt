package io.legado.app.feature.splash

import io.legado.app.domain.model.settings.OtherSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CalligraphySplashTextResolverTest {

    @Test
    fun `随机关闭时使用固定开屏文字`() {
        val settings = OtherSettings(
            calligraphySplashRandomEnabled = false,
            calligraphySplashText = "固定文字",
            calligraphySplashTextPool = "甲|乙",
        )

        assertEquals(
            "固定文字",
            CalligraphySplashTextResolver.resolve(settings, listOf("内置"), Random(0)),
        )
    }

    @Test
    fun `随机开启时优先使用自定义句子库`() {
        val settings = OtherSettings(
            calligraphySplashRandomEnabled = true,
            calligraphySplashTextPool = "甲|乙",
        )

        val result = CalligraphySplashTextResolver.resolve(settings, listOf("内置"), Random(0))

        assertTrue(result == "甲" || result == "乙")
    }

    @Test
    fun `自定义句子库为空时使用内置句子库`() {
        val settings = OtherSettings(
            calligraphySplashRandomEnabled = true,
            calligraphySplashTextPool = "  |  ",
        )

        assertEquals(
            "内置",
            CalligraphySplashTextResolver.resolve(settings, listOf("内置"), Random(0)),
        )
    }

    @Test
    fun `内置句子库不可用时回退固定文字`() {
        val settings = OtherSettings(
            calligraphySplashRandomEnabled = true,
            calligraphySplashText = "固定文字",
            calligraphySplashTextPool = "",
        )

        assertEquals(
            "固定文字",
            CalligraphySplashTextResolver.resolve(settings, emptyList(), Random(0)),
        )
    }

    @Test
    fun `支持全角竖线分隔自定义句子`() {
        val settings = OtherSettings(
            calligraphySplashRandomEnabled = true,
            calligraphySplashTextPool = "甲｜乙",
        )

        val result = CalligraphySplashTextResolver.resolve(settings, listOf("内置"), Random(0))

        assertTrue(result == "甲" || result == "乙")
    }
}
