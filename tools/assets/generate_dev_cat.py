"""Generate PawQuest's original low-poly cat and five demo clips (Python stdlib)."""
import json
import math
from pathlib import Path
import struct


def generate(output: Path):
    blob = bytearray()
    views, accessors, meshes, nodes = [], [], [], []
    materials = [
        ("Ginger coat", [0.83, 0.37, 0.12, 1]),
        ("Cream muzzle and paws", [1, 0.86, 0.65, 1]),
        ("Pink ears and nose", [0.86, 0.36, 0.4, 1]),
        ("Dark eyes", [0.025, 0.035, 0.045, 1]),
        ("Eye highlights", [1, 1, 1, 1]),
        ("Dark ginger stripes", [0.45, 0.16, 0.06, 1]),
    ]

    def accessor(values, width, kind, component, target):
        while len(blob) % 4:
            blob.append(0)
        offset = len(blob)
        flat = [v for row in values for v in row]
        blob.extend(struct.pack("<" + kind * len(flat), *flat))
        views.append(dict(buffer=0, byteOffset=offset, byteLength=len(blob) - offset))
        if target:
            views[-1]['target'] = target
        result = dict(bufferView=len(views) - 1, componentType=component,
                      count=len(values), type={1: "SCALAR", 3: "VEC3", 4: "VEC4"}[width])
        if component == 5126:
            result.update(min=[min(row[i] for row in values) for i in range(width)],
                          max=[max(row[i] for row in values) for i in range(width)])
        accessors.append(result)
        return len(accessors) - 1

    def mesh(name, positions, normals, indices, material):
        pos = accessor(positions, 3, "f", 5126, 34962)
        normal = accessor(normals, 3, "f", 5126, 34962)
        index = accessor([(i,) for i in indices], 1, "H", 5123, 34963)
        meshes.append(dict(name=name, primitives=[dict(attributes={"POSITION": pos, "NORMAL": normal},
                                                       indices=index, material=material)]))
        nodes.append(dict(name=name, mesh=len(meshes) - 1))

    def ellipsoid(name, center, radii, material, rings=10, segments=16):
        positions, normals, indices = [], [], []
        for y in range(rings + 1):
            theta = math.pi * y / rings
            for x in range(segments + 1):
                phi = 2 * math.pi * x / segments
                unit = (math.sin(theta) * math.cos(phi), math.cos(theta), math.sin(theta) * math.sin(phi))
                positions.append(tuple(center[i] + unit[i] * radii[i] for i in range(3)))
                normal = [unit[i] / radii[i] for i in range(3)]
                length = math.sqrt(sum(v * v for v in normal))
                normals.append(tuple(v / length for v in normal))
        for y in range(rings):
            for x in range(segments):
                a = y * (segments + 1) + x
                b = a + segments + 1
                indices.extend((a, a + 1, b, a + 1, b + 1, b))
        mesh(name, positions, normals, indices, material)

    def ear(name, side):
        vertices = [(side * 0.24, 1.92, 0.13), (side * 0.68, 1.89, 0.13),
                    (side * 0.59, 2.46, 0.06), (side * 0.46, 1.98, -0.22)]
        positions, normals, indices = [], [], []
        for face in [(0, 1, 2), (0, 3, 1), (0, 2, 3), (1, 3, 2)]:
            a, b, c = [vertices[i] for i in face]
            u, v = [b[i] - a[i] for i in range(3)], [c[i] - a[i] for i in range(3)]
            normal = [u[1]*v[2] - u[2]*v[1], u[2]*v[0] - u[0]*v[2], u[0]*v[1] - u[1]*v[0]]
            length = math.sqrt(sum(n*n for n in normal))
            start = len(positions)
            positions.extend((a, b, c))
            normals.extend([tuple(n / length for n in normal)] * 3)
            indices.extend((start, start+1, start+2))
        mesh(name, positions, normals, indices, 0)
        ellipsoid(name + " pink inset", (side * 0.5, 2.12, 0.14), (0.1, 0.2, 0.025), 2)

    ellipsoid("Body", (0, 0.91, 0), (0.56, 0.78, 0.4), 0)
    ellipsoid("Cream chest", (0, 0.88, 0.345), (0.33, 0.52, 0.12), 1)
    ellipsoid("Head", (0, 1.76, 0.08), (0.66, 0.55, 0.46), 0)
    for side in (-1, 1):
        ear("Ear " + str(side), side)
        ellipsoid("Front leg " + str(side), (side * 0.25, 0.44, 0.29), (0.16, 0.39, 0.18), 0)
        ellipsoid("Front paw " + str(side), (side * 0.25, 0.13, 0.35), (0.2, 0.14, 0.24), 1)
        ellipsoid("Haunch " + str(side), (side * 0.41, 0.34, -0.04), (0.25, 0.3, 0.28), 0)
        ellipsoid("Muzzle " + str(side), (side * 0.15, 1.56, 0.48), (0.2, 0.15, 0.1), 1)
        ellipsoid("Eye " + str(side), (side * 0.25, 1.83, 0.491), (0.1, 0.145, 0.045), 3)
        ellipsoid("Eye glint " + str(side), (side * 0.25 - 0.025, 1.88, 0.532), (0.028, 0.038, 0.014), 4)
    ellipsoid("Pink nose", (0, 1.62, 0.59), (0.075, 0.055, 0.035), 2)
    for x in (-0.17, 0, 0.17):
        ellipsoid("Forehead stripe", (x, 2.1, 0.39), (0.045, 0.12, 0.025), 5)
    for i in range(13):
        t = i / 12
        ellipsoid("Curled tail " + str(i), (0.38 + 0.49 * math.sin(t * math.pi * 0.8),
                   0.34 + t * 1.05, -0.24), (0.125, 0.13, 0.125), 5 if i > 10 else 0,
                  rings=6, segments=10)

    # Animate an authored parent about the cat's center; never animate the scene's
    # synthetic normalization root. Every clip has small, bounded rigid transforms.
    center_y = 1.23
    geometry_nodes = list(range(len(nodes)))
    nodes.append(dict(name="Centered geometry", translation=[0, -center_y, 0], children=geometry_nodes))
    pivot = len(nodes)
    nodes.append(dict(name="Animation pivot", translation=[0, center_y, 0], children=[pivot - 1]))
    animations = []

    def clip(name, seconds, scales, translations, rotations):
        times = accessor([(seconds * i / 4,) for i in range(5)], 1, "f", 5126, None)
        samplers, channels = [], []
        for path, values, width in [("scale", scales, 3), ("translation", translations, 3), ("rotation", rotations, 4)]:
            out = accessor(values, width, "f", 5126, None)
            samplers.append(dict(input=times, output=out, interpolation="LINEAR"))
            channels.append(dict(sampler=len(samplers)-1, target=dict(node=pivot, path=path)))
        animations.append(dict(name=name, samplers=samplers, channels=channels))

    def scales(values): return [(s, s, s) for s in values]
    def positions(values): return [(0, center_y + y, 0) for y in values]
    def rotations(axis, angles):
        return [tuple((math.sin(a/2) if i == axis else 0) for i in range(3)) + (math.cos(a/2),) for a in angles]

    neutral = positions([0]*5)
    identity = rotations(0, [0]*5)
    clip("Idle", 3, scales([1, 1.015, 1.025, 1.015, 1]), neutral, identity)
    clip("Happy", 1.2, scales([1]*5), positions([0, .08, .16, .08, 0]), rotations(2, [0, .08, 0, -.08, 0]))
    clip("Eating", 1.8, scales([1]*5), neutral, rotations(0, [0, .16, .28, .16, 0]))
    clip("Sleeping", 4, scales([1, 1.01, 1.02, 1.01, 1]), neutral, rotations(2, [1.35]*5))
    clip("Playing", 1.6, scales([1]*5), neutral, rotations(1, [0, .35, 0, -.35, 0]))

    document = dict(asset={"version": "2.0", "generator": "PawQuest original development cat generator"},
                    scene=0, scenes=[dict(nodes=[pivot])], nodes=nodes, meshes=meshes, animations=animations,
                    buffers=[dict(byteLength=len(blob))], bufferViews=views, accessors=accessors,
                    materials=[dict(name=name, doubleSided=True,
                                    pbrMetallicRoughness=dict(baseColorFactor=color, metallicFactor=0,
                                                             roughnessFactor=0.85))
                               for name, color in materials])
    encoded = json.dumps(document, separators=(",", ":")).encode()
    encoded += b" " * (-len(encoded) % 4)
    blob.extend(b"\0" * (-len(blob) % 4))
    total = 12 + 8 + len(encoded) + 8 + len(blob)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(struct.pack("<III", 0x46546C67, 2, total) +
                       struct.pack("<II", len(encoded), 0x4E4F534A) + encoded +
                       struct.pack("<II", len(blob), 0x004E4942) + blob)
    # Every mesh corner is within this sphere about the animation pivot. Uniform
    # scale <=1.025 and translation <=0.16 bound all intermediate LINEAR samples.
    radius = max(math.sqrt(sum(max(abs(accessors[m['primitives'][0]['attributes']['POSITION']][bound][axis]
                               - (center_y if axis == 1 else 0)) for bound in ('min', 'max'))**2
                               for axis in range(3))) for m in meshes)
    radius = radius * 1.025 + .16 + .05
    output.with_suffix('.animation-bounds.json').write_text(json.dumps(dict(
        min=[-radius, center_y-radius, -radius], max=[radius, center_y+radius, radius]), indent=2) + '\n')
    print(f"Created {output}: {total} bytes, {len(meshes)} meshes")


if __name__ == "__main__":
    generate(next(parent for parent in Path(__file__).resolve().parents if (parent / 'settings.gradle.kts').is_file()) / "shared/src/androidMain/assets/models/cat.glb")
