package com.poloapp.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

class CarConverters {
    @TypeConverter fun encodeDetails(value: Map<String, String>): String = JSONObject(value).toString()
    @TypeConverter fun decodeDetails(value: String): Map<String, String> {
        val json = JSONObject(value)
        return buildMap { json.keys().forEach { key -> put(key, json.getString(key)) } }
    }
    @TypeConverter fun encodeKind(value: RecordKind): String = value.name
    @TypeConverter fun decodeKind(value: String): RecordKind = RecordKind.valueOf(value)
}

@Dao
interface CarDao {
    @Query("SELECT * FROM vehicles ORDER BY name") fun observeVehicles(): Flow<List<Vehicle>>
    @Query("SELECT * FROM records ORDER BY date DESC, createdAt DESC") fun observeRecords(): Flow<List<CarRecord>>
    @Query("SELECT * FROM maintenance_rules ORDER BY title") fun observeRules(): Flow<List<MaintenanceRule>>
    @Query("SELECT * FROM workshops ORDER BY name") fun observeWorkshops(): Flow<List<Workshop>>
    @Query("SELECT * FROM attachments ORDER BY id") fun observeAttachments(): Flow<List<Attachment>>
    @Query("SELECT * FROM vehicles ORDER BY name") suspend fun vehicles(): List<Vehicle>
    @Query("SELECT * FROM records ORDER BY date DESC, createdAt DESC") suspend fun records(): List<CarRecord>
    @Query("SELECT * FROM maintenance_rules ORDER BY title") suspend fun rules(): List<MaintenanceRule>
    @Query("SELECT * FROM workshops ORDER BY name") suspend fun workshops(): List<Workshop>
    @Query("SELECT * FROM attachments ORDER BY id") suspend fun attachments(): List<Attachment>
    @Upsert suspend fun upsert(vehicle: Vehicle)
    @Upsert suspend fun upsert(record: CarRecord)
    @Upsert suspend fun upsert(rule: MaintenanceRule)
    @Upsert suspend fun upsert(workshop: Workshop)
    @Upsert suspend fun upsert(attachment: Attachment)
    @Insert suspend fun insertVehicles(rows: List<Vehicle>)
    @Insert suspend fun insertRecords(rows: List<CarRecord>)
    @Insert suspend fun insertRules(rows: List<MaintenanceRule>)
    @Insert suspend fun insertWorkshops(rows: List<Workshop>)
    @Insert suspend fun insertAttachments(rows: List<Attachment>)
    @Delete suspend fun delete(record: CarRecord)
    @Delete suspend fun delete(rule: MaintenanceRule)
    @Delete suspend fun delete(workshop: Workshop)
    @Query("DELETE FROM attachments WHERE recordId = :recordId") suspend fun deleteAttachmentsFor(recordId: String)
    @Query("DELETE FROM attachments") suspend fun clearAttachments()
    @Query("DELETE FROM records") suspend fun clearRecords()
    @Query("DELETE FROM maintenance_rules") suspend fun clearRules()
    @Query("DELETE FROM workshops") suspend fun clearWorkshops()
    @Query("DELETE FROM vehicles") suspend fun clearVehicles()
}

@Database(entities = [Vehicle::class, CarRecord::class, MaintenanceRule::class, Workshop::class, Attachment::class],
    version = 1, exportSchema = true)
@TypeConverters(CarConverters::class)
abstract class CarDatabase : RoomDatabase() {
    abstract fun carDao(): CarDao
    companion object {
        @Volatile private var instance: CarDatabase? = null
        fun get(context: Context): CarDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, CarDatabase::class.java, "polo-app.db")
                // Every future version must supply an explicit migration. Never discard the user's history.
                .build().also { instance = it }
        }
    }
}
