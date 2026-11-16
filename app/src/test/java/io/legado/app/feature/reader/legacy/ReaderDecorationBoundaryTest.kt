package io.legado.app.feature.reader.legacy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReaderDecorationBoundaryTest {

    @Test
    fun `page decoration factory no longer reads ReadBook singleton`() {
        val source = mainSourceFile(
            "io/legado/app/feature/reader/legacy/LegacyReaderPageDecorationFactory.kt",
        ).readText()
        assertFalse(
            "页眉页脚工厂应通过 ReaderPageDecorationContext 注入会话，而不是直读 ReadBook",
            source.contains("ReadBook."),
        )
        assertTrue(source.contains("session: ReaderPageDecorationContext"))
    }

    @Test
    fun `pagination style snapshot carries e ink mode`() {
        val factory = mainSourceFile(
            "io/legado/app/feature/reader/legacy/LegacyReaderPaginationStyleFactory.kt",
        ).readText()
        val style = mainSourceFile(
            "io/legado/app/feature/reader/platform/ReaderAndroidPaginationStyle.kt",
        ).readText()
        assertTrue(factory.contains("isEInkMode = isEInkMode"))
        assertTrue(style.contains("val isEInkMode: Boolean"))
    }

    @Test
    fun `controller injects decoration session at publish sites`() {
        val source = mainSourceFile("io/legado/app/ui/book/read/ReadBookController.kt").readText()
        assertTrue(source.contains("readerPageDecorationContext("))
        assertTrue(source.contains("session = decorationSession"))
    }

    @Test
    fun `route prefers pagination snapshot for e ink mode`() {
        val source = mainSourceFile("io/legado/app/ui/book/read/ReadBookRouteScreen.kt").readText()
        assertTrue(
            source.contains("ReadBook.paginationEnvironment?.style?.isEInkMode"),
        )
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
