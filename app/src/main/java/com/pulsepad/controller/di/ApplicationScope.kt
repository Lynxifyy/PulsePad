package com.pulsepad.controller.di

import javax.inject.Qualifier

/**
 * Marks the process-wide coroutine scope.
 *
 * Long-lived components such as the injection engine outlive any screen, so they
 * cannot borrow a ViewModel's scope. Injecting a named scope also makes it obvious
 * in a constructor that the component launches work whose lifetime is the process.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
