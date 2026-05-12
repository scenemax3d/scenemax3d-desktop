# Weapons Designer

The Weapons Designer is the authoring workflow for SceneMax3D weapon definitions. A weapon definition is stored as a `.smweapon` JSON file and describes:

- the weapon id used by scripts and runtime systems
- the 3D model asset used as the visible weapon
- one or more postures for attaching that model to a character joint
- per-posture local position, rotation, and scale offsets
- editor-only preview metadata, such as the player model used in the preview

At runtime, scripts equip a weapon onto a character and switch between the weapon's postures. The posture controls where the model is attached and how it is transformed relative to the selected attachment point.

## File Placement

Weapon definitions use the `.smweapon` extension.

The asset index loads weapons from these locations:

- `resources/weapons`
- `resources/Weapons`
- project folders loaded through the project asset mapping

Project weapon files can also live beside scripts. For example:

```text
projects/fighting_game_project/scripts/Fighting Game/game_level_train/player_weapon.smweapon
```

Weapons are indexed by both:

- the `id` field inside the `.smweapon` file
- the file name without the `.smweapon` extension

This means a file named `player_weapon.smweapon` with id `weapon_player_weapon` can usually be referenced as either `weapon_player_weapon` or `player_weapon`, depending on which name is clearer for the script.

## Designer Workflow

Open a `.smweapon` file in the designer to edit it with the Weapons Designer panel.

The left side contains the editable weapon fields and posture list. The right side contains the live 3D preview.

### Overview Fields

`Weapon ID`
: Stable id used by runtime scripts. Prefer lowercase ids with underscores, such as `weapon_iron_sword`.

`Model Asset`
: 3D model asset id for the weapon visual. The designer lists known model assets from the project resources. Runtime also accepts a direct model path if the id is not found in the model index.

`Player Model`
: Editor-only preview holder model. This value is saved under `designerMetadata.previewPlayerModelAssetId` and is not used by runtime weapon equip logic.

### Posture Fields

`Posture ID`
: Stable id used by scripts, such as `default`, `ready`, `attack`, or `left_hand`.

`Name`
: Human-readable name. Runtime lookup accepts posture id or posture name.

`Attach To`
: Character joint or attachment point. Examples from Mixamo-style rigs include `mixamorig:RightHand`, `mixamorig:LeftHand`, `mixamorig:Spine2`, and `mixamorig:Head`.

`Position Offset X/Y/Z`
: Local translation applied after the weapon is attached to the joint.

`Rotation Offset X/Y/Z`
: Local Euler rotation in degrees.

`Scale X/Y/Z`
: Local weapon scale. These values are often much smaller than `1.0` when the weapon model source scale is large.

### Posture Actions

`Add`
: Creates a new posture by copying the current default posture's exact attachment point and transform values, then gives it a new id and name. This makes the new posture a safe starting point with the same player/weapon proportions as the default posture.

`Duplicate`
: Copies the selected posture and inserts it after the original.

`Delete`
: Removes the selected posture. A weapon must always keep at least one posture.

`Set Default`
: Marks the selected posture as the posture used immediately after equip.

## Preview Panel

The preview panel renders the selected weapon on the selected preview player model.

Useful controls:

- drag in the viewport to orbit the camera
- mouse wheel to zoom
- `Reset View` to fit the preview camera back around the character and weapon
- `Move` and `Rotate` to edit the weapon transform with gizmos
- `Test Animation` to preview the weapon while the holder model plays an animation

Editing posture transform fields updates the weapon in place and preserves the current preview camera zoom/orbit. Changing the player model or weapon model may refit the preview camera because the visible bounds have changed.

If `Attach To` is empty or the preview player has no matching joint, the preview uses a fallback attachment point. Runtime is stricter: a missing attachment point reports a runtime error and the weapon is not attached.

## JSON Schema

A minimal weapon definition looks like this:

```json
{
  "type": "SceneMaxWeaponDefinition",
  "schemaVersion": "1.0",
  "id": "weapon_training_sword",
  "modelAssetId": "training_sword",
  "defaultPostureId": "default",
  "postures": [
    {
      "id": "default",
      "name": "Default",
      "attachmentPoint": "mixamorig:RightHand",
      "transform": {
        "offsetX": 0.0,
        "offsetY": 0.0,
        "offsetZ": 0.0,
        "rotationX": 0.0,
        "rotationY": 0.0,
        "rotationZ": 0.0,
        "scaleX": 0.1,
        "scaleY": 0.1,
        "scaleZ": 0.1
      }
    }
  ],
  "designerMetadata": {
    "previewPlayerModelAssetId": "fighter1"
  }
}
```

### Fields

`type`
: Should be `SceneMaxWeaponDefinition`.

`schemaVersion`
: Current schema version is `1.0`.

`id`
: Runtime id for this weapon.

`modelAssetId`
: Weapon model id or loadable model path.

`defaultPostureId`
: Posture id used when the weapon is equipped.

`postures`
: Ordered posture list. At least one posture is required.

`designerMetadata`
: Optional editor-only metadata. Runtime does not need this field.

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

## Java API Samples

### Create and Save a Weapon Definition

```java
import com.scenemaxeng.common.weapons.WeaponAttachmentTransform;
import com.scenemaxeng.common.weapons.WeaponDefinition;
import com.scenemaxeng.common.weapons.WeaponPostureDefinition;

import java.io.File;

public class CreateWeaponDefinitionSample {
    public static void main(String[] args) throws Exception {
        WeaponDefinition weapon = WeaponDefinition.createTemplate("Training Sword", "sword");
        weapon.setId("weapon_training_sword");
        weapon.setModelAssetId("training_sword");

        WeaponPostureDefinition defaultPosture = weapon.getDefaultPosture();
        defaultPosture.setId("default");
        defaultPosture.setName("Default");
        defaultPosture.setAttachmentPoint("mixamorig:RightHand");

        WeaponAttachmentTransform transform = defaultPosture.getTransform();
        transform.setOffsetX(0.0);
        transform.setOffsetY(0.0);
        transform.setOffsetZ(0.0);
        transform.setRotationX(0.0);
        transform.setRotationY(0.0);
        transform.setRotationZ(0.0);
        transform.setScaleX(0.1);
        transform.setScaleY(0.1);
        transform.setScaleZ(0.1);

        weapon.save(new File("resources/weapons/training_sword.smweapon"));
    }
}
```

### Add a Posture Based on the Default Posture

Use a JSON round-trip when you want an exact copy of an existing posture.

```java
WeaponDefinition weapon = WeaponDefinition.load(new File("resources/weapons/training_sword.smweapon"));

WeaponPostureDefinition attack = WeaponPostureDefinition.fromJSON(weapon.getDefaultPosture().toJSON());
attack.setId("attack");
attack.setName("Attack");
attack.getTransform().setRotationZ(35.0);
attack.getTransform().setOffsetZ(0.25);

weapon.getPostures().add(attack);
weapon.save(new File("resources/weapons/training_sword.smweapon"));
```

### Load and Validate a Weapon

```java
WeaponDefinition weapon = WeaponDefinition.load(new File("resources/weapons/training_sword.smweapon"));

if (!weapon.validate().isValid()) {
    throw new IllegalStateException("Weapon definition has validation errors.");
}

WeaponPostureDefinition posture = weapon.findPosture("attack");
System.out.println(posture.getAttachmentPoint());
```

### Equip Through the Runtime API

Most gameplay code should use SceneMax script commands, but Java runtime code can call the weapon system through `SceneMaxApp`.

```java
app.equipWeapon("player1", "weapon_training_sword");
app.setWeaponPosture("player1", "attack");
app.unequipWeapon("player1");
```

Java also exposes slot-aware overloads:

```java
app.equipWeapon("player1", "weapon_training_sword", "rightHand");
app.setWeaponPosture("player1", "rightHand", "attack");
app.unequipWeapon("player1", "rightHand");
```

The script syntax currently targets the default right-hand slot.

## Runtime Behavior

When a weapon is equipped:

1. The runtime resolves the weapon id through the asset mapping weapon index.
2. The weapon definition is validated.
3. The weapon model is loaded from `modelAssetId`.
4. The default posture is resolved through `defaultPostureId`.
5. The character attachment node is resolved from the posture's `attachmentPoint`.
6. The weapon model is attached to that node.
7. The posture transform is applied as local translation, rotation, and scale.

When a posture changes, the existing weapon model is detached and reattached using the new posture.

## Validation Rules

Weapon definitions are validated before runtime equip.

Hard errors:

- `id` must not be empty
- `postures` must contain at least one posture
- every posture must have an `id`
- every posture must have a `name`
- posture scale must be greater than zero on every axis

Warnings:

- `modelAssetId` may be empty, but most gameplay weapons should assign a model

Example validation code:

```java
WeaponValidationResult result = weapon.validate();
if (!result.isValid()) {
    result.getIssues().forEach(issue ->
            System.err.println(issue.getField() + ": " + issue.getMessage()));
}
```

## Backward Compatibility

Older weapon JSON may contain legacy top-level attachment fields instead of a `postures` array. The loader converts these fields into the default posture:

```json
{
  "id": "weapon_legacy",
  "modelAssetId": "legacy_sword",
  "defaultAttachmentPoint": "mixamorig:RightHand",
  "attachmentTransform": {
    "scaleX": 0.5,
    "scaleY": 0.5,
    "scaleZ": 0.5
  }
}
```

After loading and saving, the file is written using the current `postures` format.

## Attachment Point Notes

Attachment point names must match the target model's skeleton or attachment nodes. Mixamo rigs commonly prefix joints with `mixamorig:`.

For reliable authoring:

- create the character with the joint list needed by your gameplay
- use the preview player model that matches the runtime character model
- select attachment points from the designer dropdown when available
- keep posture ids stable once scripts reference them

Example character setup with joints:

```scenemax
player => dynamic fighter1 : pos (0, 0, 0), scale 3,
  joints (
    "mixamorig:RightHand",
    "mixamorig:LeftHand",
    "mixamorig:Spine2",
    "mixamorig:Head"
  ) async
```

## Recommended Posture Set

A simple melee weapon usually starts with:

- `default`: safe equip pose
- `ready`: idle combat pose
- `attack`: slash or thrust pose
- `carry`: relaxed out-of-combat pose

A two-handed weapon may add:

- `right_hand`: primary hand attachment
- `left_hand_preview`: alignment reference while authoring
- `block`: defensive posture

The current runtime attaches one weapon model to one attachment point per posture. Two-handed alignment is usually authored by picking the primary hand as `Attach To` and tuning offsets against the second hand visually in the preview.

## Source Architecture

The weapons system is split across shared data types, designer UI, compiler parsing, and runtime attachment.

`WeaponDefinition`
: Shared model for `.smweapon` files. Handles JSON load/save, default posture lookup, validation, and legacy conversion.

`WeaponPostureDefinition`
: Shared model for posture id, display name, attachment point, and transform.

`WeaponAttachmentTransform`
: Shared local offset/rotation/scale values.

`WeaponDesignerPanel`
: Swing editor for overview fields and posture fields. It owns dirty tracking, save/reload behavior, posture list actions, and syncing UI state into `WeaponDefinition`.

`WeaponPreviewPanel`
: Swing wrapper around the JME preview canvas and toolbar.

`WeaponPreviewApp`
: JMonkeyEngine preview scene. It loads the preview holder model, loads the weapon model, resolves attachment nodes, applies posture transforms, and manages gizmo interaction.

`SceneMax.g4`
: Defines the script grammar:

```antlr
weapon_action : weapon_equip | weapon_posture ;
weapon_equip : var_decl '.' Weapon Equals (Empty | logical_expression) ;
weapon_posture : var_decl '.' Weapon '.' Posture Equals logical_expression ;
```

`SceneMaxLanguageParser`
: Converts parsed weapon syntax into `WeaponCommand`.

`WeaponCommandController`
: Evaluates script expressions and calls `SceneMaxApp.equipWeapon`, `SceneMaxApp.unequipWeapon`, or `SceneMaxApp.setWeaponPosture`.

`WeaponSystem`
: Runtime equipment owner map. It resolves weapon definitions, validates them, tracks equipped weapons, and applies posture changes.

`WeaponAttachmentResolver`
: Loads the weapon model, resolves the owner joint attachment node, applies the posture transform, and attaches the model to the character.

## Troubleshooting

`Cannot equip weapon: weapon '...' was not found.`
: The weapon id or file name is not in the weapon index. Check the `.smweapon` location and the `id` field.

`Weapon attachment point '...' was not found on 'player'.`
: The posture's `attachmentPoint` does not exist on the runtime character model. Use the exact joint name from the model.

The weapon is huge or tiny.
: Tune `scaleX`, `scaleY`, and `scaleZ` in the posture transform. New postures created in the designer copy the default posture scale so proportions remain stable.

The preview looks correct but runtime does not.
: Confirm the preview player model is the same model used by the runtime character, or at least has the same skeleton names and scale.

Changing posture fields moves the weapon but the camera stays where it was.
: This is expected. Use `Reset View` when you want the preview camera to fit the scene again.

The weapon equips but posture switching fails.
: Check that the posture id or name in the script exactly matches a posture in the `.smweapon` file. Runtime lookup is case-insensitive for ids and names, but spelling still matters.

The weapon works in Java with a slot overload but not in script.
: The current script syntax uses the default right-hand slot. Use Java APIs for explicit slot routing until script-level slot syntax is added.

## Related Source Files

- `scenemax_designer/src/com/scenemax/designer/weapon/WeaponDesignerPanel.java`
- `scenemax_designer/src/com/scenemax/designer/weapon/WeaponPreviewPanel.java`
- `scenemax_designer/src/com/scenemax/designer/weapon/WeaponPreviewApp.java`
- `scenemax3d_common_types/src/com/scenemaxeng/common/weapons/WeaponDefinition.java`
- `scenemax3d_common_types/src/com/scenemaxeng/common/weapons/WeaponPostureDefinition.java`
- `scenemax3d_common_types/src/com/scenemaxeng/common/weapons/WeaponAttachmentTransform.java`
- `scenemax_win_projector/src/com/scenemaxeng/projector/WeaponSystem.java`
- `scenemax_win_projector/src/com/scenemaxeng/projector/WeaponAttachmentResolver.java`
