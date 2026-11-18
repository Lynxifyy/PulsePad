package com.pulsepad.controller.presentation.ui.studio

import androidx.annotation.StringRes
import com.pulsepad.controller.R
import com.pulsepad.controller.domain.model.ControllerButton
import com.pulsepad.controller.domain.model.ControllerFamily

/**
 * User-facing labels for a control.
 *
 * The canonical button names are PlayStation-flavoured, so an Xbox pad shows A/B/X/Y
 * on its face buttons while the underlying enum stays [ControllerButton.CROSS] and
 * friends. Keeping this in one place means the studio overlay, the schematic and
 * the mapping editor always agree.
 */
object ButtonLabels {

    @StringRes
    fun labelRes(button: ControllerButton, family: ControllerFamily): Int = when (button) {
        ControllerButton.CROSS -> if (family == ControllerFamily.XBOX_WIRELESS) {
            R.string.button_a
        } else {
            R.string.button_cross
        }

        ControllerButton.CIRCLE -> if (family == ControllerFamily.XBOX_WIRELESS) {
            R.string.button_b
        } else {
            R.string.button_circle
        }

        ControllerButton.SQUARE -> if (family == ControllerFamily.XBOX_WIRELESS) {
            R.string.button_x
        } else {
            R.string.button_square
        }

        ControllerButton.TRIANGLE -> if (family == ControllerFamily.XBOX_WIRELESS) {
            R.string.button_y
        } else {
            R.string.button_triangle
        }

        ControllerButton.L1 -> R.string.button_l1
        ControllerButton.R1 -> R.string.button_r1
        ControllerButton.L2 -> R.string.button_l2
        ControllerButton.R2 -> R.string.button_r2
        ControllerButton.L3 -> R.string.button_l3
        ControllerButton.R3 -> R.string.button_r3
        ControllerButton.DPAD_UP -> R.string.button_dpad_up
        ControllerButton.DPAD_DOWN -> R.string.button_dpad_down
        ControllerButton.DPAD_LEFT -> R.string.button_dpad_left
        ControllerButton.DPAD_RIGHT -> R.string.button_dpad_right
        ControllerButton.OPTIONS -> R.string.button_options
        ControllerButton.SHARE -> R.string.button_share
        ControllerButton.GUIDE -> R.string.button_guide
    }

    /** The label to draw on a 3D node, which must stay short. */
    @StringRes
    fun nodeLabelRes(button: ControllerButton, family: ControllerFamily): Int = labelRes(button, family)
}
