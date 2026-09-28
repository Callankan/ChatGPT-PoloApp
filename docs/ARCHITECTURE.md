# Arquitectura

Un módulo Android Gradle, organizado en capas por responsabilidad. UI declarativa en Compose; ViewModel con StateFlow; repositorio; Room como única fuente persistente. Inyección manual a través de `PoloApplication`. Sin servidores ni SDK de analítica.

## Modelo persistente

- `Vehicle`: identidad, datos del vehículo, compra, préstamo inicial y especificaciones.
- `CarRecord`: identificador UUID, vehículo, tipo, día, título, notas, odómetro opcional e importe en céntimos. El mapa `details` contiene atributos específicos de cada tipo, serializados como JSON por un `TypeConverter`.
- `Workshop`: ficha reutilizable del taller.
- `MaintenanceRule`: operación, intervalos, referencia inicial, márgenes y activación.
- `Attachment`: vínculo al registro y fichero privado. Las fotos se copian al almacenamiento interno, nunca se depende de una URI temporal de la galería.

Claves foráneas en registros, reglas y adjuntos; índices por vehículo, fecha y registro. Los pagos, litros, viajes, pólizas y piezas permanecen vinculados a su vehículo. El esquema inicial es versión 1 y está exportado; cualquier evolución estructural debe añadir migración explícita y prueba de migración. Los atributos de `details` y el ZIP tienen un contrato estable documentado por sus validadores y pruebas.

## Invariantes

- Importes enteros en céntimos; entrada decimal con coma o punto; BigDecimal para conversiones de formularios y última cuota ajustada al céntimo.
- Días locales `LocalDate.toEpochDay()`, sin conversiones de zona horaria para vencimientos.
- Repostajes y operaciones necesitan odómetro. Lecturas históricas permitidas; ningún día posterior puede tener una lectura inferior al máximo del día anterior. Dentro del mismo día se admiten varias lecturas porque el formulario no pide hora.
- Consumo: primer lleno establece el punto de partida, los parciales se acumulan, el siguiente lleno cierra el intervalo. Media global ponderada por distancia; combustible/precios nunca se mezclan entre vehículos.
- Intervenciones de neumáticos/frenos en ambos ejes se desdoblan lógicamente para sus ciclos. Sustituir un eje no retira el otro.
- Cada mutación valida el estado candidato dentro de una transacción. `@Upsert` conserva hijos al editar padres. Restauraciones inválidas dejan el historial anterior intacto.
- Las reglas iniciales se crean una sola vez, sin inventar la fecha de la última revisión.

## Operaciones con archivos

El selector de documentos de Android entrega archivos al ViewModel, que valida formato/tamaño, copia a `filesDir/attachments`, y guarda sus vínculos junto al registro en una transacción. Al editar o borrar se limpian solo archivos internos asociados. PDF y copias se escriben en la ubicación elegida mediante Storage Access Framework: no necesitan permiso general de almacenamiento.

El ZIP incluye un manifiesto versionado y hashes SHA-256 para cada adjunto. La importación limita tamaños/entradas, rechaza traversal y ficheros corruptos, extrae a un directorio nuevo y solo entonces reemplaza Room dentro de una transacción. Un fallo anterior al commit limpia los archivos importados; nunca borra adjuntos de una restauración ya confirmada.

## Privacidad y recordatorios

El PDF utiliza una lista explícita de tipos exportables. La financiación, notas privadas, pólizas y guantera nunca forman parte del informe para compradores. Los datos identificativos y precios tienen opciones separadas; los textos libres/fotos requieren revisión humana.

La protección de financiación usa BiometricPrompt con credencial del dispositivo como alternativa. Se bloquea al salir de la actividad, oculta el resumen de deuda y sus pagos en el historial global, y protege capturas de la vista financiera. No pretende cifrar la base de datos ni los ZIP.

WorkManager mantiene un único trabajo periódico y un trabajo de comprobación después de las ediciones. Deduplica notificaciones por vehículo, vencimiento/ciclo y margen. El widget consulta Room y muestra el vehículo seleccionado.
