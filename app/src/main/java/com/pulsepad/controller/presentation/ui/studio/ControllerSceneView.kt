package com.pulsepad.controller.presentation.ui.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.filament.MaterialInstance
import com.pulsepad.controller.R
import com.pulsepad.controller.domain.model.ControllerButton
import io.github.sceneview.SceneView
import io.github.sceneview.SceneScope
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.ModelNode as ModelNodeImpl
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberView
import kotlinx.coroutines.delay

/**
 * Renders one controller model with Filament and highlights pressed controls.
 *
 * Only composed when the capability probe reported OpenGL ES 3.0, because Filament
 * requires it. `rememberModelInstance` returns null both while the asset loads and
 * when it fails to parse; after a short grace period the studio falls back to the
 * 2D schematic rather than leaving a spinner forever.
 *
 * Highlighting keys off node names: each model ships one named node per control,
 * named after the [ControllerButton] constant. Pressing a control swaps that node's
 * material for an accent colour; releasing restores the authored material.
 */
@Composable
fun ControllerSceneView(
    assetName: String,
    pressedButtons: Set<ControllerButton>,
    modifier: Modifier = Modifier,
    onModelUnavailable: () -> Unit = {}
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val view = rememberView(engine)
    val modelInstance = rememberModelInstance(modelLoader, assetName)

    // One shared accent instance for every highlighted node. The loader owns it and
    // frees it with the loader, so swapping models never leaks a material.
    val highlightInstance = remember(materialLoader) {
        materialLoader.createColorInstance(
            color = Color(0.13f, 0.83f, 0.93f, 1f),
            metallic = 0.0f,
            roughness = 0.25f,
            reflectance = 0.5f
        )
    }

    // The loader reports neither success nor failure for a malformed asset, so
    // give it a grace period and then hand control back to the caller.
    LaunchedEffect(assetName, modelInstance) {
        if (modelInstance != null) return@LaunchedEffect
        delay(MODEL_LOAD_GRACE_MS)
        onModelUnavailable()
    }

    if (modelInstance == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.studio_loading_model),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }
        return
    }

    SceneView(
        modifier = modifier.fillMaxSize(),
        engine = engine,
        modelLoader = modelLoader,
        materialLoader = materialLoader,
        environmentLoader = environmentLoader,
        view = view,
        cameraManipulator = rememberCameraManipulator()
    ) {
        ModelNode(
            modelInstance = modelInstance,
            scaleToUnits = 0.22f,
            autoAnimate = true
        ) {
            HighlightDriver(
                modelInstance = modelInstance,
                pressedButtons = pressedButtons,
                highlight = highlightInstance,
                onUnavailable = onModelUnavailable
            )
        }
    }
}

private const val MODEL_LOAD_GRACE_MS = 4_000L

/**
 * Replaces the material of pressed nodes, from inside the scene's node scope.
 *
 * It must live in [SceneScope] because Filament is main-thread only and the
 * renderable list belongs to the scene. It draws nothing: it exists so the work
 * runs on the composition thread whenever the pressed set changes.
 */
@Composable
private fun SceneScope.HighlightDriver(
    modelInstance: ModelInstance,
    pressedButtons: Set<ControllerButton>,
    highlight: MaterialInstance,
    onUnavailable: () -> Unit
) {
    // A second wrapper over the same Filament instance: it addresses the same
    // entities the rendered node does, so material writes here are visible there.
    val node = remember(modelInstance) { ModelNodeImpl(modelInstance = modelInstance) }
    val originals = remember(modelInstance) {
        node.renderableNodes.mapNotNull { renderable ->
            renderable.name?.let { name -> name to renderable.materialInstance }
        }.toMap()
    }

    LaunchedEffect(node, originals, pressedButtons) {
        if (originals.isEmpty()) {
            onUnavailable()
            return@LaunchedEffect
        }
        node.renderableNodes.forEach { renderable ->
            val name = renderable.name ?: return@forEach
            if (pressedButtons.any { it.name == name }) {
                renderable.materialInstance = highlight
            } else {
                originals[name]?.let { renderable.materialInstance = it }
            }
        }
    }
}
