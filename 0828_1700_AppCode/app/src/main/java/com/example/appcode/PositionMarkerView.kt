package com.example.appcode

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator

class PositionMarkerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var pixelPosition: Pair<Float, Float>? = null  // 要畫的座標 (px)

    // 座標點
    private val circlePaint = Paint().apply {
        color = 0xff6347.toInt()
        alpha = 180
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // 文字
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 30f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    // 文字背景
    private val textBgPaint = Paint().apply {
        color = 0xff6347.toInt()
        alpha = 180
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val photoMatrix = Matrix()  // PhotoView 的矩陣
    private var radius = 40f  // 圓半徑

    // 放大縮小動畫
    private val animator = ValueAnimator.ofFloat(0.9f, 1.1f).apply {
        duration = 800
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            radius = 40f * it.animatedValue as Float
            invalidate()
        }
    }

    init {
        animator.start()
    }

    fun setPhotoViewMatrix(matrix: Matrix) {
        photoMatrix.set(matrix)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        pixelPosition?.let { (x, y) ->
            val pts = floatArrayOf(x, y)
            photoMatrix.mapPoints(pts)  // 套用 PhotoView 的縮放/平移

            // 畫藍色背景圓
            canvas.drawCircle(pts[0], pts[1], radius, circlePaint)

            // 計算文字位置
            val text = "現在位置"
            val textWidth = textPaint.measureText(text)
            val textHeight = textPaint.descent() - textPaint.ascent()
            val textY = pts[1] - radius - 10  // 文字置於圓上方

            // 畫文字背景（圓角矩形）
            val padding = 10f
            val rectLeft = pts[0] - textWidth / 2 - padding
            val rectTop = textY + textPaint.ascent() - padding
            val rectRight = pts[0] + textWidth / 2 + padding
            val rectBottom = textY + textPaint.descent() + padding
            val rectF = RectF(rectLeft, rectTop, rectRight, rectBottom)
            canvas.drawRoundRect(rectF, 8f, 8f, textBgPaint)

            // 畫文字
            canvas.drawText(text, pts[0], textY, textPaint)
        }
    }
}
