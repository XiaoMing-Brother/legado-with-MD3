package io.legado.app.feature.splash

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import java.io.File

/**
 * 自定义开屏字体的私有存储与校验。
 *
 * 设置只保存相对路径；字体复制到 filesDir 后再使用，避免依赖临时读取权限。
 */
internal object CalligraphySplashFontStore {
    const val RelativePath = "splash-fonts/splash-font.ttf"

    private const val DirectoryName = "splash-fonts"
    private const val FileName = "splash-font.ttf"
    private const val TempFileName = "splash-font.tmp"
    private const val MaxFontBytes = 40L * 1024 * 1024

    fun resolve(context: Context, relativePath: String): File? {
        if (relativePath != RelativePath) return null
        return File(context.filesDir, RelativePath).takeIf { it.isFile }
    }

    fun import(context: Context, uri: Uri): Boolean {
        val directory = File(context.filesDir, DirectoryName)
        if (!directory.isDirectory && !directory.mkdirs()) return false

        val temp = File(directory, TempFileName)
        temp.delete()
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)
        if (!copied || temp.length() <= 0L || temp.length() > MaxFontBytes) {
            temp.delete()
            return false
        }

        // 提前解析一次，损坏或伪装成字体的文件不会替换掉当前可用字体。
        val valid = runCatching {
            Typeface.createFromFile(temp)
            true
        }.getOrDefault(false)
        if (!valid) {
            temp.delete()
            return false
        }

        val target = File(directory, FileName)
        target.delete()
        val saved = temp.renameTo(target)
        if (!saved) temp.delete()
        return saved
    }

    fun clear(context: Context) {
        File(context.filesDir, DirectoryName).deleteRecursively()
    }
}
