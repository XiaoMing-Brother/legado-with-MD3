package io.legado.app.ui.widget.components.image.cover

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 共享元素 overlay 的裁剪几何：只验证「收起时被顶栏挡住的部分不画」与
 * 「其他情况原样返回」这两条，坐标口径（overlay = 根坐标）错了这里会直接暴露。
 */
@RunWith(RobolectricTestRunner::class)
// 与其他 Robolectric 测试同样钉住 SDK：默认（target 37）的 android-all 在 JDK 21 上起不来。
@Config(application = android.app.Application::class, sdk = [35])
class ShelfViewportOverlayClipTest {

    private val shape = RoundedCornerShape(4.dp)
    private val density = Density(1f)
    private val bounds = Rect(left = 100f, top = 200f, right = 400f, bottom = 800f)
    private val occludedBottomPx = 320f

    private fun clip(clipsOnReturn: Boolean, occludedBottom: Float = occludedBottomPx): Path =
        shelfViewportClipPath(
            shape = shape,
            bounds = bounds,
            layoutDirection = LayoutDirection.Ltr,
            density = density,
            occludedBottomPx = occludedBottom,
            clipsOnReturn = clipsOnReturn,
        )

    @Test
    fun `returning clip stops at the app bar bottom`() {
        val clipped = clip(clipsOnReturn = true)
        val clippedBounds = clipped.getBounds()
        assertEquals(occludedBottomPx, clippedBounds.top, 0.5f)
        assertEquals(bounds.bottom, clippedBounds.bottom, 0.5f)
        assertEquals(bounds.left, clippedBounds.left, 0.5f)
        assertEquals(bounds.right, clippedBounds.right, 0.5f)
    }

    @Test
    fun `opening does not clip`() {
        val unclipped = clip(clipsOnReturn = false)
        assertEquals(bounds.top, unclipped.getBounds().top, 0.5f)
        assertEquals(bounds.bottom, unclipped.getBounds().bottom, 0.5f)
    }

    @Test
    fun `unknown viewport does not clip`() {
        val unclipped = clip(clipsOnReturn = true, occludedBottom = 0f)
        assertEquals(bounds.top, unclipped.getBounds().top, 0.5f)
    }

    @Test
    fun `element fully below the app bar is untouched`() {
        val belowBar = Rect(left = 100f, top = 400f, right = 400f, bottom = 800f)
        val unclipped = shelfViewportClipPath(
            shape = shape,
            bounds = belowBar,
            layoutDirection = LayoutDirection.Ltr,
            density = density,
            occludedBottomPx = occludedBottomPx,
            clipsOnReturn = true,
        )
        assertEquals(belowBar.top, unclipped.getBounds().top, 0.5f)
    }
}
