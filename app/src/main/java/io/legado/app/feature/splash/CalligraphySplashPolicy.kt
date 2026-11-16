package io.legado.app.feature.splash

import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.model.settings.OtherSettings
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object CalligraphySplashPolicy {
    private val dayFormatter = DateTimeFormatter.BASIC_ISO_DATE

    fun shouldShow(
        settings: OtherSettings,
        today: LocalDate = LocalDate.now(),
    ): Boolean {
        if (!settings.calligraphySplashEnabled) return false
        if (!settings.calligraphySplashOncePerDay) return true
        return settings.calligraphySplashLastShownDay != today.toYyyymmdd()
    }

    suspend fun markShown(gateway: OtherSettingsGateway) {
        val settings = gateway.currentSettings
        if (!settings.calligraphySplashOncePerDay) return
        val today = LocalDate.now().toYyyymmdd()
        if (settings.calligraphySplashLastShownDay == today) return
        gateway.update { it.copy(calligraphySplashLastShownDay = today) }
    }

    private fun LocalDate.toYyyymmdd(): Int = format(dayFormatter).toInt()
}
