# Pruebas

## Resultado de esta entrega · 28 de septiembre de 2026

Verificación local completada con `testDebugUnitTest lintDebug connectedDebugAndroidTest assembleDebug`:

- **44 pruebas JVM** superadas, 0 errores y 0 omitidas.
- **23 pruebas instrumentadas Android** superadas, 0 errores y 0 omitidas, en Medium Phone API 36.1 (Android 16).
- **Android Lint sin errores**. Quedan avisos no bloqueantes, principalmente versiones más recientes de dependencias, recursos y convenciones de estilo.
- APK de desarrollo generado. No se ha probado físicamente en un Galaxy A55 ni publicado en Google Play.

Las capturas en `docs/screenshots` salen de Compose ejecutándose en el emulador con datos de demostración exclusivos del APK de pruebas.

## JVM

`./gradlew testDebugUnitTest`: consumo ponderado y parciales, gasolina entre vehículos, deuda con pagos extra/céntimos/fechas de fin de mes, simulación inversa, ciclos por eje, mantenimiento vencido/desconocido, costes, cronología histórica, validaciones, sugerencias ITV y conversiones Room.

## Android

`./gradlew connectedDebugAndroidTest` con un emulador o dispositivo de pruebas:

- Room real en memoria: altas y modificaciones conservan referencias, transacciones inválidas no dejan cambios, restauración y cascadas.
- ZIP completo: todas las entidades y bytes de adjuntos, ruta nueva al restaurar, rechazo de traversal y checksums alterados.
- PDF real: cabecera, múltiples páginas renderizables y extracción de texto para comprobar privacidad (la extracción de texto se prueba desde API 35).
- Compose: pestañas, temas, historial, protección financiera, formularios y captura de pantalla con datos exclusivamente de prueba.

La app de producción empieza vacía. Las pruebas no leen datos personales del móvil y usan ficheros temporales o bases de datos en memoria.

## Comprobación manual antes de una publicación comercial

En el Galaxy A55: fotos/PDF con proveedores de documentos instalados, letra grande y modo claro, notificaciones con ahorro de batería, autenticación del dispositivo, widget, copias exportadas a otra ubicación y actualización conservando datos. Para Google Play se necesitarán firma de distribución estable, política de privacidad publicada, ficha de tienda y pruebas en más dispositivos/versiones. Esta entrega de desarrollo no afirma haber completado ese proceso.
