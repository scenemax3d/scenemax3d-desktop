# 3D Objects & Models

## Primitive Objects

### Sphere

Create a sphere:

```scenemax
s is a sphere
```

Sphere with a material:

```scenemax
s is a sphere : material="pond"
```

Sphere with material and radius:

```scenemax
s is a sphere : material="pond" and radius=2
```

### Box

Create a box with specific dimensions (half-extents for X, Y, Z):

```scenemax
b is a box : material="pond" and size (100,1,100)
```

### Static Objects

Create a static box (can support other objects placed on it):

```scenemax
b is a static box
```

Static race track:

```scenemax
t is a static track2
```

## 3D Character Models

Load a 3D character model:

```scenemax
si is a sinbad
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
d is a dragon : scale=3
```

Scale a model after loading (2x):

```scenemax
d.scale=2
```

### Rotation at Load

Load a model pre-rotated by 90 degrees:

```scenemax
d is a dragon : turn 90
```

### Hidden at Load

Load a model in a hidden state:

```scenemax
n is a sinbad : hidden
```

### Mass

Set an object's mass (in kg):

```scenemax
d.mass=10
```

## Vehicles

Load a vehicle model:

```scenemax
c is a hatchback vehicle
```

## Collider Boxes

Create an invisible box that can receive collision events:

```scenemax
player1_head => collider box : size(1,2,1)
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
m is a (model_type)
```

## Deleting Objects

Remove an object from memory:

```scenemax
s is a sinbad
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
n is a ninja
n.show joints : size 5
```

## Shadow Modes

Cast shadows onto other objects:

```scenemax
n is a ninja : shadow mode cast
```

Receive shadows from other objects:

```scenemax
b is a static box : shadow mode receive
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
