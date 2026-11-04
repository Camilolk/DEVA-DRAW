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
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.ArrayDeque

class DrawingAccessibilityService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var controlPanel: LinearLayout? = null
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
        if (event.packageName?.toString() == packageName) return

        val detectedPackage = findGarticPackage()
        val changedApp = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.packageName?.toString() != targetPackage
        if (detectedPackage == null || changedApp) {
            stopDrawing("DEVA se detuvo: saliste de Gartic Phone.")
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
            text = "DEVA DRAW"
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        statusLabel = TextView(this).apply {
            text = "Esperando Gartic Phone"
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
            gravity = Gravity.TOP or Gravity.END
            x = 12
            y = 150
        }
        try {
            windowManager?.addView(panel, params)
            controlPanel = panel
        } catch (_: Exception) {
            Toast.makeText(this, "No se pudo mostrar el control de DEVA.", Toast.LENGTH_LONG).show()
        }
    }

    private fun removeControlPanel() {
        try {
            controlPanel?.let { windowManager?.removeView(it) }
        } catch (_: Exception) { }
        controlPanel = null
    }

    private fun findGarticPackage(): String? {
        val activeWindows = try { getWindows() } catch (_: Exception) { emptyList() }
        for (window in activeWindows) {
            val root = try { window.root } catch (_: Exception) { null } ?: continue
            if (root.packageName?.toString() == packageName) continue
            if (treeContainsGartic(root)) return root.packageName?.toString()
        }
        val root = try { rootInActiveWindow } catch (_: Exception) { null }
        if (root != null && root.packageName?.toString() != packageName && treeContainsGartic(root)) {
            return root.packageName?.toString()
        }
        return null
    }

    private fun treeContainsGartic(root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var inspected = 0
        while (queue.isNotEmpty() && inspected < 350) {
            val node = queue.removeFirst()
            inspected++
            val text = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
            if (text.contains("gartic phone", ignoreCase = true) ||
                text.contains("garticphone.com", ignoreCase = true)
            ) return true
            for (i in 0 until node.childCount) {
                val child = try { node.getChild(i) } catch (_: Exception) { null }
                if (child != null) queue.add(child)
            }
        }
        return false
    }

    private fun startDrawing() {
        if (drawing) {
            updateStatus("DEVA ya está dibujando")
            return
        }
        val activePackage = findGarticPackage()
        if (activePackage == null) {
            updateStatus("Abre Gartic Phone primero")
            Toast.makeText(this, "No detecto Gartic Phone. Abre el lienzo antes de iniciar.", Toast.LENGTH_LONG).show()
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
        updateStatus("Dibujando… pulsa Detener")
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
        if (findGarticPackage() != targetPackage) {
            stopDrawing("DEVA se detuvo porque Gartic Phone ya no está activo.")
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
