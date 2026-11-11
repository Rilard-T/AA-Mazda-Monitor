package com.aamazda.navproto

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Рисует диагностический экран: рамки поверхности, видимой и стабильной области, размеры и часы. */
class SurfaceRenderer : SurfaceCallback {
    private var surface: Surface? = null
    private var width = 0
    private var height = 0
    private var dpi = 0
    private var visible = Rect()
    private var stable = Rect()
    private val handler = Handler(Looper.getMainLooper())
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val stroke = Paint().apply { style = Paint.Style.STROKE }
    private val text = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
    }
    private val ticker = object : Runnable {
        override fun run() {
            draw()
            handler.postDelayed(this, 500)
        }
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        surface = surfaceContainer.surface
        width = surfaceContainer.width
        height = surfaceContainer.height
        dpi = surfaceContainer.dpi
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        handler.removeCallbacks(ticker)
        surface = null
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        visible = Rect(visibleArea)
        draw()
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        stable = Rect(stableArea)
        draw()
    }

    private fun draw() {
        val s = surface ?: return
        if (!s.isValid) return
        val c: Canvas = try {
            s.lockCanvas(null)
        } catch (e: Exception) {
            null
        } ?: return
        try {
            c.drawColor(Color.rgb(8, 16, 40))
            stroke.strokeWidth = 12f
            stroke.color = Color.RED
            c.drawRect(6f, 6f, width - 6f, height - 6f, stroke)
            stroke.strokeWidth = 6f
            stroke.color = Color.CYAN
            c.drawRect(visible, stroke)
            stroke.color = Color.YELLOW
            c.drawRect(stable, stroke)

            val size = maxOf(height / 14f, 22f)
            text.textSize = size
            var y = maxOf(stable.top.toFloat(), 0f) + size * 2f
            val x = maxOf(stable.left.toFloat(), 0f) + 40f
            val lines = listOf(
                "AA Monitor Nav (прототип)",
                "Поверхность: ${width}x$height, $dpi dpi",
                "Видимая (голубая): ${visible.toShortString()}",
                "Стабильная (жёлтая): ${stable.toShortString()}",
                "Красная рамка: край всей поверхности",
                "Время: ${clock.format(Date())}"
            )
            for (line in lines) {
                c.drawText(line, x, y, text)
                y += size * 1.4f
            }
        } finally {
            s.unlockCanvasAndPost(c)
        }
    }
}
