package com.aatorque.prefs

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import androidx.recyclerview.widget.RecyclerView
import com.aatorque.stats.R
import com.google.android.material.color.MaterialColors

/**
 * Список настроек с закруглёнными карточками (0.8.3). Подряд идущие настройки между заголовками разделов
 * рисуются одной карточкой: внешние углы 24 dp, внутренние 4 dp, зазор 2 dp, отступ от краёв 16 dp.
 * Подключается подменой раскладки preference_recyclerview.xml, поэтому работает на всех экранах настроек.
 */
class CardRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RecyclerView(context, attrs, defStyleAttr) {

    init {
        addItemDecoration(CardDecoration(context))
    }

    private class CardDecoration(context: Context) : RecyclerView.ItemDecoration() {
        private val density = context.resources.displayMetrics.density
        private val side = (16 * density).toInt()
        private val gap = (1 * density).toInt()
        private val outer = 24 * density
        private val inner = 4 * density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private val path = Path()
        // типы строк, которые являются заголовками разделов (узнаём по метке ms_category_root в раскладке)
        private val categoryTypes = HashSet<Int>()

        private fun isCategory(adapter: RecyclerView.Adapter<*>, pos: Int): Boolean =
            categoryTypes.contains(adapter.getItemViewType(pos))

        /** 0: не карточка; иначе CARD, плюс TOP для первой и BOTTOM для последней строки группы. */
        private fun flags(parent: RecyclerView, pos: Int): Int {
            val adapter = parent.adapter ?: return 0
            val count = adapter.itemCount
            if (pos < 0 || pos >= count || isCategory(adapter, pos)) return 0
            var f = CARD
            if (pos == 0 || isCategory(adapter, pos - 1)) f = f or TOP
            if (pos == count - 1 || isCategory(adapter, pos + 1)) f = f or BOTTOM
            return f
        }

        private fun radii(f: Int): FloatArray {
            val top = if ((f and TOP) != 0) outer else inner
            val bottom = if ((f and BOTTOM) != 0) outer else inner
            return floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom)
        }

        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val holder = parent.getChildViewHolder(view)
            if (view.findViewById<View>(R.id.ms_category_root) != null) categoryTypes.add(holder.itemViewType)
            outRect.left = side
            outRect.right = side
            val f = flags(parent, parent.getChildAdapterPosition(view))
            if ((f and CARD) != 0) {
                outRect.top = if ((f and TOP) != 0) 0 else gap
                outRect.bottom = if ((f and BOTTOM) != 0) 0 else gap
            }
        }

        override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            paint.color = MaterialColors.getColor(
                parent.context, com.google.android.material.R.attr.colorSurfaceContainerHigh, 0xFFEEEEEE.toInt()
            )
            for (i in 0 until parent.childCount) {
                val v = parent.getChildAt(i)
                val f = flags(parent, parent.getChildAdapterPosition(v))
                applyOutline(v, f)
                if ((f and CARD) == 0) continue
                rect.set(
                    v.left + v.translationX, v.top + v.translationY,
                    v.right + v.translationX, v.bottom + v.translationY
                )
                path.reset()
                path.addRoundRect(rect, radii(f), Path.Direction.CW)
                c.drawPath(path, paint)
            }
        }

        /** Закругляет нажатие (рябь) по форме карточки; пересчитывается только при смене формы или размера. */
        private fun applyOutline(v: View, f: Int) {
            val signature = (f.toLong() shl 40) or (v.width.toLong() shl 20) or v.height.toLong()
            if (v.getTag(R.id.ms_card_kind) == signature) return
            v.setTag(R.id.ms_card_kind, signature)
            if ((f and CARD) != 0) {
                val r = radii(f)
                v.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        val p = Path()
                        p.addRoundRect(0f, 0f, view.width.toFloat(), view.height.toFloat(), r, Path.Direction.CW)
                        outline.setConvexPath(p)
                    }
                }
                v.clipToOutline = true
            } else {
                v.outlineProvider = ViewOutlineProvider.BACKGROUND
                v.clipToOutline = false
            }
            v.invalidateOutline()
        }

        private companion object {
            const val CARD = 4
            const val TOP = 1
            const val BOTTOM = 2
        }
    }
}
