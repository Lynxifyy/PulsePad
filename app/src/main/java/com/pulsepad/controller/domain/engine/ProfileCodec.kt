package com.pulsepad.controller.domain.engine

import com.pulsepad.controller.domain.model.MappingConfiguration
import kotlinx.serialization.json.Json

/**
 * The profile file format.
 *
 * One codec, used by both persistence and export/import, so a profile saved on the
 * device is byte-identical to the one the user shares. It is deliberately pure: no
 * Android types, so round-tripping a profile is unit testable and a malformed file
 * is a return value rather than a crash.
 *
 * Unknown keys are ignored so a profile written by a newer version still loads on
 * an older one, and defaults are always written so a profile is readable on its own
 * without knowing the app's defaults.
 */
object ProfileCodec {

    /** Bumped when the meaning of existing fields changes, not for additions. */
    const val FORMAT_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        classDiscriminator = "action"
    }

    fun encode(configuration: MappingConfiguration): String =
        json.encodeToString(MappingConfiguration.serializer(), configuration)

    /** Null when [encoded] is not a valid profile. */
    fun decode(encoded: String): MappingConfiguration? = runCatching {
        json.decodeFromString(MappingConfiguration.serializer(), encoded)
    }.getOrNull()

    /**
     * Whether the text looks like a PulsePad profile at all.
     *
     * Used by the import dialog to reject arbitrary JSON before attempting a decode,
     * so the message can say "not a profile" rather than describing a parse error.
     */
    fun looksLikeProfile(encoded: String): Boolean {
        val trimmed = encoded.trim()
        return trimmed.startsWith("{") &&
            trimmed.contains("\"buttonMappings\"") &&
            trimmed.endsWith("}")
    }
}
