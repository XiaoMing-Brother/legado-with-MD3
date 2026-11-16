package io.legado.app.help.palette

import android.graphics.Bitmap
import androidx.palette.graphics.Palette
import io.legado.app.domain.model.settings.ReadBackgroundPalette
import io.legado.app.domain.model.settings.ReadPaletteSwatch
import io.legado.app.domain.model.settings.buildReadBackgroundPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 从背景图里提取一套配色（清墨：「主题编辑器会根据图片提取出一套配色便于快捷搭配」）。
 *
 * 用 `androidx.palette`（依赖早已在 `app/build.gradle.kts` 里声明，此前无人使用）而不是自己
 * 量化：Palette 的得分函数已经处理了「接近白色的背景不该当强调色」这类经验问题，结果比
 * 取平均色更耐看。取色结果只喂给 [buildReadBackgroundPalette] 做角色分配，纯函数部分留在
 * domain 层，便于单测。
 */
object ReaderBackgroundPaletteExtractor {

    /** 取色用的缩略图边长：Palette 内部还会再降采样，这里先压一次省内存。 */
    private const val SAMPLE_SIZE_PX = 128

    suspend fun extract(bitmap: Bitmap): ReadBackgroundPalette? = withContext(Dispatchers.Default) {
        val palette = Palette.from(bitmap).clearFilters().generate()
        val swatches = palette.swatches.map { swatch ->
            ReadPaletteSwatch(argb = swatch.rgb, population = swatch.population)
        }
        // 背景图常有的纯黑/纯白大色块 Palette 会当背景色单独给出，不并入 swatches。
        val extra = listOfNotNull(
            palette.dominantSwatch?.let { ReadPaletteSwatch(it.rgb, it.population) },
            palette.darkMutedSwatch?.let { ReadPaletteSwatch(it.rgb, it.population) },
            palette.lightMutedSwatch?.let { ReadPaletteSwatch(it.rgb, it.population) },
        )
        buildReadBackgroundPalette(swatches + extra)
    }

    /** 取色前把 Drawable 压到 [SAMPLE_SIZE_PX]，避免整张大图进 Palette。 */
    fun sampleSizePx(): Int = SAMPLE_SIZE_PX
}
