package com.scenemaxeng.compiler;

public class ThrowMotionApplyCommand extends ActionStatementBase {
    public String motionVarName;
    public VariableDef motionVarDef;
    public String appliedObjectVarName;
    public VariableDef appliedObjectVarDef;
    public int motionVarLine;
    public int appliedObjectVarLine;
    public boolean appliedObjectIsEquippedWeapon;

    @Override
    public boolean validate(ProgramDef prg) {
        if (motionVarDef == null) {
            this.lastError = "Motion '" + motionVarName + "' doesn't exist";
            return false;
        }
        if (motionVarDef.varType != VariableDef.VAR_TYPE_THROW_MOTION) {
            this.lastError = "'" + motionVarName + "' is not a motion";
            return false;
        }
        if (appliedObjectVarDef == null) {
            this.lastError = (appliedObjectIsEquippedWeapon ? "Weapon owner '" : "Object '")
                    + appliedObjectVarName + "' doesn't exist";
            return false;
        }
        return true;
    }
}
