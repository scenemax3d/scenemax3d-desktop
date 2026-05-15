package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ActionCommandAnimate;
import com.scenemaxeng.compiler.DoBlockCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VariableDef;

import java.util.ArrayList;
import java.util.List;

public class AnimationRuntimeController {

    private final SceneMaxApp app;
    private final ProgramDef prg;
    private final SceneMaxScope scope;
    private final String sourceVar;
    private final VariableDef sourceVarDef;
    private final String animationName;
    private final int varLineNum;
    private final List<AnimationRuntimeEvent> events = new ArrayList<>();
    private AnimateCompositeController runningController;
    private ModelAnimateController runningAnimationController;

    public AnimationRuntimeController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope,
                                      String sourceVar, VariableDef sourceVarDef,
                                      String animationName, int varLineNum) {
        this.app = app;
        this.prg = prg;
        this.scope = scope;
        this.sourceVar = sourceVar;
        this.sourceVarDef = sourceVarDef;
        this.animationName = animationName;
        this.varLineNum = varLineNum;
    }

    public void run() {
        stop();

        ActionCommandAnimate sequenceCommand = new ActionCommandAnimate();
        sequenceCommand.targetVar = sourceVar;
        sequenceCommand.varDef = sourceVarDef;
        sequenceCommand.varLineNum = varLineNum;

        ActionCommandAnimate animationCommand = new ActionCommandAnimate();
        animationCommand.targetVar = sourceVar;
        animationCommand.varDef = sourceVarDef;
        animationCommand.animationName = animationName;
        animationCommand.varLineNum = varLineNum;

        runningController = new AnimateCompositeController(sequenceCommand, scope);
        runningAnimationController = new ModelAnimateController(app, prg, animationCommand, scope);
        runningController.add(runningAnimationController);
        runningController.setUIProxy(app);
        runningController.init();

        for (AnimationRuntimeEvent event : events) {
            startEventMonitor(event);
        }
    }

    public boolean update(float tpf) {
        if (runningController == null) {
            return true;
        }

        boolean finished = runningController.run(tpf);
        if (finished) {
            runningController = null;
            runningAnimationController = null;
        }
        return finished;
    }

    public void stop() {
        if (runningController != null) {
            runningController.forceStop = true;
            AppModelAnimationController controller = runningAnimationController != null
                    ? runningAnimationController.getAnimationController()
                    : null;
            if (controller != null) {
                controller.stop();
            }
            runningController = null;
            runningAnimationController = null;
        }
    }

    public void addEvent(String eventAnimationName, double percent, DoBlockCommand doBlock) {
        AnimationRuntimeEvent event = new AnimationRuntimeEvent(eventAnimationName, percent, doBlock);
        events.add(event);
        if (runningController != null) {
            startEventMonitor(event);
        }
    }

    public double getCurrentPercent() {
        if (runningAnimationController == null) {
            return -1;
        }
        AppModelAnimationController controller = runningAnimationController.getAnimationController();
        return controller == null ? -1 : controller.getCurrentPercent();
    }

    public boolean isFinished() {
        if (runningAnimationController == null) {
            return true;
        }
        AppModelAnimationController controller = runningAnimationController.getAnimationController();
        return controller != null && controller.animationFinished;
    }

    public boolean matchesAnimation(String name) {
        return animationName.equalsIgnoreCase(name);
    }

    private void startEventMonitor(AnimationRuntimeEvent event) {
        AnimationRuntimeEventMonitorController monitor =
                new AnimationRuntimeEventMonitorController(app, scope, this, event);
        app.registerController(monitor);
    }

    static class AnimationRuntimeEvent {
        final String animationName;
        final double percent;
        final DoBlockCommand doBlock;

        AnimationRuntimeEvent(String animationName, double percent, DoBlockCommand doBlock) {
            this.animationName = animationName;
            this.percent = percent;
            this.doBlock = doBlock;
        }
    }
}
