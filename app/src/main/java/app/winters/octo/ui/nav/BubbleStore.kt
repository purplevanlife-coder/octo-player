package app.winters.octo.ui.nav

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.bubblePrefs by preferencesDataStore(BubbleKeys.STORE)
private val BUBBLE_X = floatPreferencesKey(BubbleKeys.X)
private val BUBBLE_Y = floatPreferencesKey(BubbleKeys.Y)
private val BUBBLE_SIZE = floatPreferencesKey(BubbleKeys.SIZE)

// Where this phone keeps the now-playing bubble (Music Space): its place,
// as fractions of the room it moves in, and its size. Nothing else; it is
// a look on this phone, not part of a backup.
@Singleton
class BubbleStore internal constructor(private val data: DataStore<Preferences>, private val scope: CoroutineScope) {
    @Inject constructor(@ApplicationContext context: Context) : this(context.bubblePrefs, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    val place: Flow<BubblePlace> = data.data.map { stored ->
        val defaults = BubblePlace()
        BubblePlace(
            x = stored[BUBBLE_X] ?: defaults.x,
            y = stored[BUBBLE_Y] ?: defaults.y,
            sizeDp = stored[BUBBLE_SIZE] ?: defaults.sizeDp,
        ).sane()
    }

    fun keep(place: BubblePlace) {
        val sane = place.sane()
        scope.launch {
            data.edit {
                it[BUBBLE_X] = sane.x
                it[BUBBLE_Y] = sane.y
                it[BUBBLE_SIZE] = sane.sizeDp
            }
        }
    }
}

// The bubble's kept place for the shell. Null until it is read, so the
// bubble never shows in its default place for a frame and then jumps.
@HiltViewModel
class BubbleViewModel @Inject constructor(private val store: BubbleStore) : ViewModel() {
    val place: StateFlow<BubblePlace?> = store.place.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun keep(place: BubblePlace) = store.keep(place)
}
