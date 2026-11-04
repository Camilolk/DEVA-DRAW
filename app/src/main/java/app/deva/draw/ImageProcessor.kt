package app.deva.draw

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object ImageProcessor {
    private const val SIZE = 112

    fun process(source: Bitmap): Pair<Bitmap, List<DrawStroke>> {
        val scaled = Bitmap.createScaledBitmap(source, SIZE, SIZE, true)
        val gray = Array(SIZE) { y ->
            IntArray(SIZE) { x ->
                val c = scaled.getPixel(x, y)
                (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
            }
        }

        val edge = Array(SIZE) { BooleanArray(SIZE) }
        for (y in 1 until SIZE - 1) {
            for (x in 1 until SIZE - 1) {
                val gx = -gray[y - 1][x - 1] + gray[y - 1][x + 1] -
                    2 * gray[y][x - 1] + 2 * gray[y][x + 1] -
                    gray[y + 1][x - 1] + gray[y + 1][x + 1]
                val gy = -gray[y - 1][x - 1] - 2 * gray[y - 1][x] - gray[y - 1][x + 1] +
                    gray[y + 1][x - 1] + 2 * gray[y + 1][x] + gray[y + 1][x + 1]
                edge[y][x] = abs(gx) + abs(gy) > 360
            }
        }

        val output = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvasPixels = IntArray(SIZE * SIZE) { Color.WHITE }
        val result = ArrayList<DrawStroke>()

        for (y in 1 until SIZE - 1) {
            var x = 1
            while (x < SIZE - 1) {
                if (!edge[y][x]) {
                    x++
                    continue
                }
                val start = x
                while (x < SIZE - 1 && edge[y][x]) x++
                val end = x - 1
                if (end - start >= 1) {
                    result.add(
                        DrawStroke(
                            start.toFloat() / (SIZE - 1),
                            y.toFloat() / (SIZE - 1),
                            end.toFloat() / (SIZE - 1),
                            y.toFloat() / (SIZE - 1)
                        )
                    )
                    for (px in start..end) canvasPixels[y * SIZE + px] = Color.BLACK
                }
            }
        }
        output.setPixels(canvasPixels, 0, SIZE, 0, 0, SIZE, SIZE)
        return Pair(output, result)
    }
}
