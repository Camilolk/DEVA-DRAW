package app.deva.draw

import android.graphics.Bitmap

data class DrawStroke(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float
)

object DrawData {
    @Volatile var strokes: List<DrawStroke> = emptyList()
    @Volatile var preview: Bitmap? = null
}
