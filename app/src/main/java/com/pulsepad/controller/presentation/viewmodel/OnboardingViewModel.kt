package com.pulsepad.controller.presentation.viewmodel

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsepad.controller.domain.model.DeviceCapabilities
import com.pulsepad.controller.util.AppLog
import com.pulsepad.controller.util.CapabilityChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pulsepad_onboarding"
)

/**
 * Drives the first-launch flow.
 *
 * The "seen" flag lives in its own DataStore rather than in the mapping store so a
 * user who clears their mappings is not made to sit through onboarding again.
 *
 * Onboarding is advisory, not a gate: it explains the architecture and the latency
 * trade-off, and every step can be skipped. The only thing it insists on is that the
 * user has seen it once.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val capabilityChecker: CapabilityChecker
) : ViewModel() {

    private val _completed = MutableStateFlow<Boolean?>(null)

    /** Null while the flag is still being read, so the UI does not flash. */
    val completed: StateFlow<Boolean?> = _completed.asStateFlow()

    private val _capabilities = MutableStateFlow<DeviceCapabilities?>(null)
    val capabilities: StateFlow<DeviceCapabilities?> = _capabilities.asStateFlow()

    init {
        viewModelScope.launch {
            val preferences = context.onboardingDataStore.data
                .catch { error ->
                    // A corrupt preferences file should not block launch; treat it as
                    // "not yet seen" and let the write below recreate it.
                    if (error is IOException) emit(emptyPreferences()) else throw error
                }
                .first()
            _completed.value = preferences[COMPLETED_KEY] ?: false
        }
        viewModelScope.launch {
            _capabilities.value = capabilityChecker.checkAll()
        }
    }

    fun markCompleted() {
        _completed.value = true
        viewModelScope.launch {
            runCatching {
                context.onboardingDataStore.edit { it[COMPLETED_KEY] = true }
            }.onFailure { error ->
                AppLog.w(TAG, "Could not persist onboarding completion", error)
            }
        }
    }

    /** Re-checks capabilities, e.g. after the user returns from system settings. */
    fun refreshCapabilities() {
        viewModelScope.launch { _capabilities.value = capabilityChecker.checkAll() }
    }

    private companion object {
        const val TAG = "Onboarding"
        val COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
    }
}
