package com.poloapp.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.poloapp.data.CarRecord
import com.poloapp.data.GarageSnapshot
import com.poloapp.data.RecordKind
import com.poloapp.data.Vehicle
import com.poloapp.ui.forms.RecordEditor
import com.poloapp.ui.theme.PoloTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class RecordEditorTest {
    @get:Rule val compose = createComposeRule()
    private val car = Vehicle(id = "editor-car", purchaseKm = 10_000,
        purchaseDate = LocalDate.of(2026, 1, 1).toEpochDay())
    private var saved: CarRecord? = null
    private var dismissed = false

    private fun show(record: CarRecord, isNew: Boolean = false) {
        compose.setContent {
            PoloTheme {
                RecordEditor(record, isNew, GarageSnapshot(vehicles = listOf(car)), false, null,
                    onDismiss = { dismissed = true }, onSave = { result, _, _ -> saved = result },
                    onDelete = {}, onOpenAttachment = {})
            }
        }
    }

    private fun replaceField(label: String, value: String) {
        compose.onNode(hasText(label) and hasSetTextAction()).performScrollTo().performTextReplacement(value)
    }

    private fun save() {
        // The app bar remains visible while the form body and the keyboard scroll independently.
        compose.onNodeWithTag("form-save-top").assertIsDisplayed().performClick()
        compose.waitForIdle()
    }

    private fun result(): CarRecord {
        compose.waitForIdle()
        assertNotNull("El formulario debe enviar un registro válido a onSave", saved)
        return checkNotNull(saved)
    }

    @Test fun editTrip_keepsExistingTollsWhenChangingAnotherField() {
        show(CarRecord(id = "trip", vehicleId = car.id, kind = RecordKind.TRIP,
            title = "Badalona → Madrid", date = car.purchaseDate + 20, odometer = 10_650,
            details = mapOf("startKm" to "10000", "endKm" to "10650", "purpose" to "Visita",
                "tollsCents" to "1875")))
        replaceField("Propósito del viaje", "Visita familiar")
        save()
        val result = result()
        assertEquals("1875", result.details["tollsCents"])
        assertEquals("Visita familiar", result.details["purpose"])
        assertEquals(10_650, result.odometer)
        assertFalse(result.details.containsKey("tollsCost"))
    }

    @Test fun editMaintenance_loadsStoredBreakdownAndRecalculatesBlankTotal() {
        show(CarRecord(id = "maintenance", vehicleId = car.id, kind = RecordKind.MAINTENANCE,
            title = "Aceite y filtro", odometer = 11_000, amountCents = 15_000,
            details = mapOf("partKey" to "oil", "provider" to "self", "partsCents" to "10000", "laborCents" to "5000")))
        compose.onNode(hasText("Coste de piezas · € · opcional") and hasSetTextAction())
            .performScrollTo().assertTextContains("100.00")
        compose.onNode(hasText("Mano de obra · € · opcional") and hasSetTextAction())
            .performScrollTo().assertTextContains("50.00")
        replaceField("Coste de piezas · € · opcional", "125,00")
        replaceField("Mano de obra · € · opcional", "55,00")
        replaceField("Importe total · €", "")
        save()
        val result = result()
        assertEquals(18_000L, result.amountCents)
        assertEquals("12500", result.details["partsCents"])
        assertEquals("5500", result.details["laborCents"])
        assertFalse(result.details.containsKey("partsCost"))
        assertFalse(result.details.containsKey("laborCost"))
    }

    @Test fun editMaintenance_rejectsTotalThatDisagreesWithBreakdown() {
        show(CarRecord(id = "maintenance-mismatch", vehicleId = car.id, kind = RecordKind.MAINTENANCE,
            odometer = 11_000, amountCents = 15_000,
            details = mapOf("partKey" to "oil", "provider" to "self", "partsCents" to "10000", "laborCents" to "5000")))
        replaceField("Coste de piezas · € · opcional", "120")
        save()
        assertNull(saved)
        compose.onNodeWithText("El total debe coincidir con piezas + mano de obra, o déjalo vacío para calcularlo.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun fuel_litersAndPriceComputeTotal() {
        show(CarRecord(id = "fuel-total", vehicleId = car.id, kind = RecordKind.FUEL, odometer = 11_000), true)
        replaceField("Litros", "40")
        replaceField("Precio por litro · €", "1,55")
        save()
        val result = result()
        assertEquals(6_200L, result.amountCents)
        assertEquals("true", result.details["fullTank"])
        assertEquals("95", result.details["fuelType"])
    }

    @Test fun fuel_litersAndTotalComputePrice() {
        show(CarRecord(id = "fuel-price", vehicleId = car.id, kind = RecordKind.FUEL, odometer = 11_000), true)
        replaceField("Litros", "40")
        replaceField("Importe total · €", "62")
        save()
        val result = result()
        assertEquals(0, BigDecimal("1.55").compareTo(BigDecimal(result.details.getValue("pricePerLiter"))))
        assertEquals(6_200L, result.amountCents)
    }

    @Test fun fuel_priceAndTotalComputeLiters() {
        show(CarRecord(id = "fuel-liters", vehicleId = car.id, kind = RecordKind.FUEL, odometer = 11_000), true)
        replaceField("Precio por litro · €", "1,55")
        replaceField("Importe total · €", "62")
        save()
        val result = result()
        assertEquals(0, BigDecimal("40").compareTo(BigDecimal(result.details.getValue("liters"))))
        assertEquals(6_200L, result.amountCents)
    }

    @Test fun note_backDismissesWithoutSavingChanges() {
        val record = CarRecord(id = "note", vehicleId = car.id, kind = RecordKind.NOTE,
            title = "Recordatorio", notes = "Comprobar neumáticos")
        show(record)
        replaceField("Escribe tu nota", "Texto aún sin guardar")
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.runOnIdle {
            assertTrue(dismissed)
            assertNull(saved)
            assertEquals("Comprobar neumáticos", record.notes)
        }
    }
}
