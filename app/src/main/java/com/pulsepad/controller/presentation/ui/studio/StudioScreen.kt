package com.pulsepad.controller.presentation.ui.studio

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulsepad.controller.R
import com.pulsepad.controller.domain.model.ControllerButton
import com.pulsepad.controller.presentation.viewmodel.StudioModel
import com.pulsepad.controller.presentation.viewmodel.StudioViewModel

/**
 * The 3D Studio: a live preview of the selected controller that mirrors the
 * physical pad's presses, and the entry point for mapping a control.
 *
 * It picks between the Filament renderer and the Compose schematic from the
 * device capability probe, and falls back to the schematic if a model fails to
 * load, so the screen is never blank or crashing on an unsupported device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    onMapButton: (ControllerButton) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StudioViewModel = hiltViewModel()
) {
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val controllerState by viewModel.controllerState.collectAsStateWithLifecycle()

    val pressed = controllerState.pressedButtons
    // Until the probe answers, assume the schematic: it is the safe default and
    // avoids briefly constructing a Filament engine on a device that cannot host one.
    val canRender3d = capabilities?.openGlEs30Available == true

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.studio_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ModelSelector(
                selected = selectedModel,
                onSelect = viewModel::selectModel
            )

            if (!canRender3d && capabilities != null) {
                SchematicNotice()
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.15f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Crossfade(
                    targetState = selectedModel,
                    animationSpec = tween(durationMillis = 320),
                    label = "modelSwap"
                ) { model ->
                    if (canRender3d) {
                        ControllerSceneView(
                            assetName = assetNameFor(model),
                            pressedButtons = pressed,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        ControllerSchematicCanvas(
                            family = model.family,
                            pressedButtons = pressed,
                            selectedButton = null,
                            onButtonSelected = onMapButton,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            PressedReadout(pressed = pressed, model = selectedModel)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Row(modifier = Modifier.padding(16.dp)) {
                    Icon(
                        imageVector = Icons.Filled.VideogameAsset,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.studio_legend_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.studio_legend_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelSelector(selected: StudioModel, onSelect: (StudioModel) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.studio_selector_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(StudioModel.entries.toList()) { model ->
                FilterChip(
                    selected = model == selected,
                    onClick = { onSelect(model) },
                    label = { Text(stringResource(modelLabelRes(model))) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.VideogameAsset,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    }
}

@Composable
private fun SchematicNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.studio_schematic_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/** Names the controls currently held, so highlighting is readable without looking. */
@Composable
private fun PressedReadout(pressed: Set<ControllerButton>, model: StudioModel) {
    val active = pressed.isNotEmpty()
    // `map` is inline, so the resource lookup stays inside the composition; the
    // join below then works on plain strings.
    val labels = pressed.map { button ->
        stringResource(ButtonLabels.labelRes(button, model.family))
    }
    val text = if (active) {
        labels.joinToString(separator = " + ")
    } else {
        stringResource(R.string.studio_no_controller)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

private fun assetNameFor(model: StudioModel): String = when (model) {
    StudioModel.DUALSHOCK_4 -> "models/dualshock4.glb"
    StudioModel.DUALSENSE -> "models/dualsense.glb"
    StudioModel.XBOX_WIRELESS -> "models/xbox_wireless.glb"
}

private fun modelLabelRes(model: StudioModel): Int = when (model) {
    StudioModel.DUALSHOCK_4 -> R.string.studio_model_dualshock4
    StudioModel.DUALSENSE -> R.string.studio_model_dualsense
    StudioModel.XBOX_WIRELESS -> R.string.studio_model_xbox
}
