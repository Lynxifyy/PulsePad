package com.pulsepad.controller.di

import android.content.Context
import com.pulsepad.controller.data.bluetooth.InputDeviceScanner
import com.pulsepad.controller.util.CapabilityChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
}
