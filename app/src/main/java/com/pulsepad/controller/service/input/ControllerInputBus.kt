package com.pulsepad.controller.service.input

import com.pulsepad.controller.domain.model.ControllerInput
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Single fan-in point for decoded controller input.
 *
 * Two producers feed this bus: the accessibility service (`onKeyEvent` /
 * `onMotionEvent`) and the foreground capture service (which polls
 * `InputManager`). Consumers are the injection engine and the UI's live highlight.
 *
 * `extraBufferCapacity` plus `DROP_OLDEST` keeps a slow consumer from stalling
 * input dispatch: dropping the oldest sample is preferable to blocking the
 * producer thread, and for a stick sample the newest value is the relevant one.
 */
object ControllerInputBus {

    private val _inputs = MutableSharedFlow<ControllerInput>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val inputs: SharedFlow<ControllerInput> = _inputs.asSharedFlow()

    fun publish(input: ControllerInput) {
        _inputs.tryEmit(input)
    }
}
