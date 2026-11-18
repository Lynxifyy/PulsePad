package com.pulsepad.controller.domain.engine

import com.pulsepad.controller.domain.model.ControllerFamily

/**
 * Identifies the controller family from its reported name.
 *
 * The three supported families advertise stable, well-known names, but Fire OS
 * and third-party drivers sometimes append suffixes or use a generic label, so
 * matching is case-insensitive and substring-based rather than exact.
 *
 * Free of Android framework types so it is unit testable on the JVM.
 */
object ControllerFamilyDetector {

    private val DUALSENSE_MARKERS = listOf("dualsense", "ps5", "cfi-zct1")

    private val DUALSENSENSE_EDGE_MARKERS = listOf("dualsense edge")

    private val DUALSHOCK_4_MARKERS = listOf(
        "dualshock 4",
        "dualshock4",
        "wireless controller",
        "ps4",
        "cuh-zct"
    )

    private val XBOX_MARKERS = listOf(
        "xbox wireless controller",
        "xbox one",
        "xbox series",
        "xinput",
        "microsoft xbox"
    )

    fun detect(rawName: String?): ControllerFamily {
        val name = rawName?.lowercase()?.trim().orEmpty()
        if (name.isEmpty()) return ControllerFamily.UNKNOWN

        // DualSense is checked first: "DualSense Edge" also matches nothing else,
        // but "DualSense Wireless Controller" would otherwise hit the PS4 marker
        // for the generic "wireless controller" label.
        if (DUALSENSENSE_EDGE_MARKERS.any { name.contains(it) }) return ControllerFamily.DUALSENSE
        if (DUALSENSE_MARKERS.any { name.contains(it) }) return ControllerFamily.DUALSENSE

        if (XBOX_MARKERS.any { name.contains(it) }) return ControllerFamily.XBOX_WIRELESS

        if (DUALSHOCK_4_MARKERS.any { name.contains(it) }) return ControllerFamily.DUALSHOCK_4

        return ControllerFamily.UNKNOWN
    }

    /** Asset file name for the family's 3D model, or null when unknown. */
    fun modelAssetName(family: ControllerFamily): String? = when (family) {
        ControllerFamily.DUALSHOCK_4 -> "models/dualshock4.glb"
        ControllerFamily.DUALSENSE -> "models/dualsense.glb"
        ControllerFamily.XBOX_WIRELESS -> "models/xbox_wireless.glb"
        ControllerFamily.UNKNOWN -> null
    }
}
