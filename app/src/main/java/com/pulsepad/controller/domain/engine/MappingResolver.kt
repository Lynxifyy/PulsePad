package com.pulsepad.controller.domain.engine

import com.pulsepad.controller.domain.model.ButtonMapping
import com.pulsepad.controller.domain.model.ControllerButton
import com.pulsepad.controller.domain.model.MacroStep
import com.pulsepad.controller.domain.model.MappedAction
import com.pulsepad.controller.domain.model.MappingConfiguration

/**
 * Turns a button edge into the work the injection engine has to do.
 *
 * Keeping this separate from the engine means the decision - which gesture, in
 * what order, for how long - is unit testable, while the engine stays a thin
 * wrapper over `dispatchGesture`. The resolver is deliberately stateless: the
 * caller owns the "is this button currently down" state, so a replayed event or
 * a lost release cannot desynchronise the resolver.
 */
object MappingResolver {

    /**
     * What the engine should do in response to one edge.
     *
     * [repeatEveryMs] is non-null only for turbo, where the engine keeps tapping
     * until the matching [Release] arrives.
     */
    data class Plan(
        val actions: List<MappedAction>,
        val repeatEveryMs: Long? = null
    ) {
        val isNoOp: Boolean
            get() = actions.isEmpty() && repeatEveryMs == null

        companion object {
            val NONE = Plan(emptyList())
        }
    }

    /** The plan for a button being pressed. */
    fun onPress(
        button: ControllerButton,
        configuration: MappingConfiguration
    ): Plan {
        return when (val action = configuration.actionFor(button)) {
            MappedAction.Disabled -> Plan.NONE
            is MappedAction.Tap -> Plan(listOf(action))
            is MappedAction.Hold -> Plan(listOf(action))
            is MappedAction.Swipe -> Plan(listOf(action))
            is MappedAction.Macro -> Plan(listOf(action))
            is MappedAction.Turbo -> Plan(
                actions = listOf(MappedAction.Tap(action.at)),
                repeatEveryMs = InputTransformEngine.turboIntervalMs(action.rateHz)
            )
        }
    }

    /**
     * Whether releasing [button] has to stop a repeat loop.
     *
     * Releasing never dispatches a gesture of its own - a hold already carries its
     * duration and the system plays it out - but a turbo assignment keeps tapping
     * until the release arrives, so the engine uses this to know which loops to
     * cancel.
     */
    fun stopsRepeatOnRelease(
        button: ControllerButton,
        configuration: MappingConfiguration
    ): Boolean = configuration.actionFor(button) is MappedAction.Turbo

    /** The steps of a macro that are actual taps, in order. */
    fun macroTaps(action: MappedAction.Macro): List<MacroStep.Tap> =
        action.steps.filterIsInstance<MacroStep.Tap>()

    /** The total scheduled duration of a macro, including explicit delays. */
    fun macroDurationMs(action: MappedAction.Macro): Long =
        action.steps.sumOf { step ->
            when (step) {
                is MacroStep.Tap -> step.holdMs.toLong()
                is MacroStep.Delay -> step.durationMs.toLong()
            }
        }

    /** Every button with a live assignment, for the mapping summary. */
    fun mappedButtons(configuration: MappingConfiguration): List<ButtonMapping> =
        configuration.buttonMappings
            .filterValues { it !is MappedAction.Disabled }
            .map { (button, action) -> ButtonMapping(button, action) }
            .sortedBy { it.source.ordinal }

    /**
     * Every explicit assignment, including disabled ones, in enum order.
     *
     * The editor needs the disabled entries too, so a control the user switched off
     * still reads as "off" rather than as "never configured".
     */
    fun explicitAssignments(configuration: MappingConfiguration): List<ButtonMapping> =
        configuration.buttonMappings
            .map { (button, action) -> ButtonMapping(button, action) }
            .sortedBy { it.source.ordinal }
}
