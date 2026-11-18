package com.pulsepad.controller.service.accessibility

import android.view.InputEvent
import com.pulsepad.controller.domain.model.ControllerInput
import com.pulsepad.controller.service.input.ControllerInputBus

/**
 * Forwards decoded input from the accessibility service into the shared bus.
 *
 * The service only *captures*; deciding whether to inject belongs to the armed
 * injection engine, which subscribes to the bus. This keeps the safety rule in
 * one place: the service never injects on its own.
 */
internal object ControllerInputRelay {

    fun dispatch(input: ControllerInput, event: InputEvent): Boolean {
        ControllerInputBus.publish(input)
        // Consume controller events so the foreground app does not also act on
        // them; PulsePad replays them as gestures instead.
        return true
    }
}
