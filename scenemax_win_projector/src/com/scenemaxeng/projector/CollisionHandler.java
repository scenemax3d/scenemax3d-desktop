package com.scenemaxeng.projector;

import com.abware.scenemaxlang.parser.SceneMaxParser;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;

public class CollisionHandler {

    public SpriteEmitter sprite1;
    public SpriteEmitter sprite2;
    public String joint1;
    public String joint2;
    public SceneMaxParser.Logical_expressionContext goExpr;

    DoBlockController doBlock;

    public boolean checkSpritesCollision() {
        if(sprite1==null) return true;

        if(sprite2!=null) {
            Spatial g1 = sprite1.getSpatial();
            Spatial g2 = sprite2.getSpatial();

            Vector3f vec1 = g1.getWorldTranslation();
            Vector3f vec2 = g2.getWorldTranslation();

            return (Math.abs(vec1.getX()-vec2.getX())<1.6 && Math.abs(vec1.getY()-vec2.getY())<1.6);

        }

        return false;
    }

    public boolean checkGoExpr() {

        boolean goCondition;

        if(goExpr!=null) {
            Object cond = new ActionLogicalExpressionVm(goExpr,this.doBlock.parentScope).evaluate();
            if(cond instanceof Boolean) {
                goCondition=(Boolean)cond;
                return goCondition;
            }

        }

        return true;
    }

}
