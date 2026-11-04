package io.legado.app.feature.reader

// 阅读器页面装饰的绘制实现：页眉页脚与电池/箭头 tip、九宫格底图、角色气泡、段评角标。
// 从 ReaderCanvasSurface 拆出：这些函数只依赖 ReaderPage/ReaderElement 与 Paint，
// 与手势、翻页栈和快照池无关，放在单独文件里便于阅读与单测。

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderNineSliceLayout
import io.legado.app.feature.reader.core.model.ReaderPage
import io.legado.app.feature.reader.core.model.ReaderPageTip
import io.legado.app.feature.reader.core.model.ReaderRect
import io.legado.app.feature.reader.core.model.ReaderTextBackgroundImage
import io.legado.app.feature.reader.core.model.ReaderTipAlignment
import io.legado.app.feature.reader.core.model.ReaderTipRow
import io.legado.app.feature.reader.core.model.ReaderTipRowLayout
import io.legado.app.feature.reader.core.model.ReaderTipVisual
import io.legado.app.feature.reader.platform.ReaderAndroidPaintFactory
import io.legado.app.feature.reader.platform.ReaderBookmarkBadgeRenderer
import splitties.init.appCtx

/** 旧 `BatteryViewOrgin` 经典模式的数字字体（`assets/font/number.ttf`）。 */
private val batteryClassicTypeface: android.graphics.Typeface? by lazy {
    runCatching {
        android.graphics.Typeface.createFromAsset(appCtx.assets, "font/number.ttf")
    }.getOrNull()
}

/** View 版 `BatteryView` 使用的轮廓；绘制时复用原有 `ic_battery` 矢量资源。 */
private val batteryOutlineState: Drawable.ConstantState? by lazy {
    appCtx.getDrawable(R.drawable.ic_battery)?.constantState
}

internal fun createTipPaint(row: ReaderTipRow) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
    color = row.colorArgb
    textSize = row.fontSizePx
    typeface = ReaderAndroidPaintFactory.loadTypeface(
        row.fontPath,
        row.fontWeight,
        row.italic,
        row.fontFamily,
    )
    textSkewX = if (row.italic) -0.25f else 0f
}

internal fun <T> resolveReaderTipResource(
    row: ReaderTipRow,
    cached: Map<ReaderTipRow, T>,
    create: (ReaderTipRow) -> T,
): T = cached[row] ?: create(row)

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPageDecoration(
    canvas: android.graphics.Canvas,
    page: ReaderPage,
    tipPaints: Map<ReaderTipRow, Paint>,
    badgeImage: Pair<io.legado.app.feature.reader.core.model.ReaderBookmarkBadge, Bitmap?>?,
) {
    page.decoration.header?.takeIf { it.visible }?.let { row ->
        val paint = resolveReaderTipResource(row, tipPaints, ::createTipPaint)
        drawTipRow(
            canvas,
            row,
            paint,
            // 旧版页眉是 includeFontPadding=false 的 TextView：基线 = −ascent（不含 leading）。
            ReaderTipRowLayout.headerBaseline(row.paddingTopPx, paint.fontMetrics.ascent),
        )
        row.dividerColorArgb?.let {
            val metrics = paint.fontMetrics
            val dividerY = ReaderTipRowLayout.extent(
                row.paddingTopPx, metrics.ascent, metrics.descent, row.paddingBottomPx,
            )
            drawReaderTipDivider(row, dividerY, it)
        }
    }
    page.decoration.footer?.takeIf { it.visible }?.let { row ->
        val paint = resolveReaderTipResource(row, tipPaints, ::createTipPaint)
        drawTipRow(
            canvas,
            row,
            paint,
            ReaderTipRowLayout.footerBaseline(
                size.height, row.paddingBottomPx, paint.fontMetrics.descent,
            ),
        )
        row.dividerColorArgb?.let {
            val metrics = paint.fontMetrics
            val dividerY = size.height - ReaderTipRowLayout.extent(
                row.paddingTopPx, metrics.ascent, metrics.descent, row.paddingBottomPx,
            )
            drawReaderTipDivider(row, dividerY, it)
        }
    }
    page.decoration.bookmarkBadge?.let { badge ->
        if (shouldDrawReaderBookmarkBadge(badge, badgeImage)) {
            ReaderBookmarkBadgeRenderer.draw(
                canvas,
                badge,
                badgeImage?.takeIf { it.first == badge }?.second,
            )
        }
    }
}

internal fun shouldDrawReaderBookmarkBadge(
    badge: io.legado.app.feature.reader.core.model.ReaderBookmarkBadge,
    loaded: Pair<io.legado.app.feature.reader.core.model.ReaderBookmarkBadge, Bitmap?>?,
): Boolean = badge.imageSource.isBlank() || loaded?.first == badge

/**
 * 正文的文字背景图绘制：平铺 / 拉伸 / 裁剪 / 九宫格四种 fit 全在这里，分页侧只负责算出
 * [io.legado.app.feature.reader.core.model.ReaderTextBackgroundRun] 的两个矩形。
 * 高亮规则编辑页的预览共用这一份，预览才会和正文一模一样。
 *
 * 背景用的画笔不许开抗锯齿：九宫格九条边落在半像素上时，相邻两格各画一条半覆盖的边，
 * source-over 合成不出满覆盖，气泡上就留下一条透出页面背景的笔直「切割线」。九宫格还要按
 * 每格的目标范围裁剪，避免补缝用的外扩区域重复叠加半透明像素。四周形状由图自身的 alpha 决定。
 */
internal fun drawTextBackground(
    canvas: android.graphics.Canvas,
    bitmap: Bitmap,
    run: io.legado.app.feature.reader.core.model.ReaderTextBackgroundRun,
    paint: Paint,
) {
    val bounds = run.contentBounds
    val image = run.image
    if (bounds.width <= 0f || bounds.height <= 0f) return
    val destination = android.graphics.RectF(bounds.left, bounds.top, bounds.right, bounds.bottom)
    // One paint is retained per page layer so page-turn frames do not allocate per styled run.
    // A tiled predecessor leaves a shader behind, therefore always clear it before reusing it.
    paint.shader = null
    paint.isAntiAlias = false
    val scale = image.scale.coerceIn(0.1f, 5f)
    when (image.fit) {
        1 -> {
            val width = bounds.width * scale
            val height = bounds.height * scale
            val rect = android.graphics.RectF(
                bounds.left + (bounds.width - width) / 2f,
                bounds.top + (bounds.height - height) / 2f,
                bounds.left + (bounds.width + width) / 2f,
                bounds.top + (bounds.height + height) / 2f,
            )
            canvas.save()
            canvas.clipRect(destination)
            canvas.drawBitmap(bitmap, null, rect, paint)
            canvas.restore()
        }
        2 -> {
            val fit = maxOf(bounds.width / bitmap.width, bounds.height / bitmap.height) * scale
            val width = bitmap.width * fit
            val height = bitmap.height * fit
            val rect = android.graphics.RectF(
                bounds.left + (bounds.width - width) / 2f,
                bounds.top + (bounds.height - height) / 2f,
                bounds.left + (bounds.width + width) / 2f,
                bounds.top + (bounds.height + height) / 2f,
            )
            canvas.save()
            canvas.clipRect(destination)
            canvas.drawBitmap(bitmap, null, rect, paint)
            canvas.restore()
        }
        3 -> drawNineSliceBackground(
            canvas = canvas,
            bitmap = bitmap,
            content = destination,
            frame = android.graphics.RectF(run.bounds.left, run.bounds.top, run.bounds.right, run.bounds.bottom),
            image = image,
            paint = paint,
        )
        else -> {
            val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            val matrix = android.graphics.Matrix().apply {
                setScale(scale, scale)
                postTranslate(bounds.left, bounds.top)
            }
            shader.setLocalMatrix(matrix)
            paint.shader = shader
            canvas.drawRect(destination, paint)
        }
    }
}

private fun drawNineSliceBackground(
    canvas: android.graphics.Canvas,
    bitmap: Bitmap,
    content: android.graphics.RectF,
    frame: android.graphics.RectF,
    image: ReaderTextBackgroundImage,
    paint: Paint,
) {
    ReaderNineSliceLayout.cells(
        bitmap.width,
        bitmap.height,
        ReaderRect(content.left, content.top, content.right, content.bottom),
        ReaderRect(frame.left, frame.top, frame.right, frame.bottom),
        image,
    ).forEach { cell ->
        val saveCount = canvas.save()
        try {
            // 外扩只负责盖到切分边界，最终每个像素仍归一格所有；否则半透明图的接缝会叠深。
            canvas.clipRect(
                cell.destination.left, cell.destination.top, cell.destination.right, cell.destination.bottom,
            )
            canvas.drawBitmap(
                bitmap,
                android.graphics.Rect(cell.source.left, cell.source.top, cell.source.right, cell.source.bottom),
                android.graphics.RectF(
                    cell.painted.left,
                    cell.painted.top,
                    cell.painted.right,
                    cell.painted.bottom,
                ),
                paint,
            )
        } finally {
            canvas.restoreToCount(saveCount)
        }
    }
}

/**
 * 胶囊底板：先铺底色（没设颜色就沿用正文反色派生的那层淡底），再按同一块圆角把底图裁进去。
 * 深浅模式各一套颜色和图，取哪一套在 [io.legado.app.help.readaloud.cast.CastCapsuleStyleStore.current] 里已经判好。
 */
private fun drawCapsuleBackground(
    native: android.graphics.Canvas,
    b: ReaderRect,
    h: Float,
    style: io.legado.app.feature.reader.core.cast.CastCapsuleStyle,
    fill: android.graphics.Paint,
) {
    val radius = style.cornerPx(h)
    val custom = style.bgColor != 0
    fill.color = if (custom) style.bgColor else (fill.color and 0x00FFFFFF) or (34 shl 24)
    native.drawRoundRect(b.left, b.top, b.right, b.bottom, radius, radius, fill)
    val image = style.bgImage
    if (image.isEmpty()) return
    val bitmap = io.legado.app.help.readaloud.cast.CastCapsuleImageCache.cached(image)
        ?.takeIf { !it.isRecycled } ?: return
    val rects = io.legado.app.help.readaloud.cast.CastCapsuleImageCache.coverRects(
        bitmap, b.left, b.top, b.right, b.bottom,
    ) ?: return
    native.save()
    val clip = android.graphics.Path().apply {
        addRoundRect(
            android.graphics.RectF(b.left, b.top, b.right, b.bottom),
            radius, radius, android.graphics.Path.Direction.CW,
        )
    }
    native.clipPath(clip)
    fill.color = android.graphics.Color.WHITE
    native.drawBitmap(bitmap, rects.first, rects.second, fill)
    native.restore()
}

/**
 * 段首配乐胶囊：`♪ 声音池名`。正文文字一字未改，朗读链路也读不到它。
 * 文案与宽度与测量侧共用 [io.legado.app.feature.reader.core.cast.CastCapsuleGeometry]。
 */
internal fun drawBgmScene(
    native: android.graphics.Canvas,
    e: ReaderElement.BgmScene,
    colorArgb: Int,
    variantArgb: Int,
) {
    val b = e.bounds
    val h = b.height
    if (h <= 0f || b.width <= 0f) return
    val style = io.legado.app.help.readaloud.cast.CastCapsuleStyleStore
        .current(io.legado.app.help.readaloud.cast.CastCapsuleStyleStore.BGM)
    val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = variantArgb
    }
    drawCapsuleBackground(native, b, h, style, fill)
    val geo = io.legado.app.feature.reader.core.cast.CastCapsuleGeometry
    val label = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = labelColor(colorArgb, style)
        textSize = h / geo.heightRatio * geo.textScale
    }
    val fm = label.fontMetrics
    native.drawText(
        geo.bgmLabel(e.poolName),
        b.left + h * geo.padRatio,
        (b.top + b.bottom) / 2f - (fm.ascent + fm.descent) / 2f,
        label,
    )
}


/**
 * 多角色分配胶囊：圆角底 + 头像（缺省画首字符占位圆）+ 名字 + 声音池小字 +（带变声时的）均衡器标记。
 * 未分配的那一颗只有人形图标，没有文字。
 * 几何与测量侧共用 [io.legado.app.feature.reader.core.cast.CastCapsuleGeometry]，颜色派生自正文色（主题安全）。
 * 圆角、底色、底图、头像形状与位移读 [io.legado.app.help.readaloud.cast.CastCapsuleStyleStore]。
 */
internal fun drawRoleCast(
    native: android.graphics.Canvas,
    e: ReaderElement.RoleCast,
    colorArgb: Int,
    variantArgb: Int,
    avatar: android.graphics.Bitmap?,
) {
    val b = e.bounds
    val h = b.height
    if (h <= 0f || b.width <= 0f) return
    val castStore = io.legado.app.help.readaloud.cast.CastCapsuleStyleStore
    val style = castStore.current(
        if (e.name.isEmpty()) castStore.PLACEHOLDER else castStore.ROLE,
    )
    val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = variantArgb
    }
    drawCapsuleBackground(native, b, h, style, fill)
    val cy = (b.top + b.bottom) / 2f
    val geo = io.legado.app.feature.reader.core.cast.CastCapsuleGeometry
    val d = style.avatarDiameter(h)
    // 两颗共用同一个落点（见 avatarLeft）：都是正方形底板、图标必须落在同一格中心，
    // 与「显示角色名/声音池」无关。
    val avatarLeft = b.left + style.avatarLeft(h)
    val avatarCy = cy + style.avatarCenterOffset(h)
    val textPx = h / geo.heightRatio * geo.textScale
    val name = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = labelColor(colorArgb, style)
        textSize = textPx
    }
    val nameFm = name.fontMetrics
    val nameBaseline = cy - (nameFm.ascent + nameFm.descent) / 2f
    if (e.name.isEmpty()) {
        // 未分配：胶囊里只有一个人形图标，文字一个不留（点它就是给这句分配角色）
        drawCastPersonIcon(
            native,
            avatarLeft + d / 2f,
            avatarCy,
            d,
            labelColor(variantArgb, style),
        )
        return
    }
    // 关掉的那一栏连宽度一起没有（测量侧读同一份开关），所以这里也只是跳过绘制
    if (style.showAvatar) {
        if (avatar != null && !avatar.isRecycled) {
            val bmp = android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG,
            )
            native.save()
            val shape = android.graphics.Path().apply {
                addRoundRect(
                    android.graphics.RectF(avatarLeft, avatarCy - d / 2f, avatarLeft + d, avatarCy + d / 2f),
                    style.avatarCornerPx(d), style.avatarCornerPx(d),
                    android.graphics.Path.Direction.CW,
                )
            }
            native.clipPath(shape)
            native.drawBitmap(
                avatar, null,
                android.graphics.RectF(avatarLeft, avatarCy - d / 2f, avatarLeft + d, avatarCy + d / 2f),
                bmp,
            )
            native.restore()
        } else {
            val radius = style.avatarCornerPx(d)
            val left = avatarLeft
            val top = avatarCy - d / 2f
            fill.color = (variantArgb and 0x00FFFFFF) or (90 shl 24)
            native.drawRoundRect(left, top, left + d, top + d, radius, radius, fill)
            val initial = e.name.firstOrNull()?.toString() ?: "?"
            name.textAlign = android.graphics.Paint.Align.CENTER
            native.drawText(initial, left + d / 2f, avatarCy - (nameFm.ascent + nameFm.descent) / 2f, name)
            name.textAlign = android.graphics.Paint.Align.LEFT
        }
    }
    val nameWidth = if (style.showName) name.measureText(e.name) else 0f
    if (style.showName) {
        native.drawText(
            e.name,
            b.left + geo.textLeftPx(h, style),
            nameBaseline,
            name,
        )
    }
    if (style.showPool && e.voicePoolLabel.isNotEmpty()) {
        // 声音池：名字后的小字（无括号），与名字各自按字形垂直居中
        val pool = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor(variantArgb, style)
            textSize = textPx * geo.poolScale
        }
        val poolFm = pool.fontMetrics
        val poolBaseline = cy - (poolFm.ascent + poolFm.descent) / 2f
        native.drawText(
            e.voicePoolLabel,
            b.left + geo.poolLeftPx(h, style, nameWidth),
            poolBaseline,
            pool,
        )
    }
    if (e.voiceEffectMark) {
        // 这一句带变声器（正文胶囊那一栏设的，或角色的全局值）：右端画一撮均衡器小竖条
        val badge = h * geo.effectRatio
        drawCastEffectMark(
            native,
            b.right - h * geo.padRatio - badge / 2f,
            cy,
            badge,
            labelColor(variantArgb, style),
        )
    }
}

/** 设了实底色（尤其是不透明的自定义底）时，字色跟着正文色走会看不见，换成对比更强的那端。 */
private fun labelColor(baseArgb: Int, style: io.legado.app.feature.reader.core.cast.CastCapsuleStyle): Int {
    if (style.bgColor == 0) return baseArgb
    val alpha = (style.bgColor ushr 24) and 0xFF
    if (alpha < 128) return baseArgb
    val background = android.graphics.Color.red(style.bgColor) * 0.299f +
        android.graphics.Color.green(style.bgColor) * 0.587f +
        android.graphics.Color.blue(style.bgColor) * 0.114f
    val current = android.graphics.Color.red(baseArgb) * 0.299f +
        android.graphics.Color.green(baseArgb) * 0.587f +
        android.graphics.Color.blue(baseArgb) * 0.114f
    return if (background > current) android.graphics.Color.BLACK else android.graphics.Color.WHITE
}


/** 未分配占位胶囊的人形图标：一个头 + 一道肩线，不依赖任何字体。 */
private fun drawCastPersonIcon(
    native: android.graphics.Canvas,
    cx: Float,
    cy: Float,
    d: Float,
    colorArgb: Int,
) {
    if (d <= 0f) return
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = colorArgb
    }
    native.drawCircle(cx, cy - d * 0.16f, d * 0.17f, paint)
    val body = android.graphics.Path().apply {
        addArc(cx - d * 0.29f, cy + d * 0.06f, cx + d * 0.29f, cy + d * 0.52f, 180f, 180f)
        close()
    }
    native.drawPath(body, paint)
}

/** 变声器标记：宽 [w] 的四根圆头竖条，高矮不齐（均衡器样式），字号再大也只是等比放大。 */
private fun drawCastEffectMark(
    native: android.graphics.Canvas,
    cx: Float,
    cy: Float,
    w: Float,
    colorArgb: Int,
) {
    if (w <= 0f) return
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = colorArgb
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeWidth = w * 0.16f
    }
    val heights = floatArrayOf(0.4f, 0.85f, 0.55f, 1f)
    val step = w / heights.size
    heights.forEachIndexed { index, ratio ->
        val x = cx - w / 2f + step * (index + 0.5f)
        val half = w * ratio / 2f
        native.drawLine(x, cy - half, x, cy + half, paint)
    }
}fun drawReview(canvas: android.graphics.Canvas, review: ReaderElement.Review, colorArgb: Int) {
    val start = review.bounds.left
    val end = review.bounds.right
    val baseline = review.baselinePx
    val height = review.textSizePx
    val path = android.graphics.Path().apply {
        moveTo(start + 1f, baseline - height * 2f / 5f)
        lineTo(start + height / 6f, baseline - height * .55f)
        lineTo(start + height / 6f, baseline - height * .8f)
        lineTo(end - 1f, baseline - height * .8f)
        lineTo(end - 1f, baseline)
        lineTo(start + height / 6f, baseline)
        lineTo(start + height / 6f, baseline - height / 4f)
        close()
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorArgb
        textSize = height * .55f
        textAlign = Paint.Align.CENTER
    }
    paint.style = Paint.Style.STROKE
    canvas.drawPath(path, paint)
    paint.style = Paint.Style.FILL
    canvas.drawText(review.count.coerceAtMost(999).toString(), (start + height / 9f + end) / 2f, baseline - height * .23f, paint)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawReaderTipDivider(
    row: ReaderTipRow,
    dividerY: Float,
    colorArgb: Int,
) {
    // 旧版分隔线是 0.5dp 的 View，且随 `vwRoot` 的刘海 padding 内缩（不是整屏通栏）。
    val left = row.insetLeftPx
    val right = size.width - row.insetRightPx
    if (right <= left) return
    drawLine(
        Color(colorArgb),
        Offset(left, dividerY),
        Offset(right, dividerY),
        TIP_DIVIDER_THICKNESS_DP.dp.toPx(),
    )
}

private const val TIP_DIVIDER_THICKNESS_DP = 0.5f

private val ellipsizedTipCache = object : LinkedHashMap<String, String>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
        size > 64
}

/**
 * 对照旧版页眉页脚 `TextView` 的 `maxLines=1 ellipsize=end`：装不下就在末尾加省略号截断，
 * 避免长章名/长模板把中、右槽的文案压在一起。结果按（文案、可用宽度、字号、字体）缓存，
 * 绘制期不重复测量。
 */
internal fun ellipsizeTipText(text: String, paint: Paint, maxWidthPx: Float): String {
    if (text.isEmpty()) return text
    if (maxWidthPx <= 0f) return ""
    if (paint.measureText(text) <= maxWidthPx) return text
    val key =
        "$text\u0000${maxWidthPx.toInt()}\u0000${paint.textSize}\u0000${paint.typeface?.hashCode()}"
    ellipsizedTipCache[key]?.let { return it }
    val ellipsis = "…"
    val ellipsisWidth = paint.measureText(ellipsis)
    var end = text.length
    while (end > 0 && paint.measureText(text, 0, end) + ellipsisWidth > maxWidthPx) end--
    val result = if (end <= 0) "" else text.substring(0, end) + ellipsis
    ellipsizedTipCache[key] = result
    return result
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTipRow(
    canvas: android.graphics.Canvas,
    row: ReaderTipRow,
    paint: Paint,
    baseline: Float,
) {
    val leftEdge = row.paddingLeftPx
    val rightEdge = size.width - row.paddingRightPx
    val startTip = row.tips.firstOrNull { it.alignment == ReaderTipAlignment.START }
    val centerTip = row.tips.firstOrNull { it.alignment == ReaderTipAlignment.CENTER }
    val endTip = row.tips.firstOrNull { it.alignment == ReaderTipAlignment.END }
    fun tipWidth(tip: ReaderPageTip): Float =
        if (tip.visual == ReaderTipVisual.TEXT) paint.measureText(tip.text)
        else visualTipWidthPx(tip, paint)
    // 对照旧 `view_book_page.xml` 的三槽约束：
    // - 左槽 `layout_width=0dp` + `constraintHorizontal_weight=1`，右边界是 `barrier`
    //   （`barrierDirection=start`、`barrierAllowsGoneWidgets=false`）= 可见的中/右槽起始边的最小值，
    //   GONE（即没有该槽 tip）不参与；所以**只有左槽会被邻槽挤压**并在 barrier 处省略；
    // - 中槽 `wrap_content` 居中、右槽 `wrap_content` 靠右，两者都不受邻槽约束，只在整行宽度处省略
    //   （旧版就是这样：中/右槽长文案会压到邻槽上，不额外让位）。
    val barrierStart = listOfNotNull(
        centerTip?.let { size.width / 2f - tipWidth(it) / 2f },
        endTip?.let { rightEdge - tipWidth(it) },
    ).minOrNull()
    row.tips.forEach { tip ->
        if (tip.visual == ReaderTipVisual.TEXT) {
            val available = when (tip.alignment) {
                ReaderTipAlignment.START -> (barrierStart ?: rightEdge) - leftEdge
                else -> rightEdge - leftEdge
            }.coerceAtLeast(0f)
            val text = ellipsizeTipText(tip.text, paint, available)
            if (text.isEmpty()) return@forEach
            val x = when (tip.alignment) {
                ReaderTipAlignment.START -> {
                    paint.textAlign = Paint.Align.LEFT; leftEdge
                }
                ReaderTipAlignment.CENTER -> { paint.textAlign = Paint.Align.CENTER; size.width / 2f }
                ReaderTipAlignment.END -> {
                    paint.textAlign = Paint.Align.RIGHT; rightEdge
                }
            }
            canvas.drawText(text, x, baseline, paint)
        } else {
            drawVisualTip(canvas, row, tip, paint, baseline)
        }
    }
}

/** 非文字 tip（电池/箭头）的绘制宽度：`drawVisualTip` 与左槽 barrier 计算共用同一份几何。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.visualTipWidthPx(
    tip: ReaderPageTip,
    paint: Paint,
): Float {
    val unit = density
    val gap = 4f * unit
    val batteryWidth = 28f * unit
    val numberWidth = paint.measureText(tip.batteryPercent.coerceIn(0, 100).toString())
    val textWidth = paint.measureText(tip.text)
    return when (tip.visual) {
        ReaderTipVisual.BATTERY_OUTER -> batteryWidth + 2f * unit + numberWidth
        ReaderTipVisual.BATTERY_INNER -> (if (tip.text.isEmpty()) 0f else textWidth + gap) + batteryWidth
        ReaderTipVisual.BATTERY_ICON -> batteryWidth
        ReaderTipVisual.BATTERY_CLASSIC -> (if (tip.text.isEmpty()) 0f else textWidth + gap) + numberWidth + 10f * unit
        ReaderTipVisual.ARROW -> 12f * unit + 8f * unit + textWidth
        ReaderTipVisual.TEXT -> textWidth
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVisualTip(
    canvas: android.graphics.Canvas,
    row: ReaderTipRow,
    tip: ReaderPageTip,
    paint: Paint,
    baseline: Float,
) {
    val unit = density
    val gap = 4f * unit
    val batteryWidth = 28f * unit
    val number = tip.batteryPercent.coerceIn(0, 100).toString()
    val numberWidth = paint.measureText(number)
    val textWidth = paint.measureText(tip.text)
    val visualWidth = visualTipWidthPx(tip, paint)
    val left = when (tip.alignment) {
        ReaderTipAlignment.START -> row.paddingLeftPx
        ReaderTipAlignment.CENTER -> (size.width - visualWidth) / 2f
        ReaderTipAlignment.END -> size.width - row.paddingRightPx - visualWidth
    }
    paint.textAlign = Paint.Align.LEFT
    when (tip.visual) {
        ReaderTipVisual.BATTERY_OUTER -> {
            drawBatteryGlyph(
                canvas,
                left,
                baseline,
                tip.batteryPercent,
                paint,
                drawNumberInside = false,
                fillInner = true
            )
            canvas.drawText(number, left + batteryWidth + 2f * unit, baseline, paint)
        }
        ReaderTipVisual.BATTERY_INNER -> {
            val batteryLeft = if (tip.text.isEmpty()) left else {
                canvas.drawText(tip.text, left, baseline, paint)
                left + textWidth + gap
            }
            // 旧 `BatteryView`：INNER 模式 `batteryFill.visibility = GONE`，只留外框与框内数字。
            drawBatteryGlyph(
                canvas,
                batteryLeft,
                baseline,
                tip.batteryPercent,
                paint,
                drawNumberInside = true,
                fillInner = false,
            )
        }
        ReaderTipVisual.BATTERY_ICON ->
            drawBatteryGlyph(
                canvas,
                left,
                baseline,
                tip.batteryPercent,
                paint,
                drawNumberInside = false,
                fillInner = true
            )
        ReaderTipVisual.BATTERY_CLASSIC -> {
            // 旧 `BatteryViewOrgin`：经典模式的文字与数字都用 `assets/font/number.ttf`。
            val numberPaint = Paint(paint).apply {
                batteryClassicTypeface?.let { typeface = it }
            }
            val classicNumberWidth = numberPaint.measureText(number)
            val numberLeft = if (tip.text.isEmpty()) left + 4f * unit else {
                canvas.drawText(tip.text, left, baseline, numberPaint)
                left + numberPaint.measureText(tip.text) + gap + 4f * unit
            }
            canvas.drawText(number, numberLeft, baseline, numberPaint)
            val top = baseline + numberPaint.fontMetrics.ascent - 2f * unit
            val bottom = baseline + numberPaint.fontMetrics.descent + 2f * unit
            val frame = Paint(numberPaint).apply {
                style = Paint.Style.STROKE; strokeWidth = unit.coerceAtLeast(1f)
            }
            canvas.drawRect(
                numberLeft - 2f * unit,
                top,
                numberLeft + classicNumberWidth + 2f * unit,
                bottom,
                frame
            )
            canvas.drawRect(
                numberLeft + classicNumberWidth + 2f * unit,
                top + (bottom - top) / 3f,
                numberLeft + classicNumberWidth + 4f * unit,
                bottom - (bottom - top) / 3f,
                Paint(numberPaint).apply { style = Paint.Style.FILL },
            )
        }
        ReaderTipVisual.ARROW -> {
            val centerY = baseline + (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
            val arrow = android.graphics.Path().apply {
                moveTo(left + 8f * unit, centerY - 5f * unit)
                lineTo(left + 3f * unit, centerY)
                lineTo(left + 8f * unit, centerY + 5f * unit)
            }
            // 旧 `BatteryView`：arrowIcon.alpha = 0.76（194/255）。
            canvas.drawPath(arrow, Paint(paint).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * unit
                strokeCap = Paint.Cap.SQUARE
                strokeJoin = Paint.Join.MITER
                alpha = 194
            })
            canvas.drawText(tip.text, left + 20f * unit, baseline, paint)
        }
        ReaderTipVisual.TEXT -> Unit
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBatteryGlyph(
    canvas: android.graphics.Canvas,
    left: Float,
    baseline: Float,
    batteryPercent: Int,
    paint: Paint,
    drawNumberInside: Boolean,
    fillInner: Boolean,
) {
    val unit = density
    val iconTop = baseline + (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f - 6f * unit
    // 旧 ImageView 为 28×12dp centerCrop：24dp 矢量放大到 28dp，垂直裁去两侧各 8dp。
    batteryOutlineState?.newDrawable(appCtx.resources)?.mutate()?.apply {
        setTint(paint.color)
        alpha = 194 // 旧 BatteryView.batteryIcon.alpha = 0.76
        setBounds(
            left.toInt(),
            (iconTop - 8f * unit).toInt(),
            (left + 28f * unit).toInt(),
            (iconTop + 20f * unit).toInt(),
        )
        draw(canvas)
    }
    val innerWidth = 17f * unit * batteryPercent.coerceIn(0, 100) / 100f
    if (fillInner && innerWidth > 0f) {
        val fill = Paint(paint).apply { style = Paint.Style.FILL }
        canvas.drawRoundRect(
            left + 4.2f * unit,
            iconTop + 2f * unit,
            left + 4.2f * unit + innerWidth,
            iconTop + 10f * unit,
            unit,
            unit,
            fill,
        )
    }
    if (drawNumberInside) {
        val numberPaint = Paint(paint).apply {
            textSize = 8f * unit
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val centerY =
            iconTop + 6f * unit - (numberPaint.fontMetrics.ascent + numberPaint.fontMetrics.descent) / 2f
        canvas.drawText(
            batteryPercent.coerceIn(0, 100).toString(),
            left + 12.8f * unit,
            centerY,
            numberPaint,
        )
    }
}
