# 3D Objects & Models

## Primitive Objects

### Sphere

Create a sphere:

```scenemax
s => sphere
```

Sphere with a material:

```scenemax
s => sphere : material="pond"
```

Sphere with material and radius:

```scenemax
s => sphere : material="pond" and radius=2
```

### Box

Create a box with specific dimensions (half-extents for X, Y, Z):

```scenemax
b => box : material="pond" and size (100,1,100)
```

### Wedge

Create a wedge primitive for ramps, roof slopes, and terrain transitions:

```scenemax
ramp => wedge
```

Wedge with width, height, and depth:

```scenemax
ramp => wedge : size (4, 2, 6)
```

Wedge with material, transform, and shadows:

```scenemax
ramp => wedge : size (4, 2, 6) and material="concrete" and pos (0, 1, 0) and rotate (0, 45, 0) and scale 1.5 and shadow mode on
```

Apply a shader to a wedge after creating it:

```scenemax
ramp => wedge : size (4, 2, 6)
ramp.shader = "heat_distortion"
```

The `size` values are `width`, `height`, and `depth`.

### Cylinder

Create a cylinder:

```scenemax
c => cylinder
```

Cylinder with top and bottom radii and height:

```scenemax
c => cylinder : radius (1, 0.5) and height 3
```

Cylinder with material, position, and rotation:

```scenemax
c => cylinder : radius (1, 1) and height 2 and material="rock" and pos (0, 1, 0) and rotate (0, 45, 0)
```

The two radius values define the top and bottom radii respectively. Using different values creates a cone or frustum shape.

### Cone

Create a cone primitive:

```scenemax
spire => cone
```

Cone with top radius, bottom radius, and height:

```scenemax
spire => cone : radius (0, 1.5) and height 5
```

Cone with material and position:

```scenemax
tower_cap => cone : radius (0.5, 2) and height 3 and material="roof_tiles" and pos (0, 4, 0)
```

Apply a shader to a cone:

```scenemax
tower_cap => cone : radius (0, 1.5) and height 5
tower_cap.shader = "lava"
```

The `radius` values are `top radius` and `bottom radius`. Use `radius (0, r)` for a sharp cone, or two non-zero values for a frustum.

### Hollow Cylinder

Create a hollow cylinder (tube/pipe):

```scenemax
h => hollow cylinder
```

Hollow cylinder with outer radii, inner radii, and height:

```scenemax
h => hollow cylinder : radius (1, 1) and inner radius (0.5, 0.5) and height 3
```

Hollow cylinder with different top/bottom radii for a funnel shape:

```scenemax
h => hollow cylinder : radius (2, 1) and inner radius (1.5, 0.5) and height 4 and material="rock" and pos (0, 2, 0)
```

The `radius` defines the outer top and bottom radii, while `inner radius` defines the hole's top and bottom radii. Both support independent values for frustum-like hollow shapes.

### Quad

Create a flat rectangular surface:

```scenemax
q => quad
```

Quad with specific width and height:

```scenemax
q => quad : size (4, 3)
```

Quad with material and position:

```scenemax
q => quad : size (10, 10) and material="grass" and pos (0, 0, 0)
```

### Stairs

Create a stairs primitive:

```scenemax
stairs_main => stairs
```

Stairs with width, step height, step depth, and step count:

```scenemax
stairs_main => stairs : size (3, 0.2, 0.35) and steps 10
```

Stairs with material, position, and shadow settings:

```scenemax
stairs_main => stairs : size (3, 0.2, 0.35) and steps 10 and material="stone" and pos (0, 1, 0) and shadow mode receive
```

Apply a shader to the stairs:

```scenemax
stairs_main => stairs : size (3, 0.2, 0.35) and steps 10
stairs_main.shader = "scanlines"
```

The `size` values are `width`, `step height`, and `step depth`. The total staircase height is `step height * steps`, and the total depth is `step depth * steps`.

### Arch

Create an arch primitive:

```scenemax
entry_arch => arch
```

Arch with width, height, depth, thickness, and segment count:

```scenemax
entry_arch => arch : size (4, 5, 0.8) and thickness 0.5 and segments 14
```

Arch with material, transform, and scale:

```scenemax
gate_arch => arch : size (6, 7, 1) and thickness 0.75 and segments 18 and material="brick" and pos (0, 3.5, 0) and scale 1.2
```

Apply a shader to an arch:

```scenemax
gate_arch => arch : size (4, 5, 0.8) and thickness 0.5 and segments 14
gate_arch.shader = "outline"
```

The `size` values are `width`, `height`, and `depth`. `thickness` controls the thickness of the arch frame, and `segments` controls how smooth the curved top is.

All primitive objects support the same common transform and rendering attributes used by the existing primitives, including `pos (...)`, `rotate (...)`, `scale N`, `shadow mode cast|receive|on`, and `collision shape box|boxes|none`. Use `static` or `collider` as declaration prefixes when needed, for example `ramp => static wedge ...` or `arch_col => collider arch ...`. Materials are set inline with `material="name"`, while shaders are applied after creation with `object.shader = "shader_name"`.

### Static Objects

Create a static box (can support other objects placed on it):

```scenemax
b => static box
```

Static cylinder and quad:

```scenemax
c => static cylinder : radius (1, 1) and height 2
q => static quad : size (10, 10)
```

Static wedge, stairs, and arch:

```scenemax
ramp => static wedge : size (4, 2, 6)
stairs_main => static stairs : size (3, 0.2, 0.35) and steps 10
entry_arch => static arch : size (4, 5, 0.8) and thickness 0.5 and segments 14
```

Static race track:

```scenemax
t => static track2
```

## 3D Character Models

Load a 3D character model:

```scenemax
si => sinbad
```

Shorthand syntax for loading a model:

```scenemax
s => sinbad
```

Load a model asynchronously (in a separate thread):

```scenemax
s => sinbad async
```

### Scale

Scale a model at load time (3x larger):

```scenemax
d => dragon : scale=3
```

Scale a model after loading (2x):

```scenemax
d.scale=2
```

### Rotation at Load

Load a model pre-rotated by 90 degrees:

```scenemax
d => dragon : turn 90
```

### Hidden at Load

Load a model in a hidden state:

```scenemax
n => sinbad : hidden
```

### Mass

Set an object's mass (in kg):

```scenemax
d.mass=10
```

## Vehicles

Load a vehicle model:

```scenemax
c => hatchback vehicle
```

## Colliders

Create an invisible box collider that can receive collision events:

```scenemax
player1_head => collider box : size(1,2,1)
```

Create a cylinder collider:

```scenemax
barrel_col => collider cylinder : radius (0.5, 0.5) and height 2
```

Create colliders with the new primitives:

```scenemax
ramp_col => collider wedge : size (4, 2, 6)
stairs_col => collider stairs : size (3, 0.2, 0.35) and steps 10
arch_col => collider arch : size (4, 5, 0.8) and thickness 0.5 and segments 14
```

Attach a collider to a character's head joint:

```scenemax
player1_head => collider box : size (0.5,0.2,0.2)
player1_head.attach to player1."mixamorig:Head")
```

## Dynamic Type Creation

Create an object whose type is determined at runtime:

```scenemax
var model_type="Sinbad"
m => (model_type)
```

## Deleting Objects

Remove an object from memory:

```scenemax
s => sinbad
s.delete
```

## Model Info & Debug

Show all available animations for a 3D model:

```scenemax
d.show info
```

Show the model's wireframe:

```scenemax
d.show wireframe
```

Show the model's movement axes:

```scenemax
d.show axis x y z
```

Show joint/bone names of a model (with font size 5):

```scenemax
n => ninja
n.show joints : size 5
```

## Shadow Modes

Cast shadows onto other objects:

```scenemax
n => ninja : shadow mode cast
```

Receive shadows from other objects:

```scenemax
b => static box : shadow mode receive
```

Shadow modes work on all primitive types (`box`, `sphere`, `wedge`, `cylinder`, `cone`, `hollow cylinder`, `quad`, `stairs`, `arch`) and models:

```scenemax
c => cylinder : radius (1, 1) and height 3 and shadow mode on
```

```scenemax
gate_arch => arch : size (4, 5, 0.8) and thickness 0.5 and segments 14 and shadow mode on
```

## Lights

Add a physical light probe:

```scenemax
lights.add probe "5" : pos (10,5,0)
```

## Resource Declaration

Explicitly declare resources used by the game (needed when the compiler cannot detect them from code, required for EXE packaging):

```scenemax
using ninja,sinbad,dragon model
using bird,runningman sprite
using walking audio
```
