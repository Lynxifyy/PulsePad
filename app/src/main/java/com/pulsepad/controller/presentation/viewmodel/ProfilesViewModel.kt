package com.pulsepad.controller.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsepad.controller.data.repository.ProfileRepository
import com.pulsepad.controller.data.repository.SavedProfile
import com.pulsepad.controller.domain.engine.ProfileCodec
import com.pulsepad.controller.domain.model.ControllerFamily
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the Profiles destination.
 *
 * The screen is deliberately thin: it reports one-shot results through [message]
 * rather than holding transient UI state, so a rotation does not lose the fact that
 * a save just succeeded.
 */
@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val repository: ProfileRepository
) : ViewModel() {

    val profiles: StateFlow<List<SavedProfile>> = repository.observeProfiles()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    private val _message = MutableStateFlow<ProfileEvent?>(null)
    val message: StateFlow<ProfileEvent?> = _message.asStateFlow()

    fun saveCurrent(name: String, family: ControllerFamily? = null) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.saveCurrentAs(trimmed, family) }
    }

    fun apply(profile: SavedProfile) {
        viewModelScope.launch {
            repository.apply(profile)
            _message.value = ProfileEvent.Applied
        }
    }

    fun delete(profile: SavedProfile) {
        viewModelScope.launch {
            repository.delete(profile.id)
            _message.value = ProfileEvent.Deleted
        }
    }

    fun export(profile: SavedProfile): String = repository.export(profile)

    /** Whether the pasted text is a profile, used to gate the import button. */
    fun looksLikeProfile(json: String): Boolean = ProfileCodec.looksLikeProfile(json)

    /** Imports a pasted profile; reports whether the JSON was accepted. */
    fun import(name: String, json: String) {
        viewModelScope.launch {
            val finalName = name.trim().ifEmpty { DEFAULT_IMPORT_NAME }
            val accepted = repository.import(finalName, json)
            _message.value = if (accepted) {
                ProfileEvent.Imported(finalName)
            } else {
                ProfileEvent.ImportRejected
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val DEFAULT_IMPORT_NAME = "Imported profile"
    }
}

/** One-shot results the Profiles screen turns into a snackbar. */
sealed interface ProfileEvent {
    data object Applied : ProfileEvent

    data object Deleted : ProfileEvent

    data class Imported(val name: String) : ProfileEvent

    data object ImportRejected : ProfileEvent
}
