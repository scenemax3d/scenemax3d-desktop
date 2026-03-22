package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;
import org.antlr.v4.runtime.ParserRuleContext;

import java.util.ArrayList;
import java.util.List;

public class VariableDef extends StatementDef{

    public boolean isAsync = false;
    public static final int COLLISION_SHAPE_DEFAULT = 0;
    public static final int COLLISION_SHAPE_BOX = 10;
    public static final int COLLISION_SHAPE_BOXES = 20;

    public static final int VAR_TYPE_3D = 1;
    public static final int VAR_TYPE_2D = 2;
    public static final int VAR_TYPE_STRING = 3;
    public static final int VAR_TYPE_NUMBER = 4;
    public static final int VAR_TYPE_ARRAY = 5;
    public static final int VAR_TYPE_CAMERA = 10;
    public static final int VAR_TYPE_SPHERE = 20;
    public static final int VAR_TYPE_OBJECT = 30;
    public static final int VAR_TYPE_BOX = 40;
    public static final int VAR_TYPE_EXPR_POINTER = 50;
    //public int threadId;

    public String resName;
    public String varName;
    public int varLineNum;

    public int varType;
    public Object value;

    public ParserRuleContext xExpr;
    public ParserRuleContext yExpr;
    public ParserRuleContext zExpr;
    public ParserRuleContext rxExpr;
    public ParserRuleContext ryExpr;
    public ParserRuleContext rzExpr;

    public ParserRuleContext scaleExpr;
    public ParserRuleContext massExpr;
    public EntityPos entityPos;
    public String entityRot;
    public boolean isStatic;
    public boolean isVehicle;
    public boolean useVerbalTurn;
    public float rotDir;
    public boolean isDynamic;
    public List<String> joints;
    public boolean visible = true;
    public int shadowMode=0;
    public SceneMaxParser.Logical_expressionContext resNameExpr;
    public String entityPosJoint;
    public EntityPos calibration;
    public int collisionShape;
    public boolean isExprPointer;
    public VariableDeclarationCommand declaration;
    public boolean isShared;

    @Override
    public boolean validate(ProgramDef prg) {
        ModelDef md = prg.getModel(resName);
        if (md == null) {
            SpriteDef sd = prg.getSprite(resName);
            if(sd==null) {
                //System.err.println(varName + " cannot use resource " + resName + " because this resource is not defined yet");
                return false;
            } else {
                varType=ProgramDef.VAR_TYPE_2D;
            }
        } else {
            varType=ProgramDef.VAR_TYPE_3D;
        }

        return true;
    }
}
