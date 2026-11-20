package app.winters.octo.discover

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// The bundled listening history (DiscoverTakeout.kt), read from the app's
// assets the first time a batch asks for it and kept in memory from then
// on. Nothing of it is sent to the server, written to disk or logged; a
// file that is missing or does not read is no seed (an empty one).
@Singleton
class TakeoutSeedSource @Inject constructor(@ApplicationContext private val context: Context) {
    val seed: TakeoutSeed by lazy {
        val text = try {
            context.assets.open(TAKEOUT_SEED_FILE).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        }
        parseTakeoutSeed(text)
    }
}
