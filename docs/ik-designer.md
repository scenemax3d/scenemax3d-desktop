# IK Designer

The IK Designer is the authoring workflow for inverse-kinematics assets. IK assets are stored as `.smik` files and describe how a model's armature should rotate selected joints so that a hand, foot, head, weapon aim, or other body part reaches toward a target.

IK only works on models that use the new animation system and expose a `SkinningControl` armature. The designer scans project models once, marks compatible model entries with `hasSkeleton`, `hasArmature`, `ikReady`, and `ikCompatibilityScanned`, and then uses those flags for faster loading later.

## File Placement

IK definitions use the `.smik` extension.

There are two copies involved while authoring:

- the designer document, which can live anywhere in the project files tree
- the runtime resource, which is exported on Save under `resources/ik`

When you save an IK asset, SceneMax writes:

```text
resources/ik/<ik_id>.smik
```

Runtime IK assets are indexed by both the `id` field and the file name without `.smik`.

## Designer Workflow

Create an IK asset from:

```text
Assets > Create New IK Asset...
```

or right-click a project folder and choose:

```text
Create IK Asset
```

Choose a target model in the `Target Model` combo. The designer only lists project models that have a scanned IK-ready armature.

The `Use Case` combo is the quickest way to start. Pick a common setup such as `Right hand reach`, `Left foot placement`, `Head look at`, or `Upper body aim`, then press `Apply`. The designer will switch to the right solver and try to fill the needed joints from the selected model's armature names. If the rig uses unusual joint names, it fills what it can and tells you which fields still need a manual choice.

Selected IK joints are highlighted in the preview skeleton and labeled with short IK role names such as `Root`, `Middle`, `End`, `Start`, `Look 1`, or `Aim 1`. This makes it easier to confirm that the template picked the correct arm, leg, head, or spine chain before saving.

## Running The Preview

The preview supports two ways to test the selected layer:

1. Choose a `Target Model`.
2. Pick a `Use Case` and press `Apply`, or fill the visible joint fields manually.
3. In the preview toolbar, choose `Edit Target`.
4. Move the red `ik_preview_target` with the gizmo.
5. Press `Run IK` to see the final solved pose immediately, or press `Play` to simulate runtime IK over time.

`Run IK` is a one-shot authoring check. It runs the selected layer once from the model's original pose, using `ik_preview_target` as the preview target.

`Play` is the closer game-style simulation. It runs the selected IK layer every frame and blends the layer weight in over a short moment, so moving the preview target shows the chain reacting continuously. Press `Stop` to stop live IK, or `Reset Pose` to return the model to the original pose.

To test IK on top of normal animation, choose a clip from the preview toolbar's `Animation` combo and press `Play Anim`. Then press `Play` in the designer form. The preview applies the animation pose first and the IK correction after it, matching the intended runtime order.

## Joint Field Mental Model

A joint name is a bone from the selected model's armature. The designer fills joint combos from that armature. They are editable so you can still type a name manually if an imported rig has unusual naming.

### Two-Bone IK

Use this for arms, legs, and any simple three-joint chain.

`Root Joint`
: The first joint in the chain, closest to the body. For an arm this is usually upper arm or shoulder. For a leg this is usually upper leg or hip.

`Middle Joint`
: The bending joint. For an arm this is the elbow. For a leg this is the knee.

`End Joint`
: The joint that should reach the target. For an arm this is the hand or wrist. For a leg this is the foot or ankle.

`Target Object`
: The scene entity the end joint tries to reach.

`Pole Target`
: Optional scene entity that tells the chain which way to bend. Use it to keep elbows or knees pointing in a controlled direction. Without a pole target, the solver infers the bend direction from the current pose.

Example arm:

```text
Root Joint   = mixamorig:RightArm
Middle Joint = mixamorig:RightForeArm
End Joint    = mixamorig:RightHand
Target Object = right_hand_goal
Pole Target   = right_elbow_pole
```

Example leg:

```text
Root Joint   = mixamorig:RightUpLeg
Middle Joint = mixamorig:RightLeg
End Joint    = mixamorig:RightFoot
Target Object = right_foot_goal
Pole Target   = right_knee_pole
```

### Foot IK

Foot IK currently uses the same chain fields as Two-Bone IK:

- `Root Joint`: upper leg
- `Middle Joint`: knee/lower leg
- `End Joint`: foot/ankle

Foot-specific ground alignment can build on this same chain.

### FABRIK

Use this for a longer chain where the solver should work from one end of the chain to the other.

`Start Joint`
: The first joint in the FABRIK chain.

`End Joint`
: The final joint that should move toward the target.

`Target Object`
: The scene entity the end joint tries to reach.

In the first runtime implementation, FABRIK is backed by the same solving behavior as Two-Bone IK, so prefer Two-Bone IK for arms and legs until full multi-joint FABRIK limits are added.

### Look-At IK and Aim IK

Use this when one or more joints should rotate to face a target, such as a head looking at the player or a spine/weapon aim chain turning toward an enemy.

`Affected Joints`
: A comma-separated list of joints that are allowed to rotate. Add joints from the picker. If this list is empty, the solver falls back to `End Joint`.

`End Joint`
: Optional fallback joint for Look-At/Aim when `Affected Joints` is empty.

`Target Object`
: The scene entity the affected joints rotate toward.

Example head look-at:

```text
Affected Joints = mixamorig:Neck, mixamorig:Head
Target Object   = player
```

Example upper-body aim:

```text
Affected Joints = mixamorig:Spine1, mixamorig:Spine2, mixamorig:RightShoulder
Target Object   = enemy
```

## Target Object

`Target Object` is not a joint. It is the name of a scene entity in the game world. At runtime, the IK solver asks the scene for that entity and uses its world position.

Use target objects for things like:

- a hand reach point
- an enemy to look at
- a foot placement marker
- a weapon aim point

In the designer preview, SceneMax creates a test target named:

```text
ik_preview_target
```

Use this value while authoring if you only want to test the chain. The preview toolbar lets you edit either the model or this target. You can show the target as a sphere, box, or simple project model.

For runtime, replace `ik_preview_target` with the real entity name created by your scene or script.

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

## Pole Target

`Pole Target` is also a scene entity name. It is only used by bending chains such as arms and legs.

Think of it as a hint for where the elbow or knee should point:

- an elbow pole in front of the character keeps the elbow bending forward
- a knee pole in front of the character keeps the knee bending naturally
- a pole to the side can deliberately push the limb sideways

If the chain twists or bends backward, add a pole target and move it until the bend direction is stable.

## Choosing Good Chains

Use the shortest chain that describes the motion you want:

- arm reach: upper arm -> forearm -> hand
- leg placement: upper leg -> lower leg -> foot
- head look: neck/head as affected joints
- torso aim: spine joints as affected joints

Avoid including unrelated joints. For example, do not put the whole spine in a hand reach chain unless you really want the torso to participate.

## Practical Setup Recipe

For a hand reach:

1. Select the character model in `Target Model`.
2. Pick the arm's upper joint as `Root Joint`.
3. Pick the elbow/lower-arm joint as `Middle Joint`.
4. Pick the hand/wrist joint as `End Joint`.
5. Set `Target Object` to `ik_preview_target`.
6. Use the preview toolbar to edit `Target`.
7. Move the target sphere/box and confirm the hand follows.
8. Add a `Pole Target` in runtime if the elbow bends the wrong way.

For a look-at:

1. Choose `LookAtIK` or `AimIK`.
2. Add neck/head/spine joints to `Affected Joints`.
3. Set `Target Object` to `ik_preview_target` for testing.
4. Move the preview target and confirm the selected joints rotate toward it.
