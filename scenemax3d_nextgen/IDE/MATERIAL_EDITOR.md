# Material Studio

Material Studio is a native retained Bevy editor for reusable PBR surfaces. Create a material from **Assets → Create New Material…**, or right-click a scripts folder and choose **Create Material…**. Double-click a `.smmat` document to reopen it.

## Authoring

The left panel contains surface presets and project texture channels. The center is a lit, isolated 3D preview. The right inspector exposes exact values and sliders. Start with Porcelain, Brushed metal, Gold, Car paint, Glass, Rubber, or Emissive, then customize the surface.

- Preview a sphere, cube, plane, or a glTF/GLB file from project resources.
- Orbit with right drag, zoom with the wheel, and use Frame to fit the model.
- Toggle automatic rotation and neutral/warm key lighting. The studio includes generated environment lighting for readable metal reflections.
- Set base color and emission using swatches or RGB/RGBA hex values.
- Adjust metallic, roughness, reflectance, clearcoat, anisotropy, transmission, thickness, IOR, alpha mode, UV tiling/offset/rotation, double-sided rendering, and normal-map orientation.
- Assign PNG/JPEG files already in project resources to base color, normal, metallic/roughness, occlusion, and emission channels. Packed metallic/roughness uses blue/green; occlusion uses red. Color channels use sRGB; data channels use linear sampling.
- Use named slot buttons from a loaded model, or enter an exact glTF material name. A blank target slot applies to the whole object.

Save, Undo and Redo use the ordinary document workflow, including conflict protection and recovery. Preview changes do not rewrite the source model. Presets keep identity, texture references and unknown extension metadata. Documents preserve unknown JSON fields during editing.

## Use in a game

Save the material under the project's scripts tree. Give material files unique names within the project. For `copper.smmat`, apply it to an existing SceneMax entity:

```scenemax
object.material = "copper"
```

The editor's **Copy script assignment** button copies this statement using the current filename. Replace `object` with your entity name. The game applies the material to that instance and its matching model slots, including meshes that finish loading asynchronously. Set the material to an empty string to restore the imported surface.

`object.shader` is reserved for Shader Designer output and does not load `.smmat` files. Earlier Material Studio snippets using `.shader` must be changed to `.material`.

Native materials also appear in the scene designer's Material selector and preview there. Scene document properties and executable SceneMax scripts are separate: keep the corresponding generated/authored script assignment in sync before running the game.

Packaging starts from root `main`, follows reachable material names and includes their texture dependencies. Materials not referenced by the game are excluded. Use a matching bundled runtime that supports the material format when distributing games.

## Design research and scope

Sources reviewed:

- [Renzora Material Editor](https://renzora.com/docs/r1-alpha5/editor/materials): asset documents, material-slot workflows, live previews, and the separation between reusable surfaces and shader graphs.
- [Renzora material editor source](https://github.com/renzora/engine/tree/main/crates/renzora_material_editor/src): graph state, editor integration and isolated preview ownership.
- [Renzora material graph model](https://github.com/renzora/engine/blob/main/crates/renzora_shader/src/material/graph.rs): typed connections and shader-generation boundary.
- [Bevy glTF material editing example](https://bevy.org/examples/gltf/edit-material-on-gltf/): material handles and named glTF material slots.
- [Bevy PBR development](https://bevy.org/news/bevy-0-14/): advanced material controls including clearcoat and anisotropy.

The implementation is original SceneMax code. It uses a shared pure document schema in Common/assets and a shared Bevy adapter in Engine/materials; neither the preview nor the game duplicates the surface mapping. It does not import the Renzora engine or copy the previous material designer.

The selected first-release workflow is the visual PBR surface composer. Graph authoring, layered procedural textures, shader generation, material inheritance, texture baking, and external texture import are separate capabilities. Transmission and reflections depend on scene lighting and camera configuration; an isolated preview cannot reproduce every game's environment. Model previews are static. Texture files should be prepared in project resources before selection; use Refresh assets after adding files. Normal mapping and anisotropy require mesh UVs and normals; missing tangents are generated when possible.
