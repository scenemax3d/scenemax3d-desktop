package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.CinematicCameraVariableDef;

class CinematicCameraInst extends EntityInstBase {
    public final CinematicCameraVariableDef cinematicVarDef;
    public final RuntimeCinematicRig rig;

    CinematicCameraInst(CinematicCameraVariableDef varDef, RuntimeCinematicRig rig, SceneMaxScope scope) {
        this.cinematicVarDef = varDef;
        this.rig = rig;
        this.varDef = varDef;
        this.scope = scope;
    }
}
