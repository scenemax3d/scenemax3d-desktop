# Collisions & Physics

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
