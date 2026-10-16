package com.example.pfptodo.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * One-shot UI requests that originate outside the Compose tree (launcher shortcuts,
 * the home-screen widget). The task list consumes [openVoiceInput] and resets it.
 */
object AppActions {
    val openVoiceInput = MutableStateFlow(false)
}
