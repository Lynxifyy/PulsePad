package com.pulsepad.controller.service.accessibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide bridge between [PulsePadAccessibilityService] and the UI layer.
 *
 * The service is created and destroyed by the system, not by the app, so there is
 * no safe way to inject it. Instead it publishes its own liveness here and the
 * presentation layer observes [connectionState]. The service instance itself is
 * retained only so the injection engine can call `dispatchGesture` on it.
 */
object AccessibilityServiceBridge {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    @Volatile
    private var service: PulsePadAccessibilityService? = null

    fun onServiceConnected(connectedService: PulsePadAccessibilityService) {
        service = connectedService
        _connectionState.value = ConnectionState.CONNECTED
    }

    fun onServiceDisconnected() {
        service = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun onServiceInterrupted() {
        service = null
        _connectionState.value = ConnectionState.INTERRUPTED
    }

    /** Null when the service is not currently running. */
    fun currentService(): PulsePadAccessibilityService? = service

    enum class ConnectionState {
        DISCONNECTED,

        /** The service is running; system-wide gesture dispatch is possible. */
        CONNECTED,

        /**
         * The system or the user disabled the service while the app was running,
         * usually because the OS killed it to reclaim memory.
         */
        INTERRUPTED
    }
}
