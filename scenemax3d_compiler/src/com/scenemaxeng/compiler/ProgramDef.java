package com.scenemaxeng.compiler;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ProgramDef {

    public enum ScreenMode {
        UNSPECIFIED,
        WINDOW,
        FULL,
        BORDERLESS
    }

    public ProgramDef parent = null;

    public static int scopeSeq=0;
    public static final int VAR_TYPE_3D = 1;
    public static final int VAR_TYPE_2D = 2;
    public static final int VAR_TYPE_CAMERA = 10; // aligned with VariableDef.VAR_TYPE_CAMERA
    public static final int VAR_TYPE_SPHERE = 20; // aligned with VariableDef.VAR_TYPE_SPHERE
    public static final int VAR_TYPE_EFFEKSEER = 80; // aligned with VariableDef.VAR_TYPE_EFFEKSEER
    public static final int VAR_TYPE_CINEMATIC_CAMERA = 90; // aligned with VariableDef.VAR_TYPE_CINEMATIC_CAMERA

//
    public int scopeId = ++scopeSeq;
    public List<String> syntaxErrors = new ArrayList<>();
    public ArrayList<VariableDef> vars = new ArrayList<>();
    public HashMap<String, VariableDef> vars_index = new HashMap<String, VariableDef>();
    public HashMap<String, GroupDef> groups = new HashMap<>();
    public HashMap<String, ModelDef> models = new HashMap<>();
    public HashMap<String, FunctionBlockDef> functions = new HashMap<>();
    public HashMap<String, SpriteDef> sprites = new HashMap<String, SpriteDef>();
    public ArrayList<StatementDef> actions = new ArrayList<>();
    public ArrayList<StatementDef> requireResourceActions = new ArrayList<>();
    public List<String> inParams=new ArrayList<>();
    public ScreenMode screenMode = ScreenMode.UNSPECIFIED;

    public void addCameraVariableDef() {
        VariableDef def = new VariableDef();
        def.varType=VariableDef.VAR_TYPE_CAMERA;
        def.varName="camera";
        vars_index.put("camera",def);
        vars_index.put("Camera",def);
    }

    public FunctionBlockDef getFunc(String funcName) {
        FunctionBlockDef def=functions.get(funcName);
        if(def==null) {
            if(parent==null) {
                return null;
            }

            return parent.getFunc(funcName);
        } else {
            return def;
        }

    }

    public SpriteDef getSprite(String resName) {
        SpriteDef def = sprites.get(resName);
        if(def==null){
            if(parent==null) {
                return null;
            }

            return parent.getSprite(resName);
        } else {
            return def;
        }
    }

    public ModelDef getModel(String resName) {

        ModelDef def = models.get(resName);
        if(def==null){
            if(parent==null) {
                return null;
            }

            return parent.getModel(resName);
        } else {
            return def;
        }

    }

    public GroupDef getGroup(String resName) {

        GroupDef def = groups.get(resName);
        if(def==null){
            if(parent==null) {
                return null;
            }

            return parent.getGroup(resName);
        } else {
            return def;
        }

    }

    public VariableDef getVar(String targetVar) {

        // first priority is to function arguments
        // - inParams only exists in ProgramDef which is dedicated to function code block
        if(inParams!=null && inParams.contains(targetVar)) {
            VariableDef tempVar = new VariableDef();
            tempVar.varType=VariableDef.VAR_TYPE_OBJECT;
            tempVar.varName=targetVar;
            return tempVar;
        }

        VariableDef def = vars_index.get(targetVar);
        if(def==null){
            if(parent==null) {
                return null;
            }

            return parent.getVar(targetVar);
        } else {
            return def;
        }


    }

    public void copySharedEntities(ProgramDef prevPrg) {
        if (prevPrg == null) {
            return;
        }

        prevPrg.vars_index.forEach((key, varDef) -> {
            if (varDef.isShared) {
                this.vars_index.put(key, varDef);
                if (varDef.varType == VAR_TYPE_3D) {
                    this.models.put(varDef.resName, prevPrg.models.get(varDef.resName));
                }
            }
        });

    }
}
