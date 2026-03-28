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

Shadow modes work on all primitive types (box, sphere, cylinder, quad) and models:

```scenemax
c => cylinder : radius (1, 1) and height 3 and shadow mode both
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
