# Weapons Designer

Create and edit `.smweapon` assets with the native [Weapon Designer](../scenemax3d_nextgen/IDE/WEAPON_DESIGNER.md). That guide covers model selection, named postures, attachment joints, colliders, animation previews, saving, and the asset schema.

The examples below use project-defined weapon and actor names; create or substitute those resources in your project.

## SceneMax Script Samples

### Equip a Weapon

```scenemax
player => dynamic fighter1 : pos (0, 0, 0), scale 3
player.weapon = "weapon_training_sword"
```

The left side of the assignment must be a model variable. The right side is a string expression that resolves to a weapon id or weapon file name.

### Switch Posture

```scenemax
player.weapon = "weapon_training_sword"
player.weapon.posture = "ready"
```

The posture value can be either the posture id or posture name.

### Unequip

```scenemax
player.weapon = empty
```

### Use Postures During Animation

```scenemax
player.weapon = "weapon_training_sword"
player.weapon.posture = "ready"
player."Sword Idle" loop

when key space is pressed do
  player.weapon.posture = "attack"
  player."Sword Slash"
  wait 0.35 seconds
  player.weapon.posture = "ready"
end do
```

### Equip From a Variable

```scenemax
player => dynamic fighter1 : pos (0, 0, 0), scale 3
selectedWeapon = "weapon_training_sword"
selectedPosture = "default"

player.weapon = selectedWeapon
player.weapon.posture = selectedPosture
```
