package app.winters.octo.di

import android.content.Context
import androidx.room.Room
import app.winters.octo.discover.DISCOVER_DB_FILE
import app.winters.octo.discover.DiscoverDao
import app.winters.octo.discover.DiscoverDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Discover's own database: its batches, the picks turned down, and the
// artist extras kept on disk. Apart from the catalog (see DiscoverTables.kt).
@Module
@InstallIn(SingletonComponent::class)
object DiscoverModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): DiscoverDatabase =
        Room.databaseBuilder(context, DiscoverDatabase::class.java, DISCOVER_DB_FILE).build()

    @Provides
    fun dao(db: DiscoverDatabase): DiscoverDao = db.dao()
}
