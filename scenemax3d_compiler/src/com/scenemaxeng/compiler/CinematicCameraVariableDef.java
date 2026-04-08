package com.scenemaxeng.compiler;

public class CinematicCameraVariableDef extends VariableDef {

    public String cinematicCameraId;
    public String cinematicSourceFile;

    @Override
    public boolean validate(ProgramDef prg) {
        this.varType = VAR_TYPE_CINEMATIC_CAMERA;
        if (cinematicCameraId == null || cinematicCameraId.isBlank()) {
            return false;
        }
        if (cinematicSourceFile == null || cinematicSourceFile.isBlank()) {
            return false;
        }
        return true;
    }
}
