package com.poloapp.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poloapp.MainActivity
import com.poloapp.PoloViewModel
import com.poloapp.data.*
import com.poloapp.domain.CarCalculations
import com.poloapp.platform.PdfOptions
import com.poloapp.ui.components.PoloArtwork
import com.poloapp.ui.forms.*
import com.poloapp.ui.screens.MainScreens
import com.poloapp.ui.theme.PoloTheme
import java.io.File
import java.time.LocalDate
import java.util.UUID

@Composable
fun PoloRoot(model: PoloViewModel, financeUnlocked: Boolean, authenticate: (() -> Unit) -> Unit, lockFinance: () -> Unit) {
    val snapshot by model.snapshot.collectAsStateWithLifecycle()
    val ready by model.ready.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val notice by model.notice.collectAsStateWithLifecycle()
    val selected by model.selectedVehicleId.collectAsStateWithLifecycle()
    val theme by model.theme.collectAsStateWithLifecycle()
    val financeLock by model.financeLock.collectAsStateWithLifecycle()
    val pendingRestore by model.pendingRestore.collectAsStateWithLifecycle()
    val vehicle = snapshot.vehicles.firstOrNull { it.id == selected } ?: snapshot.vehicles.firstOrNull()
    var section by rememberSaveable { mutableStateOf("home") }
    var action by rememberSaveable { mutableStateOf<String?>(null) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var newKind by rememberSaveable { mutableStateOf<String?>(null) }
    var newId by rememberSaveable { mutableStateOf<String?>(null) }
    var workshopId by rememberSaveable { mutableStateOf<String?>(null) }
    var ruleId by rememberSaveable { mutableStateOf<String?>(null) }
    var pdfPrices by rememberSaveable { mutableStateOf(true) }
    var pdfPersonal by rememberSaveable { mutableStateOf(false) }
    var pdfPhotos by rememberSaveable { mutableStateOf(false) }
    var pdfVehicleId by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) pdfVehicleId?.let { model.exportPdf(uri, it, PdfOptions(pdfPrices, pdfPersonal, pdfPhotos)) }
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if (uri != null) model.exportBackup(uri) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) model.prepareRestore(uri) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        model.notice.value = if (granted) "Avisos activados. Android puede retrasarlos por ahorro de batería." else "Puedes activar las notificaciones desde los ajustes de Android."
    }
    fun closeEditor() { editId = null; newKind = null; newId = null; model.clearError() }
    fun dismissAction() { action = null; workshopId = null; ruleId = null; model.clearError() }
    fun add(kind: RecordKind) {
        if (kind == RecordKind.PAYMENT && financeLock && !financeUnlocked) { authenticate { newKind = kind.name; newId = UUID.randomUUID().toString() }; return }
        model.clearError(); editId = null; newKind = kind.name; newId = UUID.randomUUID().toString()
    }
    fun openAction(value: String) {
        model.clearError()
        when (value) {
            "odometer" -> add(RecordKind.ODOMETER)
            "unlockFinance" -> authenticate { section = "finance" }
            "debt", "simulator" -> if (financeLock && !financeUnlocked) authenticate { action = value } else action = value
            "backup" -> if (financeLock && !financeUnlocked) authenticate { action = "backup" } else action = "backup"
            "restore" -> restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
            "assistance" -> {
                val phone = vehicle?.let { v -> snapshot.recordsFor(v.id).firstOrNull { it.kind == RecordKind.INSURANCE }?.text("assistancePhone") }
                if (phone.isNullOrBlank()) model.reportError("Añade el teléfono de asistencia en el registro de tu seguro.")
                else try { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))) } catch (_: Exception) { model.reportError("No se ha podido abrir el teléfono. Asistencia: $phone") }
            }
            else -> action = value
        }
    }
    LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it); model.notice.value = null } }
    LaunchedEffect(error, action, editId, newKind) { if (action == null && editId == null && newKind == null) error?.let { snackbar.showSnackbar(it); model.clearError() } }
    val locked = financeLock && !financeUnlocked
    val financialView = section == "finance" || action in listOf("debt", "simulator") || newKind == RecordKind.PAYMENT.name || snapshot.records.firstOrNull { it.id == editId }?.kind == RecordKind.PAYMENT
    DisposableEffect(financialView, financeLock) {
        val window = (context as? MainActivity)?.window
        if (financialView && financeLock) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    BackHandler(enabled = section != "home" && action == null && editId == null && newKind == null) { section = "home" }
    PoloTheme(theme) {
        val darkBars = when (theme) { "light" -> false; "system" -> androidx.compose.foundation.isSystemInDarkTheme(); else -> true }
        SideEffect {
            (context as? MainActivity)?.window?.let { window ->
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkBars
                    isAppearanceLightNavigationBars = !darkBars
                }
            }
        }
        Box(Modifier.fillMaxSize()) {
            when {
                !ready -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                vehicle == null -> WelcomeScreen(onCreate = { action = "addVehicle" }, onRestore = { openAction("restore") })
                financialView && locked -> Surface(Modifier.fillMaxSize()) { Column(Modifier.safeDrawingPadding().fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("Tu financiación es privada", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(18.dp)); Text("Usa la huella o el bloqueo del móvil para consultar tus pagos.")
                    Spacer(Modifier.height(24.dp)); Button(onClick = { authenticate {} }) { Text("Desbloquear") }
                    TextButton(onClick = { section = "home"; dismissAction(); closeEditor() }) { Text("Volver al inicio") }
                } }
                else -> MainScreens(snapshot, vehicle, section, { target ->
                    if (target == "finance" && locked) authenticate { section = target } else section = target
                }, ::add, { record ->
                    if (record.kind == RecordKind.PAYMENT && locked) authenticate { editId = record.id }
                    else { model.clearError(); newKind = null; editId = record.id }
                }, ::openAction, financeLocked = locked)
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (vehicle != null) 82.dp else 12.dp))
            if (busy && action == null && editId == null && newKind == null) LinearProgressIndicator(Modifier.fillMaxWidth().statusBarsPadding())
        }
        val currentRecord = editId?.let { id -> snapshot.records.firstOrNull { it.id == id } }
            ?: if (newKind != null && vehicle != null && newId != null) {
                val kind = RecordKind.valueOf(newKind!!)
                val currentKm = CarCalculations.currentKm(vehicle, snapshot.recordsFor(vehicle.id))
                CarRecord(id = newId!!, vehicleId = vehicle.id, kind = kind, odometer = currentKm,
                    details = if (kind == RecordKind.TRIP) mapOf("startKm" to currentKm.toString()) else emptyMap())
            } else null
        if (currentRecord != null && !(currentRecord.kind == RecordKind.PAYMENT && locked)) {
            key(currentRecord.id) { RecordEditor(currentRecord, editId == null, snapshot, busy, error, ::closeEditor,
                { record, uris, kept -> model.saveRecord(record, uris, kept, ::closeEditor) },
                { model.deleteRecord(currentRecord, ::closeEditor) }, { attachment ->
                    try {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(attachment.localPath))
                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, attachment.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    } catch (_: Exception) { model.reportError("No se puede abrir este adjunto. Comprueba que tienes una app para ese formato.") }
                }) }
        }
        if (!(locked && action in listOf("debt", "simulator", "backup"))) when (action) {
            "vehicle", "addVehicle" -> key(action) { VehicleEditor(if (action == "vehicle") vehicle else null, busy, error, ::dismissAction) { model.saveVehicle(it, ::dismissAction) } }
            "switchVehicle" -> FormPage("Mi garaje", "Un historial independiente para cada vehículo.", onDismiss = ::dismissAction) {
                snapshot.vehicles.forEach { car -> ElevatedCard(onClick = { model.selectVehicle(car.id); section = "home"; dismissAction() }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) { Text(car.name, style = MaterialTheme.typography.titleLarge); Text("${car.make} ${car.model}") } } }
                Button(onClick = { action = "addVehicle" }, modifier = Modifier.fillMaxWidth()) { Text("Añadir vehículo") }
            }
            "debt" -> vehicle?.let { DebtEditor(it, busy, error, ::dismissAction) { updated -> model.saveVehicle(updated, ::dismissAction) } }
            "simulator" -> vehicle?.let { DebtSimulator(it, snapshot.recordsFor(it.id), ::dismissAction) }
            "specs" -> vehicle?.let { SpecsEditor(it, busy, error, ::dismissAction) { updated -> model.saveVehicle(updated, ::dismissAction) } }
            "workshops" -> FormPage("Mis talleres", "Toca una ficha para editarla. Los talleres se conservan en las intervenciones.", error = error, onDismiss = ::dismissAction) {
                snapshot.workshops.forEach { workshop -> OutlinedCard(onClick = { workshopId = workshop.id; action = "workshopEditor" }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text(workshop.name, style = MaterialTheme.typography.titleMedium); Text(workshop.address); if (workshop.phone.isNotBlank()) Text(workshop.phone); if (workshop.rating > 0) Text("${workshop.rating} / 5") } } }
                Button(onClick = { workshopId = UUID.randomUUID().toString(); action = "workshopEditor" }, modifier = Modifier.fillMaxWidth()) { Text("Añadir taller") }
            }
            "workshopEditor" -> {
                val base = snapshot.workshops.firstOrNull { it.id == workshopId } ?: Workshop(id = workshopId ?: UUID.randomUUID().toString(), name = "")
                WorkshopEditor(base, busy, error, { action = "workshops" }) { model.saveWorkshop(it) { action = "workshops" } }
            }
            "rules" -> vehicle?.let { car -> FormPage("Plan de cuidado", "Intervalos orientativos: ajústalos al manual y al uso de tu coche. Sin datos del último cambio, el estado se muestra como desconocido.", error = error, onDismiss = ::dismissAction) {
                snapshot.rulesFor(car.id).forEach { rule -> OutlinedCard(onClick = { ruleId = rule.id; action = "ruleEditor" }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text(rule.title, style = MaterialTheme.typography.titleMedium); Text(listOfNotNull(rule.intervalKm?.let { "$it km" }, rule.intervalMonths?.let { "$it meses" }).joinToString(" / ").ifEmpty { "Intervalo por confirmar" }); Text(if (rule.enabled) "Activo" else "Desactivado", style = MaterialTheme.typography.labelSmall) } } }
                Button(onClick = { ruleId = UUID.randomUUID().toString(); action = "ruleEditor" }, modifier = Modifier.fillMaxWidth()) { Text("Añadir aviso") }
            } }
            "ruleEditor" -> vehicle?.let { car ->
                val base = snapshot.rules.firstOrNull { it.id == ruleId } ?: MaintenanceRule(id = ruleId ?: UUID.randomUUID().toString(), vehicleId = car.id, title = "", partKey = "other")
                RuleEditor(base, busy, error, { action = "rules" }) { model.saveRule(it) { action = "rules" } }
            }
            "pdf" -> FormPage("Un historial que inspira confianza", "Informe de reventa con vehículo, kilometraje registrado, mantenimiento, talleres, ITV y daños. La financiación, notas privadas, pólizas y guantera quedan excluidas.", onDismiss = ::dismissAction) {
                FormSwitch("Incluir precios", pdfPrices, { pdfPrices = it })
                FormSwitch("Incluir datos personales", pdfPersonal, { pdfPersonal = it }, "Nombre, matrícula y bastidor.")
                FormSwitch("Anexar fotos", pdfPhotos, { pdfPhotos = it }, "Las fotos originales pueden contener datos personales o precios aunque los interruptores anteriores estén desactivados. Revísalas antes de compartir.")
                Text("El kilometraje es un historial introducido por ti. No equivale a una certificación oficial.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { pdfVehicleId = vehicle?.id; pdfLauncher.launch("Polo-historial-${LocalDate.now()}.pdf"); dismissAction() }, enabled = vehicle != null, modifier = Modifier.fillMaxWidth()) { Text("Guardar informe PDF") }
            }
            "backup" -> FormPage("Todo tu garaje, a salvo", "La copia ZIP incluye todos tus vehículos, registros y archivos. También incluye deuda y datos personales. Guárdala en un lugar privado: el archivo no está cifrado.", onDismiss = ::dismissAction) {
                Text("${snapshot.vehicles.size} vehículos · ${snapshot.records.size} registros · ${snapshot.attachments.size} archivos", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { backupLauncher.launch("Polo-copia-${LocalDate.now()}.zip"); dismissAction() }, modifier = Modifier.fillMaxWidth()) { Text("Exportar copia completa") }
            }
            "settings" -> FormPage("A tu manera", "Polo App · versión 0.1.0", error = error, onDismiss = ::dismissAction) {
                FormChoice("Apariencia", theme, listOf("dark" to "Grafito", "light" to "Claro", "system" to "Seguir al sistema"), model::setTheme)
                FormSwitch("Proteger financiación", financeLock, { enabled -> authenticate { model.setFinanceLock(enabled); if (!enabled) lockFinance() } }, "Huella o bloqueo del móvil. Los archivos de copia no están cifrados.")
                Button(onClick = { if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }, modifier = Modifier.fillMaxWidth()) { Text("Activar avisos del móvil") }
                OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }, modifier = Modifier.fillMaxWidth()) { Text("Ajustes de notificaciones de Android") }
                Text("Avisos de ITV y seguro: 30, 7 y 1 día antes, además del vencimiento. Los avisos por km se recalculan al registrar una lectura; el móvil no conoce tus kilómetros en tiempo real. Android puede retrasar las notificaciones por batería.", style = MaterialTheme.typography.bodySmall)
                FormHeading("Tus datos son tuyos")
                OutlinedButton(onClick = { openAction("backup") }, modifier = Modifier.fillMaxWidth()) { Text("Exportar copia de seguridad") }
                OutlinedButton(onClick = { openAction("restore") }, modifier = Modifier.fillMaxWidth()) { Text("Restaurar copia") }
                Text("Sin cuenta, publicidad ni conexión a internet. Si desinstalas la app se elimina el historial local: exporta una copia antes. Para añadir el widget, mantén pulsado el escritorio de Android y busca Polo App.", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (pendingRestore != null) AlertDialog(onDismissRequest = { if (!busy) model.cancelRestore() }, title = { Text("Restaurar este garaje") }, text = { Text("La copia contiene ${pendingRestore!!.vehicles.size} vehículos, ${pendingRestore!!.records.size} registros y ${pendingRestore!!.attachments.size} archivos. Sustituirá todo el historial actual. Exporta una copia antes si quieres conservarlo.") }, confirmButton = { TextButton(onClick = model::commitRestore, enabled = !busy) { Text("Sustituir y restaurar") } }, dismissButton = { TextButton(onClick = model::cancelRestore, enabled = !busy) { Text("Cancelar") } })
    }
}

@Composable
private fun WelcomeScreen(onCreate: () -> Unit, onRestore: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
            Text("POLO / APP", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(26.dp))
            Text("Cuida tu coche.\nDisfruta el camino.", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(18.dp))
            Text("Cada revisión, cada viaje y cada pequeño detalle. Una historia completa, siempre en tu bolsillo.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PoloArtwork(Modifier.fillMaxWidth().height(230.dp))
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Empezar con mi coche") }
            TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) { Text("Ya tengo una copia de seguridad") }
            Spacer(Modifier.height(14.dp))
            Text("100 % local · Sin cuenta · Sin conexión", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
