package app.roamling

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.roamling.data.Backup
import app.roamling.data.Cell
import app.roamling.data.Day
import app.roamling.data.Db
import app.roamling.data.Entry
import app.roamling.data.Find
import app.roamling.data.Patch
import app.roamling.data.Pet
import app.roamling.data.Settings
import app.roamling.data.SettingsStore
import app.roamling.data.Walk
import app.roamling.data.Waypoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A file saved on one phone brings everything back on another. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private fun db() = Room.inMemoryDatabaseBuilder(ctx, Db::class.java).allowMainThreadQueries().build()

    @Test fun roundTrip() = runBlocking {
        val a = db(); val dao = a.dao()
        val leader = dao.insert(Pet(species = "plod", name = "Топа", stage = 3, form = 1, points = 12.5, diet = "GREEN:300", shiny = true, bond = 6.0, meters = 4200.0, skies = "RAIN:400", gear = "scarf", grownAt = 5))
        val egg = dao.insert(Pet(species = "beaver", name = "", stage = 0, eggLeft = 700.0, eggTaste = "WATER"))
        val w = dao.insert(Walk(start = 1000, end = 90_000, meters = 2400.0, steps = 3100, patches = 7, tastes = "GREEN:900", sky = "RAIN", temp = 12.0))
        dao.insertWaypoints(listOf(Waypoint(w, 1000, 50.1, 14.4), Waypoint(w, 2000, 50.2, 14.5)))
        dao.upsert(Day(20000, 3100, 2400.0, 7, 3100, true))
        dao.insert(Find(item = "key", at = 5000, lat = 50.0, lon = 14.0, place = "gift", ref = "gift:20000"))
        dao.grant(Entry("demo", 1, 40))
        dao.upsertPatches(listOf(Patch(123L, 1, 2, 3, 20000, "GREEN")))
        dao.insertCells(listOf(Cell(9L, 1), Cell(10L, 1)))
        val s = Settings(onboarded = true, team = listOf(leader), eggs = listOf(egg), walking = true, walkId = w, lures = 2, seals = mapOf(77L to "STONE|3|5"))

        val json = Backup.export(a, s)
        val b = db()
        val store = SettingsStore(ctx)
        Backup.restore(b, store, Backup.parse(json))

        val d2 = b.dao()
        assertEquals(dao.petList(), d2.petList())
        assertEquals(dao.walkList(), d2.walkList())
        assertEquals(dao.allWaypoints(), d2.allWaypoints())
        assertEquals(dao.dayList(), d2.dayList())
        assertEquals(dao.findList().map { it.copy(id = 0) }, d2.findList().map { it.copy(id = 0) })
        assertEquals(dao.entryList(), d2.entryList())
        assertEquals(dao.patchList(), d2.patchList())
        assertEquals(dao.cellIds().sorted(), d2.cellIds().sorted())
        val s2 = store.now()
        assertEquals(s.copy(walking = false, walkId = 0), s2)
    }
}
