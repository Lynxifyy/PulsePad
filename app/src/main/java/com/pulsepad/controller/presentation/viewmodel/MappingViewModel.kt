package com.pulsepad.controller.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsepad.controller.data.repository.MappingRepository
import com.pulsepad.controller.domain.engine.MappingResolver
import com.pulsepad.controller.domain.model.ButtonMapping
import com.pulsepad.controller.domain.model.ControllerButton
import com.pulsepad.controller.domain.model.MappedAction
import com.pulsepad.controller.domain.model.MappingConfiguration
import com.pulsepad.controller.domain.model.StickConfig
import com.pulsepad.controller.domain.model.TriggerConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the mapping editor and the stick/trigger tuning sections.
 *
 * Every write goes through [MappingRepository], which is the single source of truth
 * shared with the injection engine, so a change made here is live for the engine
 * the moment it is saved - there is no separate "apply" step that could be missed.
 */
@HiltViewModel
class MappingViewModel @Inject constructor(
    private val repository: MappingRepository
) : ViewModel() {

    val configuration: StateFlow<MappingConfiguration> = repository.configuration

    /** Assignments that do something, for the summary list. */
    val activeMappings: StateFlow<List<ButtonMapping>> = repository.configuration
        .map { MappingResolver.mappedButtons(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch { repository.load() }
    }

    fun actionFor(button: ControllerButton): MappedAction =
        repository.configuration.value.actionFor(button)

    fun saveMapping(button: ControllerButton, action: MappedAction) {
        viewModelScope.launch { repository.setMapping(button, action) }
    }

    fun resetMapping(button: ControllerButton) {
        viewModelScope.launch { repository.clearMapping(button) }
    }

    fun setLeftStick(config: StickConfig) {
        viewModelScope.launch { repository.setLeftStick(config) }
    }

    fun setRightStick(config: StickConfig) {
        viewModelScope.launch { repository.setRightStick(config) }
    }

    fun setTrigger(config: TriggerConfig) {
        viewModelScope.launch { repository.setTrigger(config) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
