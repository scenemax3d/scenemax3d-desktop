# Font Generator

Open **Tools > Font Generator…** in an active project.

Choose a system font and Plain, Bold, Italic or Bold Italic style. Set font size (12–256), outline width (0–24), shadow X/Y (−32–32) and padding (0–32). Numeric steppers support one-pixel adjustments and direct entry. Click any color swatch to edit its RGBA channels, or enter a hex color directly. Smooth edges and Gradient fill are independent switches.

Preview Text changes the live 640×320 sample. Character Set offers Latin + Hebrew, Basic Latin and Custom; the Characters field remains editable. Export removes duplicate characters while preserving their order and always includes a space. The default repertoire has 122 characters. Refresh Preview regenerates the sample explicitly.

**Export To Project** creates:

- `resources/fonts/<asset_name>.fnt`: AngelCode text BMFont descriptor, one page, Unicode character IDs, advances and offsets, no kerning table entries.
- `resources/fonts/<asset_name>.png`: transparent RGBA atlas, power-of-two dimensions, packed in rows up to 1024 pixels wide.
- A `{ "name": "<asset_name>", "path": "fonts/<asset_name>.fnt" }` registration in `resources/fonts/fonts-ext.json`.

Fill, gradient, round outline and shadow are baked into the atlas. The descriptor includes the padded line height; effects must not be added again at runtime. The shared runtime and UI designer bitmap-font reader can load the result. Generated fonts also appear in Project Inventory after refresh.

Existing registrations and output files are never overwritten. Duplicate names are checked case-insensitively in project and shared font indexes. Existing index metadata is preserved. Export performs background work, protects against project switching/closing during publication, and displays a completion or failure dialog. A failed index publication rolls back its newly created atlas and descriptor.

Font discovery and rasterization are native Rust and require no external font tool or Java runtime. Native rasterization may differ slightly in metrics and antialiasing from other font rasterizers; output is format-compatible, not byte-identical. Installed font availability depends on the OS. Very large character sets/atlases are rejected with an error rather than exhausting memory (4,096 input characters, 16,384-pixel atlas height, 32 megapixels).
