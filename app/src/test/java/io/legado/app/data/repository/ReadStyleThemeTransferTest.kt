package io.legado.app.data.repository

import android.app.Application
import io.legado.app.data.entities.HighlightRule
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.gateway.AppShellSettingsGateway
import io.legado.app.domain.gateway.ThemeSettingsGateway
import io.legado.app.domain.model.settings.ReadBackgroundPalette
import io.legado.app.domain.model.settings.ReadSettings
import io.legado.app.domain.model.settings.contrastRatio
import io.legado.app.domain.model.settings.parseArgbColor
import io.legado.app.domain.model.settings.relativeLuminance
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
    fun applyingALightBuiltInThemeStillTurnsThePageDarkAtNight() {
        val theme = DefaultData.readThemes.first { it.id == 312 } // 漫绿：浅纸色
        assertTrue(gateway.applyReadTheme(theme.id))

        val config = store.configAt(5)
        assertEquals("白天仍是主题原色", theme.background, config.bgStr)
        assertNotEquals("夜间必须换一套底色", config.bgStr, config.bgStrNight)
        val nightBackground = parseArgbColor(config.bgStrNight)!!
        assertTrue(
            "浅色主题的夜间底色必须变深：${config.bgStrNight}",
            relativeLuminance(nightBackground) < 0.1,
        )
        assertTrue(
            "夜间正文对比度不足",
            contrastRatio(parseArgbColor(config.getTextColorNight())!!, nightBackground) >= 4.5,
        )
        assertTrue(
            "夜间页眉页脚对比度不足",
            contrastRatio(config.tipHeaderColorNight, nightBackground) >= 4.5,
        )
        assertFalse("夜间深底要用浅色状态栏图标", config.getDarkStatusIconNight())
    }

    @Test
    fun applyingADarkBuiltInThemeReusesItsPaletteAtNight() {
        val theme = DefaultData.readThemes.first { it.id == 513 } // 大海：本来就是深色
        assertTrue(gateway.applyReadTheme(theme.id))

        val config = store.configAt(5)
        assertEquals(theme.background, config.bgStrNight)
        assertEquals(theme.content, config.getTextColorNight())
    }

    @Test
    fun lightThemeBackgroundImageDoesNotLeakIntoNight() {
        val theme = DefaultData.readThemes.first { it.id == 312 }
        gateway.applyReadTheme(theme.id)
        val derivedNightColor = store.configAt(5).bgStrNight

        gateway.setReadThemeBackgroundImage("/tmp/theme.png", useForNight = false)
        val lightThemeConfig = store.configAt(5)
        assertEquals(2, lightThemeConfig.bgType)
        assertEquals("/tmp/theme.png", lightThemeConfig.bgStr)
        assertEquals("夜间仍用派生深色底", 0, lightThemeConfig.bgTypeNight)
        assertEquals(derivedNightColor, lightThemeConfig.bgStrNight)

        gateway.setReadThemeBackgroundImage("/tmp/night.png", useForNight = true)
        val darkThemeConfig = store.configAt(5)
        assertEquals(2, darkThemeConfig.bgTypeNight)
        assertEquals("/tmp/night.png", darkThemeConfig.bgStrNight)
    }

    @Test
    fun extractedPaletteTextIsRederivedForTheSolidNightBackground() {
        val theme = DefaultData.readThemes.first { it.id == 312 }
        gateway.applyReadTheme(theme.id)
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
