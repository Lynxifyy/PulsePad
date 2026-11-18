#!/usr/bin/env python3
"""Generate the three bundled controller models as glTF-binary (.glb) assets.

These are procedurally built low-poly representations, not scanned or licensed
artwork. They exist so the 3D Studio renders something recognisable in shape and
button layout on a clean checkout; see README.md for how to replace them.

Every individually addressable control is emitted as its own named node, and the
node name is the `ControllerButton` enum constant it corresponds to. That naming
is a contract with the app: the studio highlights a 3D node by looking up the
pressed button's name, so do not rename a node without updating
`ControllerButton`. Decorative parts use the `shell_` prefix.

Only the standard library is used. Output is glTF 2.0 binary with one primitive
per node, each carrying POSITION and NORMAL attributes and 16-bit indices.

Run:  python3 scripts/generate_controller_models.py
"""

from __future__ import annotations

import json
import math
import pathlib
import struct

OUTPUT_DIR = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/models"

Vec3 = tuple

# --------------------------------------------------------------------------- #
# Geometry primitives. Each returns (positions, normals, indices).
# --------------------------------------------------------------------------- #


def _normalize(v: Vec3) -> Vec3:
    length = math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
    if length == 0.0:
        return (0.0, 0.0, 1.0)
    return (v[0] / length, v[1] / length, v[2] / length)


def _cross(a: Vec3, b: Vec3) -> Vec3:
    return (
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0],
    )


def _sub(a: Vec3, b: Vec3) -> Vec3:
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def ellipsoid(center: Vec3, radii: Vec3, segments: int = 28, rings: int = 18):
    """A UV sphere scaled by `radii`, with smooth normals."""
    positions: list[Vec3] = []
    normals: list[Vec3] = []
    indices: list[int] = []

    for ring in range(rings + 1):
        phi = math.pi * ring / rings
        sin_phi, cos_phi = math.sin(phi), math.cos(phi)
        for segment in range(segments + 1):
            theta = 2.0 * math.pi * segment / segments
            unit = (sin_phi * math.cos(theta), cos_phi, sin_phi * math.sin(theta))
            positions.append(
                (
                    center[0] + unit[0] * radii[0],
                    center[1] + unit[1] * radii[1],
                    center[2] + unit[2] * radii[2],
                )
            )
            # The normal of an ellipsoid is its gradient: scale the unit
            # direction by the inverse squared radius, then renormalise.
            normals.append(
                _normalize(
                    (
                        unit[0] / (radii[0] * radii[0]),
                        unit[1] / (radii[1] * radii[1]),
                        unit[2] / (radii[2] * radii[2]),
                    )
                )
            )

    stride = segments + 1
    for ring in range(rings):
        for segment in range(segments):
            a = ring * stride + segment
            b = a + stride
            indices.extend([a, b, a + 1, a + 1, b, b + 1])
    return positions, normals, indices


def capsule(start: Vec3, end: Vec3, radius: float, segments: int = 20, rings: int = 5):
    """A cylinder with hemispherical caps, oriented from `start` to `end`."""
    axis = _normalize(_sub(end, start))
    helper = (0.0, 1.0, 0.0) if abs(axis[1]) < 0.9 else (1.0, 0.0, 0.0)
    side = _normalize(_cross(axis, helper))
    up = _normalize(_cross(side, axis))

    positions: list[Vec3] = []
    normals: list[Vec3] = []
    indices: list[int] = []

    def push(point: Vec3, normal: Vec3) -> int:
        positions.append(point)
        normals.append(normal)
        return len(positions) - 1

    def ring_point(center: Vec3, theta: float) -> Vec3:
        radial, vertical = math.cos(theta), math.sin(theta)
        return (
            center[0] + (side[0] * radial + up[0] * vertical) * radius,
            center[1] + (side[1] * radial + up[1] * vertical) * radius,
            center[2] + (side[2] * radial + up[2] * vertical) * radius,
        )

    ring_start, ring_end = [], []
    for segment in range(segments + 1):
        theta = 2.0 * math.pi * segment / segments
        radial, vertical = math.cos(theta), math.sin(theta)
        normal = _normalize(
            (
                side[0] * radial + up[0] * vertical,
                side[1] * radial + up[1] * vertical,
                side[2] * radial + up[2] * vertical,
            )
        )
        ring_start.append(push(ring_point(start, theta), normal))
        ring_end.append(push(ring_point(end, theta), normal))

    for segment in range(segments):
        a, b = ring_start[segment], ring_start[segment + 1]
        c, d = ring_end[segment], ring_end[segment + 1]
        indices.extend([a, c, b, b, c, d])

    for center, sign in ((start, -1.0), (end, 1.0)):
        base = len(positions)
        for cap_ring in range(1, rings + 1):
            phi = (math.pi / 2.0) * cap_ring / rings
            sin_phi, cos_phi = math.sin(phi), math.cos(phi)
            for segment in range(segments + 1):
                theta = 2.0 * math.pi * segment / segments
                radial, vertical = cos_phi * math.cos(theta), cos_phi * math.sin(theta)
                normal = _normalize(
                    (
                        side[0] * radial + up[0] * vertical + axis[0] * sign * sin_phi,
                        side[1] * radial + up[1] * vertical + axis[1] * sign * sin_phi,
                        side[2] * radial + up[2] * vertical + axis[2] * sign * sin_phi,
                    )
                )
                push(
                    (
                        center[0] + normal[0] * radius,
                        center[1] + normal[1] * radius,
                        center[2] + normal[2] * radius,
                    ),
                    normal,
                )
        pole = push(
            (
                center[0] + axis[0] * radius * sign,
                center[1] + axis[1] * radius * sign,
                center[2] + axis[2] * radius * sign,
            ),
            (axis[0] * sign, axis[1] * sign, axis[2] * sign),
        )
        for cap_ring in range(rings):
            row = base + cap_ring * (segments + 1)
            for segment in range(segments):
                a, b = row + segment, row + segment + 1
                if cap_ring == 0:
                    indices.extend([pole, a, b] if sign > 0 else [pole, b, a])
                else:
                    prev = row - (segments + 1)
                    c, d = prev + segment, prev + segment + 1
                    if sign > 0:
                        indices.extend([c, a, d, d, a, b])
                    else:
                        indices.extend([c, d, a, d, b, a])
    return positions, normals, indices


def box(center: Vec3, size: Vec3, yaw: float = 0.0):
    """A box with flat per-face normals, optionally rotated about the Y axis."""
    half = (size[0] / 2.0, size[1] / 2.0, size[2] / 2.0)
    corners = []
    for sx in (-1, 1):
        for sy in (-1, 1):
            for sz in (-1, 1):
                x, z = sx * half[0], sz * half[2]
                if yaw:
                    cos_y, sin_y = math.cos(yaw), math.sin(yaw)
                    x, z = x * cos_y - z * sin_y, x * sin_y + z * cos_y
                corners.append((center[0] + x, center[1] + sy * half[1], center[2] + z))

    def c(sx: int, sy: int, sz: int) -> Vec3:
        return corners[(sx + 1) // 2 * 4 + (sy + 1) // 2 * 2 + (sz + 1) // 2]

    faces = [
        (c(-1, -1, -1), c(-1, -1, 1), c(-1, 1, 1), c(-1, 1, -1), (-1.0, 0.0, 0.0)),
        (c(1, -1, -1), c(1, 1, -1), c(1, 1, 1), c(1, -1, 1), (1.0, 0.0, 0.0)),
        (c(-1, -1, -1), c(1, -1, -1), c(1, -1, 1), c(-1, -1, 1), (0.0, -1.0, 0.0)),
        (c(-1, 1, -1), c(-1, 1, 1), c(1, 1, 1), c(1, 1, -1), (0.0, 1.0, 0.0)),
        (c(-1, -1, -1), c(-1, 1, -1), c(1, 1, -1), c(1, -1, -1), (0.0, 0.0, -1.0)),
        (c(-1, -1, 1), c(1, -1, 1), c(1, 1, 1), c(-1, 1, 1), (0.0, 0.0, 1.0)),
    ]

    positions: list[Vec3] = []
    normals: list[Vec3] = []
    indices: list[int] = []
    for a, b, cc, d, normal in faces:
        base = len(positions)
        positions.extend([a, b, cc, d])
        normals.extend([normal] * 4)
        indices.extend([base, base + 1, base + 2, base, base + 2, base + 3])
    return positions, normals, indices


def merge(*parts):
    positions: list[Vec3] = []
    normals: list[Vec3] = []
    indices: list[int] = []
    for part_positions, part_normals, part_indices in parts:
        offset = len(positions)
        positions.extend(part_positions)
        normals.extend(part_normals)
        indices.extend(index + offset for index in part_indices)
    return positions, normals, indices


# --------------------------------------------------------------------------- #
# Controller definitions
# --------------------------------------------------------------------------- #

MATERIALS = {
    "shellDark": (0.078, 0.086, 0.102),
    "shellLight": (0.898, 0.902, 0.910),
    "gripDark": (0.125, 0.133, 0.149),
    "stickBlack": (0.043, 0.047, 0.055),
    "stickGrey": (0.220, 0.235, 0.255),
    "touchpad": (0.816, 0.831, 0.851),
    "faceCross": (0.278, 0.639, 0.945),
    "faceCircle": (0.937, 0.325, 0.376),
    "faceSquare": (0.937, 0.541, 0.757),
    "faceTriangle": (0.373, 0.831, 0.596),
    "faceGreen": (0.373, 0.831, 0.478),
    "faceRed": (0.937, 0.325, 0.376),
    "faceBlue": (0.278, 0.588, 0.945),
    "faceYellow": (0.949, 0.812, 0.302),
    "accentCyan": (0.133, 0.827, 0.933),
    "accentBlue": (0.231, 0.510, 0.965),
}

BODY_RX = 0.058
BODY_RY = 0.019
BODY_RZ = 0.026
GRIP_SPREAD = 0.046


def _body_nodes(radius_x, radius_y, radius_z):
    return [
        ("shell_body", "shellDark", ellipsoid((0.0, 0.0, 0.0), (radius_x, radius_y, radius_z))),
        (
            "shell_grip_left",
            "shellDark",
            capsule(
                (-GRIP_SPREAD, -0.004, 0.004),
                (-(GRIP_SPREAD + 0.012), -0.048, 0.020),
                0.019,
            ),
        ),
        (
            "shell_grip_right",
            "shellDark",
            capsule(
                (GRIP_SPREAD, -0.004, 0.004),
                (GRIP_SPREAD + 0.012, -0.048, 0.020),
                0.019,
            ),
        ),
    ]


def _stick_node(center_x: float, center_z: float, name: str, material: str):
    well = capsule(
        (center_x, BODY_RY - 0.004, center_z), (center_x, BODY_RY + 0.010, center_z), 0.017
    )
    shaft = capsule(
        (center_x, BODY_RY + 0.008, center_z), (center_x, BODY_RY + 0.026, center_z), 0.011
    )
    cap = ellipsoid(
        (center_x, BODY_RY + 0.027, center_z), (0.017, 0.005, 0.017), segments=20, rings=10
    )
    return (name, material, merge(well, shaft, cap))


def _dpad_nodes(center_x: float, center_z: float):
    """Four separate arms, so each direction highlights on its own."""
    half = 0.013
    return [
        ("DPAD_UP", "stickGrey",
         box((center_x, BODY_RY + 0.003, center_z - half), (0.011, 0.006, 0.016))),
        ("DPAD_DOWN", "stickGrey",
         box((center_x, BODY_RY + 0.003, center_z + half), (0.011, 0.006, 0.016))),
        ("DPAD_LEFT", "stickGrey",
         box((center_x - half, BODY_RY + 0.003, center_z), (0.016, 0.006, 0.011))),
        ("DPAD_RIGHT", "stickGrey",
         box((center_x + half, BODY_RY + 0.003, center_z), (0.016, 0.006, 0.011))),
        ("shell_dpad_center", "gripDark",
         box((center_x, BODY_RY + 0.002, center_z), (0.014, 0.005, 0.014))),
    ]


def _shoulder_nodes():
    return [
        ("L1", "gripDark", box((-0.026, 0.020, -0.018), (0.038, 0.011, 0.014), yaw=0.20)),
        ("R1", "gripDark", box((0.026, 0.020, -0.018), (0.038, 0.011, 0.014), yaw=-0.20)),
        ("L2", "gripDark", capsule((-0.030, 0.012, -0.030), (-0.030, 0.026, -0.046), 0.013)),
        ("R2", "gripDark", capsule((0.030, 0.012, -0.030), (0.030, 0.026, -0.046), 0.013)),
    ]


def _face_button_nodes(center_x: float, center_z: float, mapping: dict):
    offset = 0.014
    slots = {
        "top": (center_x, center_z - offset),
        "right": (center_x + offset, center_z),
        "bottom": (center_x, center_z + offset),
        "left": (center_x - offset, center_z),
    }
    nodes = []
    for slot, (button_name, material) in mapping.items():
        x, z = slots[slot]
        nodes.append(
            (button_name, material,
             ellipsoid((x, BODY_RY + 0.005, z), (0.008, 0.005, 0.008), 16, 8))
        )
    return nodes


def build_dualshock4():
    return _body_nodes(BODY_RX, BODY_RY, BODY_RZ) + [
        ("GUIDE", "stickGrey",
         ellipsoid((0.0, BODY_RY + 0.004, 0.008), (0.006, 0.004, 0.006), 14, 8)),
        ("OPTIONS", "stickGrey",
         ellipsoid((0.012, BODY_RY + 0.003, 0.019), (0.005, 0.004, 0.005), 12, 6)),
        ("SHARE", "stickGrey",
         ellipsoid((-0.012, BODY_RY + 0.003, 0.019), (0.005, 0.004, 0.005), 12, 6)),
        ("shell_touchpad", "touchpad",
         box((0.0, BODY_RY + 0.002, -0.006), (0.048, 0.005, 0.026))),
        ("shell_lightbar", "accentBlue",
         box((0.0, BODY_RY - 0.002, -0.021), (0.036, 0.004, 0.004))),
    ] + _shoulder_nodes() + [
        _stick_node(-0.021, 0.013, "L3", "stickBlack"),
        _stick_node(0.021, 0.013, "R3", "stickBlack"),
    ] + _dpad_nodes(-0.021, -0.015) + _face_button_nodes(
        0.021,
        -0.015,
        {
            "top": ("TRIANGLE", "faceTriangle"),
            "right": ("CIRCLE", "faceCircle"),
            "bottom": ("CROSS", "faceCross"),
            "left": ("SQUARE", "faceSquare"),
        },
    )


def build_dualsense():
    return [
        ("shell_body", "shellLight",
         ellipsoid((0.0, 0.0, 0.0), (BODY_RX + 0.003, BODY_RY + 0.002, BODY_RZ))),
        ("shell_center", "shellDark",
         ellipsoid((0.0, 0.002, 0.0), (0.040, BODY_RY, BODY_RZ - 0.004))),
        ("shell_grip_left", "shellLight",
         capsule((-GRIP_SPREAD, -0.004, 0.004), (-(GRIP_SPREAD + 0.012), -0.050, 0.020), 0.020)),
        ("shell_grip_right", "shellLight",
         capsule((GRIP_SPREAD, -0.004, 0.004), (GRIP_SPREAD + 0.012, -0.050, 0.020), 0.020)),
        ("GUIDE", "accentCyan",
         ellipsoid((0.0, BODY_RY + 0.005, 0.010), (0.006, 0.004, 0.006), 14, 8)),
        ("OPTIONS", "stickGrey",
         ellipsoid((0.012, BODY_RY + 0.003, 0.019), (0.005, 0.004, 0.005), 12, 6)),
        ("SHARE", "stickGrey",
         ellipsoid((-0.012, BODY_RY + 0.003, 0.019), (0.005, 0.004, 0.005), 12, 6)),
        ("shell_mic", "accentCyan",
         box((0.0, BODY_RY - 0.001, 0.014), (0.020, 0.003, 0.003))),
        ("shell_touchpad", "touchpad",
         box((0.0, BODY_RY + 0.004, -0.004), (0.052, 0.006, 0.028))),
    ] + _shoulder_nodes() + [
        _stick_node(-0.022, 0.014, "L3", "stickBlack"),
        _stick_node(0.022, 0.014, "R3", "stickBlack"),
    ] + _dpad_nodes(-0.023, -0.016) + _face_button_nodes(
        0.023,
        -0.016,
        {
            "top": ("TRIANGLE", "faceTriangle"),
            "right": ("CIRCLE", "faceCircle"),
            "bottom": ("CROSS", "faceCross"),
            "left": ("SQUARE", "faceSquare"),
        },
    )


def build_xbox_wireless():
    """Asymmetric layout: left stick high, D-pad low-left, right stick low-right."""
    return _body_nodes(BODY_RX, BODY_RY + 0.001, BODY_RZ) + [
        ("GUIDE", "faceGreen",
         ellipsoid((0.0, BODY_RY + 0.004, -0.006), (0.008, 0.004, 0.008), 14, 8)),
        ("OPTIONS", "stickGrey",
         ellipsoid((0.012, BODY_RY + 0.003, 0.019), (0.005, 0.004, 0.005), 12, 6)),
        ("SHARE", "stickGrey",
         ellipsoid((-0.012, BODY_RY + 0.003, 0.019), (0.005, 0.004, 0.005), 12, 6)),
    ] + _shoulder_nodes() + [
        _stick_node(-0.021, -0.012, "L3", "stickGrey"),
        _stick_node(0.020, 0.011, "R3", "stickGrey"),
    ] + _dpad_nodes(-0.021, 0.013) + _face_button_nodes(
        0.021,
        -0.014,
        {
            "top": ("TRIANGLE", "faceYellow"),
            "right": ("CIRCLE", "faceRed"),
            "bottom": ("CROSS", "faceGreen"),
            "left": ("SQUARE", "faceBlue"),
        },
    )


# --------------------------------------------------------------------------- #
# glTF assembly
# --------------------------------------------------------------------------- #


def _pad_len(length: int) -> int:
    return (4 - (length % 4)) % 4


def build_glb(nodes) -> bytes:
    """Assemble a glTF 2.0 binary blob from a list of (name, material, geometry)."""
    buffer = bytearray()
    views = []
    accessors = []
    primitives = []
    materials = []
    gltf_nodes = []
    material_lookup: dict[str, int] = {}

    for node_name, material_name, (positions, normals, indices) in nodes:
        material_index = material_lookup.get(material_name)
        if material_index is None:
            material_index = len(materials)
            material_lookup[material_name] = material_index
            materials.append(
                {
                    "name": material_name,
                    "pbrMetallicRoughness": {
                        "baseColorFactor": [*MATERIALS[material_name], 1.0],
                        "metallicFactor": 0.0,
                        "roughnessFactor": 0.45 if material_name.startswith("shell")
                        else 0.35,
                    },
                }
            )

        for component_type, payload, kind, count in (
            (5126, positions, "VEC3", len(positions)),
            (5126, normals, "VEC3", len(normals)),
            (5123, indices, "SCALAR", len(indices)),
        ):
            view_index = len(views)
            start = len(buffer)
            views.append({"buffer": 0, "byteOffset": start, "byteLength": 0})
            if component_type == 5126:
                for vector in payload:
                    buffer.extend(struct.pack("<3f", *vector))
            else:
                for index_value in payload:
                    buffer.extend(struct.pack("<H", index_value))
            views[view_index]["byteLength"] = len(buffer) - start
            buffer.extend(b"\x00" * _pad_len(len(buffer)))

            accessor = {
                "bufferView": view_index,
                "componentType": component_type,
                "count": count,
                "type": kind,
            }
            if kind == "VEC3":
                accessor["min"] = [min(v[i] for v in payload) for i in range(3)]
                accessor["max"] = [max(v[i] for v in payload) for i in range(3)]
            accessors.append(accessor)

        primitives.append(
            {
                "attributes": {
                    "POSITION": len(accessors) - 3,
                    "NORMAL": len(accessors) - 2,
                },
                "indices": len(accessors) - 1,
                "material": material_index,
            }
        )
        gltf_nodes.append({"name": node_name, "mesh": len(primitives) - 1})

    gltf = {
        "asset": {"version": "2.0", "generator": "PulsePad procedural model generator"},
        "scene": 0,
        "scenes": [{"nodes": list(range(len(gltf_nodes)))}],
        "nodes": gltf_nodes,
        "meshes": [{"primitives": [primitive]} for primitive in primitives],
        "materials": materials,
        "accessors": accessors,
        "bufferViews": views,
        "buffers": [{"byteLength": len(buffer)}],
    }

    json_chunk = json.dumps(gltf, separators=(",", ":")).encode("utf-8")
    json_chunk += b" " * _pad_len(len(json_chunk))
    binary_chunk = bytes(buffer)
    if _pad_len(len(binary_chunk)):
        binary_chunk += b"\x00" * _pad_len(len(binary_chunk))

    total = 12 + 8 + len(json_chunk) + 8 + len(binary_chunk)
    out = bytearray()
    out.extend(struct.pack("<III", 0x46546C67, 2, total))
    out.extend(struct.pack("<II", len(json_chunk), 0x4E4F534A))
    out.extend(json_chunk)
    out.extend(struct.pack("<II", len(binary_chunk), 0x004E4942))
    out.extend(binary_chunk)
    return bytes(out)


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    models = {
        "dualshock4.glb": build_dualshock4,
        "dualsense.glb": build_dualsense,
        "xbox_wireless.glb": build_xbox_wireless,
    }
    for filename, builder in models.items():
        glb = build_glb(builder())
        (OUTPUT_DIR / filename).write_bytes(glb)
        print(f"wrote {filename} ({len(glb):,} bytes)")


if __name__ == "__main__":
    main()
