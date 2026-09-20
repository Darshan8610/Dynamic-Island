package dev.island.service

import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * View-tree owners for a [ComposeView] that is NOT inside an Activity.
 *
 * `ComposeView` looks its `LifecycleOwner`, `ViewModelStoreOwner` and `SavedStateRegistryOwner` up
 * the view tree and throws when they are missing — an Activity normally provides them, an overlay
 * window attached straight to the `WindowManager` has none. This supplies all three from the
 * service's own [Lifecycle], which is what makes Compose (and therefore animation, `LaunchedEffect`
 * and state restoration) work in an overlay.
 *
 * The [ViewModelStore] is cleared when the service dies so nothing leaks past the overlay's life.
 */
internal class OverlayComposeOwners(
    private val hostLifecycle: Lifecycle,
) : ViewModelStoreOwner, SavedStateRegistryOwner {

    private val registryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = hostLifecycle

    override val viewModelStore: ViewModelStore get() = store

    override val savedStateRegistry: SavedStateRegistry get() = registryController.savedStateRegistry

    /** Must be called during `Service.onCreate`, before the ComposeView is attached. */
    fun restore() {
        registryController.performRestore(null)
    }

    fun clear() {
        store.clear()
    }
}
