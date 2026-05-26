package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ActionCommandAnimate;
import com.scenemaxeng.compiler.ActionStatementBase;
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
    private final List<ActionCommandAnimate> animationCommands = new ArrayList<>();
    private final List<AnimationRuntimeEvent> events = new ArrayList<>();
    private AnimateCompositeController runningController;
    private ModelAnimateController runningAnimationController;
    private RewindState rewindState;

    public AnimationRuntimeController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope,
                                      String sourceVar, VariableDef sourceVarDef,
                                      String animationName, int varLineNum) {
        this(app, prg, scope, sourceVar, sourceVarDef, animationName, varLineNum, null);
    }

    public AnimationRuntimeController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope,
                                      String sourceVar, VariableDef sourceVarDef,
                                      String animationName, int varLineNum,
                                      List<ActionStatementBase> animationStatements) {
        this.app = app;
        this.prg = prg;
        this.scope = scope;
        this.sourceVar = sourceVar;
        this.sourceVarDef = sourceVarDef;
        this.animationName = animationName;
        this.varLineNum = varLineNum;

        if (animationStatements != null) {
            for (ActionStatementBase statement : animationStatements) {
                if (statement instanceof ActionCommandAnimate) {
                    animationCommands.add((ActionCommandAnimate) statement);
                }
            }
        }
    }

    public void run() {
        stop();

        ActionCommandAnimate sequenceCommand = new ActionCommandAnimate();
        sequenceCommand.targetVar = sourceVar;
        sequenceCommand.varDef = sourceVarDef;
        sequenceCommand.varLineNum = varLineNum;

        runningController = new AnimateCompositeController(sequenceCommand, scope);
        for (ActionCommandAnimate animationCommand : createAnimationCommands()) {
            ModelAnimateController animationController =
                    new ModelAnimateController(app, prg, animationCommand, scope);
            if (runningAnimationController == null) {
                runningAnimationController = animationController;
            }
            runningController.add(animationController);
        }
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
            rewindState = null;
        }
    }

    public void startRewind(double percent, double durationSeconds, MotionEase.MotionEaseSpec easeSpec) {
        AppModelAnimationController controller = getActiveAppAnimationController();
        if (controller == null) {
            rewindState = null;
            return;
        }

        double length = controller.getLength();
        double currentTime = controller.getCurrentTime();
        if (length <= 0 || currentTime < 0) {
            rewindState = null;
            return;
        }

        double targetTime = Math.max(0, currentTime - length * Math.max(0, percent) / 100.0);
        rewindState = new RewindState(controller, currentTime, targetTime,
                Math.max(0, durationSeconds), controller.getPlaybackSpeed(), easeSpec);
        controller.setPlaybackSpeed(0);
        if (rewindState.durationSeconds <= 0) {
            controller.setCurrentTime(targetTime);
            finishRewind();
        }
    }

    public boolean updateRewind(float tpf) {
        if (rewindState == null) {
            return true;
        }

        if (rewindState.controller != getActiveAppAnimationController()) {
            finishRewind();
            return true;
        }

        rewindState.elapsedSeconds += Math.max(0, tpf);
        double progress = rewindState.durationSeconds <= 0
                ? 1.0
                : Math.min(1.0, rewindState.elapsedSeconds / rewindState.durationSeconds);
        float easedProgress = MotionEase.apply(rewindState.easeSpec, (float) progress);
        double currentTime = rewindState.startTime
                + (rewindState.targetTime - rewindState.startTime) * easedProgress;
        rewindState.controller.setCurrentTime(currentTime);

        if (progress >= 1.0) {
            finishRewind();
            return true;
        }
        return false;
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
        AppModelAnimationController controller = getActiveAnimationController().getAnimationController();
        return controller == null ? -1 : controller.getCurrentPercent();
    }

    public boolean isFinished() {
        ModelAnimateController activeController = getActiveAnimationController();
        if (activeController == null) {
            return true;
        }
        AppModelAnimationController controller = activeController.getAnimationController();
        return controller != null && controller.animationFinished;
    }

    public boolean matchesAnimation(String name) {
        ModelAnimateController activeController = getActiveAnimationController();
        return activeController != null
                && activeController.getAnimationName() != null
                && activeController.getAnimationName().equalsIgnoreCase(name);
    }

    public boolean hasAnimation(String name) {
        if (name == null) {
            return false;
        }

        if (!animationCommands.isEmpty()) {
            for (ActionCommandAnimate animationCommand : animationCommands) {
                if (animationCommand.animationName != null
                        && animationCommand.animationName.equalsIgnoreCase(name)) {
                    return true;
                }
            }
            return false;
        }

        return animationName != null && animationName.equalsIgnoreCase(name);
    }

    private void startEventMonitor(AnimationRuntimeEvent event) {
        AnimationRuntimeEventMonitorController monitor =
                new AnimationRuntimeEventMonitorController(app, scope, this, event);
        app.registerController(monitor);
    }

    private List<ActionCommandAnimate> createAnimationCommands() {
        List<ActionCommandAnimate> commands = new ArrayList<>();
        if (animationCommands.isEmpty()) {
            ActionCommandAnimate animationCommand = new ActionCommandAnimate();
            animationCommand.animationName = animationName;
            animationCommand.targetVar = sourceVar;
            animationCommand.varDef = sourceVarDef;
            animationCommand.varLineNum = varLineNum;
            commands.add(animationCommand);
            return commands;
        }

        for (ActionCommandAnimate sourceCommand : animationCommands) {
            ActionCommandAnimate animationCommand = new ActionCommandAnimate();
            animationCommand.animationName = sourceCommand.animationName;
            animationCommand.targetVar = sourceVar;
            animationCommand.varDef = sourceVarDef;
            animationCommand.varLineNum = sourceCommand.varLineNum;
            animationCommand.speedExpr = sourceCommand.speedExpr;
            animationCommand.goExpr = sourceCommand.goExpr;
            animationCommand.loop = sourceCommand.loop;
            animationCommand.isProtected = sourceCommand.isProtected;
            commands.add(animationCommand);
        }
        return commands;
    }

    private ModelAnimateController getActiveAnimationController() {
        if (runningController != null) {
            SceneMaxBaseController activeController = runningController.getActiveController();
            if (activeController instanceof ModelAnimateController) {
                return (ModelAnimateController) activeController;
            }
        }
        return runningAnimationController;
    }

    private AppModelAnimationController getActiveAppAnimationController() {
        ModelAnimateController activeController = getActiveAnimationController();
        return activeController == null ? null : activeController.getAnimationController();
    }

    private void finishRewind() {
        if (rewindState != null) {
            rewindState.controller.setCurrentTime(rewindState.targetTime);
            rewindState.controller.setPlaybackSpeed(rewindState.resumeSpeed);
            rewindState = null;
        }
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

    private static class RewindState {
        final AppModelAnimationController controller;
        final double startTime;
        final double targetTime;
        final double durationSeconds;
        final double resumeSpeed;
        final MotionEase.MotionEaseSpec easeSpec;
        double elapsedSeconds;

        RewindState(AppModelAnimationController controller, double startTime, double targetTime,
                    double durationSeconds, double resumeSpeed, MotionEase.MotionEaseSpec easeSpec) {
            this.controller = controller;
            this.startTime = startTime;
            this.targetTime = targetTime;
            this.durationSeconds = durationSeconds;
            this.resumeSpeed = resumeSpeed;
            this.easeSpec = easeSpec == null ? MotionEase.LINEAR_SPEC : easeSpec;
        }
    }
}
