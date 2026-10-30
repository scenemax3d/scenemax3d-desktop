# IK Designer

Use the native [IK Designer](../scenemax3d_nextgen/IDE/IK_DESIGNER.md) to author `.smik` assets for registered skinned glTF/GLB models. The guide covers joint chains, target objects, solver choices, preview controls, and runtime limits. FootIK targets an authored contact point; it does not automatically raycast terrain or compensate the pelvis.

## Runtime Commands

Attach an IK resource to a model with:

```text
player.ik = "player_interaction_ik"
```

Remove it with:

```text
player.ik = empty
```

After the resource is attached, address layers explicitly by layer id:

```text
player.ik.right_hand_reach.target = lever_handle
player.ik.right_hand_reach.blend = 0.2
player.ik.right_hand_reach.weight = 1
player.ik.right_hand_reach.play
player.ik.right_hand_reach.stop
```

You can also provide common play/stop options inline:

```text
player.ik.right_hand_reach.play : target lever_handle, blend 0.2, weight 1
player.ik.head_look.play : target enemy, blend 0.15, weight 0.8
player.ik.weapon_aim.stop : blend 0.1
```

Targets are live scene references. If `lever_handle`, `enemy`, or any other target entity moves, the IK layer follows it every frame while the layer is playing. `blend` is the fade time in seconds, and `weight` is the layer influence from `0` to `1`.
