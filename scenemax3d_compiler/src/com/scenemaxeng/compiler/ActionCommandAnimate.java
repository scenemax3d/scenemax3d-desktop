package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxParser;
import org.antlr.v4.runtime.ParserRuleContext;

public class ActionCommandAnimate extends VariableActionStatement {
    public String animationName;
    public ParserRuleContext speedExpr;
    public boolean loop;
    public SceneMaxParser.Logical_expressionContext goExpr;
    public boolean isProtected = false;
    public String frameRangeStart;
    public boolean frameRangeStartPercent;
    public String frameRangeEnd;
    public boolean frameRangeEndPercent;

    public boolean hasFrameRange() {
        return frameRangeStart != null && frameRangeEnd != null;
    }

    public void copyFrameRangeFrom(ActionCommandAnimate source) {
        if (source == null) {
            return;
        }
        this.frameRangeStart = source.frameRangeStart;
        this.frameRangeStartPercent = source.frameRangeStartPercent;
        this.frameRangeEnd = source.frameRangeEnd;
        this.frameRangeEndPercent = source.frameRangeEndPercent;
    }

}
