package app.winters.octo.ui.discover

import androidx.lifecycle.ViewModel
import app.winters.octo.discover.DiscoverRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

// Held by MainShell so Discover's repository starts with the app: it reads
// the kept batch from disk (and makes a stale one again behind the app)
// long before the tab is opened, so the tab's first frame has it.
@HiltViewModel
class DiscoverShellViewModel @Inject constructor(repository: DiscoverRepository) : ViewModel() {
    init {
        repository.warm()
    }
}
