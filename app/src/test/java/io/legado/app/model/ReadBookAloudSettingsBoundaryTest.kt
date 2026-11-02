package io.legado.app.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReadBookAloudSettingsBoundaryTest {

    @Test
    fun `read book chapter layout uses read aloud gateway not read config`() {
        val source = mainSourceFile("io/legado/app/model/ReadBook.kt").readText()
        assertFalse("排版注入角色/配乐标签不应再经废弃 ReadConfig", source.contains("ReadConfig."))
        assertTrue(source.contains("readAloudSettingsGateway.currentSettings"))
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
