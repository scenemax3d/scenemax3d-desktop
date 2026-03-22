package com.scenemaxeng.projector;

import com.abware.scenemaxlang.parser.SceneMaxParser;
import com.scenemaxeng.compiler.VariableDef;

class FunctionInvoker {

    private SceneMaxParser.Function_valueContext ctx;
    private SceneMaxApp app;
    private SceneMaxScope scope;

    public Object retval;
    public int retvalType = VariableDef.VAR_TYPE_NUMBER;
    public String runtimeError;

    public FunctionInvoker(SceneMaxParser.Function_valueContext ctx, SceneMaxApp app, SceneMaxScope scope) {
        this.ctx=ctx;
        this.app=app;
        this.scope=scope;
    }

    public boolean invoke() {
        String funcName = ctx.java_func_name().getText();

        // Dispatch by first character (case-insensitive) to avoid toLowerCase() allocation
        // and reduce the number of string comparisons
        char c0 = funcName.charAt(0);
        switch (c0 | 0x20) { // lowercase the first char via bitmask
            case 'r':
                if (funcName.equalsIgnoreCase("rnd")) return invokeRnd();
                if (funcName.equalsIgnoreCase("round")) return invokeRound();
                break;
            case 'f':
                if (funcName.equalsIgnoreCase("floor")) return invokeFloor();
                break;
            case 'c':
                if (funcName.equalsIgnoreCase("ceil")) return invokeCeiling();
                if (funcName.equalsIgnoreCase("cos")) return invokeCos();
                break;
            case 'a':
                if (funcName.equalsIgnoreCase("abs")) return invokeAbs();
                break;
            case 's':
                if (funcName.equalsIgnoreCase("sin")) return invokeSin();
                break;
        }

        this.runtimeError = "Function '"+funcName+"' is not supported";
        return false;
    }

    private boolean invokeCos() {
        if(ctx.logical_expression().size()>0) {
            Object val = new ActionLogicalExpression(ctx.logical_expression(0), scope).evaluate();
            retval = Math.cos(ActionLogicalExpression.toDouble(val));
            return true;
        } else {
            this.runtimeError = "Function '"+ctx.java_func_name().getText()+"' expecting argument";
            return false;
        }
    }

    private boolean invokeSin() {
        if(ctx.logical_expression().size()>0) {
            Object val = new ActionLogicalExpression(ctx.logical_expression(0), scope).evaluate();
            retval = Math.sin(ActionLogicalExpression.toDouble(val));
            return true;
        } else {
            this.runtimeError = "Function '"+ctx.java_func_name().getText()+"' expecting argument";
            return false;
        }
    }

    private boolean invokeRound() {
        if(ctx.logical_expression().size()>0) {
            Object val = new ActionLogicalExpression(ctx.logical_expression(0), scope).evaluate();
            retval = Math.round(ActionLogicalExpression.toDouble(val));
            return true;
        } else {
            this.runtimeError = "Function '"+ctx.java_func_name().getText()+"' expecting argument";
            return false;
        }
    }

    private boolean invokeAbs() {
        if(ctx.logical_expression().size()>0) {
            Object val = new ActionLogicalExpression(ctx.logical_expression(0), scope).evaluate();
            retval = Math.abs(ActionLogicalExpression.toDouble(val));
            return true;
        } else {
            this.runtimeError = "Function '"+ctx.java_func_name().getText()+"' expecting argument";
            return false;
        }
    }

    private boolean invokeCeiling() {
        if(ctx.logical_expression().size()>0) {
            Object val = new ActionLogicalExpression(ctx.logical_expression(0), scope).evaluate();
            retval = Math.ceil(ActionLogicalExpression.toDouble(val));
            return true;
        } else {
            this.runtimeError = "Function '"+ctx.java_func_name().getText()+"' expecting argument";
            return false;
        }
    }

    private boolean invokeFloor() {
        if(ctx.logical_expression().size()>0) {
            Object val = new ActionLogicalExpression(ctx.logical_expression(0), scope).evaluate();
            retval = Math.floor(ActionLogicalExpression.toDouble(val));
            return true;
        } else {
            this.runtimeError = "Function '"+ctx.java_func_name().getText()+"' expecting argument";
            return false;
        }
    }


    private boolean invokeRnd() {
        if(ctx.logical_expression().size()>0) {
            Object val = new ActionLogicalExpression(ctx.logical_expression(0), scope).evaluate();
            Double d = (Math.random() * ActionLogicalExpression.toDouble(val));
            retval = Math.floor(d);
            return true;
        } else {
            retval = Math.random();
            return true;
        }

    }

}
