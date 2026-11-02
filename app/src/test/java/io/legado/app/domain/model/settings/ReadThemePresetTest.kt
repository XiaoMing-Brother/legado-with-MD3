package io.legado.app.domain.model.settings

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 内置阅读主题数据的契约。
 *
 * `assets/defaultData/readThemes.json` 是从清墨 LightInk 主题商店接口抓下来的快照，
 * 属于**外部数据**：字段缺失、颜色格式变化、重复 id 都不会让编译失败，只会在运行期变成
 * 「选了主题没反应」或「列表里点谁都选中同一个」。所以这里把数据形状钉住。
 *
 * 用裸 `Gson()` 而不是项目的 `GSON`：后者带 appCtx 相关的注册，JVM 单测里起不来
 * （同 `ReadConfigJsonRoundTripTest`）。
 */
class ReadThemePresetTest {

    private val gson = Gson()

    @Test
    fun `主题数据可解析且条数与 id 都符合预期`() {
        val themes = loadThemes()

        assertEquals("主题条数变化请同步 DefaultData.readThemes 的注释", 1027, themes.size)
        assertEquals("同名主题靠 id 区分，id 必须唯一", themes.size, themes.map { it.id }.toSet().size)
        assertTrue("id 是所有引用与持久化的键，不允许为 0", themes.none { it.id == 0 })
        assertTrue(
            "列表默认按下载量降序，重排会改变用户看到的第一屏",
            themes.zipWithNext().all { (a, b) -> a.downloadCount >= b.downloadCount },
        )
        // author 参与列表过滤；Gson 对缺字段的引用类型给 null，这里顺手钉住字段真的被灌进来了。
        assertTrue(
            "author 只可能是这三种，出现别的值说明字段没解析进来",
            themes.all { it.author in setOf("清墨", "", "厚墨") },
        )
    }

    @Test
    fun `每条主题都有完整配色`() {
        val colorFields = listOf(
            "background" to ReadThemePreset::background,
            "content" to ReadThemePreset::content,
            "secondary" to ReadThemePreset::secondary,
            "control" to ReadThemePreset::control,
        )
        val malformed = loadThemes().flatMap { theme ->
            colorFields.mapNotNull { (name, getter) ->
                val value = getter(theme)
                (name to value).takeIf { parseArgbColor(value) == null }
            } + listOfNotNull(
                // 名称是列表主键的展示面，空名字在列表里就是一张认不出的卡片。
                ("name" to theme.name).takeIf { theme.name.isBlank() }
            )
        }

        assertTrue("颜色字段必须都是 #AARRGGBB，名称不能为空：$malformed", malformed.isEmpty())
    }

    @Test
    fun `背景图地址按 host 决定是否升级为 https`() {
        assertEquals(
            listOf("https://minio.zhendong.ltd/a.png", "http://minio.zhendong.ltd/a.png"),
            themeMipmapUrlCandidates("http://minio.zhendong.ltd/a.png"),
        )
        assertEquals(
            "已经是 https 的地址不该再拼一层",
            listOf("https://minio.zhendong.ltd/a.png"),
            themeMipmapUrlCandidates("https://minio.zhendong.ltd/a.png"),
        )
        assertEquals(
            "别的图床不猜协议，只试原地址",
            listOf("https://cdn-us.imgs.moe/a.jpg"),
            themeMipmapUrlCandidates("https://cdn-us.imgs.moe/a.jpg"),
        )
    }

    @Test
    fun `负边距会被钳到 0`() {
        val theme = theme(horizontal = -27, top = 80, bottom = -3)
        val style = theme.toStyle()

        assertEquals(0, style.paddingHorizontal)
        assertEquals(80, style.paddingTop)
        assertEquals(0, style.paddingBottom)
    }

    @Test
    fun `深色底推断为深色主题并取浅色状态栏图标`() {
        // 数据里的 dark 字段全是 false，深浅只能按底色亮度推断。
        assertTrue(theme(background = "#ff000000").isDark)
        assertTrue(theme(background = "#ff111826").isDark)
        assertFalse(theme(background = "#fff1f3f4").isDark)
        assertFalse(theme(background = "#ffffffff").isDark)

        assertFalse(
            "深色底要配浅色状态栏图标（darkStatusIcon = false）",
            theme(background = "#ff000000").toStyle().darkStatusIcon,
        )
        assertTrue(theme(background = "#ffffffff").toStyle().darkStatusIcon)
    }

    @Test
    fun `解析不出颜色时按浅色处理而不是崩掉`() {
        assertFalse(isDarkBackgroundColor(""))
        assertFalse(isDarkBackgroundColor("不是颜色"))
        assertFalse(isDarkBackgroundColor("#0000000000"))
    }

    @Test
    fun `Gson 反射写入后派生属性仍然有效`() {
        // isDark 必须是 get() 而不是构造期字段：Gson 走反射写字段、不执行构造体，
        // 构造期算出来的值会停在默认值（同 ReadConfigJsonRoundTripTest 钉住的约束）。
        val restored = gson.fromJson<ReadThemePreset>(
            """{"id":7,"name":"夜","background":"#ff000000","content":"#ffffffff",
                "secondary":"#80ffffff","control":"#ff3473e7","mipmap":"http://minio.zhendong.ltd/a.png"}""",
            ReadThemePreset::class.java,
        )

        assertEquals(7, restored.id)
        assertTrue("深色底经 Gson 反序列化后仍要判为深色", restored.isDark)
        assertEquals("#ff000000", restored.toStyle().background)
        assertEquals(
            "缩略图取 https 候选",
            "https://minio.zhendong.ltd/a.png",
            restored.thumbnailUrl,
        )
    }

    @Test
    fun `缺 mipmap 的主题只有配色`() {
        val restored = gson.fromJson<ReadThemePreset>(
            """{"id":8,"name":"纯色","background":"#fff1f3f4","content":"#ff2d2d33",
                "secondary":"#ffababad","control":"#ff73966a"}""",
            ReadThemePreset::class.java,
        )

        assertEquals(null, restored.thumbnailUrl)
        // Gson 用 UnsafeAllocator 分配对象、不执行构造体，缺字段的引用类型字段留下 null
        // 而不是声明的默认值，所以消费方必须用 orEmpty() 兜底。
        assertEquals(null, restored.author)
        assertEquals("", restored.author.orEmpty())
    }

    private fun theme(
        background: String = "#fff1f3f4",
        horizontal: Int = 0,
        top: Int = 0,
        bottom: Int = 0,
    ) = ReadThemePreset(
        id = 1,
        name = "t",
        background = background,
        content = "#ff000000",
        secondary = "#80000000",
        control = "#ff3473e7",
        horizontal = horizontal,
        top = top,
        bottom = bottom,
    )

    private fun loadThemes(): List<ReadThemePreset> =
        gson.fromJson(themeJsonFile().readText(), Array<ReadThemePreset>::class.java).toList()

    private companion object {
        fun themeJsonFile(): File {
            var directory: File? = File("").absoluteFile
            while (directory != null) {
                for (prefix in listOf("src/main", "app/src/main")) {
                    val candidate = File(directory, "$prefix/assets/defaultData/readThemes.json")
                    if (candidate.isFile) return candidate
                }
                directory = directory.parentFile
            }
            error("从 ${File("").absolutePath} 向上找不到 readThemes.json")
        }
    }
}
