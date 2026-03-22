package com.scenemaxeng.compiler;

public class CreateSpriteCommand extends ActionStatementBase {

    public final SpriteDef spriteDef;

    public CreateSpriteCommand(SpriteDef spriteDef, VariableDef varDef) {
        this.spriteDef=spriteDef;
        this.varDef = varDef;
    }

}
