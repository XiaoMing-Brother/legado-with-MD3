package io.legado.app.data.repository

import android.app.Application
import io.legado.app.data.entities.HighlightRule
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.gateway.AppShellSettingsGateway
import io.legado.app.domain.gateway.ThemeSettingsGateway
import io.legado.app.domain.model.settings.ReadBackgroundPalette
import io.legado.app.domain.model.settings.ReadSettings
import io.legado.app.domain.model.settings.ReadThemeTarget
import io.legado.app.domain.model.settings.contrastRatio
import io.legado.app.domain.model.settings.parseArgbColor
import io.legado.app.help.DefaultData
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.support.InMemoryAppDatabaseFixture
import io.legado.app.ui.book.read.sheet.buildThemeKeys
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ReadStyleThemeTransferTest {
    private lateinit var database: InMemoryAppDatabaseFixture
    private lateinit var rules: HighlightRuleRepository
    private lateinit var store: ReadStyleConfigStore
    private lateinit var gateway: ReadBookStyleConfigRepository

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        AppConfigStore.init(context)
        stopKoin()
        startKoin {
            modules(module {
                single<AppShellSettingsGateway> { AppShellSettingsRepository() }
                single<ThemeSettingsGateway> { ThemeSettingsRepository() }
            })
        }
        database = InMemoryAppDatabaseFixture(context)
        rules = HighlightRuleRepository(database.database.highlightRuleDao, context)
        val repository = ReadStyleRepository(rules)
        File(repository.configFilePath).writeText(GSON.toJson(
            (0..4).map { ReadBookConfig.Config(name = "preset-$it") } +
                listOf(ReadBookConfig.Config(name = "A"), ReadBookConfig.Config(name = ""))
        ))
        File(repository.shareConfigFilePath).writeText(GSON.toJson(ReadBookConfig.Config()))
        store = ReadStyleConfigStore(repository)
        val settings = object : ReadSettingsGateway {
            override val settings = MutableStateFlow(ReadSettings(readStyleSelect = 5))
            override val currentSettings get() = settings.value
            override suspend fun update(transform: (ReadSettings) -> ReadSettings) {
                settings.value = transform(settings.value)
            }
        }
        ReadBookConfig.initialize(store, settings)
        gateway = ReadBookStyleConfigRepository(repository, rules, store)
    }

    @After
    fun tearDown() {
        database.close()
        stopKoin()
    }

    @Test
    fun importingSameNameAsNewKeepsTheExistingThemesRules() {
        val old = rule("old", "A", "old pattern")
        rules.save("A", listOf(old))
        val index = gateway.importStylesAsNew(pack("A", "new pattern"))!!

        assertEquals(listOf(old), rules.load("A"))
        assertEquals("A (2)", store.configAt(index).name)
        assertEquals("new pattern", rules.load("A (2)").single().pattern)
        assertEquals("A", store.configAt(5).name)
    }

    @Test
    fun importingAnUnnamedThemeDoesNotReplaceGlobalRules() {
        val global = rule("global", null, "global pattern")
        rules.save("", listOf(global))
        val index = gateway.importStylesAsNew(pack("", "imported pattern"))!!
        val importedName = store.configAt(index).name

        assertTrue(importedName.isNotBlank())
        assertEquals(listOf(global), rules.load(""))
        assertEquals(
            setOf("global pattern", "imported pattern"),
            rules.load(importedName).map { it.pattern }.toSet(),
        )
    }

    @Test
    fun aBatchReservesDistinctNamesBeforeSavingEachRuleSet() {
        val old = rule("old", "A", "old pattern")
        rules.save("A", listOf(old))
        val bundle = zip(mapOf("first.zip" to pack("A", "first"), "second.zip" to pack("A", "second")))

        assertNull(gateway.importStylesAsNew(bundle))

        assertEquals(listOf(old), rules.load("A"))
        assertEquals(listOf("A (2)", "A (3)"), store.configsSnapshot().takeLast(2).map { it.name })
        assertEquals("first", rules.load("A (2)").single().pattern)
        assertEquals("second", rules.load("A (3)").single().pattern)
    }

    @Test
    fun everyExportPathLoadsTheLatestRulesAndTheirAssets() {
        val context = RuntimeEnvironment.getApplication()
        val background = File(context.filesDir, "bg_images/latest.png").apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val latest = rule("latest", "A", "edited pattern").copy(bgImage = background.absolutePath)
        rules.save("A", listOf(latest))
        assertTrue(store.configAt(5).highlightRules.isEmpty())

        val single = gateway.exportStyleAt(5)!!
        val batch = unzip(gateway.exportStyles(listOf(5, 6))).getValue("A.zip")
        for (bytes in listOf(single, batch)) {
            val files = unzip(bytes)
            val config = GSON.fromJsonObject<ReadBookConfig.Config>(
                files.getValue(ReadBookConfig.configFileName).decodeToString()
            ).getOrThrow()
            assertEquals("edited pattern", config.highlightRules.single().pattern)
            assertArrayEquals(
                background.readBytes(), files.getValue(requireNotNull(config.highlightRules.single().bgImage)),
            )
        }
    }

    @Test
    fun duplicateNamesAndTheAddButtonNameHaveDistinctKeysThatFollowReordering() {
        listOf("A", "A#1", "add-style").forEach { store.addConfig(ReadBookConfig.Config(name = it)) }
        gateway.save()
        val before = gateway.currentState.items
        val keys = buildThemeKeys(before)
        assertEquals(keys.size + 1, (keys + "add-style").toSet().size)
        val movedId = before[5].id

        gateway.moveStyle(5, 7)

        val after = gateway.currentState.items
        assertEquals(movedId, after[7].id)
        assertEquals(keys[5], buildThemeKeys(after)[7])
        assertEquals(keys[7], buildThemeKeys(after)[6])
    }

    @Test
    fun editingAndReplacingPreserveIdentityButAddingACopyCreatesANewIdentity() {
        val original = store.configAt(5)
        store.updateStyleAt(5) { it.copy(name = "renamed") }
        assertEquals(original.id, store.configAt(5).id)
        store.replaceConfigAt(5, ReadBookConfig.Config(name = "replacement"), alsoShare = false)
        assertEquals(original.id, store.configAt(5).id)
        val addedIndex = store.addConfig(original)
        assertNotEquals(original.id, store.configAt(addedIndex).id)
        assertFalse(GSON.toJson(store.configAt(addedIndex)).contains("\"id\""))
    }

    @Test
    fun applyingADayThemeLeavesTheNightBackgroundUntouched() {
        val theme = DefaultData.readThemes.first { it.id == 312 } // 漫绿：浅纸色
        val before = store.configAt(5)
        assertTrue(gateway.applyReadTheme(theme.id, ReadThemeTarget.Day))

        val config = store.configAt(5)
        assertEquals("日间用主题原色", theme.background, config.bgStr)
        assertEquals("记录日间应用的主题 id", theme.id, config.readThemeIdDay)
        assertNull("夜间主题 id 不受影响", config.readThemeIdNight)
        assertEquals("夜间背景保持用户原来的设置", before.bgStrNight, config.bgStrNight)
        assertEquals(before.bgTypeNight, config.bgTypeNight)
        assertEquals(before.getDarkStatusIconNight(), config.getDarkStatusIconNight())
        assertTrue("浅色日间底要用深色状态栏图标", config.getDarkStatusIcon())
    }

    @Test
    fun applyingANightThemeOnlyChangesTheNightBackground() {
        val theme = DefaultData.readThemes.first { it.id == 513 } // 大海：深色
        val before = store.configAt(5)
        assertTrue(gateway.applyReadTheme(theme.id, ReadThemeTarget.Night))

        val config = store.configAt(5)
        assertEquals("夜间直接用主题底色", theme.background, config.bgStrNight)
        assertEquals("记录夜间应用的主题 id", theme.id, config.readThemeIdNight)
        assertNull("日间主题 id 不受影响", config.readThemeIdDay)
        assertEquals("样式名保持用户设置", before.name, config.name)
        assertEquals("日间背景保持用户原来的设置", before.bgStr, config.bgStr)
        assertEquals(before.bgType, config.bgType)
        assertEquals(before.getDarkStatusIcon(), config.getDarkStatusIcon())
        assertFalse("深色夜间底要用浅色状态栏图标", config.getDarkStatusIconNight())
    }

    @Test
    fun appliedThemeIdsFollowTheModeTheyWereAppliedTo() {
        val dayTheme = DefaultData.readThemes.first { it.id == 312 }
        val nightTheme = DefaultData.readThemes.first { it.id == 513 }

        assertTrue(gateway.applyReadTheme(dayTheme.id, ReadThemeTarget.Day))
        val afterDay = store.configAt(5)
        assertEquals(dayTheme.id, afterDay.readThemeIdDay)
        assertNull(afterDay.readThemeIdNight)

        assertTrue(gateway.applyReadTheme(nightTheme.id, ReadThemeTarget.Night))
        val afterNight = store.configAt(5)
        assertEquals(dayTheme.id, afterNight.readThemeIdDay)
        assertEquals(nightTheme.id, afterNight.readThemeIdNight)
    }

    @Test
    fun manuallyChangingOneBackgroundClearsOnlyThatModesThemeId() {
        val dayTheme = DefaultData.readThemes.first { it.id == 312 }
        val nightTheme = DefaultData.readThemes.first { it.id == 513 }
        gateway.applyReadTheme(dayTheme.id, ReadThemeTarget.Day)
        gateway.applyReadTheme(nightTheme.id, ReadThemeTarget.Night)

        gateway.setCurrentBackgroundImageForMode("/tmp/manual-night.png", isNight = true)
        val afterNight = store.configAt(5)
        assertEquals("手动换夜间背景不影响日间主题标记", dayTheme.id, afterNight.readThemeIdDay)
        assertNull("手动换夜间背景后不再算作夜间主题", afterNight.readThemeIdNight)

        gateway.setCurrentBackgroundImageForMode("/tmp/manual-day.png", isNight = false)
        val afterDay = store.configAt(5)
        assertNull(afterDay.readThemeIdDay)
        assertNull(afterDay.readThemeIdNight)
    }

    @Test
    fun applyingTheThemesOwnBackgroundImageKeepsTheThemeId() {
        val theme = DefaultData.readThemes.first { it.id == 312 }
        gateway.applyReadTheme(theme.id, ReadThemeTarget.Day)

        gateway.setReadThemeBackgroundImage("/tmp/theme.png", ReadThemeTarget.Day)

        assertEquals(theme.id, store.configAt(5).readThemeIdDay)
    }

    @Test
    fun aNewStyleDoesNotInheritAppliedThemeIds() {
        val dayTheme = DefaultData.readThemes.first { it.id == 312 }
        val nightTheme = DefaultData.readThemes.first { it.id == 513 }
        gateway.applyReadTheme(dayTheme.id, ReadThemeTarget.Day)
        gateway.applyReadTheme(nightTheme.id, ReadThemeTarget.Night)

        val newIndex = gateway.addStyle()

        assertNull(store.configAt(newIndex).readThemeIdDay)
        assertNull(store.configAt(newIndex).readThemeIdNight)
    }

    @Test
    fun applyingABuiltInThemeKeepsTypographyAndMargins() {
        val mine = ReadBookConfig.Config(
            name = "mine",
            textColor = "#ff112233",
            textColorNight = "#ff445566",
            textColorEInk = "#ff778899",
            textAccentColor = "#ff0a0b0c",
            textAccentColorNight = "#ff0d0e0f",
            textAccentColorEInk = "#ff101112",
            titleColor = 0xFF131415.toInt(),
            titleColorNight = 0xFF161718.toInt(),
            tipHeaderColor = 0xFF191A1B.toInt(),
            tipHeaderColorNight = 0xFF1C1D1E.toInt(),
            tipFooterColor = 0xFF1F2021.toInt(),
            tipFooterColorNight = 0xFF222324.toInt(),
            textSize = 33,
            paddingTop = 11,
            paddingBottom = 12,
            paddingLeft = 13,
            paddingRight = 14,
        )
        store.updateStyleAt(5) { mine }

        val theme = DefaultData.readThemes.first { it.id == 312 }
        assertTrue(gateway.applyReadTheme(theme.id, ReadThemeTarget.Day))

        val after = store.configAt(5)
        assertEquals("背景仍然按主题应用", theme.background, after.bgStr)
        assertEquals("样式名保持用户设置", "mine", after.name)
        val beforeJson = GSON.toJsonTree(mine).asJsonObject
        val afterJson = GSON.toJsonTree(after).asJsonObject
        listOf(
            "textColor", "textColorNight", "textColorEInk",
            "textAccentColor", "textAccentColorNight", "textAccentColorEInk",
            "titleColor", "titleColorNight",
            "tipHeaderColor", "tipHeaderColorNight",
            "tipFooterColor", "tipFooterColorNight",
            "textSize", "paddingTop", "paddingBottom", "paddingLeft", "paddingRight",
        ).forEach { key ->
            assertEquals("应用主题不应修改 $key", beforeJson.get(key), afterJson.get(key))
        }
    }

    @Test
    fun themeBackgroundImagesStayInTheirOwnMode() {
        val theme = DefaultData.readThemes.first { it.id == 312 }
        gateway.applyReadTheme(theme.id, ReadThemeTarget.Day)
        val before = store.configAt(5)
        val nightBefore = before.bgStrNight
        val nightTypeBefore = before.bgTypeNight

        gateway.setReadThemeBackgroundImage("/tmp/theme.png", ReadThemeTarget.Day)
        val dayImageConfig = store.configAt(5)
        assertEquals(2, dayImageConfig.bgType)
        assertEquals("/tmp/theme.png", dayImageConfig.bgStr)
        assertEquals("日间主题的图不进入夜间", nightTypeBefore, dayImageConfig.bgTypeNight)
        assertEquals(nightBefore, dayImageConfig.bgStrNight)
        assertEquals("墨水屏跟随日间", 2, dayImageConfig.bgTypeEInk)
        assertEquals("/tmp/theme.png", dayImageConfig.bgStrEInk)

        gateway.setReadThemeBackgroundImage("/tmp/night.png", ReadThemeTarget.Night)
        val nightImageConfig = store.configAt(5)
        assertEquals(2, nightImageConfig.bgTypeNight)
        assertEquals("/tmp/night.png", nightImageConfig.bgStrNight)
        assertEquals("夜间主题的图不覆盖日间", "/tmp/theme.png", nightImageConfig.bgStr)
        assertEquals(2, nightImageConfig.bgType)
    }

    @Test
    fun extractedPaletteTextIsRederivedForTheSolidNightBackground() {
        val theme = DefaultData.readThemes.first { it.id == 312 }
        gateway.applyReadTheme(theme.id, ReadThemeTarget.Day)
        gateway.applyReadTheme(theme.id, ReadThemeTarget.Night)
        val palette = ReadBackgroundPalette(
            backgroundArgb = 0xFFF5F5F0.toInt(),
            contentArgb = 0xFF2D2D33.toInt(),
            secondaryArgb = 0xFF6A6A6A.toInt(),
            controlArgb = 0xFF3473E7.toInt(),
        )

        gateway.applyReadBackgroundPalette(palette)

        val config = store.configAt(5)
        assertEquals("白天仍用图片取色", 0xFF2D2D33.toInt(), parseArgbColor(config.getTextColor()))
        val nightBackground = parseArgbColor(config.bgStrNight)!!
        assertTrue(
            "夜间正文要重新反推成浅色",
            contrastRatio(parseArgbColor(config.getTextColorNight())!!, nightBackground) >= 4.5,
        )
        assertTrue(
            "夜间页眉页脚要重新反推成浅色",
            contrastRatio(config.tipHeaderColorNight, nightBackground) >= 4.5,
        )
        assertFalse("夜间深底要用浅色状态栏图标", config.getDarkStatusIconNight())
    }

    private fun rule(id: String, name: String?, pattern: String) = HighlightRule(
        id = id, pattern = pattern, configName = name?.let { listOf(it).toJsonArray() },
    )

    private fun pack(name: String, pattern: String): ByteArray = zip(mapOf(
        ReadBookConfig.configFileName to GSON.toJson(ReadBookConfig.Config(
            name = name, highlightRules = arrayListOf(rule("packed", name, pattern)),
        )).encodeToByteArray()
    ))

    private fun zip(files: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().use { bytes ->
        ZipOutputStream(bytes).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        bytes.toByteArray()
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> =
        ZipInputStream(bytes.inputStream()).use { zip ->
            buildMap {
                generateSequence { zip.nextEntry }.forEach { entry ->
                    if (!entry.isDirectory) put(entry.name, zip.readBytes())
                }
            }
        }
}
