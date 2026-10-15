# Physics Simulation

Open **Tools > Physics Simulation**. This is an isolated, native laboratory inside the IDE; it does not change the project tree or any open document.

Choose Throw, Impulse, Force, Velocity, Angular velocity, Torque, Torque impulse, or Stop. Only relevant fields are shown. Use a box to see tumbling, or a sphere to see rolling. Both are one metre across. The floor is 80 by 80 metres at Y=0. The red marker is an aim point, not a collider.

- Play/Pause continues the current experiment; Reset returns to its initial pose. Step advances exactly 1/64 second, matching the runtime default fixed step. Loop repeats at the configured duration.
- Drag the preview to orbit, use the wheel to zoom, or Frame to restore the view of the start and target.
- The green trail records actual simulated positions, including impacts. Position, elapsed time, linear speed and angular speed are measured from the physics world. The asymmetric red body stripe makes rotation visible.
- Edit the start/target coordinates, mass, gravity, damping, friction and restitution to experiment. Changes reset the simulation. Invalid edits pause it and prevent copying stale code.
- Copy snippet provides configuration and motion for an existing physics body. Copy full example includes body/floor declarations and camera setup. Paste the example into a SceneMax script and run it normally.

## Units and motion semantics

Vectors and directions are **world space**: X right, Y up, Z forward. Metres, seconds and kilograms are used. Velocity is m/s, angular velocity/spin is rad/s, force is N, torque is N m, and impulses are N s (angular impulses N m s). A vector impulse adds momentum and therefore depends on mass. Velocity replaces the current velocity. Torque uses the collider's inertia.

Force duration zero means sustained until replaced or stopped; a positive duration stops applying force after that interval. Torque is sustained, while torque impulse is applied once. Stop clears linear/angular velocity and this adapter's sustained force/torque; gravity still acts afterward. In the Stop preview/full example the vector supplies initial velocity and Stop after supplies the delay. The short snippet contains the stop command without that demonstration delay.

Throw computes explicit launch velocity and optional spin from the current coordinates. Low and High are the two vacuum ballistic solutions when reachable. Medium and Custom interpolate their angles; an interpolated angle does **not** necessarily hit the target. Angle supplies an elevation directly. Toward target uses the selected arc as an elevation. None launches directly. Damping and collisions change the actual trajectory. An unreachable ballistic target is reported and uses a direct launch. Generated throws bake the authored target into the velocity; for a moving target, recompute the launch or use the runtime's existing dynamic `throw at` command.

## Runtime commands

The following literal-vector commands are now wired in startup actions, functions and runtime actions:

```scenemax
body.physics configure mass 2 drag 0.1 gravity 9.81 restitution 0.4 friction 0.8
body.physics velocity (0, 8, 12)
body.physics angular velocity (0, 4, 0)
body.physics impulse (2, 0, 0)
body.physics force (0, 5, 0) for 0.5 seconds
body.physics torque (0, 2, 0)
body.physics torque (0, 2, 0) impulse
body.physics stop
```

Configuration requires all five named values in that order. Values must be finite; mass must be positive, drag/gravity/friction nonnegative, and restitution between 0 and 1. Vector components and durations currently use numeric literals. Existing directional impulse and `throw at` syntax retain their prior behavior. In particular, the legacy directional impulse replaces velocity; the laboratory emits explicit vector impulses with physical momentum semantics.

A dynamic body must have a collision shape. In native SceneMax, `mass` in a declaration enables an Avian dynamic body; the legacy `dynamic` keyword means kinematic. Example:

```scenemax
body => box : mass 1, size (1,1,1), pos (0,2,0), collision shape box
```

## Architecture and limits

`IDE/core/physics` owns settings, validation and source generation. `IDE/services/physics` validates the generated commands with the real parser. `Engine/physics` owns the shared Avian command adapter used by both the projector and an isolated headless simulation world. The presentation layer renders measured poses into its own camera/render layer and owns retained controls. The IDE does not link the projector or Engine/runtime.

This lab tests one primitive against a floor, not an entire project scene. It does not export a recording, alter project models, or guarantee identical trajectories when copied into a game with different geometry, transforms, collider dimensions or timestep settings. Trails are bounded to 1,200 samples per run. Preview duration is limited to 120 seconds.
