package com.pulsepad.controller.domain.model

/**
 * A semantic version parsed from a release tag.
 *
 * PulsePad reads release tags such as `v1.2.0` or `1.2.0-rc.1`. Parsing lives here
 * rather than in the network layer so the comparison that decides whether to offer
 * an update is plain arithmetic that can be unit-tested without a device.
 *
 * Missing components default to zero, so `v1.2` and `1.2.0` are the same version.
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: String? = null
) : Comparable<AppVersion> {

    /**
     * Orders by number first, then puts a final release above any prerelease of
     * the same numbers: `1.2.0` is newer than `1.2.0-rc.1`, which is the
     * convention every release tool follows.
     */
    override fun compareTo(other: AppVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        return when {
            prerelease == null && other.prerelease == null -> 0
            prerelease == null -> 1
            other.prerelease == null -> -1
            else -> prerelease.compareTo(other.prerelease)
        }
    }

    /** True when this version is strictly newer than [current]. */
    fun isNewerThan(current: AppVersion): Boolean = compareTo(current) > 0

    override fun toString(): String = buildString {
        append("$major.$minor.$patch")
        if (prerelease != null) append("-$prerelease")
    }

    companion object {
        private val PATTERN = Regex(
            """^[vV]?(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:[-+](.+))?$"""
        )

        /** Returns null for anything that is not a recognisable version tag. */
        fun parse(raw: String): AppVersion? {
            val match = PATTERN.matchEntire(raw.trim()) ?: return null
            return AppVersion(
                major = match.groupValues[1].toIntOrNull() ?: return null,
                minor = match.groupValues[2].toIntOrNull() ?: 0,
                patch = match.groupValues[3].toIntOrNull() ?: 0,
                prerelease = match.groupValues[4].ifBlank { null }
            )
        }
    }
}
