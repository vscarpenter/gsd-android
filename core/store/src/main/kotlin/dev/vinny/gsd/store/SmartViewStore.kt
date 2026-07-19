package dev.vinny.gsd.store

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.vinny.gsd.model.BuiltInSmartViews
import dev.vinny.gsd.model.FilterCriteria
import dev.vinny.gsd.model.NanoId
import dev.vinny.gsd.model.SmartView
import dev.vinny.gsd.model.TaskJson
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
interface SmartViewDao {
    @Query("SELECT * FROM smart_views")
    fun observeAll(): Flow<List<SmartViewEntity>>

    @Query("SELECT * FROM smart_views WHERE id = :id")
    suspend fun byId(id: String): SmartViewEntity?

    @Query("SELECT COUNT(*) FROM smart_views")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(entity: SmartViewEntity)

    @Query("DELETE FROM smart_views WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface AppPreferencesDao {
    @Query("SELECT * FROM app_preferences WHERE id = 1")
    fun observe(): Flow<AppPreferencesEntity?>

    @Query("SELECT * FROM app_preferences WHERE id = 1")
    suspend fun get(): AppPreferencesEntity?

    @Upsert
    suspend fun upsert(entity: AppPreferencesEntity)
}

/** Smart views (spec §8.13): nine seeded built-ins, custom CRUD, ≤5 ordered pins. */
class SmartViewStore(private val db: GsdDatabase, private val clock: Clock) {
    class BuiltInImmutable : Exception("Built-in views can't be edited or deleted")
    class TooManyPins : Exception("At most 5 pinned views")

    suspend fun seedBuiltInsIfEmpty() {
        if (db.smartViewDao().count() > 0) return
        BuiltInSmartViews.all.forEach { db.smartViewDao().upsert(it.toEntity()) }
    }

    fun observeViews(): Flow<List<SmartView>> =
        db.smartViewDao().observeAll().map { rows ->
            rows.map { it.toSmartView() }.sortedWith(
                compareByDescending<SmartView> { it.isBuiltIn }.thenBy { it.createdAt },
            )
        }

    suspend fun create(name: String, icon: String, criteria: FilterCriteria): SmartView {
        val now = clock.instant()
        val view = SmartView(NanoId.generate(12), name, icon, criteria,
            isBuiltIn = false, createdAt = now, updatedAt = now)
        db.smartViewDao().upsert(view.toEntity())
        return view
    }

    suspend fun update(view: SmartView) {
        val existing = db.smartViewDao().byId(view.id) ?: return
        if (existing.isBuiltIn) throw BuiltInImmutable()
        db.smartViewDao().upsert(view.copy(updatedAt = clock.instant()).toEntity())
    }

    suspend fun delete(id: String) {
        val existing = db.smartViewDao().byId(id) ?: return
        if (existing.isBuiltIn) throw BuiltInImmutable()
        db.smartViewDao().deleteById(id)
        setPinned(pinned().filter { it != id })
    }

    suspend fun pinned(): List<String> =
        db.appPreferencesDao().get()?.pinnedSmartViewIdsJson?.let(TaskJson::decodeIds) ?: emptyList()

    fun observePinned(): Flow<List<String>> =
        db.appPreferencesDao().observe().map { it?.pinnedSmartViewIdsJson?.let(TaskJson::decodeIds) ?: emptyList() }

    suspend fun setPinned(ids: List<String>) {
        if (ids.size > 5) throw TooManyPins()
        db.appPreferencesDao().upsert(AppPreferencesEntity(pinnedSmartViewIdsJson = TaskJson.encodeIds(ids)))
    }
}

private fun SmartView.toEntity(): SmartViewEntity = SmartViewEntity(
    id = id, name = name, icon = icon,
    criteriaJson = TaskJson.json.encodeToString(FilterCriteria.serializer(), criteria),
    isBuiltIn = isBuiltIn, createdAt = createdAt.toEpochMilli(), updatedAt = updatedAt.toEpochMilli(),
)

private fun SmartViewEntity.toSmartView(): SmartView = SmartView(
    id = id, name = name, icon = icon,
    criteria = try {
        TaskJson.json.decodeFromString(FilterCriteria.serializer(), criteriaJson)
    } catch (_: Exception) {
        FilterCriteria()
    },
    isBuiltIn = isBuiltIn,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)
