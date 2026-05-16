package com.scenemaxeng.projector;

import com.abware.scenemaxlang.parser.SceneMaxParser;
import com.scenemaxeng.compiler.DoBlockCommand;

import java.util.ArrayList;
import java.util.List;

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
    final List<ThrowMotionRuntimeEvent> events = new ArrayList<>();

    void addEvent(String eventName, double indexPercent, DoBlockCommand doBlock) {
        events.add(new ThrowMotionRuntimeEvent(eventName, indexPercent, doBlock));
    }

    static class ThrowMotionRuntimeEvent {
        final String eventName;
        final double indexPercent;
        final DoBlockCommand doBlock;

        ThrowMotionRuntimeEvent(String eventName, double indexPercent, DoBlockCommand doBlock) {
            this.eventName = eventName;
            this.indexPercent = indexPercent;
            this.doBlock = doBlock;
        }
    }
}
