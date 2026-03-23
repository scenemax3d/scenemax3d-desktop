# Collisions & Physics

## Collision Detection

Detect collision between two objects:

```scenemax
when s1 collides with s2 do
end do
```

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
