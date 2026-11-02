package io.legado.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReadAloudServiceSettingsBoundaryTest {

    @Test
    fun `read aloud services use gateway instead of deprecated read config`() {
        val base = mainSourceFile("io/legado/app/service/BaseReadAloudService.kt").readText()
        val tts = mainSourceFile("io/legado/app/service/TTSReadAloudService.kt").readText()
        assertFalse("BaseReadAloudService 不应再直读 ReadConfig", base.contains("ReadConfig."))
        assertFalse("TTSReadAloudService 不应再直读 ReadConfig", tts.contains("ReadConfig."))
        assertTrue(base.contains("readAloudSettingsGateway"))
        assertTrue(base.contains("aloudSettings"))
    }

    @Test
    fun `read aloud cast helpers use gateway instead of deprecated read config`() {
        val castFiles = listOf(
            "io/legado/app/help/readaloud/cast/CastRenderOptions.kt",
            "io/legado/app/help/readaloud/cast/CastSpeechOverlay.kt",
            "io/legado/app/help/readaloud/cast/CastVoicePicker.kt",
            "io/legado/app/help/readaloud/cast/VoiceAudition.kt",
        )
        castFiles.forEach { path ->
            val source = mainSourceFile(path).readText()
            assertFalse("$path 不应再直读 ReadConfig", source.contains("ReadConfig."))
            assertFalse("$path 不应再导入废弃 ReadConfig 门面", source.contains("ui.config.readConfig.ReadConfig"))
        }
    }

    private companion object {
        fun mainSourceFile(relativePath: String): File {
            var directory: File? = File("").absoluteFile
            while (directory != null) {
                for (prefix in listOf("src/main/java", "app/src/main/java")) {
                    val candidate = File(directory, "$prefix/$relativePath")
                    if (candidate.isFile) return candidate
                }
                directory = directory.parentFile
            }
            error("从 ${File("").absolutePath} 向上找不到 $relativePath")
        }
    }
}
