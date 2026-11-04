package app.deva.draw

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.Toast

class DrawingAccessibilityService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var floatingButton: Button? = null
    private val handler = Handler(Looper.getMainLooper())
    private var nextStroke = 0
    private var drawing = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        showFloatingButton()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        drawing = false
    }

    override fun onDestroy() {
        removeFloatingButton()
        super.onDestroy()
    }

    private fun showFloatingButton() {
        if (floatingButton != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val button = Button(this).apply {
            text = "DEVA ▶"
            textSize = 12f
            setOnClickListener { startDrawing() }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 18
            y = 180
        }
        try {
            windowManager?.addView(button, params)
            floatingButton = button
        } catch (_: Exception) {
            Toast.makeText(this, "No se pudo mostrar el botón flotante.", Toast.LENGTH_LONG).show()
        }
    }

    private fun removeFloatingButton() {
        try {
            floatingButton?.let { windowManager?.removeView(it) }
        } catch (_: Exception) { }
        floatingButton = null
    }

    private fun startDrawing() {
        if (drawing) {
            Toast.makeText(this, "DEVA ya está dibujando.", Toast.LENGTH_SHORT).show()
            return
        }
        val strokes = DrawData.strokes
        if (strokes.isEmpty()) {
            Toast.makeText(this, "Primero prepara una imagen en DEVA DRAW.", Toast.LENGTH_LONG).show()
            return
        }
        drawing = true
        nextStroke = 0
        Toast.makeText(this, "DEVA comenzó a dibujar.", Toast.LENGTH_SHORT).show()
        dispatchNextBatch(strokes)
    }

    private fun dispatchNextBatch(strokes: List<DrawStroke>) {
        if (!drawing) return
        if (nextStroke >= strokes.size) {
            drawing = false
            Toast.makeText(this, "Dibujo terminado.", Toast.LENGTH_LONG).show()
            return
        }

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()
        val left = width * 0.08f
        val right = width * 0.92f
        val top = height * 0.20f
        val bottom = height * 0.78f

        val builder = GestureDescription.Builder()
        var count = 0
        while (nextStroke < strokes.size && count < 10) {
            val stroke = strokes[nextStroke]
            val path = Path().apply {
                moveTo(left + stroke.x1 * (right - left), top + stroke.y1 * (bottom - top))
                lineTo(left + stroke.x2 * (right - left), top + stroke.y2 * (bottom - top))
            }
            builder.addStroke(
                GestureDescription.StrokeDescription(path, count * 90L, 70L)
            )
            nextStroke++
            count++
        }

        val callback = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                handler.post { dispatchNextBatch(strokes) }
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                drawing = false
                Toast.makeText(this@DrawingAccessibilityService, "Dibujo interrumpido.", Toast.LENGTH_LONG).show()
            }
        }

        if (!dispatchGesture(builder.build(), callback, handler)) {
            drawing = false
            Toast.makeText(this, "Android no aceptó el gesto. Inténtalo otra vez.", Toast.LENGTH_LONG).show()
        }
    }
}
