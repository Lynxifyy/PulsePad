package com.pulsepad.controller.di

import android.content.Context
import com.pulsepad.controller.data.bluetooth.InputDeviceScanner
import com.pulsepad.controller.util.CapabilityChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideCapabilityChecker(
        @ApplicationContext context: Context
    ): CapabilityChecker = CapabilityChecker(context)

    @Provides
    @Singleton
    fun provideInputDeviceScanner(
        @ApplicationContext context: Context
    ): InputDeviceScanner = InputDeviceScanner(context)

    /**
     * One scope for work whose lifetime is the process, not a screen.
     *
     * A [SupervisorJob] keeps one failing child - a gesture dispatch that throws,
     * say - from tearing down the input pipeline for everything else.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
