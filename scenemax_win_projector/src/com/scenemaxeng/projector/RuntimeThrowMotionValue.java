package com.scenemaxeng.projector;

import com.abware.scenemaxlang.parser.SceneMaxParser;

class RuntimeThrowMotionValue {
    enum TargetKind {
        NONE,
        OBJECT,
        POSITION
    }

    String motionAssetId;
    TargetKind targetKind = TargetKind.NONE;
    String targetVarName;
    SceneMaxParser.Logical_expressionContext targetXExpr;
    SceneMaxParser.Logical_expressionContext targetYExpr;
    SceneMaxParser.Logical_expressionContext targetZExpr;
}
