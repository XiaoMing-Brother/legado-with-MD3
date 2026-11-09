package io.legado.app.feature.splash

import io.legado.app.domain.model.settings.OtherSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CalligraphySplashPolicyTest {

    @Test
    fun disabledSplashNeverShows() {
        val settings = OtherSettings(calligraphySplashEnabled = false)
        assertFalse(CalligraphySplashPolicy.shouldShow(settings))
    }

    @Test
    fun oncePerDaySkipsWhenAlreadyShownToday() {
        val today = LocalDate.of(2026, 3, 27)
        val settings = OtherSettings(
            calligraphySplashOncePerDay = true,
            calligraphySplashLastShownDay = 20260327,
        )
        assertFalse(CalligraphySplashPolicy.shouldShow(settings, today))
    }

    @Test
    fun oncePerDayShowsOnNewDay() {
        val today = LocalDate.of(2026, 3, 28)
        val settings = OtherSettings(
            calligraphySplashOncePerDay = true,
            calligraphySplashLastShownDay = 20260327,
        )
        assertTrue(CalligraphySplashPolicy.shouldShow(settings, today))
    }
}
