package com.pokedaisy.app.companion.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.composed
import androidx.compose.ui.focus.focusRequester

/** The game's buttons as the companion's window reads them (through the player's own GAME BUTTONS bindings). */
enum class MenuKey { UP, DOWN, LEFT, RIGHT, A, B, L, R, START, SELECT }

/**
 * The game's buttons for the companion while it's open as a window over the game (one screen, SidePanel):
 * handlers take a key newest first, like [CompanionBack] - an overlay that steers its own cursor registers over the
 * tab under it, and returns false for keys it leaves alone. B never comes here: it's the companion's back
 * ([CompanionBack]), and with nothing left to go back from it closes the window.
 *
 * [repeat]: a key held down repeating (a direction, so a cursor runs down a list).
 */
class CompanionKeys {
    private class Entry(var enabled: Boolean, var onKey: (MenuKey, Boolean) -> Boolean)

    private val entries = mutableListOf<Entry>()

    /** Hands [key] to the newest enabled handler that takes it; false = none did. */
    fun key(key: MenuKey, repeat: Boolean = false): Boolean =
        entries.asReversed().any { it.enabled && it.onKey(key, repeat) }

    @Composable
    internal fun Handler(enabled: Boolean, onKey: (MenuKey, Boolean) -> Boolean) {
        val current by rememberUpdatedState(onKey)
        val entry = remember { Entry(enabled) { k, r -> current(k, r) } }
        SideEffect { entry.enabled = enabled }
        DisposableEffect(entry) {
            entries += entry
            onDispose { entries -= entry }
        }
    }
}

val LocalCompanionKeys = staticCompositionLocalOf<CompanionKeys?> { null }

/** [onKey] gets the window's keys while [enabled] and no newer handler took them; true = taken. No-op outside it. */
@Composable
fun CompanionKeyHandler(enabled: Boolean = true, onKey: (key: MenuKey, repeat: Boolean) -> Boolean) {
    LocalCompanionKeys.current?.Handler(enabled, onKey)
}

/**
 * Where the D-pad's focus starts in the companion's window when nothing has it yet (a tap left touch mode on, a tab
 * just opened): the newest registered place - SETTINGS' cursor row ([focusStart]), an open pop-up's current choice
 * ([OptionOverlay]). Nothing registered: Compose's own entry from the edge the key points away from.
 */
class FocusStarts {
    internal val entries = mutableListOf<() -> androidx.compose.ui.focus.FocusRequester?>()

    /** Focuses the newest start; false = none, or it couldn't take focus. */
    fun focusNewest(): Boolean {
        for (e in entries.asReversed()) {
            val r = e() ?: continue
            return runCatching { r.requestFocus(); true }.getOrDefault(false)
        }
        return false
    }
}

val LocalFocusStarts = staticCompositionLocalOf<FocusStarts?> { null }

/** This element is where the D-pad starts in the companion's window while [on] (the white cursor row). No-op
 * outside the window. */
fun androidx.compose.ui.Modifier.focusStart(on: Boolean): androidx.compose.ui.Modifier = composed {
    val starts = LocalFocusStarts.current ?: return@composed this
    val requester = remember { androidx.compose.ui.focus.FocusRequester() }
    if (on) {
        DisposableEffect(requester) {
            val entry: () -> androidx.compose.ui.focus.FocusRequester? = { requester }
            starts.entries += entry
            onDispose { starts.entries -= entry }
        }
    }
    focusRequester(requester)
}
