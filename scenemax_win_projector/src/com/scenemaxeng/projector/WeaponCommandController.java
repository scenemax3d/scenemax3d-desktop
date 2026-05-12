package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.WeaponCommand;

public class WeaponCommandController extends SceneMaxBaseController {

    public WeaponCommandController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, WeaponCommand cmd) {
        super(app, prg, scope, cmd);
    }

    @Override
    public boolean run(float tpf) {
        if (forceStop) {
            return true;
        }
        WeaponCommand weaponCommand = (WeaponCommand) cmd;
        if (!targetCalculated) {
            if (findTargetVar() != 0) {
                return true;
            }
            targetCalculated = true;
        }
        String owner = targetVar;
        if (weaponCommand.action == WeaponCommand.ACTION_EQUIP) {
            Object value = new ActionLogicalExpressionVm(weaponCommand.weaponNameExpr, scope).evaluate();
            if (value != null) {
                app.equipWeapon(owner, value.toString());
            }
        } else if (weaponCommand.action == WeaponCommand.ACTION_UNEQUIP) {
            app.unequipWeapon(owner);
        } else if (weaponCommand.action == WeaponCommand.ACTION_SET_POSTURE) {
            Object value = new ActionLogicalExpressionVm(weaponCommand.postureNameExpr, scope).evaluate();
            if (value != null) {
                app.setWeaponPosture(owner, value.toString());
            }
        }
        return true;
    }
}
