package com.scenemaxeng.projector;


import com.abware.scenemaxlang.parser.SceneMaxParser;
import com.scenemaxeng.compiler.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class DoBlockController extends SceneMaxBaseController {

    public final SceneMaxScope parentScope; // the scope which runs this sequence

    public SceneMaxParser.Logical_expressionContext goExpr;
    private DoBlockCommand cmd = null;
    private ArrayList<SceneMaxBaseController> _controllers = new ArrayList<>();
    private SceneMaxScope scope = null;
    private double count = 0;
    private double target = -1;
    public HashMap<String,Object> funcScopeParams;
    private List<String> paramsNames;
    private List<SceneMaxParser.Logical_expressionContext> paramsExp;
    public ActionLogicalExpressionVm intervalExpr;
    private Double interval=-1.0;
    private double timerIntervalCount=0;
    private boolean timerTicked;
    private boolean checkGoExpr = true;
    private int eventHandlersCount;

    // Cached to avoid per-frame allocation
    private ActionLogicalExpressionVm goExprCached;
    private ActionLogicalExpressionVm loopExprCached;


    public DoBlockController(SceneMaxApp app, SceneMaxScope scope, DoBlockCommand cmd) {
        this.app=app;
        this.parentScope=scope;
        this.cmd=cmd;

        this.adhereToPauseStatus=false; // do block works even when the scene is paused
    }

    public void reset() {
        this.scope = null;
    }

    @Override
    public void init() {

        if(cmd.prg==null) {
            app.handleRuntimeError("Check 3D/2D resources scope");
        }

    }

    @Override
    public boolean run(float tpf) {

        if(forceStop) {
            return true;
        }

        boolean goCondition=true;
        boolean loopCondition = false;

        if(this.interval==-1 && this.intervalExpr!=null) {
            Object interval = this.intervalExpr.evaluate();
            if(interval instanceof Double) {
                this.interval=(Double)interval;
            }
        }

        if(checkGoExpr && goExpr!=null) {
            checkGoExpr=this.cmd.useGoExprEveryIteration;
            if(goExprCached == null) {
                goExprCached = new ActionLogicalExpressionVm(goExpr, parentScope);
            }
            Object cond = goExprCached.evaluate();
            if(cond instanceof Boolean) {
                goCondition=(Boolean)cond;
                if(!goCondition) { // no-go & regular procedure, no timer - stop
                    if(this.interval==-1) {
                        return true;
                    } else {
                        // set a void scope to prevent the controller from working while waiting for the timer tick event
                        scope = new SceneMaxScope();
                    }
                }
            }

        }

        if(this.funcScopeParams==null) {
            evalFunctionScopeParams();
        }

        // run actions
        if(scope==null && goCondition) {
            scope = new SceneMaxScope();
            scope.mainController.app=app;
            scope.isReturnPoint=cmd.isReturnPoint;
            scope.isSecondLevelReturnPoint=cmd.isSecondLevelReturnPoint;
            scope.mainController.adhereToPauseStatus=false; // scope main controller never pauses
            scope.funcScopeParams=this.funcScopeParams;
            scope.parent=parentScope;

            if(cmd.creatorScope!=null) {
                scope.sequenceCreatorScope = (SceneMaxScope)cmd.creatorScope;
            }

            if(this.interval!=-1) {
                scope.isReturnPoint=true;
                scope.type= SceneMaxScope.SCOPE_TYPE_LOOPER;
                scope.setCreatorController(this);
            }

            for(StatementDef st:cmd.prg.requireResourceActions){
                app.loadResource(st);
            }

            for(FunctionBlockDef f: cmd.prg.functions.values()) {
                f.doBlock.creatorScope = scope;
            }

            for (StatementDef action : cmd.prg.actions) {
                app.runAction(cmd.prg,(ActionStatementBase) action, scope);
            }

            registerController(scope.mainController);
        }

        if(this.target==-1 && goCondition) {
            if(this.cmd.amountExpr==null){
                this.target=0;
            } else {
                this.target = ActionLogicalExpressionVm.toDouble(new ActionLogicalExpressionVm(this.cmd.amountExpr,scope).evaluate());
                scope.type= SceneMaxScope.SCOPE_TYPE_LOOPER;
                scope.isReturnPoint=true;
                scope.setCreatorController(this);
            }
        }


        //////////////  RUN //////////////
        boolean loopFinished = _controllers.size()==0;
        for(int i=_controllers.size()-1;i>=0;--i) {

            SceneMaxBaseController ctl =  _controllers.get(i);

            // check whether this controller should be paused
            if(app.scenePaused) {
                if(ctl.adhereToPauseStatus) {
                    continue;
                }
            }

            boolean finished = ctl.run(tpf);
            if(finished) {
                ctl.isRunning=false;
                _controllers.remove(i);
            }

            if(_controllers.size()==0) {
                count++;

                if (this.cmd.loopExpr!=null) {
                    if(loopExprCached == null) {
                        loopExprCached = new ActionLogicalExpressionVm(this.cmd.loopExpr, this.scope);
                    }
                    loopCondition = (Boolean) loopExprCached.evaluate();
                }

                if(this.interval==-1) {
                    scope=null; // trigger re-run all actions
                }

                if(count>=target) {
                    loopFinished= true;
                } else {
                    scope=null; // trigger re-run all actions
                }
            }

        }


        if(this.interval!=-1) {

            // timer doesn't work when scene is paused
            if(app.scenePaused) {
                return false;
            }

            timerIntervalCount+=tpf;
            if(timerIntervalCount>=this.interval) {
                timerTicked=true;
                timerIntervalCount=0;
            }

            if(loopFinished && timerTicked) {
                checkGoExpr=true;  // in the next run, recheck the go expression
                timerTicked=false; // enable next ticking
                count = 0; // reset loop counter
                scope = null;
            }
            return false; // since we have a timer, we cannot end the controller even if the loop is finished
        }

        return loopFinished && !loopCondition;
    }

    private int registerController(SceneMaxBaseController c) {

        c.setUIProxy(this.app); // this probably should be changed -
                                //  - we need to have LoopBlockController to implement its own IUiProxy
        c.init();
        _controllers.add(c);
        if(c.isEventHandler) {
            this.eventHandlersCount++;
        }
        return 0;
    }

    public void setFuncScopeParams(HashMap<String,Object> params) {
        this.funcScopeParams=params;
    }

    public void setFunctionScopeParams(List<String> paramsNames, List<SceneMaxParser.Logical_expressionContext> paramsExp) {

        this.paramsNames=paramsNames;
        this.paramsExp=paramsExp;

    }

    public void setFunctionScopeParam(List<String> paramsNames, EntityInstBase param) {
        funcScopeParams=new HashMap<>();
        this.funcScopeParams.put(paramsNames.get(0), param);
    }

        private void evalFunctionScopeParams() {
        if(paramsNames!=null && paramsNames.size()>0) {
            funcScopeParams=new HashMap<>();
            int index = 0;
            for (SceneMaxParser.Logical_expressionContext ctx : paramsExp) {
                ActionLogicalExpressionVm exp = new ActionLogicalExpressionVm(ctx, this.parentScope);
                Object obj = exp.evaluate();

                if(obj instanceof String) {
                    VariableDef vd = new VariableDef();
                    vd.varType=VariableDef.VAR_TYPE_STRING;
                    VarInst vi = new VarInst(vd,null);
                    vi.varType=VariableDef.VAR_TYPE_STRING;
                    vi.value=obj;
                    this.funcScopeParams.put(paramsNames.get(index), vi);
                } else if(obj instanceof Double) {
                    VariableDef vd = new VariableDef();
                    vd.varType=VariableDef.VAR_TYPE_NUMBER;
                    VarInst vi = new VarInst(vd,null);
                    vi.varType=VariableDef.VAR_TYPE_NUMBER;
                    vi.value=obj;
                    this.funcScopeParams.put(paramsNames.get(index), vi);
                } else {
                    this.funcScopeParams.put(paramsNames.get(index), obj);
                }
                index++;
                if(index>=paramsNames.size()) {
                    break;
                }
            }

        }

    }
}
