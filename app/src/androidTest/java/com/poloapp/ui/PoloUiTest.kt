package com.poloapp.ui

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.poloapp.data.*
import com.poloapp.ui.screens.MainScreens
import com.poloapp.ui.theme.PoloTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** Demo data exists only in the instrumentation APK. Production always starts empty. */
@RunWith(AndroidJUnit4::class)
class PoloUiTest {
    @get:Rule val compose = createComposeRule()

    private val day = LocalDate.of(2026, 9, 28).toEpochDay()
    private val car = Vehicle(id = "test-car", name = "Mi Polo", purchaseDate = day - 200,
        purchaseKm = 140_000, initialDebtCents = 450_000, plate = "1234 ABC")
    private val demo = GarageSnapshot(
        vehicles = listOf(car),
        records = listOf(
            CarRecord(id = "fill-1", vehicleId = car.id, kind = RecordKind.FUEL, date = day - 20,
                title = "Repostaje de septiembre", odometer = 147_500, amountCents = 6_080,
                details = mapOf("liters" to "40", "fullTank" to "true")),
            CarRecord(id = "fill-2", vehicleId = car.id, kind = RecordKind.FUEL, date = day - 2,
                title = "Parada en la gasolinera", odometer = 148_200, amountCents = 6_165,
                details = mapOf("liters" to "41.1", "fullTank" to "true")),
            CarRecord(id = "oil", vehicleId = car.id, kind = RecordKind.MAINTENANCE, date = day - 60,
                title = "Aceite y filtro", odometer = 145_000, amountCents = 12_500,
                details = mapOf("partKey" to "oil", "provider" to "workshop")),
            CarRecord(id = "itv", vehicleId = car.id, kind = RecordKind.ITV, date = day - 100,
                title = "ITV favorable", odometer = 143_000,
                details = mapOf("dueDate" to (day + 240).toString(), "result" to "Favorable")),
            CarRecord(id = "insurance", vehicleId = car.id, kind = RecordKind.INSURANCE, date = day - 60,
                title = "Seguro del coche", details = mapOf("dueDate" to (day + 72).toString())),
            CarRecord(id = "payment", vehicleId = car.id, kind = RecordKind.PAYMENT, date = day - 5,
                title = "Pago familiar privado", amountCents = 50_000)
        ),
        rules = listOf(MaintenanceRule(id = "oil-rule", vehicleId = car.id, title = "Aceite del motor",
            partKey = "oil", intervalKm = 15_000, intervalMonths = 12))
    )

    @Test fun emptyGarage_navigatesAcrossEveryTabInBothThemes() {
        val section = mutableStateOf("home")
        val mode = mutableStateOf("dark")
        compose.setContent {
            PoloTheme(mode.value) {
                MainScreens(GarageSnapshot(vehicles = listOf(car)), car, section.value,
                    onSection = { section.value = it }, onAdd = {}, onEdit = {}, onAction = {})
            }
        }
        listOf("dark", "light").forEach { theme ->
            compose.runOnIdle { mode.value = theme }
            listOf(
                "Repostajes" to "Cada litro cuenta.", "Taller" to "Cuidarlo es avanzar.",
                "Finanzas" to "Poco a poco, tuyo.", "Garaje" to "Tu garaje, al detalle.",
                "Inicio" to "Tu próxima aventura."
            ).forEach { (tab, heading) ->
                compose.onNodeWithText(tab).performClick()
                compose.waitForIdle()
                compose.onNodeWithText(heading).assertIsDisplayed()
            }
        }
    }

    @Test fun populatedFuel_opensSelectedRecord() {
        var edited: String? = null
        compose.setContent {
            PoloTheme {
                MainScreens(demo, car, "fuel", onSection = {}, onAdd = {},
                    onEdit = { edited = it.id }, onAction = {})
            }
        }
        compose.onNodeWithText("5,9").assertIsDisplayed()
        compose.onNodeWithTag("polo-page").performScrollToNode(hasText("Parada en la gasolinera"))
        compose.onNodeWithText("Parada en la gasolinera").performClick()
        compose.runOnIdle { assertEquals("fill-2", edited) }
    }

    @Test fun protectedFinance_hidesBalanceAndPaymentHistory() {
        val section = mutableStateOf("finance")
        compose.setContent {
            PoloTheme {
                MainScreens(demo, car, section.value, onSection = { section.value = it },
                    onAdd = {}, onEdit = {}, onAction = {}, financeLocked = true)
            }
        }
        compose.onNodeWithText("Financiación protegida").assertIsDisplayed()
        compose.onNodeWithText("Pendiente de devolver").assertDoesNotExist()
        compose.runOnIdle { section.value = "history" }
        compose.waitForIdle()
        compose.onNodeWithText("5 registros").assertIsDisplayed()
        compose.onNodeWithText("Pago familiar privado").assertDoesNotExist()
    }

    @Test fun dashboard_savesVisualReviewScreenshot() {
        val mode = mutableStateOf("dark")
        compose.setContent {
            PoloTheme(mode.value) {
                MainScreens(demo, car, "home", onSection = {}, onAdd = {}, onEdit = {}, onAction = {})
            }
        }
        compose.onNodeWithText("Tu próxima aventura.").assertIsDisplayed()
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        listOf("dark", "light").forEach { theme ->
            compose.runOnIdle { mode.value = theme }
            compose.waitForIdle()
            val suffix = if (theme == "dark") "" else "-light"
            val output = File(context.getExternalFilesDir(null), "polo-dashboard$suffix.png")
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
