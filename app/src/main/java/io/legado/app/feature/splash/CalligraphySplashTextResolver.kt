package io.legado.app.feature.splash

import io.legado.app.domain.model.settings.OtherSettings
import kotlin.random.Random

object CalligraphySplashTextResolver {
    private const val CUSTOM_POOL_SEPARATOR = '|'

    fun resolve(
        settings: OtherSettings,
        random: Random = Random.Default,
    ): String {
        if (!settings.calligraphySplashRandomEnabled) return settings.calligraphySplashText
        return resolve(settings, CalligraphySplashQuotes.builtIn, random)
    }

    internal fun resolve(
        settings: OtherSettings,
        builtInQuotes: List<String>,
        random: Random,
    ): String {
        if (!settings.calligraphySplashRandomEnabled) return settings.calligraphySplashText
        val customQuotes = settings.calligraphySplashTextPool
            .replace('｜', CUSTOM_POOL_SEPARATOR)
            .split(CUSTOM_POOL_SEPARATOR)
            .map(String::trim)
            .filter(String::isNotEmpty)
        val pool = customQuotes.ifEmpty { builtInQuotes }
        return pool.randomOrNull(random) ?: settings.calligraphySplashText
    }
}
