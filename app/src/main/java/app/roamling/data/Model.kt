package app.roamling.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A companion. [stage]: 0 egg, 1 baby, 2 teen, 3 grown. [points] is the way walked with it, in growth points,
 * since its last stage. [diet] is what the teen ate, metres per taste ("GREEN:812;WATER:300"); [form] is the grown form, −1 before.
 */
@Entity
data class Pet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val species: String,
    val name: String,
    val stage: Int,
    val points: Double = 0.0,
    val diet: String = "",
    val form: Int = -1,
    val shiny: Boolean = false,
    val born: Long = System.currentTimeMillis(),
    /** An egg: metres it still needs to be carried. */
    val eggLeft: Double = 0.0,
    /** An egg: the taste of the place it was found (its look). */
    val eggTaste: String = "",
    /** Where it was found or hatched. */
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    /** Hearts, 0..10. */
    val bond: Double = 0.0,
    /** Metres walked with it in the backpack. */
    val meters: Double = 0.0,
    /** Weathers walked in, metres per sky kind ("RAIN:400;SNOW:120"). */
    val skies: String = "",
    /** The gear it wears, or "". */
    val gear: String = "",
    val grownAt: Long = 0,
)

/** One walk, from the «Walk» button to its end. */
@Entity
data class Walk(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val start: Long,
    val end: Long,
    val meters: Double = 0.0,
    val steps: Int = 0,
    val patches: Int = 0,
    /** Metres per taste on this walk. */
    val tastes: String = "",
    /** The walk's weather stamp (a [app.roamling.domain.Sky] name), "" before known. */
    val sky: String = "",
    /** °C, or below −200 when unknown. */
    val temp: Double = -999.0,
    val finds: Int = 0,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
)

@Entity(primaryKeys = ["walk", "at"])
data class Waypoint(val walk: Long, val at: Long, val lat: Double, val lon: Double)

/** An explored base cell of the fog (Fogwalk's grid). */
@Entity
data class Cell(@PrimaryKey val key: Long, val at: Long)

/** A patch: a zoom-18 square, about 100 m. Each first visit is fresh food; [days] counts the different days it was walked. */
@Entity
data class Patch(@PrimaryKey val key: Long, val first: Long, val last: Long, val days: Int = 1, val lastDay: Long = 0, val taste: String = "")

/** A day: steps from the pedometer or Health Connect, and what the walks gave. */
@Entity
data class Day(
    @PrimaryKey val day: Long,
    val steps: Int = 0,
    val meters: Double = 0.0,
    val patches: Int = 0,
    /** Steps already turned into growth. */
    val fed: Int = 0,
    val gift: Boolean = false,
)

/** Something found: a scent's end, a rainbow's end, a gift. [item] is a key of [app.roamling.domain.Finds]. */
@Entity
data class Find(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val item: String,
    val at: Long,
    val lat: Double,
    val lon: Double,
    /** What it was found at: «фонтан», «старый дуб»… */
    val place: String = "",
    val ref: String = "",
)

/** Cones earned and spent; [ref] keeps a reward from being paid twice. */
@Entity
data class Entry(@PrimaryKey val ref: String, val at: Long, val cones: Int)

@Dao
interface Dao0 {
    @Query("SELECT * FROM Pet ORDER BY id") fun pets(): Flow<List<Pet>>
    @Query("SELECT * FROM Pet ORDER BY id") suspend fun petList(): List<Pet>
    @Query("SELECT * FROM Pet WHERE id = :id") suspend fun pet(id: Long): Pet?
    @Insert suspend fun insert(p: Pet): Long
    @Update suspend fun update(p: Pet)
    @Query("DELETE FROM Pet WHERE id = :id") suspend fun deletePet(id: Long)

    @Query("SELECT * FROM Walk ORDER BY start DESC") fun walks(): Flow<List<Walk>>
    @Query("SELECT * FROM Walk ORDER BY start DESC") suspend fun walkList(): List<Walk>
    @Query("SELECT * FROM Walk ORDER BY start DESC LIMIT 1") suspend fun lastWalk(): Walk?
    @Insert suspend fun insert(w: Walk): Long
    @Update suspend fun update(w: Walk)
    @Query("DELETE FROM Walk WHERE id = :id") suspend fun deleteWalk(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertWaypoints(w: List<Waypoint>)
    @Query("SELECT * FROM Waypoint WHERE walk = :walk ORDER BY at") suspend fun route(walk: Long): List<Waypoint>
    @Query("SELECT * FROM Waypoint ORDER BY at") suspend fun allWaypoints(): List<Waypoint>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCells(c: List<Cell>)
    @Query("SELECT key FROM Cell") suspend fun cellIds(): List<Long>
    @Query("SELECT COUNT(*) FROM Cell") suspend fun cellCount(): Int

    @Query("SELECT * FROM Patch") fun patches(): Flow<List<Patch>>
    @Query("SELECT * FROM Patch") suspend fun patchList(): List<Patch>
    @Query("SELECT COUNT(*) FROM Patch") fun patchCount(): Flow<Int>
    @Query("SELECT * FROM Patch WHERE `key` = :key") suspend fun patch(key: Long): Patch?
    @Upsert suspend fun upsert(p: Patch)
    @Upsert suspend fun upsertPatches(p: List<Patch>)

    @Query("SELECT * FROM Day ORDER BY day") fun days(): Flow<List<Day>>
    @Query("SELECT * FROM Day ORDER BY day") suspend fun dayList(): List<Day>
    @Query("SELECT * FROM Day WHERE day = :day") suspend fun day(day: Long): Day?
    @Upsert suspend fun upsert(d: Day)

    @Query("SELECT * FROM Find ORDER BY at DESC") fun finds(): Flow<List<Find>>
    @Query("SELECT * FROM Find ORDER BY at DESC") suspend fun findList(): List<Find>
    @Insert suspend fun insert(f: Find): Long

    @Query("SELECT * FROM Entry ORDER BY at") fun entries(): Flow<List<Entry>>
    @Query("SELECT * FROM Entry ORDER BY at") suspend fun entryList(): List<Entry>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun grant(e: Entry): Long
}

@Database(entities = [Pet::class, Walk::class, Waypoint::class, Cell::class, Patch::class, Day::class, Find::class, Entry::class], version = 1, exportSchema = true)
abstract class Db : RoomDatabase() {
    abstract fun dao(): Dao0

    companion object {
        fun create(ctx: Context) = Room.databaseBuilder(ctx, Db::class.java, "roamling.db").build()
    }
}

/** "A:1.5;B:2" ⇄ map. */
fun parseMap(s: String): Map<String, Double> = if (s.isEmpty()) emptyMap() else s.split(';').mapNotNull { p ->
    val i = p.indexOf(':'); if (i <= 0) null else p.substring(0, i) to (p.substring(i + 1).toDoubleOrNull() ?: 0.0)
}.toMap()

fun Map<String, Double>.pack(): String = entries.filter { it.value > 0 }.joinToString(";") { "${it.key}:${"%.1f".format(java.util.Locale.US, it.value)}" }

fun Map<String, Double>.plus(k: String, v: Double): Map<String, Double> = this + (k to ((this[k] ?: 0.0) + v))
