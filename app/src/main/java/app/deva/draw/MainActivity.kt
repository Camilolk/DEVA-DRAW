package app.deva.draw

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var preview: ImageView
    private lateinit var status: TextView

    private val pickImage = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        val processed = ImageProcessor.process(bitmap)
                        DrawData.preview = processed.first
                        DrawData.strokes = processed.second
                        preview.setImageBitmap(processed.first)
                        status.text = "Imagen preparada: " + processed.second.size + " trazos. Activa el servicio y abre Gartic Phone."
                    }
                }
            } catch (e: Exception) {
                status.text = "No se pudo procesar la imagen: " + (e.message ?: "error")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(0xFF10131B.toInt())
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val title = TextView(this).apply {
            text = "DEVA DRAW"
            textSize = 30f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "Convierte imágenes en trazos para dibujarlos en Android"
            textSize = 14f
            setTextColor(0xFFB8C0D4.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 22)
        }
        preview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 620
            )
        }
        status = TextView(this).apply {
            text = "Primero selecciona una imagen."
            textSize = 14f
            setTextColor(0xFFE0E5F2.toInt())
            setPadding(0, 18, 0, 18)
        }

        content.addView(title)
        content.addView(subtitle)
        content.addView(makeButton("1. Elegir imagen") { pickImage.launch("image/*") })
        content.addView(preview)
        content.addView(status)
        content.addView(makeButton("2. Activar servicio de dibujo") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        content.addView(makeButton("3. Cómo dibujar en Gartic") {
            Toast.makeText(
                this,
                "Abre Gartic Phone, entra al lienzo y pulsa el botón flotante DEVA ▶. Mantén visible el lienzo.",
                Toast.LENGTH_LONG
            ).show()
        })
        content.addView(TextView(this).apply {
            text = "Aviso: esta versión inicial usa un área aproximada de pantalla. Puede requerir ajustes según el tamaño y la posición del lienzo."
            textSize = 12f
            setTextColor(0xFF9AA4B8.toInt())
            setPadding(0, 18, 0, 12)
        })
        root.addView(content)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun makeButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 }
        }
    }
}
