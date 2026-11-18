package com.pulsepad.controller.domain.engine

import com.pulsepad.controller.domain.model.ControllerButton
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the contract between the bundled models and the app: the studio highlights
 * a node by looking up the pressed [ControllerButton]'s name, so a node named after
 * a button must exist in every model, and no model may name a node after a button
 * that no longer exists.
 *
 * The test reads the .glb files directly from `src/main/assets`, which is how the
 * APK packages them, and parses only the JSON chunk - enough to read the node names.
 */
class ControllerModelContractTest {

    private val modelFiles = listOf(
        "dualshock4.glb",
        "dualsense.glb",
        "xbox_wireless.glb"
    )

    @Test
    fun `each model is a well formed glb`() {
        modelFiles.forEach { name ->
            val bytes = modelFile(name).readBytes()
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals("magic for $name", GLB_MAGIC, buffer.int)
            assertEquals("version for $name", 2, buffer.int)
            assertEquals("declared length for $name", bytes.size, buffer.int)
            val jsonLength = buffer.int
            assertEquals("json chunk type for $name", CHUNK_JSON, buffer.int)
            assertTrue("json chunk present in $name", jsonLength > 0 && jsonLength <= bytes.size)
        }
    }

    @Test
    fun `every model exposes a node for every button`() {
        val expectedNames = ControllerButton.entries.map { it.name }.toSet()
        modelFiles.forEach { name ->
            val nodeNames = readNodeNames(name)
            expectedNames.forEach { buttonName ->
                assertTrue(
                    "$name must contain a node named $buttonName so it can be highlighted",
                    nodeNames.contains(buttonName)
                )
            }
        }
    }

    @Test
    fun `no model names a node after a button that does not exist`() {
        val validNames = ControllerButton.entries.map { it.name }.toSet()
        modelFiles.forEach { name ->
            readNodeNames(name)
                .filterNot { it.startsWith("shell_") }
                .forEach { nodeName ->
                    assertTrue(
                        "$name has node $nodeName which is not a ControllerButton or a shell_ part",
                        validNames.contains(nodeName)
                    )
                }
        }
    }

    @Test
    fun `the family to asset mapping covers every supported family`() {
        assertEquals(
            "models/dualshock4.glb",
            ControllerFamilyDetector.modelAssetName(com.pulsepad.controller.domain.model.ControllerFamily.DUALSHOCK_4)
        )
        assertEquals(
            "models/dualsense.glb",
            ControllerFamilyDetector.modelAssetName(com.pulsepad.controller.domain.model.ControllerFamily.DUALSENSE)
        )
        assertEquals(
            "models/xbox_wireless.glb",
            ControllerFamilyDetector.modelAssetName(
                com.pulsepad.controller.domain.model.ControllerFamily.XBOX_WIRELESS
            )
        )
        assertTrue(
            ControllerFamilyDetector.modelAssetName(
                com.pulsepad.controller.domain.model.ControllerFamily.UNKNOWN
            ) == null
        )
    }

    private fun modelFile(name: String): File {
        // Unit tests run with the module directory as the working directory.
        val file = File("src/main/assets/models/$name")
        assertTrue("missing bundled model ${file.absolutePath}", file.isFile)
        return file
    }

    /** Node names from the glTF JSON chunk, without a JSON dependency. */
    private fun readNodeNames(name: String): Set<String> {
        val bytes = modelFile(name).readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.int // magic
        buffer.int // version
        buffer.int // length
        val jsonLength = buffer.int
        buffer.int // chunk type
        val json = String(bytes, 20, jsonLength, Charsets.UTF_8)

        // The generator emits `"nodes":[{"name":"X","mesh":0},...]`. Extracting the
        // node names with a regex avoids pulling in a JSON parser just for a test.
        val nodesSection = json.substringAfter("\"nodes\":[", "")
            .substringBefore("],\"meshes\"")
        return Regex("\"name\":\"([^\"]+)\"").findAll(nodesSection)
            .map { it.groupValues[1] }
            .toSet()
    }

    private companion object {
        const val GLB_MAGIC = 0x46546C67
        const val CHUNK_JSON = 0x4E4F534A
    }
}
