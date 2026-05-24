package com.scenemaxeng.projector;

public class AnimationRuntimeEventMonitorController extends SceneMaxBaseController {

    private final AnimationRuntimeController runtimeController;
    private final AnimationRuntimeController.AnimationRuntimeEvent event;
    private double previousPercent = -1;

    public AnimationRuntimeEventMonitorController(SceneMaxApp app, SceneMaxScope scope,
                                                  AnimationRuntimeController runtimeController,
                                                  AnimationRuntimeController.AnimationRuntimeEvent event) {
        this.app = app;
        this.scope = scope;
        this.runtimeController = runtimeController;
        this.event = event;
        this.adhereToPauseStatus = false;
    }

    @Override
    public boolean run(float tpf) {
        if (forceStop) {
            return true;
        }

        if (!runtimeController.hasAnimation(event.animationName)) {
            return true;
        }

        if (!runtimeController.matchesAnimation(event.animationName)) {
            previousPercent = -1;
            return runtimeController.isFinished();
        }

        double currentPercent = runtimeController.getCurrentPercent();
        if (currentPercent < 0) {
            return runtimeController.isFinished();
        }

        if (previousPercent > currentPercent) {
            previousPercent = -1;
        }

        boolean crossed = previousPercent < event.percent && currentPercent >= event.percent;
        previousPercent = currentPercent;
        if (!crossed) {
            return runtimeController.isFinished();
        }

        DoBlockController doBlockController = new DoBlockController(app, scope, event.doBlock);
        doBlockController.async = event.doBlock.isAsync;
        app.registerController(doBlockController);
        return true;
    }
}
