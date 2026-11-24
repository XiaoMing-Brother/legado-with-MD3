package io.legado.app.domain.model.settings

data class OtherSettings(
    val updateToVariant: String = "official_version",
    // 更新清单与安装包来源，取值见 AppUpdateEndpoint：auto / github / mirror
    val updateEndpoint: String = "auto",
    val autoCheckUpdateOnStart: Boolean = true,
    val webServiceAutoStart: Boolean = false,
    val autoRefresh: Boolean = false,
    val defaultToRead: Boolean = false,
    val notificationsPost: Boolean = true,
    val ignoreBatteryPermission: Boolean = true,
    val firebaseEnable: Boolean = true,
    val defaultBookTreeUri: String? = null,
    val antiAlias: Boolean = false,
    val replaceEnableDefault: Boolean = true,
    val autoClearExpired: Boolean = true,
    val showAddToShelfAlert: Boolean = true,
    val showMangaUi: Boolean = true,
    val webServiceWakeLock: Boolean = false,
    val sourceEditMaxLine: Int = Int.MAX_VALUE,
    val webPort: Int = 1122,
    val processText: Boolean = true,
    val recordLog: Boolean = false,
    val recordHeapDump: Boolean = false,
    val audioPlayUseWakeLock: Boolean = false,
    val importKeepName: Boolean = false,
    val importKeepGroup: Boolean = false,
    val importKeepEnable: Boolean = false,
    val fontSort: Int = 0,
    val blockSourceBrowser: Boolean = false,
    val calligraphySplashEnabled: Boolean = true,
    val calligraphySplashOncePerDay: Boolean = false,
    val calligraphySplashLastShownDay: Int = 0,
    /** 开屏竖排文字，空串表示使用内置默认诗句；用 \n 分列 */
    val calligraphySplashText: String = "",
    /** 每次冷启动从句子库随机取一句；关闭时使用 [calligraphySplashText] */
    val calligraphySplashRandomEnabled: Boolean = false,
    /** 自定义随机句子库，用 | 分隔；空串表示使用内置句子库 */
    val calligraphySplashTextPool: String = "",
    /** 自定义开屏字体在应用私有目录内的相对路径；空串表示使用内置书法字体 */
    val calligraphySplashCustomFontPath: String = "",
)
