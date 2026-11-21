package com.grandpa.biglauncher

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.widget.TextView

object Ui {
    val BG = 0xFF101418.toInt()

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    fun round(c: Context, color: Int, radiusDp: Int = 24): Drawable {
        val shape = GradientDrawable().apply { setColor(color); cornerRadius = dp(c, radiusDp).toFloat() }
        val mask = GradientDrawable().apply { setColor(Color.BLACK); cornerRadius = dp(c, radiusDp).toFloat() }
        return RippleDrawable(ColorStateList.valueOf(0x66FFFFFF), shape, mask)
    }

    fun oval(color: Int): Drawable = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }

    fun text(c: Context, s: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(c).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
}
