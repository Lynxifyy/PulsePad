package com.pulsepad.controller.data.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A saved profile.
 *
 * The whole configuration is stored as the same JSON the export uses, rather than
 * as normalised tables. That is a deliberate trade: a profile is always read and
 * written whole, and sharing one format between storage and export removes any
 * chance of the two drifting apart.
 *
 * [controllerFamilyKey] lets the app auto-apply the last profile used for a given
 * controller family; it is null for a profile the user has not bound to one.
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val name: String,

    /** Serialised [com.pulsepad.controller.domain.model.MappingConfiguration]. */
    val configurationJson: String,

    val controllerFamilyKey: String? = null,

    val updatedAtEpochMs: Long = System.currentTimeMillis()
)
