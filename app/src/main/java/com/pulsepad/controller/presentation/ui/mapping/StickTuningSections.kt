package com.pulsepad.controller.presentation.ui.mapping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pulsepad.controller.R
import com.pulsepad.controller.domain.model.CurveType
import com.pulsepad.controller.domain.model.StickConfig
import com.pulsepad.controller.domain.model.TriggerConfig
import kotlin.math.roundToInt

/** Deadzone, curve and sensitivity for one stick, with a live graph. */
@Composable
fun StickTuningSection(
    title: String,
    config: StickConfig,
    onChange: (StickConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        ResponseCurveGraph(config = config)

        LabelledSlider(
            label = stringResource(R.string.stick_deadzone_label),
            valueLabel = stringResource(
                R.string.stick_deadzone_value,
                (config.deadzone * PERCENT).roundToInt()
            ),
            value = config.deadzone,
            valueRange = 0f..StickConfig.MAX_DEADZONE,
            onChange = { onChange(config.copy(deadzone = it)) }
        )

        CurveSelector(
            selected = config.curve,
            onSelect = { onChange(config.copy(curve = it)) }
        )

        // The exponent only shapes the exponential curve, so it is only offered
        // where it has an effect.
        if (config.curve == CurveType.EXPONENTIAL) {
            LabelledSlider(
                label = stringResource(R.string.stick_exponent_label),
                valueLabel = stringResource(R.string.stick_exponent_value, config.exponent),
                value = config.exponent,
                valueRange = MIN_EXPONENT..MAX_EXPONENT,
                onChange = { onChange(config.copy(exponent = it)) }
            )
        }

        LabelledSlider(
            label = stringResource(R.string.stick_sensitivity_label),
            valueLabel = stringResource(R.string.stick_sensitivity_value, config.sensitivity),
            value = config.sensitivity,
            valueRange = 0f..StickConfig.MAX_SENSITIVITY,
            onChange = { onChange(config.copy(sensitivity = it)) }
        )
    }
}

/** Activation threshold and maximum output for the triggers. */
@Composable
fun TriggerTuningSection(
    config: TriggerConfig,
    onChange: (TriggerConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.trigger_tuning_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        LabelledSlider(
            label = stringResource(R.string.trigger_threshold_label),
            valueLabel = stringResource(
                R.string.trigger_threshold_value,
                (config.activationThreshold * PERCENT).roundToInt()
            ),
            value = config.activationThreshold,
            valueRange = 0f..1f,
            onChange = { onChange(config.copy(activationThreshold = it)) }
        )

        LabelledSlider(
            label = stringResource(R.string.trigger_max_value_label),
            valueLabel = stringResource(
                R.string.trigger_max_value_value,
                (config.maxValue * PERCENT).roundToInt()
            ),
            value = config.maxValue,
            valueRange = 0f..1f,
            onChange = { onChange(config.copy(maxValue = it)) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurveSelector(selected: CurveType, onSelect: (CurveType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.stick_curve_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            CurveType.entries.forEachIndexed { index, curve ->
                SegmentedButton(
                    selected = curve == selected,
                    onClick = { onSelect(curve) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = CurveType.entries.size
                    )
                ) {
                    Text(
                        text = stringResource(curveLabelRes(curve)),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun LabelledSlider(
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

private fun curveLabelRes(curve: CurveType): Int = when (curve) {
    CurveType.LINEAR -> R.string.stick_curve_linear
    CurveType.EXPONENTIAL -> R.string.stick_curve_exponential
    CurveType.S_CURVE -> R.string.stick_curve_scurve
}

private const val PERCENT = 100f
private const val MIN_EXPONENT = 1f
private const val MAX_EXPONENT = 5f
