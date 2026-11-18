package com.pulsepad.controller.presentation.ui.mapping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsepad.controller.R
import com.pulsepad.controller.domain.model.ControllerButton
import com.pulsepad.controller.domain.model.ControllerFamily
import com.pulsepad.controller.domain.model.MacroStep
import com.pulsepad.controller.domain.model.MappedAction
import com.pulsepad.controller.domain.model.Point
import com.pulsepad.controller.presentation.ui.studio.ButtonLabels
import kotlin.math.roundToInt

/**
 * The bottom sheet that assigns an action to one control.
 *
 * It edits a local draft and only writes back on Save, so an accidental tap on the
 * pad does not silently rewrite the profile. The available fields follow the
 * selected action type, which keeps the sheet short instead of showing every
 * option at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MappingEditorSheet(
    button: ControllerButton,
    family: ControllerFamily,
    initialAction: MappedAction,
    onSave: (MappedAction) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val buttonLabel = stringResource(ButtonLabels.labelRes(button, family))

    var actionType by remember(button, initialAction) { mutableStateOf(actionTypeOf(initialAction)) }
    var marker by remember(button, initialAction) { mutableStateOf(initialPoint(initialAction)) }
    var holdMs by remember(button, initialAction) {
        mutableStateOf((initialAction as? MappedAction.Hold)?.durationMs ?: DEFAULT_HOLD_MS)
    }
    var turboRate by remember(button, initialAction) {
        mutableStateOf((initialAction as? MappedAction.Turbo)?.rateHz ?: DEFAULT_TURBO_HZ)
    }
    var swipePoints by remember(button, initialAction) {
        mutableStateOf((initialAction as? MappedAction.Swipe)?.path ?: emptyList())
    }
    var swipeDuration by remember(button, initialAction) {
        mutableStateOf((initialAction as? MappedAction.Swipe)?.durationMs ?: DEFAULT_SWIPE_MS)
    }
    var macroSteps by remember(button, initialAction) {
        mutableStateOf((initialAction as? MappedAction.Macro)?.steps ?: emptyList())
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = SHEET_MAX_HEIGHT)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.mapping_editor_title, buttonLabel),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.profiles_cancel)
                    )
                }
            }

            ActionTypeSelector(
                selected = actionType,
                onSelect = { actionType = it }
            )

            when (actionType) {
                ActionType.DISABLED -> Unit

                ActionType.TAP, ActionType.HOLD, ActionType.TURBO -> {
                    TargetPad(
                        marker = marker,
                        onMarkerMoved = { marker = it },
                        contentDescription = stringResource(R.string.mapping_preview_label)
                    )
                    Text(
                        text = stringResource(R.string.mapping_position_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                ActionType.SWIPE -> {
                    TargetPad(
                        marker = marker,
                        onMarkerMoved = { marker = it },
                        swipePath = swipePoints,
                        contentDescription = stringResource(R.string.mapping_preview_label),
                        onPadTapped = { point ->
                            if (swipePoints.size < MAX_SWIPE_POINTS) {
                                swipePoints = swipePoints + point
                            }
                        }
                    )
                    Text(
                        text = stringResource(R.string.mapping_swipe_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(
                            R.string.mapping_swipe_point_count,
                            swipePoints.size,
                            MAX_SWIPE_POINTS
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(
                        onClick = { swipePoints = emptyList() },
                        enabled = swipePoints.isNotEmpty()
                    ) {
                        Text(stringResource(R.string.mapping_swipe_clear))
                    }
                }

                ActionType.MACRO -> {
                    TargetPad(
                        marker = marker,
                        onMarkerMoved = { marker = it },
                        macroPoints = macroSteps.filterIsInstance<MacroStep.Tap>().map { it.at },
                        contentDescription = stringResource(R.string.mapping_preview_label)
                    )
                    Text(
                        text = stringResource(R.string.mapping_macro_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { macroSteps = macroSteps + MacroStep.Tap(marker) },
                            enabled = macroSteps.size < MAX_MACRO_STEPS
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.mapping_macro_add_tap))
                        }
                        OutlinedButton(
                            onClick = { macroSteps = macroSteps + MacroStep.Delay(DEFAULT_MACRO_DELAY_MS) },
                            enabled = macroSteps.size < MAX_MACRO_STEPS
                        ) {
                            Text(stringResource(R.string.mapping_macro_add_delay))
                        }
                    }
                    if (macroSteps.isEmpty()) {
                        Text(
                            text = stringResource(R.string.mapping_macro_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        macroSteps.forEachIndexed { index, step ->
                            MacroStepRow(
                                index = index,
                                step = step,
                                onRemove = { macroSteps = macroSteps.filterIndexed { i, _ -> i != index } }
                            )
                        }
                    }
                }
            }

            when (actionType) {
                ActionType.HOLD -> LabelledSliderRow(
                    label = stringResource(R.string.mapping_hold_duration_label),
                    valueLabel = stringResource(R.string.mapping_hold_duration_value, holdMs),
                    value = holdMs.toFloat(),
                    valueRange = MIN_HOLD_MS.toFloat()..MAX_HOLD_MS.toFloat(),
                    onChange = { holdMs = it.roundToInt() }
                )

                ActionType.TURBO -> LabelledSliderRow(
                    label = stringResource(R.string.mapping_turbo_rate_label),
                    valueLabel = stringResource(R.string.mapping_turbo_rate_value, turboRate),
                    value = turboRate.toFloat(),
                    valueRange = MappedAction.Turbo.TURBO_MIN_HZ.toFloat()..
                        MappedAction.Turbo.TURBO_MAX_HZ.toFloat(),
                    onChange = { turboRate = it.roundToInt() }
                )

                ActionType.SWIPE -> LabelledSliderRow(
                    label = stringResource(R.string.mapping_swipe_duration_label),
                    valueLabel = stringResource(R.string.mapping_hold_duration_value, swipeDuration),
                    value = swipeDuration.toFloat(),
                    valueRange = MIN_SWIPE_MS.toFloat()..MAX_SWIPE_MS.toFloat(),
                    onChange = { swipeDuration = it.roundToInt() }
                )

                else -> Unit
            }

            Text(
                text = stringResource(R.string.mapping_preview_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { onSave(MappedAction.Disabled) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.mapping_reset))
                }
                Button(
                    onClick = {
                        onSave(
                            buildAction(
                                type = actionType,
                                marker = marker,
                                holdMs = holdMs,
                                turboRate = turboRate,
                                swipePoints = swipePoints,
                                swipeDuration = swipeDuration,
                                macroSteps = macroSteps
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.mapping_save))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MacroStepRow(index: Int, step: MacroStep, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = when (step) {
                is MacroStep.Tap -> stringResource(
                    R.string.mapping_macro_step_tap,
                    (step.at.x * 100).roundToInt(),
                    (step.at.y * 100).roundToInt()
                )

                is MacroStep.Delay -> stringResource(R.string.mapping_macro_step_delay, step.durationMs)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.mapping_macro_remove_step, index + 1)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionTypeSelector(selected: ActionType, onSelect: (ActionType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.mapping_action_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ActionType.entries.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = type == selected,
                    onClick = { onSelect(type) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = ActionType.entries.size
                    )
                ) {
                    Text(
                        text = stringResource(type.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (type == selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun LabelledSliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private enum class ActionType(val labelRes: Int) {
    DISABLED(R.string.mapping_action_disabled),
    TAP(R.string.mapping_action_tap),
    HOLD(R.string.mapping_action_hold),
    SWIPE(R.string.mapping_action_swipe),
    MACRO(R.string.mapping_action_macro),
    TURBO(R.string.mapping_action_turbo)
}

private fun actionTypeOf(action: MappedAction): ActionType = when (action) {
    MappedAction.Disabled -> ActionType.DISABLED
    is MappedAction.Tap -> ActionType.TAP
    is MappedAction.Hold -> ActionType.HOLD
    is MappedAction.Swipe -> ActionType.SWIPE
    is MappedAction.Macro -> ActionType.MACRO
    is MappedAction.Turbo -> ActionType.TURBO
}

private fun initialPoint(action: MappedAction): Point = when (action) {
    is MappedAction.Tap -> action.at
    is MappedAction.Hold -> action.at
    is MappedAction.Turbo -> action.at
    is MappedAction.Swipe -> action.path.lastOrNull() ?: DEFAULT_POINT
    is MappedAction.Macro -> action.steps.filterIsInstance<MacroStep.Tap>().lastOrNull()?.at
        ?: DEFAULT_POINT

    MappedAction.Disabled -> DEFAULT_POINT
}

private fun buildAction(
    type: ActionType,
    marker: Point,
    holdMs: Int,
    turboRate: Int,
    swipePoints: List<Point>,
    swipeDuration: Int,
    macroSteps: List<MacroStep>
): MappedAction = when (type) {
    ActionType.DISABLED -> MappedAction.Disabled
    ActionType.TAP -> MappedAction.Tap(marker)
    ActionType.HOLD -> MappedAction.Hold(marker, holdMs)
    ActionType.TURBO -> MappedAction.Turbo(marker, turboRate)
    // A swipe needs two points to be a path; the pad keeps the first one it was
    // given, so a single tap still produces a usable short swipe.
    ActionType.SWIPE -> MappedAction.Swipe(
        path = if (swipePoints.size >= 2) swipePoints else swipePoints + marker,
        durationMs = swipeDuration
    )

    ActionType.MACRO -> MappedAction.Macro(macroSteps)
}

private val DEFAULT_POINT = Point(0.5f, 0.5f)
private const val DEFAULT_HOLD_MS = 400
private const val MIN_HOLD_MS = 50
private const val MAX_HOLD_MS = 3000
private const val DEFAULT_TURBO_HZ = 12
private const val DEFAULT_SWIPE_MS = 150
private const val MIN_SWIPE_MS = 50
private const val MAX_SWIPE_MS = 1200
private const val MAX_SWIPE_POINTS = 6
private const val MAX_MACRO_STEPS = 8
private const val DEFAULT_MACRO_DELAY_MS = 150
private val SHEET_MAX_HEIGHT = 640.dp
