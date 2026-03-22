package com.scenemaxeng.compiler;

public class GraphicEntityCreationCommand extends ActionStatementBase {

    public GraphicEntityCreationCommand(VariableDef varDef) {
        this.varDef = varDef;
        this.isAsync = varDef.isAsync;
    }

}
