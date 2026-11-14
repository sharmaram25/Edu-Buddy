package com.edubuddy.app.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.edubuddy.app.AppContainer
import com.edubuddy.app.EduBuddyApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as EduBuddyApp).container

/** Creates a screen ViewModel with access to the app's dependency container. */
@Composable
inline fun <reified VM : ViewModel> eduViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = appContainer()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

/**
 * App-wide snackbar. Lives at the root so messages survive navigation, e.g. "Task deleted · Undo"
 * shown after leaving the editor.
 */
class Messenger(private val host: SnackbarHostState, private val scope: CoroutineScope) {
    fun show(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            val result = host.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                withDismissAction = actionLabel == null,
                duration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }

    fun undo(message: String, onUndo: () -> Unit) = show(message, "Undo", onUndo)
}

val LocalMessenger = staticCompositionLocalOf<Messenger> { error("Messenger not provided") }

/**
 * Base for screen ViewModels. Writes go through [write], which runs on the app scope so a write
 * started just before navigating away is never cancelled half-way.
 */
abstract class EduViewModel(protected val c: AppContainer) : ViewModel() {
    protected fun write(block: suspend () -> Unit) {
        c.appScope.launch { block() }
    }
}
