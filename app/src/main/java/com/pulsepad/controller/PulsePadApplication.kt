package com.pulsepad.controller

import android.app.Application
import com.pulsepad.controller.data.repository.MappingRepository
import com.pulsepad.controller.di.ApplicationScope
import com.pulsepad.controller.service.accessibility.InjectionCoordinator
import com.pulsepad.controller.util.AppLog
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PulsePadApplication : Application() {

    /**
     * Injected so the coordinator exists from process start, not from the first time
     * the Tuning screen happens to be opened. It owns the input subscription that
     * makes injection possible, so creating it lazily would mean mappings silently
     * did nothing until the user visited a screen they had no reason to visit.
     */
    @Inject
    lateinit var injectionCoordinator: InjectionCoordinator

    @Inject
    lateinit var mappingRepository: MappingRepository

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        AppLog.i("Application", "PulsePad starting; accessibility-only injection engine")
        // Load the persisted configuration before input can arrive, so the engine
        // resolves against the user's mappings rather than empty defaults.
        applicationScope.launch { mappingRepository.load() }
    }
}
