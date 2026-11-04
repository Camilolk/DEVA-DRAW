package app.deva.draw

import android.graphics.Bitmap

data class DrawPoint(val x: Float, val y: Float)

data class DrawStroke(val points: List<DrawPoint>)

object DrawData {
    @Volatile var strokes: List<DrawStroke> = emptyList()
    @Volatile var preview: Bitmap? = null
}
