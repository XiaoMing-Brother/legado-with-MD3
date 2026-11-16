package io.legado.app.ui.config.readConfig

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ApplyReadSettingUseCaseTest {

    @Test
    fun `layout settings post typed reader config updates instead of direct loadContent`() {
        val source = mainSourceFile("io/legado/app/ui/config/readConfig/ApplyReadSettingUseCase.kt").readText()
        val reloadLayout = section(source, "private fun reloadLayout()")
        assertTrue(reloadLayout.contains("ReadConfigUpdateBus.post("))
        assertTrue(reloadLayout.contains("ConfigUpdateAction.UpdateStyle"))
        assertTrue(reloadLayout.contains("ConfigUpdateAction.UpdateLayout"))
        assertTrue(reloadLayout.contains("ConfigUpdateAction.ReloadContent"))
        assertFalse("底部对齐等排版项不能再只调 ReadBook.loadContent", reloadLayout.contains("ReadBook.loadContent"))
    }

    @Test
    fun `optimize render posts style reload and keeps page anim refresh`() {
        val source = mainSourceFile("io/legado/app/ui/config/readConfig/ApplyReadSettingUseCase.kt").readText()
        val reloadStyle = section(source, "private fun reloadStyle()")
        assertTrue(reloadStyle.contains("ConfigUpdateAction.UpdateStyle"))
        assertTrue(reloadStyle.contains("ConfigUpdateAction.ReloadContent"))
        assertTrue(reloadStyle.contains("ReadBook.renderCallBack?.upPageAnim(true)"))
    }

    @Test
    fun `underline change rebuilds style before invalidating pages`() {
        val source = mainSourceFile("io/legado/app/ui/config/readConfig/ApplyReadSettingUseCase.kt").readText()
        val branch = section(source, "is ReadConfigIntent.UseUnderlineChanged")
        assertTrue(branch.contains("ConfigUpdateAction.UpdateStyle"))
        assertTrue(branch.contains("ConfigUpdateAction.InvalidateTextPage"))
        assertTrue(branch.contains("ConfigUpdateAction.UpdateContent"))
    }

    private companion object {
        fun section(source: String, startMarker: String, endMarker: String = "\n    }"): String {
            val start = source.indexOf(startMarker)
            assertTrue("$startMarker not found", start >= 0)
            val rest = source.substring(start)
            val end = rest.indexOf(endMarker, startMarker.length)
            return if (end < 0) rest else rest.substring(0, end)
        }

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
