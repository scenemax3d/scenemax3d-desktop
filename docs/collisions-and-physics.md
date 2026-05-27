# Collisions & Physics

## Physics Motion Commands

Physics motion commands apply motion through the physics engine. Use them for thrown rocks, grenades, projectiles, physics props, knockback, torque, and direct velocity control.

The target object should have a rigid body, usually by giving it mass and a collision shape:

```scenemax
rock => meshy_rock1 : mass 8, collision shape box
enemy => fighter2 : pos (12, 0, 4)
```

### Throw

Use `throw` for common gameplay launches.

Throw in the direction of a target:

```scenemax
rock.throw toward enemy power 30
rock.throw toward (enemy up 1) power 30
rock.throw toward (enemy up 1) power 30 angle 35
```

Throw at a target using a ballistic arc:

```scenemax
grenade.throw at enemy power 24 arc high
grenade.throw at (enemy up 1) power 24 arc 0.6
grenade.throw at (10, 1, 4) power 24
```

Add spin at launch:

```scenemax
rock.throw toward enemy power 30 spin (0, 8, 0)
grenade.throw at (enemy up 1) power 24 arc high spin (0, 12, 0)
```

`toward` treats the target as a direction. `at` calculates a ballistic launch velocity toward the target position. `power` is the launch speed/strength. `angle` sets an explicit launch angle in degrees. `arc` accepts `low`, `medium`, `high`, or a number from `0` to `1`.

### Raw Physics

Use `physics` commands when you want direct control over the rigid body.

Apply an impulse:

```scenemax
rock.physics impulse toward enemy power 30
rock.physics impulse forward 20
rock.physics impulse (0, 8, 20)
```

Apply force, optionally over time:

```scenemax
rock.physics force forward 10
rock.physics force toward enemy power 10 for 0.5 seconds
rock.physics force (0, 40, 0) for 1 second
```

Set velocity directly:

```scenemax
rock.physics velocity (0, 8, 20)
rock.physics angular velocity (0, 12, 0)
```

Apply torque:

```scenemax
rock.physics torque (0, 20, 0)
rock.physics torque (0, 20, 0) impulse
```

Stop physics motion:

```scenemax
rock.physics stop
```

Raw physics command summary:

- `impulse` applies an immediate push.
- `force` applies a continuous force for the current physics tick, or for the requested duration with `for ... seconds`.
- `velocity` replaces the current linear velocity.
- `angular velocity` replaces the current spin.
- `torque` rotates the body through physics.
- `stop` clears linear velocity, angular velocity, and accumulated forces.

## Collision Detection

Detect collision between two objects:

```scenemax
when s1 collides with s2 do
end do
```

Detect collision against a collider on the currently equipped right-hand weapon:

```scenemax
when player1.weapon.colliders["weapon_sphere_collider_1"] collides with crystal_box do
  crystal_box.hide
end do
```

Use this form for reusable equipped weapons. The collider name is the collider id from the weapon asset; SceneMax resolves it through the owner at runtime, so the same weapon definition can be used by multiple players in the same scene.

Detect collision of multiple objects against one target (each checked individually, not simultaneously):

```scenemax
when obj1, obj2, obj3 collides with obj4 do

end do
```

## Joint Mapping

Map joints (bones) to a character for precise collision detection and bone control:

```scenemax
n => dynamic ninja :
  joints ("Joint9","Joint11","Joint12","Joint16","Joint17","Joint18","Joint19",
  "Joint20","Joint21","Joint23","Joint24","Joint25")
```

## Debug Mode

Turn on debug display:

```scenemax
debug.on
```

Turn off debug display:

```scenemax
debug.off
```
