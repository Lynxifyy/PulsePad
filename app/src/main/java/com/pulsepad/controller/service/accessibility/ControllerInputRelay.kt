package com.pulsepad.controller.service.accessibility

import com.pulsepad.controller.domain.model.ControllerInput
import com.pulsepad.controller.service.input.ControllerInputBus

/**
 * Forwards decoded input from the accessibility service into the shared bus.
 *
 * The service only *captures*. Deciding whether to inject belongs to the armed
 * injection engine, which subscribes to the bus; that keeps the safety rule
 * ("never inject without arming") in one place.
 */
internal object ControllerInputRelay {

    /**
     * Publishes [input] and reports whether the event was handled.
     *
     * Returning true from `onKeyEvent` consumes the controller button so the
     * foreground app does not also act on it, since PulsePad replays it as a
     * mapped gesture instead.
     */
    fun dispatch(input: ControllerInput): Boolean {
        ControllerInputBus.publish(input)
        return true
    }
}
