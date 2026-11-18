package com.pulsepad.controller.domain.engine

import com.pulsepad.controller.domain.model.ControllerInput
import com.pulsepad.controller.domain.model.ControllerState

/**
 * Folds a stream of [ControllerInput] events into a [ControllerState] snapshot.
 *
 * Kept pure so the live-highlight view and the injection engine can both be
 * driven from the same deterministic reduction in tests.
 */
object ControllerStateReducer {

    fun reduce(state: ControllerState, input: ControllerInput): ControllerState = when (input) {
        is ControllerInput.Button -> state
            .copy(deviceId = input.deviceId)
            .withButton(input.button, input.pressed)

        is ControllerInput.Axis -> state
            .copy(deviceId = input.deviceId)
            .withAxis(input.axis, input.value)
    }

    fun reduce(state: ControllerState, inputs: Iterable<ControllerInput>): ControllerState =
        inputs.fold(state) { current, input -> reduce(current, input) }
}
