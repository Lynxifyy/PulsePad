package com.pulsepad.controller.data.repository

import com.pulsepad.controller.data.persistence.ProfileDao
import com.pulsepad.controller.data.persistence.ProfileEntity
import com.pulsepad.controller.domain.engine.ProfileCodec
import com.pulsepad.controller.domain.model.ControllerFamily
import com.pulsepad.controller.domain.model.MappingConfiguration
import com.pulsepad.controller.util.AppLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** A profile as the UI sees it: the stored row plus its decoded configuration. */
data class SavedProfile(
    val id: Long,
    val name: String,
    val configuration: MappingConfiguration,
    val controllerFamily: ControllerFamily?,
    val updatedAtEpochMs: Long
) {
    val mappedCount: Int
        get() = configuration.mappedCount
}

/**
 * Reads and writes named profiles.
 *
 * A stored profile whose JSON no longer decodes is skipped rather than crashing the
 * list: an old or hand-edited row should cost the user that one entry, not the
 * whole screen. The same JSON format is what export/import uses, so a profile can
 * round-trip through a share sheet unchanged.
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val dao: ProfileDao,
    private val mappingRepository: MappingRepository
) {

    fun observeProfiles(): Flow<List<SavedProfile>> = dao.observeAll().map { rows ->
        rows.mapNotNull { row -> row.toSavedProfile() }
    }

    /** Saves the current configuration under [name], updating a same-named profile. */
    suspend fun saveCurrentAs(
        name: String,
        family: ControllerFamily? = null
    ): Long {
        val configuration = mappingRepository.configuration.value
        val encoded = ProfileCodec.encode(configuration)
        // Re-saving the same name updates in place, so "save" twice does not leave
        // two identical entries in the list.
        val existing = dao.findByName(name)
        return if (existing != null) {
            dao.update(
                existing.copy(
                    configurationJson = encoded,
                    controllerFamilyKey = family?.name ?: existing.controllerFamilyKey,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
            existing.id
        } else {
            dao.insert(
                ProfileEntity(
                    name = name,
                    configurationJson = encoded,
                    controllerFamilyKey = family?.name
                )
            )
        }
    }

    /** Imports a profile from exported JSON and stores it under [name]. */
    suspend fun import(name: String, json: String): Boolean {
        val configuration = ProfileCodec.decode(json) ?: return false
        dao.insert(
            ProfileEntity(
                name = name,
                configurationJson = ProfileCodec.encode(configuration)
            )
        )
        return true
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    /** Applies [profile] to the live configuration. */
    suspend fun apply(profile: SavedProfile) {
        mappingRepository.replace(profile.configuration)
    }

    /** The export JSON for [profile]. */
    fun export(profile: SavedProfile): String = ProfileCodec.encode(profile.configuration)

    /** The most recently used profile for [family], if any. */
    suspend fun latestForFamily(family: ControllerFamily): SavedProfile? =
        dao.findLatestForFamily(family.name)?.toSavedProfile()

    private fun ProfileEntity.toSavedProfile(): SavedProfile? {
        val configuration = ProfileCodec.decode(configurationJson)
        if (configuration == null) {
            AppLog.w(TAG, "Skipping profile $id: stored JSON could not be decoded")
            return null
        }
        val family = controllerFamilyKey?.let { key ->
            runCatching { ControllerFamily.valueOf(key) }.getOrNull()
        }
        return SavedProfile(
            id = id,
            name = name,
            configuration = configuration,
            controllerFamily = family,
            updatedAtEpochMs = updatedAtEpochMs
        )
    }

    private companion object {
        const val TAG = "ProfileRepository"
    }
}
