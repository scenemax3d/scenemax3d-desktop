package com.scenemaxeng.projector;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.scenemaxeng.compiler.CameraModifierApplyCommand;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VariableDef;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class CinematicCameraModifierTest {

    @Test
    public void controllerAllowsModifierApplyWhileCinematicCameraIsPlaying() {
        StubSceneMaxApp app = new StubSceneMaxApp();
        app.onCinematicCameraStarted();

        SceneMaxScope scope = new SceneMaxScope();
        RuntimeCameraSystemValue systemValue = new RuntimeCameraSystemValue();
        RuntimeCameraModifierValue modifierValue = new RuntimeCameraModifierValue();
        modifierValue.modifierType = RuntimeCameraModifierValue.TYPE_HIT;

        scope.vars_index.put("fight_cam", createVar(scope, "fight_cam", systemValue));
        scope.vars_index.put("hit_fx", createVar(scope, "hit_fx", modifierValue));

        CameraModifierApplyCommand cmd = new CameraModifierApplyCommand();
        cmd.targetVar = "fight_cam";
        cmd.modifierVar = "hit_fx";

        CameraModifierApplyController controller =
                new CameraModifierApplyController(app, new ProgramDef(), scope, cmd);

        assertTrue(controller.run(0.016f));
        assertNull(app.runtimeError);
        assertSame(systemValue, app.appliedTargetSystem);
        assertSame(modifierValue, app.appliedModifier);
    }

    @Test
    public void cinematicPlaybackFrameCanReceiveModifierOffsets() {
        StubSceneMaxApp app = new StubSceneMaxApp();
        app.onCinematicCameraStarted();

        RuntimeCameraModifierValue modifier = new RuntimeCameraModifierValue();
        modifier.modifierType = RuntimeCameraModifierValue.TYPE_EARTHQUAKE;
        modifier.duration = 1f;
        modifier.amplitude = 2.5f;
        modifier.frequency = 7f;
        modifier.x = 1f;
        modifier.y = 1f;
        modifier.z = 1f;
        modifier.rx = 0f;
        modifier.ry = 0f;
        modifier.rz = 0f;
        modifier.fov = 0f;

        app.applyCameraModifier(new RuntimeCameraSystemValue(),
                modifier,
                Collections.emptyMap(),
                "fight_cam",
                0);

        Vector3f basePos = new Vector3f(0f, 4f, -10f);
        Vector3f baseLookAt = new Vector3f(0f, 4f, 0f);
        app.applyCameraFrame(basePos, baseLookAt, 50f, 0.1f);

        assertTrue(app.getCamera().getLocation().distance(basePos) > 1e-4f);
    }

    private static VarInst createVar(SceneMaxScope scope, String name, Object value) {
        VariableDef def = new VariableDef();
        def.varName = name;
        VarInst inst = new VarInst(def, scope);
        inst.value = value;
        return inst;
    }

    private static class StubSceneMaxApp extends SceneMaxApp {
        String runtimeError;
        RuntimeCameraSystemValue appliedTargetSystem;
        RuntimeCameraModifierValue appliedModifier;
        Map<String, Float> appliedOverrides;

        StubSceneMaxApp() {
            super();
            this.cam = new Camera(1280, 720);
            this.cam.setFrustumPerspective(50f, 1280f / 720f, 1f, 1000f);
        }

        @Override
        public void handleRuntimeError(String err) {
            runtimeError = err;
        }

        @Override
        public void applyCameraModifier(RuntimeCameraSystemValue targetSystem,
                                        RuntimeCameraModifierValue modifier,
                                        Map<String, Float> overrides,
                                        String targetVarName,
                                        int targetVarLine) {
            appliedTargetSystem = targetSystem;
            appliedModifier = modifier;
            appliedOverrides = overrides;
            super.applyCameraModifier(targetSystem, modifier, overrides, targetVarName, targetVarLine);
        }
    }
}
