package com.pulsepad.controller.service.accessibility

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.pulsepad.controller.data.bluetooth.BluetoothControllerRepository
import com.pulsepad.controller.data.repository.MappingRepository
import com.pulsepad.controller.di.ApplicationScope
import com.pulsepad.controller.domain.engine.MappingResolver
import com.pulsepad.controller.domain.engine.MappingResolver.Plan
import com.pulsepad.controller.domain.model.ControllerConnectionState
import com.pulsepad.controller.domain.model.ControllerInput
import com.pulsepad.controller.domain.model.MappedAction
import com.pulsepad.controller.service.input.ControllerInputBus
import com.pulsepad.controller.util.AppLog
import com.pulsepad.controller.util.NotificationChannels
import com.pulsepad.controller.util.OngoingNotificationFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the safety rule: nothing is injected unless the user armed the engine.
 *
 * It subscribes to the same input bus the UI observes, resolves each button edge
 * into a [Plan], and hands the plan to [AccessibilityInjectionEngine]. Arming
 * raises an ongoing notification, which is the user's visible guarantee that
 * gesture dispatch is live; disarming cancels every in-flight repeat immediately.
 *
 * Capture and injection are deliberately split. The accessibility service only
 * *captures*; it never decides to inject. That keeps the arming check on one path
 * and means a future capture source (the foreground service) needs no changes here.
 */
@Singleton
class InjectionCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: AccessibilityInjectionEngine,
    private val mappingRepository: MappingRepository,
    private val bluetoothControllerRepository: BluetoothControllerRepository,
    @ApplicationScope private val scope: CoroutineScope
) {

    private val _armed = MutableStateFlow(false)
    val armed: StateFlow<Boolean> = _armed.asStateFlow()

    private val _lastInjectionFailed = MutableStateFlow(false)

    /**
     * True when the last dispatch was refused by the system.
     *
     * Some apps and some builds refuse accessibility gestures. Surfacing this lets
     * the tuning screen show a diagnostic instead of leaving the user guessing why
     * their mappings appear to do nothing.
     */
    val lastInjectionFailed: StateFlow<Boolean> = _lastInjectionFailed.asStateFlow()

    init {
        scope.launch {
            ControllerInputBus.inputs.collect { input -> handleInput(input) }
        }
        // The OS can revoke the accessibility service at any time. Observing the
        // bridge keeps the arming state honest: a switch that says "armed" while
        // nothing can be injected would be a lie the user cannot see through.
        scope.launch {
            AccessibilityServiceBridge.connectionState.collect { state ->
                if (state != AccessibilityServiceBridge.ConnectionState.CONNECTED) {
                    onServiceLost()
                }
            }
        }
        // A disconnect pauses injection but keeps the arming state, so plugging the
        // controller back in resumes without the user having to re-arm. Any turbo
        // loop in flight is cancelled: with no input coming, it would otherwise tap
        // forever.
        scope.launch {
            bluetoothControllerRepository.connectionState.collect { state ->
                if (state !is ControllerConnectionState.Connected) {
                    engine.cancelAll()
                }
            }
        }
    }

    /**
     * Arms or disarms injection.
     *
     * Arming requires the accessibility service to be running; the return value
     * tells the caller whether the request took effect, so the UI can explain the
     * failure rather than showing a switch that silently does nothing.
     */
    fun setArmed(armed: Boolean): Boolean {
        if (armed && !engine.isAvailable()) {
            AppLog.w(TAG, "Arm requested but gesture dispatch is unavailable")
            return false
        }
        if (!armed) {
            engine.cancelAll()
            hideInjectionNotice()
        } else {
            showInjectionNotice()
        }
        _armed.value = armed
        AppLog.i(TAG, if (armed) "Injection armed" else "Injection disarmed")
        return true
    }

    /** Called when the accessibility service disappears; injection cannot continue. */
    fun onServiceLost() {
        if (!_armed.value) return
        engine.cancelAll()
        _armed.value = false
        hideInjectionNotice()
        AppLog.w(TAG, "Accessibility service lost; disarmed injection")
    }

    private fun handleInput(input: ControllerInput) {
        val button = (input as? ControllerInput.Button) ?: return
        if (!_armed.value) return

        val configuration = mappingRepository.configuration.value
        val key = button.button.name
        if (button.pressed) {
            val plan = MappingResolver.onPress(button.button, configuration)
            if (plan.isNoOp) return
            if (plan.repeatEveryMs != null) {
                val tap = plan.actions.filterIsInstance<MappedAction.Tap>().firstOrNull() ?: return
                engine.startRepeat(key, tap, plan.repeatEveryMs)
            } else {
                scope.launch { dispatch(plan) }
            }
        } else if (MappingResolver.stopsRepeatOnRelease(button.button, configuration)) {
            engine.stopRepeat(key)
        }
    }

    private suspend fun dispatch(plan: Plan) {
        plan.actions.forEach { action ->
            val accepted = engine.inject(action)
            _lastInjectionFailed.value = !accepted
            if (!accepted) AppLog.w(TAG, "Gesture refused for ${action::class.simpleName}")
        }
    }

    private fun showInjectionNotice() {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
            ?: return
        NotificationChannels.ensureChannels(context)
        val notice = OngoingNotificationFactory.buildInjectionNotice(context)
        runCatching {
            manager.notify(NotificationChannels.INJECTION_NOTIFICATION_ID, notice)
        }.onFailure { error ->
            // A missing POST_NOTIFICATIONS grant is not fatal: injection still works,
            // it is only the ongoing notice that cannot be shown.
            AppLog.w(TAG, "Injection notice could not be shown", error)
        }
    }

    private fun hideInjectionNotice() {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
            ?: return
        manager.cancel(NotificationChannels.INJECTION_NOTIFICATION_ID)
    }

    private companion object {
        const val TAG = "InjectionCoordinator"
    }
}
