package com.scenemaxeng.projector;
import com.scenemaxeng.compiler.ActionStatementBase;
import com.scenemaxeng.compiler.ProgramDef;

import java.util.ArrayList;

public class CompositeController extends SceneMaxBaseController {

    protected ArrayList<SceneMaxBaseController> _controllers = new ArrayList<>();
    protected int runningControllerIndex=0;
    private int eventHandlersCount;

    public CompositeController() {

    }

    public CompositeController(SceneMaxApp app, ProgramDef prg, SceneMaxScope thread, ActionStatementBase cmd) {
        super(app,prg,thread,cmd);
    }

    public int size() {
        return _controllers.size();
    }

    public void addFirst(SceneMaxBaseController c) {
        _controllers.add(0,c);
    }

    public void add(SceneMaxBaseController c) {
        _controllers.add(c);
        c.parentController = this;
        if(c.isEventHandler) {
            this.eventHandlersCount++;
        }
    }

    public void remove(SceneMaxBaseController c) {
        _controllers.remove(c);
    }

    public void init(int startIndex) {
        for(int i=startIndex;i<_controllers.size();++i) {
            ISceneMaxController c = _controllers.get(i);
            c.setUIProxy(this.app);
            c.init();
        }
    }

    @Override
    public void init() {
        for(int i=0;i<_controllers.size();++i) {
            ISceneMaxController c = _controllers.get(i);
            c.setUIProxy(this.app);
            c.init();
        }
    }

    public SceneMaxBaseController getActiveController() {
        if(_controllers.size()==0) return null;
        if(runningControllerIndex>=_controllers.size()) return null;
        SceneMaxBaseController ctl = _controllers.get(runningControllerIndex);
        return ctl;
    }

    @Override
    public boolean run(float tpf) {

        if(forceStop || _controllers.size()==this.eventHandlersCount) {
            this.unregisterEventHandlers();
            return true;
        }

        SceneMaxBaseController ctl = _controllers.get(runningControllerIndex);
        boolean finished = false;
        boolean async = ctl.async;
        if(async) {
            this.app.registerController(ctl);
        } else {
            if(app!=null && app.scenePaused) {
                if (ctl.adhereToPauseStatus) {
                    finished = false;
                } else {
                    finished = ctl.run(tpf);
                }

            } else {
                finished = ctl.run(tpf);
            }
        }

        if(finished || async) {

            runningControllerIndex++;
            if(runningControllerIndex < _controllers.size()) {
                return false;
            } else {
                this.unregisterEventHandlers();
                return true; // no more controllers to run
            }

        }

        return false; // current controller not finished

    }

    private void unregisterEventHandlers() {
        for(int i=0;i<_controllers.size();++i) {
            if (_controllers.get(i).isEventHandler) {
                _controllers.get(i).forceStop = true;
            }
        }
    }

    public void forceStop() {
        this.forceStop=true;
        for(SceneMaxBaseController c: _controllers) {
            if(c instanceof CompositeController) {
                ((CompositeController)c).forceStop();
            } else {
                c.forceStop=true;
            }
        }
    }
}
