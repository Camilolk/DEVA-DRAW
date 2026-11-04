package app.deva.draw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import java.util.ArrayDeque

object ImageProcessor {
    private const val SIZE = 160
    private const val LOW_THRESHOLD = 65f
    private const val HIGH_THRESHOLD = 125f

    fun process(source: Bitmap): Pair<Bitmap, List<DrawStroke>> {
        val fitted = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(fitted)
        canvas.drawColor(Color.WHITE)
        val scale = min(SIZE.toFloat() / source.width, SIZE.toFloat() / source.height)
        val width = max(1, (source.width * scale).roundToInt())
        val height = max(1, (source.height * scale).roundToInt())
        val scaled = Bitmap.createScaledBitmap(source, width, height, true)
        val left = (SIZE - width) / 2f
        val top = (SIZE - height) / 2f
        canvas.drawBitmap(scaled, left, top, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

        val gray = Array(SIZE) { y ->
            IntArray(SIZE) { x ->
                val color = fitted.getPixel(x, y)
                (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000
            }
        }

        val magnitude = Array(SIZE) { FloatArray(SIZE) }
        val direction = Array(SIZE) { FloatArray(SIZE) }
        for (y in 1 until SIZE - 1) {
            for (x in 1 until SIZE - 1) {
                val gx = -gray[y - 1][x - 1] + gray[y - 1][x + 1] -
                    2 * gray[y][x - 1] + 2 * gray[y][x + 1] -
                    gray[y + 1][x - 1] + gray[y + 1][x + 1]
                val gy = -gray[y - 1][x - 1] - 2 * gray[y - 1][x] - gray[y - 1][x + 1] +
                    gray[y + 1][x - 1] + 2 * gray[y + 1][x] + gray[y + 1][x + 1]
                magnitude[y][x] = sqrt((gx * gx + gy * gy).toFloat())
                direction[y][x] = atan2(gy.toFloat(), gx.toFloat())
            }
        }

        val thin = Array(SIZE) { BooleanArray(SIZE) }
        for (y in 1 until SIZE - 1) {
            for (x in 1 until SIZE - 1) {
                val angle = Math.toDegrees(direction[y][x].toDouble())
                val a = ((angle % 180.0) + 180.0) % 180.0
                val (dx, dy) = when {
                    a < 22.5 || a >= 157.5 -> Pair(1, 0)
                    a < 67.5 -> Pair(1, -1)
                    a < 112.5 -> Pair(0, 1)
                    else -> Pair(1, 1)
                }
                val value = magnitude[y][x]
                if (value >= LOW_THRESHOLD &&
                    value >= magnitude[y + dy][x + dx] &&
                    value >= magnitude[y - dy][x - dx]
                ) thin[y][x] = true
            }
        }

        val edges = Array(SIZE) { BooleanArray(SIZE) }
        val visited = Array(SIZE) { BooleanArray(SIZE) }
        val queue = ArrayDeque<Pair<Int, Int>>()
        for (y in 1 until SIZE - 1) {
            for (x in 1 until SIZE - 1) {
                if (!thin[y][x] || visited[y][x] || magnitude[y][x] < HIGH_THRESHOLD) continue
                visited[y][x] = true
                queue.add(Pair(x, y))
                while (queue.isNotEmpty()) {
                    val point = queue.removeFirst()
                    val px = point.first
                    val py = point.second
                    edges[py][px] = true
                    for (oy in -1..1) for (ox in -1..1) {
                        val nx = px + ox
                        val ny = py + oy
                        if (nx in 1 until SIZE - 1 && ny in 1 until SIZE - 1 &&
                            thin[ny][nx] && !visited[ny][nx] &&
                            magnitude[ny][nx] >= LOW_THRESHOLD
                        ) {
                            visited[ny][nx] = true
                            queue.add(Pair(nx, ny))
                        }
                    }
                }
            }
        }

        val strokes = traceContours(edges)
        val preview = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val previewCanvas = Canvas(preview)
        previewCanvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1.35f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (stroke in strokes) {
            if (stroke.points.size < 2) continue
            val path = android.graphics.Path()
            path.moveTo(stroke.points.first().x * (SIZE - 1), stroke.points.first().y * (SIZE - 1))
            stroke.points.drop(1).forEach { path.lineTo(it.x * (SIZE - 1), it.y * (SIZE - 1)) }
            previewCanvas.drawPath(path, paint)
        }
        return Pair(preview, strokes)
    }

    private fun traceContours(edges: Array<BooleanArray>): List<DrawStroke> {
        val size = edges.size
        val seen = Array(size) { BooleanArray(size) }
        val result = ArrayList<DrawStroke>()
        val componentQueue = ArrayDeque<Pair<Int, Int>>()

        for (y in 1 until size - 1) {
            for (x in 1 until size - 1) {
                if (!edges[y][x] || seen[y][x]) continue
                val component = ArrayList<Pair<Int, Int>>()
                seen[y][x] = true
                componentQueue.add(Pair(x, y))
                while (componentQueue.isNotEmpty()) {
                    val p = componentQueue.removeFirst()
                    component.add(p)
                    for (oy in -1..1) for (ox in -1..1) {
                        val nx = p.first + ox
                        val ny = p.second + oy
                        if (nx in 1 until size - 1 && ny in 1 until size - 1 &&
                            edges[ny][nx] && !seen[ny][nx]
                        ) {
                            seen[ny][nx] = true
                            componentQueue.add(Pair(nx, ny))
                        }
                    }
                }
                if (component.size < 3) continue

                val remaining = HashSet<Int>()
                component.forEach { remaining.add(it.second * size + it.first) }
                while (remaining.isNotEmpty()) {
                    val startKey = remaining.minByOrNull { key ->
                        val px = key % size
                        val py = key / size
                        py * size + px
                    } ?: break
                    var current = startKey
                    val points = ArrayList<DrawPoint>()
                    while (remaining.remove(current)) {
                        val cx = current % size
                        val cy = current / size
                        points.add(DrawPoint(cx.toFloat() / (size - 1), cy.toFloat() / (size - 1)))
                        var next = -1
                        loop@ for (radius in 1..1) {
                            for (oy in -radius..radius) for (ox in -radius..radius) {
                                if (ox == 0 && oy == 0) continue
                                val nx = cx + ox
                                val ny = cy + oy
                                val key = ny * size + nx
                                if (nx in 1 until size - 1 && ny in 1 until size - 1 && key in remaining) {
                                    next = key
                                    break@loop
                                }
                            }
                        }
                        if (next < 0) break
                        current = next
                    }
                    if (points.size >= 2) result.add(DrawStroke(points))
                }
            }
        }
        return result
    }
}
