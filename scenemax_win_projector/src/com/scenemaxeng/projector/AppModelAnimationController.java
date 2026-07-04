package com.scenemaxeng.projector;

import com.jme3.anim.AnimComposer;
import com.jme3.anim.AnimClip;
import com.jme3.anim.AnimTrack;
import com.jme3.anim.TransformTrack;
import com.jme3.anim.tween.action.Action;
import com.jme3.animation.AnimChannel;
import com.jme3.animation.AnimControl;
import com.jme3.animation.AnimEventListener;
import com.jme3.animation.Animation;
import com.jme3.animation.LoopMode;
import com.jme3.animation.Track;

public class AppModelAnimationController implements AnimEventListener {

    private static final double FALLBACK_ANIMATION_FPS = 24.0;

    public boolean animationFinished = false;
    public SceneMaxBaseController hostController = null;
    private String animationName = null;
    public AppModel appModel;
    private double globalSpeed;
    private boolean paused;
    public boolean isProtected = false;
    private AnimControl control;
    private AnimChannel channel;
    private String frameRangeStart;
    private boolean frameRangeStartPercent;
    private String frameRangeEnd;
    private boolean frameRangeEndPercent;
    private RangeTiming activeRange;

    public AppModelAnimationController(SceneMaxBaseController hostController) {
        this.hostController=hostController;
    }

    public void setFrameRange(String start, boolean startPercent, String end, boolean endPercent) {
        this.frameRangeStart = start;
        this.frameRangeStartPercent = startPercent;
        this.frameRangeEnd = end;
        this.frameRangeEndPercent = endPercent;
        this.activeRange = null;
    }

    @Override
    public void onAnimCycleDone(AnimControl animControl, AnimChannel animChannel, String animName) {

        if (animName.equals(animationName)) {
            animControl.removeListener(this);
            finishControllerAnimation();
        }
    }

    @Override
    public void onAnimChange(AnimControl animControl, AnimChannel animChannel, String animName) {
        if (!animName.equals(animationName)) {
            animControl.removeListener(this);
            finishControllerAnimation();
        }
    }

    public void animate(AppModel m, String animationName, String speed) {

        try {
            this.animationName = animationName;
            this.appModel = m;
            this.animationFinished = false;
            this.activeRange = null;

            AnimComposer composer = m.getAnimComposer();
            if (composer == null && m.resource != null && m.resource.isJ3O()) {
                composer = m.getOrCreateAnimComposerForSkinningControl();
            }
            if (composer != null) {
                boolean hasLocalAnimation = composer.hasAction(animationName) || composer.hasAnimClip(animationName);
                boolean attachedExternal = hasLocalAnimation
                        || m.attachExternalAnimation(hostController.app.getAssetManager(), hostController.app.getAssetsMapping(), animationName);
                Action ac = composer.getAction(animationName);
                if (ac == null && !attachedExternal && !composer.hasAnimClip(animationName)) {
                    System.out.println("Animation not found on model or external animation resources: " + animationName);
                    animationFinished = true;
                    return;
                }

                if (!(ac instanceof CharacterAction)) {
                    Action originalAction = ac != null ? ac : composer.action(animationName);
                    ac = new CharacterAction(this, originalAction, composer);
                    composer.addAction(animationName, ac);

                } else {
                    CharacterAction characterAction = (CharacterAction) ac;
                    if (characterAction.controller != this) {
                        characterAction.finishAnimation(); // free previous controller's animation
                    }
                    characterAction.setController(this); // set new controller
                }

                Double animSpeed = Double.parseDouble(speed);
                ac.setSpeed(animSpeed);
                this.activeRange = resolveRangeForComposer(composer, animationName, ac.getLength());

                if (m.currentAction == null) {
                    m.currentAction = (CharacterAction) ac;
                    m.currentAnimationController = this;
                    composer.setCurrentAction(animationName);
                    applyRangeStart(composer);
                } else {
                    if(ac!=m.currentAction) {
                        if (m.currentAction.controller != this) {
                            m.currentAction.finishAnimation();
                        }
                        m.currentAction = (CharacterAction)ac;
                        m.currentAnimationController = this;
                        composer.setCurrentAction(animationName);
                        applyRangeStart(composer);
                    } else {
                        m.currentAnimationController = this;
                        composer.setCurrentAction(animationName);
                        applyRangeStart(composer);
                    }

                }
                this.animationFinished = false;

            } else if(m.resource.isJ3O()) {
                AnimControl control = m.getAnimControl();
                if (control == null) {
                    if (m.getSkinningControl() == null) {
                        System.out.println("Animation target has no AnimComposer, SkinningControl, or AnimControl: "
                                + animationName + ". Imported animations require a rigged/skinned model.");
                    } else {
                        System.out.println("Animation control not found for J3O model: " + animationName);
                    }
                    animationFinished = true;
                    return;
                }
                this.control = control;
                control.addListener(this);
                AnimChannel channel = m.getChannel();
                if (channel == null) {
                    System.out.println("Animation channel could not be created for J3O model: " + animationName);
                    animationFinished = true;
                    return;
                }
                this.channel = channel;
                channel.reset(false);
                channel.setAnim(animationName);
                channel.setLoopMode(LoopMode.DontLoop);
                Float animSpeed = Float.parseFloat(speed);
                channel.setSpeed(animSpeed);
                this.activeRange = resolveRangeForLegacy(control, animationName, channel.getAnimMaxTime());
                applyRangeStart(channel);
                m.currentAnimationController = this;

            } else {
                animationFinished = true;
            }

        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("Problem running animation " + animationName);
            animationFinished = true;
        }
    }

    public boolean updateFrameRangeState() {
        if (animationFinished || activeRange == null) {
            return animationFinished;
        }

        double currentTime = getCurrentTime();
        if (currentTime < 0) {
            return animationFinished;
        }

        if (getPlaybackSpeed() < 0) {
            if (currentTime <= activeRange.startTime) {
                setCurrentTime(activeRange.startTime);
                finishActiveRange();
            }
        } else if (currentTime >= activeRange.endTime) {
            setCurrentTime(activeRange.endTime);
            finishActiveRange();
        }

        return animationFinished;
    }

    public void pause() {
        if(!this.paused && this.appModel.currentAction!=null) {
            double speed = this.appModel.currentAction.getSpeed();//getAnimComposer().getGlobalSpeed();
            if(speed==0) {
                return;
            }
            this.globalSpeed = speed;
            this.appModel.currentAction.setSpeed(0);//this.appModel.getAnimComposer().setGlobalSpeed(0);
        }
        this.paused=true;
    }

    public void resume() {
        if(this.paused && this.appModel.currentAction!=null) {
            this.appModel.currentAction.setSpeed(this.globalSpeed);
        }

        this.paused = false;

    }

    public boolean isPaused() {
        return this.paused;
    }

    public void stop() {
        animationFinished = true;
        if (appModel != null && appModel.currentAction != null && appModel.currentAction.controller == this) {
            appModel.currentAction.setSpeed(0);
            appModel.currentAction.finishAnimation();
            appModel.currentAction.isProtected = false;
        }
        if (appModel != null && appModel.currentAnimationController == this) {
            appModel.currentAnimationController = null;
        }
        if (control != null) {
            control.removeListener(this);
        }
        if (channel != null) {
            channel.setSpeed(0);
        }
    }

    public void finishControllerAnimation() {
        animationFinished = true;
        if (appModel != null && appModel.currentAnimationController == this) {
            appModel.currentAnimationController = null;
        }
    }

    public double getCurrentPercent() {
        double length = getLength();
        double currentTime = getCurrentTime();
        if (length <= 0 || currentTime < 0) {
            return -1;
        }
        if (activeRange != null && activeRange.endTime > activeRange.startTime) {
            double rangePercent = (currentTime - activeRange.startTime)
                    / (activeRange.endTime - activeRange.startTime) * 100.0;
            return Math.max(0, Math.min(100, rangePercent));
        }
        return currentTime / length * 100.0;
    }

    public double getCurrentTime() {
        if (appModel == null) {
            return -1;
        }

        if (appModel.currentAction != null && appModel.currentAction.controller == this) {
            AnimComposer composer = appModel.getAnimComposer();
            if (composer == null) {
                return -1;
            }
            return composer.getTime("Default");
        }

        if (channel != null) {
            return channel.getTime();
        }

        return -1;
    }

    public double getLength() {
        if (appModel != null && appModel.currentAction != null && appModel.currentAction.controller == this) {
            return appModel.currentAction.getLength();
        }

        if (channel != null) {
            return channel.getAnimMaxTime();
        }

        return -1;
    }

    public double getPlaybackSpeed() {
        if (appModel != null && appModel.currentAction != null && appModel.currentAction.controller == this) {
            return appModel.currentAction.getSpeed();
        }

        if (channel != null) {
            return channel.getSpeed();
        }

        return 0;
    }

    public void setPlaybackSpeed(double speed) {
        if (appModel != null && appModel.currentAction != null && appModel.currentAction.controller == this) {
            appModel.currentAction.setSpeed(speed);
        }

        if (channel != null) {
            channel.setSpeed((float) speed);
        }
    }

    public void setCurrentTime(double time) {
        double length = getLength();
        if (length <= 0) {
            return;
        }

        double clampedTime = Math.max(0, Math.min(length, time));
        if (appModel != null && appModel.currentAction != null && appModel.currentAction.controller == this) {
            AnimComposer composer = appModel.getAnimComposer();
            if (composer != null) {
                composer.setTime("Default", clampedTime);
            }
        }

        if (channel != null) {
            channel.setTime((float) clampedTime);
        }
    }

    boolean hasReachedFrameRangeEnd(double time) {
        return activeRange != null && time >= activeRange.endTime;
    }

    double clampToFrameRangeEnd(double time) {
        return activeRange == null ? time : Math.min(time, activeRange.endTime);
    }

    private boolean hasRequestedFrameRange() {
        return frameRangeStart != null && frameRangeEnd != null;
    }

    private void applyRangeStart(AnimComposer composer) {
        if (activeRange != null && composer != null) {
            composer.setTime("Default", activeRange.startTime);
        }
    }

    private void applyRangeStart(AnimChannel channel) {
        if (activeRange != null && channel != null) {
            channel.setTime((float) activeRange.startTime);
        }
    }

    private void finishActiveRange() {
        if (appModel != null && appModel.currentAction != null && appModel.currentAction.controller == this) {
            appModel.currentAction.setSpeed(0);
            appModel.currentAction.isProtected = false;
        }
        if (channel != null) {
            channel.setSpeed(0);
        }
        finishControllerAnimation();
    }

    private RangeTiming resolveRangeForComposer(AnimComposer composer, String animationName, double actionLength) {
        if (!hasRequestedFrameRange()) {
            return null;
        }
        AnimClip clip = composer == null ? null : composer.getAnimClip(animationName);
        FrameTimeline timeline = timelineFromClip(clip);
        double length = actionLength > 0 ? actionLength : clip == null ? 0 : clip.getLength();
        return resolveRange(length, timeline);
    }

    private RangeTiming resolveRangeForLegacy(AnimControl control, String animationName, double channelLength) {
        if (!hasRequestedFrameRange()) {
            return null;
        }
        Animation animation = control == null ? null : control.getAnim(animationName);
        FrameTimeline timeline = timelineFromAnimation(animation);
        double length = channelLength > 0 ? channelLength : animation == null ? 0 : animation.getLength();
        return resolveRange(length, timeline);
    }

    private RangeTiming resolveRange(double length, FrameTimeline timeline) {
        if (length <= 0) {
            return null;
        }

        double maxFrame = timeline != null
                ? timeline.getMaxFrame()
                : Math.max(0, length * FALLBACK_ANIMATION_FPS);
        double startFrame = resolveFrameValue(frameRangeStart, frameRangeStartPercent, maxFrame, 0);
        double endFrame = resolveFrameValue(frameRangeEnd, frameRangeEndPercent, maxFrame, maxFrame);
        double startTime = frameToTime(startFrame, length, timeline);
        double endTime = frameToTime(endFrame, length, timeline);

        startTime = clamp(startTime, 0, length);
        endTime = clamp(endTime, 0, length);
        if (endTime < startTime) {
            endTime = startTime;
        }
        return new RangeTiming(startTime, endTime);
    }

    private double resolveFrameValue(String text, boolean percent, double maxFrame, double fallbackFrame) {
        double fallback = percent && maxFrame > 0 ? fallbackFrame / maxFrame * 100.0 : fallbackFrame;
        double value = parseDouble(text, fallback);
        if (percent) {
            value = maxFrame * value / 100.0;
        }
        return clamp(value, 0, maxFrame);
    }

    private double frameToTime(double frame, double length, FrameTimeline timeline) {
        if (timeline != null) {
            return timeline.timeAtFrame(frame);
        }
        return length <= 0 ? 0 : frame / FALLBACK_ANIMATION_FPS;
    }

    private double parseDouble(String text, double fallback) {
        try {
            return Double.parseDouble(text);
        } catch (Exception e) {
            return fallback;
        }
    }

    private double clamp(double value, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private FrameTimeline timelineFromClip(AnimClip clip) {
        if (clip == null || clip.getTracks() == null) {
            return null;
        }
        float[] bestTimes = null;
        for (AnimTrack track : clip.getTracks()) {
            if (track instanceof TransformTrack) {
                float[] times = ((TransformTrack) track).getTimes();
                if (times != null && (bestTimes == null || times.length > bestTimes.length)) {
                    bestTimes = times;
                }
            }
        }
        return FrameTimeline.from(bestTimes);
    }

    private FrameTimeline timelineFromAnimation(Animation animation) {
        if (animation == null || animation.getTracks() == null) {
            return null;
        }
        float[] bestTimes = null;
        for (Track track : animation.getTracks()) {
            float[] times = track == null ? null : track.getKeyFrameTimes();
            if (times != null && (bestTimes == null || times.length > bestTimes.length)) {
                bestTimes = times;
            }
        }
        return FrameTimeline.from(bestTimes);
    }

    private static class RangeTiming {
        final double startTime;
        final double endTime;

        RangeTiming(double startTime, double endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }

    private static class FrameTimeline {
        private final float[] times;

        private FrameTimeline(float[] times) {
            this.times = times;
        }

        static FrameTimeline from(float[] times) {
            return times == null || times.length == 0 ? null : new FrameTimeline(times);
        }

        double getMaxFrame() {
            return Math.max(0, times.length - 1);
        }

        double timeAtFrame(double frame) {
            if (times.length == 1) {
                return times[0];
            }
            double clampedFrame = Math.max(0, Math.min(getMaxFrame(), frame));
            int left = (int) Math.floor(clampedFrame);
            int right = Math.min(times.length - 1, (int) Math.ceil(clampedFrame));
            if (left == right) {
                return times[left];
            }
            double amount = clampedFrame - left;
            return times[left] + (times[right] - times[left]) * amount;
        }
    }
}
