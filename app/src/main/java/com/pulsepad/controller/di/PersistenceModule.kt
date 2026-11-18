package com.pulsepad.controller.di

import android.content.Context
import androidx.room.Room
import com.pulsepad.controller.data.persistence.PulsePadDatabase
import com.pulsepad.controller.data.persistence.ProfileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PersistenceModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): PulsePadDatabase = Room.databaseBuilder(
        context,
        PulsePadDatabase::class.java,
        "pulsepad.db"
    )
        // A profile is a convenience artefact, not primary data, and the JSON export
        // is the durable copy. Rebuilding rather than migrating on a version change
        // keeps a broken schema from bricking the app.
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    @Provides
    fun provideProfileDao(database: PulsePadDatabase): ProfileDao = database.profileDao()
}
