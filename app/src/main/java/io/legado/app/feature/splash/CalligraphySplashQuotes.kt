package io.legado.app.feature.splash

import splitties.init.appCtx

/** 内置古典短句，来源与许可见 assets/licenses/ChinesePoetry-Data-NOTICE.txt。 */
object CalligraphySplashQuotes {
    private const val ASSET_PATH = "defaultData/splashQuotes.txt"

    val builtIn: List<String> by lazy {
        runCatching {
            appCtx.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { reader ->
                reader.lineSequence()
                    .map(String::trim)
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .distinct()
                    .toList()
            }
        }.getOrDefault(emptyList())
    }
}
