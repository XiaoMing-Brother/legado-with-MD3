package io.legado.app.di

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Koin 缺绑定只在运行时炸（编译和普通单测都不会发现）。
 *
 * 这里做一次静态对账：界面里 `koinViewModel<T>` / `koinInject<T>` / `get<T>()`
 * 用到的类型，必须在 `di/` 的声明里出现过。历史上删代码时整块误删绑定，
 * 直到打开对应页面才崩，这类回归由本测试兜住。
 */
class KoinBindingSmokeTest {

    @Test
    fun `koinViewModel 使用点都有对应声明`() {
        val missing = missingBindings(viewModelOnly = true)
        assertTrue(
            "以下 ViewModel 被 koinViewModel 使用但 di/ 里没有声明：$missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun `koinInject 与 get 的类型都有对应声明`() {
        val missing = missingBindings(viewModelOnly = false)
        assertTrue(
            "以下类型被 koinInject/get 使用但 di/ 里没有声明：$missing",
            missing.isEmpty(),
        )
    }

    private fun missingBindings(viewModelOnly: Boolean): List<String> {
        val root = projectRoot()
        val diSource = File(root, "app/src/main/java/io/legado/app/di")
            .listFiles { file -> file.extension == "kt" }
            ?.joinToString("\n") { it.readText() }
            .orEmpty()
        val bound = buildSet {
            addAll(Regex("""viewModelOf\(::(\w+)\)""").findAll(diSource).map { it.groupValues[1] })
            addAll(Regex("""viewModel<(\w+)>""").findAll(diSource).map { it.groupValues[1] })
            addAll(
                Regex("""viewModel\s*\{[^\n]*\n\s*(\w+ViewModel)\(""")
                    .findAll(diSource).map { it.groupValues[1] }
            )
            if (!viewModelOnly) {
                addAll(Regex("""(?:single|factory)<(\w+)>""").findAll(diSource).map { it.groupValues[1] })
                addAll(Regex("""(?:singleOf|factoryOf)\(::(\w+)\)""").findAll(diSource).map { it.groupValues[1] })
                addAll(
                    Regex("""(?:single|factory)\s*\{[^\n]*\n\s*(\w+)\(""")
                        .findAll(diSource).map { it.groupValues[1] }
                )
            }
        }
        val used = buildSet {
            File(root, "app/src/main/java").walkTopDown()
                .filter { it.extension == "kt" }
                .forEach { file ->
                    val text = file.readText()
                    if (viewModelOnly) {
                        addAll(Regex("""koinViewModel<(\w+)>""").findAll(text).map { it.groupValues[1] })
                        // 省略泛型、靠参数类型推断的写法：`viewModel: FooViewModel = koinViewModel()`。
                        addAll(
                            Regex("""(\w+ViewModel)\s*=\s*koinViewModel\(\)""")
                                .findAll(text).map { it.groupValues[1] }
                        )
                    } else {
                        addAll(Regex("""koinInject<(\w+)>""").findAll(text).map { it.groupValues[1] })
                        addAll(Regex("""\.get<(\w+)>\(\)""").findAll(text).map { it.groupValues[1] })
                    }
                }
        }
        // Context 由 Koin 的 androidContext 提供，不属于模块声明。
        return (used - bound - setOf("Context")).sorted()
    }

    private fun projectRoot(): File {
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            if (File(directory, ".git").exists()) return directory
            directory = directory.parentFile
        }
        error("找不到仓库根目录")
    }
}
