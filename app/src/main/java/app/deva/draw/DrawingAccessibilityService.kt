package app.deva.draw

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.ArrayDeque

class DrawingAccessibilityService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var controlPanel: LinearLayout? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var statusLabel: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var nextStroke = 0
    @Volatile private var drawing = false
    private var targetPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        showControlPanel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!drawing || event == null) return
        val eventPackage = event.packageName?.toString() ?: return
        if (isIgnoredPackage(eventPackage)) return

        // La página de Gartic suele estar dentro de Chrome/Firefox y no siempre
        // expone su título al árbol de accesibilidad. La sesión se inicia
        // manualmente desde el panel y se vigila que no cambie la app.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventPackage != targetPackage
        ) {
            stopDrawing("DEVA se detuvo porque cambiaste de aplicación.")
        }
    }

    override fun onInterrupt() {
        stopDrawing("Servicio interrumpido.")
    }

    override fun onDestroy() {
        stopDrawing(null)
        removeControlPanel()
        super.onDestroy()
    }

    private fun showControlPanel() {
        if (controlPanel != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 8, 12, 8)
            setBackgroundColor(0xEE18202E.toInt())
        }
        val title = TextView(this).apply {
            text = "DEVA DRAW  ⋮⋮"
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 8)
        }
        statusLabel = TextView(this).apply {
            text = "Abre Gartic y entra al lienzo"
            textSize = 10f
            setTextColor(0xFFD5DCEC.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 6)
        }
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val start = Button(this).apply {
            text = "Iniciar"
            textSize = 11f
            isAllCaps = false
            setOnClickListener { startDrawing() }
        }
        val stop = Button(this).apply {
            text = "Detener"
            textSize = 11f
            isAllCaps = false
            setOnClickListener { stopDrawing("Dibujo detenido manualmente.") }
        }
        buttons.addView(start, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(stop, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        panel.addView(title)
        panel.addView(statusLabel)
        panel.addView(buttons)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 12
            y = 150
        }
        title.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = startX + (event.rawX - touchX).toInt()
                        params.y = startY + (event.rawY - touchY).toInt()
                        try { windowManager?.updateViewLayout(panel, params) } catch (_: Exception) { }
                        return true
                    }
                }
                return event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL
            }
        })
        try {
            windowManager?.addView(panel, params)
            controlPanel = panel
            panelParams = params
        } catch (_: Exception) {
            Toast.makeText(this, "No se pudo mostrar el control de DEVA.", Toast.LENGTH_LONG).show()
        }
    }

    private fun removeControlPanel() {
        try {
            controlPanel?.let { windowManager?.removeView(it) }
        } catch (_: Exception) { }
        controlPanel = null
        panelParams = null
    }

    private fun isIgnoredPackage(value: String): Boolean {
        return value == packageName ||
            value == "com.android.systemui" ||
            value == "com.android.settings" ||
            value.contains("inputmethod", ignoreCase = true) ||
            value.contains("keyboard", ignoreCase = true)
    }

    private fun findForegroundPackage(): String? {
        val activeRoot = try { rootInActiveWindow } catch (_: Exception) { null }
        val activeName = activeRoot?.packageName?.toString()
        if (!activeName.isNullOrBlank() && !isIgnoredPackage(activeName)) return activeName

        val activeWindows = try { getWindows() } catch (_: Exception) { emptyList() }
        for (window in activeWindows) {
            if (!window.isActive && !window.isFocused) continue
            val root = try { window.root } catch (_: Exception) { null } ?: continue
            val name = root.packageName?.toString()
            if (!name.isNullOrBlank() && !isIgnoredPackage(name)) return name
        }
        return null
    }

    private fun startDrawing() {
        if (drawing) {
            updateStatus("DEVA ya está dibujando")
            return
        }
        val activePackage = findForegroundPackage()
        if (activePackage == null) {
            updateStatus("Abre Gartic en el navegador")
            Toast.makeText(this, "Abre Gartic Phone en el navegador, entra al lienzo y vuelve a pulsar Iniciar.", Toast.LENGTH_LONG).show()
            return
        }
        val strokes = DrawData.strokes
        if (strokes.isEmpty()) {
            Toast.makeText(this, "Primero prepara una imagen en DEVA DRAW.", Toast.LENGTH_LONG).show()
            return
        }
        targetPackage = activePackage
        drawing = true
        nextStroke = 0
        updateStatus("Sesión activa · pulsa Detener")
        Toast.makeText(this, "Sesión iniciada en la app actual. Asegúrate de estar en el lienzo de Gartic.", Toast.LENGTH_SHORT).show()
        dispatchNextBatch(strokes)
    }

    private fun stopDrawing(message: String?) {
        drawing = false
        targetPackage = null
        updateStatus("En pausa")
        if (message != null) Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun updateStatus(message: String) {
        handler.post { statusLabel?.text = message }
    }

    private fun dispatchNextBatch(strokes: List<DrawStroke>) {
        if (!drawing) return
        if (findForegroundPackage() != targetPackage) {
            stopDrawing("DEVA se detuvo porque cambiaste de aplicación.")
            return
        }
        if (nextStroke >= strokes.size) {
            drawing = false
            targetPackage = null
            updateStatus("Dibujo terminado")
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
        while (nextStroke < strokes.size && count < 3) {
            val stroke = strokes[nextStroke]
            if (stroke.points.size >= 2) {
                val path = Path()
                val first = stroke.points.first()
                path.moveTo(left + first.x * (right - left), top + first.y * (bottom - top))
                stroke.points.drop(1).forEach { point ->
                    path.lineTo(left + point.x * (right - left), top + point.y * (bottom - top))
                }
                builder.addStroke(GestureDescription.StrokeDescription(path, count * 70L, 65L))
                count++
            }
            nextStroke++
        }

        if (count == 0) {
            handler.post { dispatchNextBatch(strokes) }
            return
        }

        val callback = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                handler.post {
                    if (drawing) dispatchNextBatch(strokes)
                }
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                stopDrawing("Dibujo interrumpido.")
            }
        }

        if (!dispatchGesture(builder.build(), callback, handler)) {
            stopDrawing("Android rechazó el gesto. Inténtalo otra vez.")
        }
    }
}
