# DEVA DRAW

Aplicación Android experimental que convierte una imagen en trazos y los reproduce mediante el servicio de accesibilidad de Android.

## Estado actual

Primera versión MVP:
- Importar una imagen desde el almacenamiento del teléfono.
- Convertir bordes de la imagen en segmentos negros.
- Mostrar una vista previa del resultado.
- Activar un servicio de accesibilidad que permite reproducir gestos.
- Mostrar un botón flotante **DEVA ▶** para iniciar el dibujo en otra aplicación.
- Compilar un APK de depuración automáticamente con GitHub Actions.

## Compilar

El workflow **Build DEVA APK** se ejecuta al hacer push a `main` o manualmente desde la pestaña Actions. Descarga el APK en los artefactos de la ejecución.

## Uso en Android

1. Instala el APK.
2. Abre DEVA DRAW y selecciona una imagen.
3. Pulsa **Activar servicio de dibujo** y habilita DEVA DRAW en Accesibilidad.
4. Abre Gartic Phone y entra al lienzo.
5. Pulsa el botón flotante **DEVA ▶**.

## Limitaciones de esta versión

El área de dibujo está configurada inicialmente entre el 8% y el 92% del ancho y entre el 20% y el 78% de la altura de la pantalla. La ubicación real del lienzo puede variar según el dispositivo, la orientación y la interfaz de Gartic Phone. Esta versión requiere pruebas y calibración antes de poder garantizar un encaje correcto.

El servicio de accesibilidad solo ejecuta gestos cuando el usuario pulsa el botón flotante. El usuario debe habilitarlo manualmente desde los ajustes de Android.
