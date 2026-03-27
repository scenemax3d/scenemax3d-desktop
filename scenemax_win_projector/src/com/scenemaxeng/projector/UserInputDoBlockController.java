package com.scenemaxeng.projector;

import com.abware.scenemaxlang.parser.SceneMaxParser;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.scenemaxeng.compiler.DoBlockCommand;

public class UserInputDoBlockController extends DoBlockController {

    public PictureExt targetPicture;
    public SceneMaxParser.Logical_expressionContext goExpr;

    public UserInputDoBlockController(SceneMaxApp app, SceneMaxScope scope, DoBlockCommand cmd) {
        super(app, scope, cmd);
    }

    public boolean checkTargetEntityClicked() {

        if(targetPicture!=null) {
            Vector2f pos = app.getCursorPosition();
            Vector3f targetPos = targetPicture.getLocalTranslation();
            return(pos.x>targetPos.x && pos.x<targetPos.x+targetPicture.getWidthEx() &&
               pos.y>=targetPos.y && pos.y<=targetPos.y+targetPicture.getHeightEx());
        } else if(this.targetVar!=null) {
            Spatial sp= app.getEntitySpatial(this.targetVar,this.targetVarDef.varType);
            return app.rayCastCheck(sp);
        }

        return true;
    }

    public boolean checkGoExpr() {

        boolean goCondition;

        if(goExpr!=null) {
            Object cond = new ActionLogicalExpressionVm(goExpr,parentScope).evaluate();
            if(cond instanceof Boolean) {
                goCondition=(Boolean)cond;
                return goCondition;
            }

        }

        return true;
    }
}
