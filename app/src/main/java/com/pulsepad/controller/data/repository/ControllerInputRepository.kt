package com.pulsepad.controller.data.repository

import com.pulsepad.controller.domain.engine.ControllerStateReducer
import com.pulsepad.controller.domain.model.ControllerInput
import com.pulsepad.controller.domain.model.ControllerState
import com.pulsepad.controller.service.input.ControllerInputBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reduces the raw controller input stream into an observable snapshot.
 *
 * Both the live-highlight view and the mapping editor read this instead of the bus
 * directly, so they see one consistent, already-folded state rather than having to
 * replay events themselves.
 */
@Singleton
class ControllerInputRepository @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(ControllerState())
    val state: StateFlow<ControllerState> = _state.asStateFlow()

    private val _lastInput = MutableStateFlow<ControllerInput?>(null)

    /** Most recent raw event, useful for a debug readout. */
    val lastInput: StateFlow<ControllerInput?> = _lastInput.asStateFlow()

    init {
        scope.launch {
            ControllerInputBus.inputs.collect { input ->
                _lastInput.value = input
                _state.value = ControllerStateReducer.reduce(_state.value, input)
            }
        }
    }

    /** Clears button and axis state, e.g. after the controller disconnects. */
    fun reset() {
        _state.value = ControllerState()
    }
}
