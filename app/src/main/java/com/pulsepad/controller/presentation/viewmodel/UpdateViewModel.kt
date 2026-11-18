package com.pulsepad.controller.presentation.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsepad.controller.data.repository.UpdateRepository
import com.pulsepad.controller.domain.engine.AvailableUpdate
import com.pulsepad.controller.domain.model.AppVersion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the update screen is currently doing. */
enum class UpdatePhase {
    /** Nothing checked yet, or the result was cleared. */
    IDLE,

    /** A check is in flight. */
    CHECKING,

    /** The running build is the newest available. */
    UP_TO_DATE,

    /** A newer release exists and can be downloaded. */
    UPDATE_AVAILABLE,

    /** Downloading or handing the APK to the installer. */
    DOWNLOADING,

    /** The install intent was launched; the user finishes it in the system UI. */
    INSTALLING,

    /** The check or the download failed. */
    FAILED
}

data class UpdateUiState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val currentVersion: AppVersion = AppVersion(0, 0, 0),
    val update: AvailableUpdate? = null,
    /** True when the platform will accept an install from PulsePad. */
    val canSelfInstall: Boolean = true,
    /** Set when the release body is long, so the screen can collapse it. */
    val releaseNotes: String = ""
)

/**
 * Backs the update screen.
 *
 * The check is on demand rather than automatic: PulsePad is used on tablets that
 * may be offline, and a background network call on every launch would be both
 * surprising and wasteful. The screen is opened from the overflow menu, so the
 * user is already asking for it.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateRepository: UpdateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        UpdateUiState(
            currentVersion = updateRepository.currentVersion,
            canSelfInstall = updateRepository.canSelfInstall()
        )
    )
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    fun checkForUpdates(allowPrerelease: Boolean = false) {
        _uiState.value = _uiState.value.copy(phase = UpdatePhase.CHECKING)
        viewModelScope.launch {
            val update = updateRepository.checkForUpdate(allowPrerelease)
            _uiState.value = _uiState.value.copy(
                phase = if (update == null) UpdatePhase.UP_TO_DATE else UpdatePhase.UPDATE_AVAILABLE,
                update = update,
                releaseNotes = update?.releaseNotes.orEmpty(),
                canSelfInstall = updateRepository.canSelfInstall()
            )
        }
    }

    /** Opens the settings page where the install permission is granted. */
    fun installPermissionIntent(): Intent = updateRepository.installPermissionIntent()

    fun downloadAndInstall() {
        val update = _uiState.value.update ?: return
        if (!updateRepository.canSelfInstall()) {
            // Re-read the permission: the user may have just granted it in settings.
            _uiState.value = _uiState.value.copy(canSelfInstall = false)
            return
        }
        _uiState.value = _uiState.value.copy(phase = UpdatePhase.DOWNLOADING)
        viewModelScope.launch {
            val launched = updateRepository.downloadAndInstall(update)
            _uiState.value = _uiState.value.copy(
                phase = if (launched) UpdatePhase.INSTALLING else UpdatePhase.FAILED
            )
        }
    }

    /** Re-reads the install permission after the user returns from settings. */
    fun refreshInstallPermission() {
        _uiState.value = _uiState.value.copy(canSelfInstall = updateRepository.canSelfInstall())
    }
}
