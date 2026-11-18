package com.pulsepad.controller.data.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.pulsepad.controller.domain.model.ConnectionErrorReason
import com.pulsepad.controller.domain.model.ControllerConnectionState
import com.pulsepad.controller.domain.model.ControllerDevice
import com.pulsepad.controller.domain.model.DiscoverySource
import com.pulsepad.controller.util.AppLog
import com.pulsepad.controller.util.BluetoothSupport
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discovers and connects Bluetooth controllers.
 *
 * Two paths exist because no single API works on every target device:
 *
 * 1. **HID host profile** - `BluetoothAdapter.getProfileProxy(HID_HOST)` returns a
 *    proxy whose `connect`/`disconnect` methods (which are `@hide` in the public
 *    SDK) drive the connection directly. This is the preferred path on standard
 *    Android.
 * 2. **Bonded devices + input devices** - several Fire OS builds return no HID
 *    host proxy at all. There, PulsePad enumerates bonded peripherals and the
 *    `InputManager` input devices, and guides the user to pair through system
 *    settings; the controller is then picked up by the capture service.
 */
@Singleton
class BluetoothControllerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val inputDeviceScanner: InputDeviceScanner
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _devices = MutableStateFlow<List<ControllerDevice>>(emptyList())
    val devices: StateFlow<List<ControllerDevice>> = _devices.asStateFlow()

    private val _connectionState = MutableStateFlow<ControllerConnectionState>(
        ControllerConnectionState.Disconnected
    )
    val connectionState: StateFlow<ControllerConnectionState> = _connectionState.asStateFlow()

    /** True when the HID host proxy is unavailable and the fallback is in use. */
    private val _hidHostUnavailable = MutableStateFlow(false)
    val hidHostUnavailable: StateFlow<Boolean> = _hidHostUnavailable.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    @Volatile
    private var hidHost: HidHostProxyController? = null

    private var receiverRegistered = false

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            AppLog.d(TAG, "HID host proxy connected")
            hidHost = HidHostProxyController(proxy)
            _hidHostUnavailable.value = false
            refresh()
        }

        override fun onServiceDisconnected(profile: Int) {
            AppLog.w(TAG, "HID host proxy disconnected")
            hidHost = null
            _hidHostUnavailable.value = true
            refresh()
        }
    }

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    AppLog.d(TAG, "ACL connected")
                    refresh()
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    AppLog.d(TAG, "ACL disconnected")
                    markDisconnectedIfCurrent(intent.bluetoothDeviceExtra())
                    refresh()
                }

                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                    if (state == BluetoothAdapter.STATE_OFF) {
                        AppLog.w(TAG, "Bluetooth turned off; clearing controller state")
                        _devices.value = emptyList()
                        _connectionState.value = ControllerConnectionState.Disconnected
                    } else if (state == BluetoothAdapter.STATE_ON) {
                        requestHidHostProxy()
                        refresh()
                    }
                }
            }
        }
    }

    fun start() {
        registerReceiver()
        requestHidHostProxy()
        refresh()
    }

    /** Re-runs discovery from every source. Safe to call repeatedly. */
    fun refresh() {
        scope.launch {
            _devices.value = withContext(Dispatchers.Default) { collectDevices() }
        }
    }

    /**
     * Shows a short scanning window. Discovery itself is event driven; the flag
     * exists so the Scan button can display progress.
     */
    fun scan() {
        _isScanning.value = true
        refresh()
        scope.launch {
            delay(SCAN_UI_MILLIS)
            _isScanning.value = false
        }
    }

    private fun collectDevices(): List<ControllerDevice> =
        ControllerDiscovery.merge(
            inputDeviceScanner.scan() + bondedPeripheralRecords() + hidHostRecords()
        )

    private fun bondedPeripheralRecords(): List<ControllerDiscovery.DiscoveryRecord> {
        val adapter = bluetoothAdapter() ?: return emptyList()
        val bonded = try {
            adapter.bondedDevices?.toList().orEmpty()
        } catch (securityException: SecurityException) {
            AppLog.w(TAG, "Bonded device list needs BLUETOOTH_CONNECT", securityException)
            return emptyList()
        }
        return bonded.mapNotNull { device ->
            val name = device.safeName() ?: return@mapNotNull null
            val majorClass = device.safeMajorClass()
            if (!ControllerDiscovery.isControllerCandidate(name, majorClass)) return@mapNotNull null
            ControllerDiscovery.DiscoveryRecord(
                name = name,
                address = device.safeAddress(),
                majorClass = majorClass,
                source = DiscoverySource.BONDED,
                isConnected = device.safeBondState() == BluetoothDevice.BOND_BONDED
            )
        }
    }

    private fun hidHostRecords(): List<ControllerDiscovery.DiscoveryRecord> {
        val proxy = hidHost ?: return emptyList()
        return proxy.connectedDevices().mapNotNull { device ->
            val name = device.safeName() ?: return@mapNotNull null
            ControllerDiscovery.DiscoveryRecord(
                name = name,
                address = device.safeAddress(),
                majorClass = null,
                source = DiscoverySource.HID_HOST,
                isConnected = true
            )
        }
    }

    private fun requestHidHostProxy() {
        val adapter = bluetoothAdapter() ?: run {
            _hidHostUnavailable.value = true
            return
        }
        val requested = try {
            adapter.getProfileProxy(context, profileListener, BluetoothSupport.hidHostProfile)
        } catch (securityException: SecurityException) {
            AppLog.w(TAG, "Missing Bluetooth permission for HID host proxy", securityException)
            false
        } catch (illegalArgumentException: IllegalArgumentException) {
            AppLog.w(TAG, "HID host profile unsupported on this build", illegalArgumentException)
            false
        }
        if (!requested) {
            AppLog.w(TAG, "HID host proxy unavailable; using bonded/input fallback")
            _hidHostUnavailable.value = true
        }
    }

    /**
     * Connects to [device] through the HID host proxy when possible.
     *
     * When the proxy is unavailable the user is guided to pair through system
     * settings: the state becomes
     * [ConnectionErrorReason.HID_HOST_UNAVAILABLE] and the UI explains the manual
     * path rather than failing silently.
     */
    suspend fun connect(device: ControllerDevice) {
        _connectionState.value = ControllerConnectionState.Connecting(device)

        val proxy = hidHost
        if (proxy == null || !proxy.isUsable) {
            AppLog.w(TAG, "No usable HID host proxy; pairing must happen in system settings")
            _connectionState.value = ControllerConnectionState.Error(
                device = device,
                reason = ConnectionErrorReason.HID_HOST_UNAVAILABLE
            )
            return
        }

        val bluetoothDevice = resolveBluetoothDevice(device)
        if (bluetoothDevice == null) {
            AppLog.w(TAG, "Could not resolve Bluetooth device for ${device.name}")
            _connectionState.value = ControllerConnectionState.Error(
                device = device,
                reason = ConnectionErrorReason.SYSTEM_REJECTED
            )
            return
        }

        val accepted = withTimeoutOrNull(CONNECT_TIMEOUT_MILLIS) {
            withContext(Dispatchers.Default) { proxy.connect(bluetoothDevice) }
        } ?: false

        if (accepted) {
            _connectionState.value = ControllerConnectionState.Connected(device)
            refresh()
        } else {
            _connectionState.value = ControllerConnectionState.Error(
                device = device,
                reason = ConnectionErrorReason.SYSTEM_REJECTED
            )
        }
    }

    suspend fun disconnect(device: ControllerDevice) {
        val proxy = hidHost
        val bluetoothDevice = resolveBluetoothDevice(device)
        if (proxy != null && proxy.isUsable && bluetoothDevice != null) {
            withTimeoutOrNull(CONNECT_TIMEOUT_MILLIS) {
                withContext(Dispatchers.Default) { proxy.disconnect(bluetoothDevice) }
            }
        }
        _connectionState.value = ControllerConnectionState.Disconnected
        refresh()
    }

    fun stop() {
        unregisterReceiver()
        hidHost = null
    }

    private fun resolveBluetoothDevice(device: ControllerDevice): BluetoothDevice? {
        val adapter = bluetoothAdapter() ?: return null
        val address = device.address ?: return null
        return try {
            adapter.getRemoteDevice(address)
        } catch (illegalArgument: IllegalArgumentException) {
            AppLog.w(TAG, "Malformed Bluetooth address for ${device.name}", illegalArgument)
            null
        }
    }

    private fun markDisconnectedIfCurrent(device: BluetoothDevice?) {
        val current = (_connectionState.value as? ControllerConnectionState.Connected)?.device
            ?: return
        val address = device?.safeAddress()
        if (address == null || current.address == null || address == current.address) {
            _connectionState.value = ControllerConnectionState.Disconnected
        }
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        try {
            context.registerReceiver(systemReceiver, filter)
            receiverRegistered = true
        } catch (securityException: SecurityException) {
            AppLog.w(TAG, "Could not register Bluetooth receiver", securityException)
        }
    }

    private fun unregisterReceiver() {
        if (!receiverRegistered) return
        try {
            context.unregisterReceiver(systemReceiver)
        } catch (illegalArgument: IllegalArgumentException) {
            AppLog.w(TAG, "Bluetooth receiver was already unregistered", illegalArgument)
        } finally {
            receiverRegistered = false
        }
    }

    private fun bluetoothAdapter(): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }

    private fun BluetoothDevice.safeName(): String? = try {
        name
    } catch (securityException: SecurityException) {
        AppLog.w(TAG, "Device name needs BLUETOOTH_CONNECT", securityException)
        null
    }

    private fun BluetoothDevice.safeAddress(): String? = try {
        address
    } catch (securityException: SecurityException) {
        AppLog.w(TAG, "Device address needs BLUETOOTH_CONNECT", securityException)
        null
    }

    private fun BluetoothDevice.safeBondState(): Int = try {
        bondState
    } catch (securityException: SecurityException) {
        BluetoothDevice.BOND_NONE
    }

    private fun BluetoothDevice.safeMajorClass(): Int? = try {
        bluetoothClass?.majorDeviceClass
    } catch (securityException: SecurityException) {
        AppLog.w(TAG, "Device class needs BLUETOOTH_CONNECT", securityException)
        null
    }

    private fun Intent.bluetoothDeviceExtra(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    private companion object {
        const val TAG = "BluetoothControllerRepository"
        const val CONNECT_TIMEOUT_MILLIS = 15_000L
        const val SCAN_UI_MILLIS = 1_200L
    }
}
