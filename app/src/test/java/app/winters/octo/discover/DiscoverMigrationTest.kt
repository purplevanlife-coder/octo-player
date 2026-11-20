package app.winters.octo.discover

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import app.winters.octo.testing.testContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

// A version 1 discover.db, as Room made it for the first Discover build,
// with a kept batch and the listener's dismissals, opens through the app's
// own migrations: Room checks every table against the entities, the
// dismissals are all kept, and the kept batch reads as generation 0.
@RunWith(RobolectricTestRunner::class)
class DiscoverMigrationTest {
    private val version1 = listOf(
        "CREATE TABLE IF NOT EXISTS `discover_batch` (`owner` TEXT NOT NULL, `generatedAt` INTEGER NOT NULL, `format` INTEGER NOT NULL, PRIMARY KEY(`owner`))",
        "CREATE TABLE IF NOT EXISTS `discover_section` (`owner` TEXT NOT NULL, `position` INTEGER NOT NULL, `sectionKey` TEXT NOT NULL, " +
            "`kind` TEXT NOT NULL, `title` TEXT NOT NULL, `payload` TEXT NOT NULL, PRIMARY KEY(`owner`, `position`))",
        "CREATE TABLE IF NOT EXISTS `discover_dismissed` (`owner` TEXT NOT NULL, `kind` TEXT NOT NULL, `itemKey` TEXT NOT NULL, " +
            "`label` TEXT NOT NULL, `dismissedAt` INTEGER NOT NULL, PRIMARY KEY(`owner`, `kind`, `itemKey`))",
        "CREATE TABLE IF NOT EXISTS `artist_extras_cache` (`server` TEXT NOT NULL, `artistId` TEXT NOT NULL, `payload` TEXT NOT NULL, " +
            "`fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`server`, `artistId`))",
        "CREATE INDEX IF NOT EXISTS `index_artist_extras_cache_artistId` ON `artist_extras_cache` (`artistId`)",
        "CREATE INDEX IF NOT EXISTS `index_artist_extras_cache_fetchedAt` ON `artist_extras_cache` (`fetchedAt`)",
    )

    @Test
    fun aVersion1DatabaseKeepsItsDismissalsAndBatch() = runBlocking {
        val file = File(Files.createTempDirectory("discover").toFile(), DISCOVER_DB_FILE)
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            version1.forEach(db::execSQL)
            db.execSQL("INSERT INTO discover_batch (owner, generatedAt, format) VALUES ('me', 1000, 1)")
            db.execSQL("INSERT INTO discover_section (owner, position, sectionKey, kind, title, payload) VALUES ('me', 0, 'for-you', 'ForYou', 'For You', '{}')")
            db.execSQL("INSERT INTO discover_dismissed (owner, kind, itemKey, label, dismissedAt) VALUES ('me', 'song', 'find:x', 'X', 5)")
            db.execSQL("INSERT INTO discover_dismissed (owner, kind, itemKey, label, dismissedAt) VALUES ('me', 'artist', 'band', 'Band', 6)")
            db.version = 1
        }
        val room = Room.databaseBuilder(testContext(), DiscoverDatabase::class.java, file.path)
            .addMigrations(*DiscoverMigrations)
            .allowMainThreadQueries()
            .build()
        val dao = room.dao()
        assertEquals(
            setOf("song|find:x", "artist|band"),
            dao.dismissedNow("me").mapTo(HashSet()) { "${it.kind}|${it.itemKey}" },
        )
        val head = dao.batch("me")!!
        assertEquals(0L, head.generation)
        assertEquals(1, head.format)
        assertEquals(1, dao.sections("me").size)
        // The new column takes a generation as the app writes it.
        dao.putBatch(head.copy(generation = 4, format = BATCH_FORMAT))
        assertEquals(4L, dao.batch("me")!!.generation)
        room.openHelper.writableDatabase.query("PRAGMA user_version").use { it.moveToFirst(); assertEquals(DISCOVER_DB_VERSION, it.getInt(0)) }
        room.close()
    }
}
