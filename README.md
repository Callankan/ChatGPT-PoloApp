# Polo App

Un garaje personal para Android. Kotlin + Jetpack Compose, diseño grafito con acentos rojos y todo el historial guardado localmente con Room.

Pensada inicialmente para un Volkswagen Polo Mk5 1.2 TSI de 90 CV, matriculado en junio de 2013. Permite registrar otros vehículos, con sus historiales y financiación separados. La aplicación empieza vacía: no contiene datos personales ni registros de demostración.

## Instalar y probar

- Android 8.0 o superior, incluido Samsung Galaxy A55.
- En [Actions](https://github.com/Callankan/ChatGPT-PoloApp/actions), abre la última ejecución correcta de **Android · Build & checks** y descarga el artefacto **Polo-App-debug**. Descomprime el ZIP e instala el APK en tu móvil. GitHub puede pedir iniciar sesión para descargar artefactos.
- Esta es una primera versión instalable de desarrollo (`0.1.0`), no una publicación en Google Play. Android pedirá permitir la instalación desde la aplicación desde la que abras el APK.
- Para empezar, registra el coche y el kilometraje inicial. Configura tu préstamo, los vencimientos y los datos del último mantenimiento. Activa las notificaciones en **Garaje → Ajustes**.
- Los APK de distintas compilaciones de desarrollo pueden usar claves distintas. Antes de desinstalar para cambiar de versión, exporta una copia completa.

## Lo que incluye

| Área | Funciones |
| --- | --- |
| Inicio | Kilometraje, ITV y seguro, mantenimiento, estado orientativo del cuidado, deuda, accesos rápidos y actividad reciente. |
| Repostajes | Fecha, km, litros, precio y total: introduce dos importes y se calcula el tercero. Depósito lleno/parcial, gasolinera y combustible. Consumo real de lleno a lleno, media ponderada y gráfica. |
| Taller | Intervenciones propias o en taller; agenda con contacto y valoración; coste de piezas y mano de obra; fotos/facturas; ciclos de piezas y neumáticos por eje; reglas por km o meses y antelación editable. |
| Finanzas | Deuda sin intereses, pagos irregulares, saldo y progreso. Simulación por cuota o fecha objetivo, pago extra y calendario completo. Bloqueo opcional con huella o credencial del dispositivo. |
| Viajes y notas | Km de salida/llegada, propósito, observaciones, peajes y estimación de combustible; notas fijadas y recordatorios. |
| ITV y seguro | Historial, vencimientos, resultado ITV, defectos, compañía, póliza, cobertura y llamada a asistencia. Avisos 30, 7 y 1 día antes, al vencer y si ya está vencido. |
| Neumáticos y daños | Presión por eje y fecha, medición en frío, zona y gravedad del daño, estado de reparación y fotos. |
| Gastos | Categorías, gasto mensual, distribución y coste por km. Los pagos del préstamo no se duplican como coste de uso. |
| Guantera y ficha | Documentos locales, especificaciones introducidas desde el manual, medidas, presiones, aceite, motor y bombillas. |
| PDF | Informe de reventa paginado con kilometraje, talleres, mantenimientos, piezas, ITV, daños y fotografías opcionales. Opciones para precios y datos identificativos. Excluye siempre financiación, notas, pólizas y documentos privados. |
| Copias y widget | ZIP completo con vehículos, registros y adjuntos; restauración validada y transaccional. Widget del vehículo seleccionado con km, ITV y mantenimiento. |
| Apariencia | Oscuro, claro o sistema; Manrope y Space Grotesk incluidas, ilustración original del coche en Compose Canvas, transiciones y estados vacíos. |

## Compilar

Requisitos: JDK 17 o 21 y Android SDK 36. Las versiones están fijadas para reproducir la compilación: AGP 8.12.0, Gradle 8.14.3, Kotlin 2.2.21, Compose BOM 2025.08.00 y Room 2.7.2.

```bash
# local.properties: sdk.dir=/ruta/al/Android/Sdk
./gradlew testDebugUnitTest lintDebug assembleDebug
# Con un emulador/dispositivo Android iniciado:
./gradlew connectedDebugAndroidTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Las compilaciones de GitHub Actions ejecutan pruebas unitarias y lint y publican el APK como artefacto. Las pruebas instrumentadas se ejecutan localmente con un emulador.

## Código y arquitectura

```text
app/src/main/java/com/poloapp/
├── data/          Entidades Room, DAO, base de datos y repositorio transaccional
├── domain/        Consumo, deuda, mantenimiento, costes y validación sin Android
├── ui/
│   ├── screens/   Inicio, Repostajes, Taller, Finanzas y Garaje
│   ├── forms/     Edición de registros, vehículo, reglas y simulador
│   ├── components/  Tarjetas, gráficas e ilustración nativa
│   └── theme/     Colores y tipografía
├── platform/      PDF, ZIP, WorkManager y widget
├── PoloViewModel.kt  Estado observable y coordinación de operaciones
└── MainActivity.kt   Compose y autenticación del dispositivo
```

Más detalles en [arquitectura](docs/ARCHITECTURE.md), [privacidad](docs/PRIVACY.md) y [criterios de prueba](docs/TESTING.md). Los esquemas Room se versionan en `app/schemas`; no se usan migraciones destructivas.

## Criterios importantes

- Los kilómetros son **registrados por el propietario**, no certificados. El PDF lo explica expresamente.
- El consumo necesita dos llenados completos y una distancia válida; acumula los repostajes parciales intermedios. No inventa resultados cuando faltan datos.
- Los intervalos de mantenimiento precargados son propuestas editables, **no un plan oficial Volkswagen**. Debes confirmar motor, aceite, distribución e intervalos con la documentación de tu coche. Sin una intervención o referencia conocida no se muestra un contador como válido.
- La sugerencia de ITV corresponde a turismos particulares M1 en España, conforme al [artículo 6 del RD 920/2017](https://www.boe.es/buscar/act.php?id=BOE-A-2017-12841#a6). La fecha del informe de inspección prevalece; hay excepciones por uso/clasificación y renovación anticipada. Los resultados desfavorables/negativos exigen seleccionar expresamente su próxima fecha.
- La puntuación de cuidado es orientativa y depende de los registros conocidos: no es un diagnóstico mecánico. Las predicciones dependen del ritmo de km registrado.
- WorkManager ofrece avisos diferidos, no alarmas exactas. Android puede retrasarlos por ahorro de batería. La app solo conoce los km que registras.
- Las copias ZIP incluyen deuda y datos privados y **no están cifradas**. No las subas al repositorio. Desinstalar borra el historial local: exporta una copia antes.
- Los interruptores del PDF controlan los campos estructurados. Las fotos y el texto libre no se censuran automáticamente; revisa el documento antes de compartirlo.

Tipografías bajo SIL Open Font License: [Manrope](docs/Manrope-OFL.txt) y [Space Grotesk](docs/SpaceGrotesk-OFL.txt). La ilustración y el icono son gráficos originales. La fotografía de referencia no se redistribuye. Polo App es un proyecto independiente, sin afiliación con Volkswagen.
