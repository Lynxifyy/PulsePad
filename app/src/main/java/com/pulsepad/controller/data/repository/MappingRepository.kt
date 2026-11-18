package com.pulsepad.controller.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pulsepad.controller.domain.engine.LatencyExpectation
import com.pulsepad.controller.domain.engine.ProfileCodec
import com.pulsepad.controller.domain.model.ControllerButton
import com.pulsepad.controller.domain.model.MappedAction
import com.pulsepad.controller.domain.model.MappingConfiguration
import com.pulsepad.controller.domain.model.Point
import com.pulsepad.controller.domain.model.StickConfig
import com.pulsepad.controller.domain.model.TriggerConfig
import com.pulsepad.controller.util.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.mappingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pulsepad_mappings"
)

/**
 * Holds the live mapping configuration and persists it between launches.
 *
 * The configuration is the single source of truth for both the UI and the
 * injection engine, so it is exposed as one [StateFlow] rather than as separate
 * streams that could disagree. Writes go to DataStore on the caller's coroutine;
 * the in-memory value updates first so the UI stays responsive.
 *
 * The serialised form is the profile JSON, which is also what export/import uses,
 * so there is exactly one format to keep stable.
 */
@Singleton
class MappingRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val _configuration = MutableStateFlow(MappingConfiguration())
    val configuration: StateFlow<MappingConfiguration> = _configuration.asStateFlow()

    private val _injectionIntervalMs = MutableStateFlow(LatencyExpectation.MIN_INTERVAL_MS)
    val injectionIntervalMs: StateFlow<Int> = _injectionIntervalMs.asStateFlow()

    private val _turboRateHz = MutableStateFlow(DEFAULT_TURBO_RATE_HZ)
    val turboRateHz: StateFlow<Int> = _turboRateHz.asStateFlow()

    @Volatile
    private var loaded = false

    /** Loads the persisted configuration once; safe to call repeatedly. */
    suspend fun load() {
        if (loaded) return
        val preferences: Preferences? = runCatching {
            context.mappingDataStore.data.first()
        }.getOrElse { error ->
            AppLog.w(TAG, "Mapping store unreadable; starting from defaults", error)
            null
        }
        preferences?.let { prefs ->
            prefs[CONFIGURATION_KEY]?.let { encoded ->
                decodeConfiguration(encoded)?.let { _configuration.value = it }
            }
            prefs[INJECTION_INTERVAL_KEY]?.let { _injectionIntervalMs.value = it }
            prefs[TURBO_RATE_KEY]?.let { _turboRateHz.value = it }
        }
        loaded = true
    }

    suspend fun update(transform: (MappingConfiguration) -> MappingConfiguration) {
        val updated = transform(_configuration.value)
        _configuration.value = updated
        persistConfiguration(updated)
    }

    suspend fun setMapping(button: ControllerButton, action: MappedAction) {
        update { it.withMapping(button, action) }
    }

    suspend fun clearMapping(button: ControllerButton) {
        update { it.withoutMapping(button) }
    }

    suspend fun setLeftStick(config: StickConfig) {
        update { it.copy(leftStick = config) }
    }

    suspend fun setRightStick(config: StickConfig) {
        update { it.copy(rightStick = config) }
    }

    suspend fun setTrigger(config: TriggerConfig) {
        update { it.copy(trigger = config) }
    }

    suspend fun setInjectionInterval(intervalMs: Int) {
        val clamped = intervalMs.coerceIn(
            LatencyExpectation.MIN_INTERVAL_MS,
            LatencyExpectation.MAX_INTERVAL_MS
        )
        _injectionIntervalMs.value = clamped
        writePreference(INJECTION_INTERVAL_KEY, clamped)
    }

    suspend fun setTurboRate(rateHz: Int) {
        val clamped = rateHz.coerceIn(
            MappedAction.Turbo.TURBO_MIN_HZ,
            MappedAction.Turbo.TURBO_MAX_HZ
        )
        _turboRateHz.value = clamped
        writePreference(TURBO_RATE_KEY, clamped)
    }

    /** Replaces the whole configuration, used by profile import. */
    suspend fun replace(configuration: MappingConfiguration) {
        _configuration.value = configuration
        persistConfiguration(configuration)
    }

    /** Encodes the configuration as the export JSON. */
    fun encode(configuration: MappingConfiguration = _configuration.value): String =
        ProfileCodec.encode(configuration)

    /**
     * Decodes an exported profile.
     *
     * Returns null instead of throwing so a malformed file pasted in by the user
     * produces a message, not a crash.
     */
    fun decodeConfiguration(encoded: String): MappingConfiguration? =
        ProfileCodec.decode(encoded)

    private suspend fun persistConfiguration(configuration: MappingConfiguration) {
        writePreference(CONFIGURATION_KEY, encode(configuration))
    }

    private suspend fun writePreference(key: Preferences.Key<String>, value: String) {
        runCatching {
            context.mappingDataStore.edit { it[key] = value }
        }.onFailure { error ->
            AppLog.w(TAG, "Could not persist mapping change", error)
        }
    }

    private suspend fun writePreference(key: Preferences.Key<Int>, value: Int) {
        runCatching {
            context.mappingDataStore.edit { it[key] = value }
        }.onFailure { error ->
            AppLog.w(TAG, "Could not persist tuning change", error)
        }
    }

    private companion object {
        const val TAG = "MappingRepository"
        const val DEFAULT_TURBO_RATE_HZ = 12

        val CONFIGURATION_KEY = stringPreferencesKey("configuration_json")
        val INJECTION_INTERVAL_KEY = intPreferencesKey("injection_interval_ms")
        val TURBO_RATE_KEY = intPreferencesKey("turbo_rate_hz")
    }
}

/** Default configuration used by the onboarding demo profile. */
fun defaultMappingConfiguration(): MappingConfiguration = MappingConfiguration(
    buttonMappings = mapOf(
        ControllerButton.CROSS to MappedAction.Tap(Point(0.5f, 0.75f)),
        ControllerButton.CIRCLE to MappedAction.Tap(Point(0.5f, 0.25f)),
        ControllerButton.R1 to MappedAction.Turbo(Point(0.85f, 0.5f), rateHz = 12),
        ControllerButton.L2 to MappedAction.Hold(Point(0.15f, 0.5f), durationMs = 500)
    )
)
