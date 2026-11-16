package io.legado.app.ui.widget.components.image.cover

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * 书架列表可见区域的上边界（根坐标）。
 *
 * 共享元素的 overlay 画在整棵 SharedTransitionLayout 之上 —— 包括顶栏与分组 TabRow。
 * 返回书架时封面/正文要收回列表里的格子，格子一旦落在顶栏后面，overlay 就会把这些元素
 * 画在顶栏上方（顶栏在动画期间「消失」，收尾时又突然出现），看起来就是动画层级错了。
 * 这里记下顶栏实际遮挡到的高度，只在它以下绘制。
 *
 * 两项都在布局/绘制期写入与读取，不进快照状态：任何一项进组合都会让书架每帧重组。
 */
internal object ShelfCoverViewport {

    /** 顶栏占位的底边（根坐标，px）。0 = 还没布局过，此时不裁剪。 */
    @Volatile
    var layoutBottomPx: Float = 0f

    /**
     * 顶栏当前的滚动偏移（px，`<= 0`）。`exitUntilCollapsed` 收起时顶栏整体上移，
     * 实际遮挡区域的下边界要跟着抬起来；不跟着抬就会在可见区域的中间把飞行动画切一刀。
     *
     * 只在绘制的裁剪路径里调用，读的是顶栏的滚动状态，不会触发重组。
     */
    @Volatile
    var heightOffsetPx: () -> Float = { 0f }

    /** 当前真正被顶栏遮住的下边界（px）。 */
    val occludedBottomPx: Float
        get() = layoutBottomPx + heightOffsetPx().coerceAtMost(0f)
}

/**
 * 把共享元素在 overlay 里的绘制裁到书架列表可见区域。
 *
 * [clipsOnReturn] 只在「正在往列表里收」的那一端启用：打开书籍时元素是从格子长出去的，
 * 那一侧不能被顶栏截断。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
internal class ShelfViewportOverlayClip(
    /** 元素自身形状（封面圆角 / 阅读页圆角），先按它算出基础裁剪路径。 */
    private val shape: Shape,
    /** 本次转场里该元素是否正收回列表。 */
    private val clipsOnReturn: () -> Boolean,
) : SharedTransitionScope.OverlayClip {

    override fun getClipPath(
        sharedContentState: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path = shelfViewportClipPath(
        shape = shape,
        bounds = bounds,
        layoutDirection = layoutDirection,
        density = density,
        occludedBottomPx = ShelfCoverViewport.occludedBottomPx,
        clipsOnReturn = clipsOnReturn(),
    )
}

/**
 * 基础路径 = 元素自身形状铺在 [bounds] 上；需要收起时再与被顶栏遮住的上方区域求交。
 *
 * [bounds] 是 overlay 坐标（= 根坐标），与 [occludedBottomPx] 同一套坐标。
 */
internal fun shelfViewportClipPath(
    shape: Shape,
    bounds: Rect,
    layoutDirection: LayoutDirection,
    density: Density,
    occludedBottomPx: Float,
    clipsOnReturn: Boolean,
): Path {
    val path = Path().apply {
        addOutline(shape.createOutline(bounds.size, layoutDirection, density))
        translate(Offset(bounds.left, bounds.top))
    }
    // occludedBottomPx <= 0 表示顶栏还没布局过：不裁剪，退化成库的默认行为。
    if (occludedBottomPx <= 0f || bounds.top >= occludedBottomPx || !clipsOnReturn) return path
    val visibleBand = Path().apply {
        addRect(Rect(bounds.left, occludedBottomPx, bounds.right, bounds.bottom))
    }
    return Path().apply { op(path, visibleBand, PathOperation.Intersect) }
}
