package app.winters.octo.discover

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// Discover's own small database (discover.db), apart from the catalog, so
// adding it needs no catalog migration and an upstream catalog change never
// meets it. Everything here is a cache except the dismissals, which are
// the listener's choices: a future version bump must migrate (or at least
// keep) discover_dismissed rather than drop it.
const val DISCOVER_DB_VERSION = 2
const val DISCOVER_DB_FILE = "discover.db"

// Version 2 adds the batch's generation (DiscoverBatch.generation) to its
// head row, a column with a default, in place: every other table, and
// above all discover_dismissed, is left exactly as it was.
internal const val ADD_GENERATION_SQL = "ALTER TABLE discover_batch ADD COLUMN generation INTEGER NOT NULL DEFAULT 0"

val DiscoverMigration1To2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(ADD_GENERATION_SQL)
    }
}

// Every step from an older discover.db, for the database's builder.
val DiscoverMigrations: Array<Migration> = arrayOf(DiscoverMigration1To2)

// The latest batch's head, per server and account (`owner`, see
// DiscoverRepository.ownerOf): when it was made, the format its sections
// were written in (a batch of another format is not read), and its
// generation (0 for one kept before generations were).
@Entity(tableName = "discover_batch")
data class DiscoverBatchEntity(
    @PrimaryKey val owner: String,
    val generatedAt: Long,
    val format: Int,
    @ColumnInfo(defaultValue = "0") val generation: Long = 0,
)

// One section of that batch, in its place: what it is, and its items as
// JSON (the DiscoverSection itself).
@Entity(tableName = "discover_section", primaryKeys = ["owner", "position"])
data class DiscoverSectionEntity(
    val owner: String,
    val position: Int,
    val sectionKey: String,
    val kind: String,
    val title: String,
    val payload: String,
)

// A pick turned down with "Not interested": a song by id ("song"), a song
// by title and artist ("song-key"), or an artist by name key ("artist").
// `label` is what was shown, for a later "turned down" list.
@Entity(tableName = "discover_dismissed", primaryKeys = ["owner", "kind", "itemKey"])
data class DiscoverDismissedEntity(
    val owner: String,
    val kind: String,
    val itemKey: String,
    val label: String,
    val dismissedAt: Long,
)

// What a server said about one artist (ArtistExtrasSource): top songs,
// biography and similar artists as JSON, keyed by server and account and
// the artist's id ("online:<server id>" for an artist only on the server),
// with when it was read.
@Entity(tableName = "artist_extras_cache", primaryKeys = ["server", "artistId"], indices = [Index("artistId"), Index("fetchedAt")])
data class ArtistExtrasCacheEntity(
    val server: String,
    val artistId: String,
    val payload: String,
    val fetchedAt: Long,
)

object DismissKind {
    const val SONG = "song"
    const val SONG_KEY = "song-key"
    const val ARTIST = "artist"
}

@Dao
interface DiscoverDao {
    @Query("SELECT * FROM discover_batch WHERE owner = :owner")
    suspend fun batch(owner: String): DiscoverBatchEntity?

    @Query("SELECT * FROM discover_section WHERE owner = :owner ORDER BY position")
    suspend fun sections(owner: String): List<DiscoverSectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putBatch(row: DiscoverBatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSections(rows: List<DiscoverSectionEntity>)

    @Query("DELETE FROM discover_section WHERE owner = :owner")
    suspend fun clearSections(owner: String)

    // A new batch in place of the old, whole or not at all.
    @Transaction
    suspend fun replaceBatch(head: DiscoverBatchEntity, sections: List<DiscoverSectionEntity>) {
        clearSections(head.owner)
        putSections(sections)
        putBatch(head)
    }

    @Query("SELECT * FROM discover_dismissed WHERE owner = :owner")
    fun dismissed(owner: String): Flow<List<DiscoverDismissedEntity>>

    @Query("SELECT * FROM discover_dismissed WHERE owner = :owner")
    suspend fun dismissedNow(owner: String): List<DiscoverDismissedEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun dismiss(rows: List<DiscoverDismissedEntity>)

    @Query("DELETE FROM discover_dismissed WHERE owner = :owner AND kind = :kind AND itemKey = :itemKey")
    suspend fun undismiss(owner: String, kind: String, itemKey: String)

    @Query("SELECT * FROM artist_extras_cache WHERE server = :server AND artistId = :artistId")
    suspend fun extras(server: String, artistId: String): ArtistExtrasCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putExtras(row: ArtistExtrasCacheEntity)

    // An artist's kept extras on every server, so a pull on their page asks again.
    @Query("DELETE FROM artist_extras_cache WHERE artistId = :artistId")
    suspend fun forgetExtras(artistId: String)

    // Extras not read again for a long while go, so the table stays small.
    @Query("DELETE FROM artist_extras_cache WHERE fetchedAt < :before")
    suspend fun pruneExtras(before: Long)
}

@Database(
    entities = [DiscoverBatchEntity::class, DiscoverSectionEntity::class, DiscoverDismissedEntity::class, ArtistExtrasCacheEntity::class],
    version = DISCOVER_DB_VERSION,
    exportSchema = true,
)
abstract class DiscoverDatabase : RoomDatabase() {
    abstract fun dao(): DiscoverDao
}
