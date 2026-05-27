package com.scenemax.designer.physics;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Plane;
import com.jme3.math.Quaternion;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.debug.Arrow;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;
import com.jme3.util.BufferUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

class PhysicsSimulationPreviewApp extends SimpleApplication {
    private static final String ACTION_LEFT = "PhysicsSimulationLeft";
    private static final String ACTION_SCROLL_UP = "PhysicsSimulationScrollUp";
    private static final String ACTION_SCROLL_DOWN = "PhysicsSimulationScrollDown";
    private static final float PICK_THRESHOLD_PX = 18f;
    private static final float SPHERE_RADIUS = 0.45f;
    private static final float RUNTIME_THROW_GRAVITY = 9.81f;

    private final Node sceneRoot = new Node("PhysicsSimulationRoot");
    private final Node objectRoot = new Node("PhysicsObjectRoot");
    private final Node targetRoot = new Node("PhysicsTargetRoot");
    private final Node trailRoot = new Node("PhysicsTrailRoot");
    private final Node gizmoRoot = new Node("PhysicsTranslateGizmo");

    private BulletAppState bulletAppState;
    private Geometry objectSphere;
    private Geometry targetSphere;
    private Geometry floor;
    private RigidBodyControl objectBody;
    private RigidBodyControl targetBody;
    private RigidBodyControl floorBody;

    private PhysicsSimulationSettings settings = new PhysicsSimulationSettings();
    private boolean playing;
    private boolean commandApplied;
    private float simulationTime;
    private float forceRemaining;
    private float previousForceStepTime;
    private Vector3f lastTrailPosition;
    private String selectedHandle = "object";

    private boolean orbiting;
    private String dragAxis;
    private boolean directDragging;
    private Plane dragPlane;
    private Vector3f dragAxisDir;
    private Vector3f dragStartPosition;
    private Vector3f directDragOffset;
    private float dragStartT;

    private float cameraDistance = 18f;
    private float yaw = (float) Math.toRadians(35);
    private float pitch = (float) Math.toRadians(24);
    private final Vector2f lastMouse = new Vector2f();
    private PointLight cameraLight;
    private Consumer<PhysicsSimulationSettings> positionsChangedCallback;
    private Consumer<String> statusChangedCallback;
    private Consumer<Boolean> playbackChangedCallback;

    @Override
    public void simpleInitApp() {
        viewPort.setBackgroundColor(new ColorRGBA(0.043f, 0.047f, 0.054f, 1f));
        flyCam.setEnabled(false);
        inputManager.setCursorVisible(true);
        setDisplayFps(false);
        setDisplayStatView(false);

        rootNode.attachChild(sceneRoot);
        sceneRoot.attachChild(objectRoot);
        sceneRoot.attachChild(targetRoot);
        sceneRoot.attachChild(trailRoot);
        sceneRoot.attachChild(gizmoRoot);

        bulletAppState = new BulletAppState();
        stateManager.attach(bulletAppState);
        bulletAppState.setEnabled(false);

        buildLighting();
        buildScene();
        buildGizmo();
        registerInput();
        recreatePhysicsBodies();
        resetSimulationInternal();
        fitCamera();
    }

    @Override
    public void simpleUpdate(float tpf) {
        if (dragAxis != null && inputManager != null) {
            updateGizmoDrag(inputManager.getCursorPosition());
        } else if (directDragging && inputManager != null) {
            updateDirectDrag(inputManager.getCursorPosition());
        }

        if (orbiting && inputManager != null) {
            Vector2f current = inputManager.getCursorPosition();
            float dx = current.x - lastMouse.x;
            float dy = current.y - lastMouse.y;
            lastMouse.set(current);
            yaw -= dx * 0.006f;
            pitch = FastMath.clamp(pitch + dy * 0.006f, (float) Math.toRadians(-70), (float) Math.toRadians(70));
            updateCamera(currentCenter());
        }

        if (playing) {
            simulationTime += tpf;
            applyRuntimeCommandForFrame(tpf);
            appendTrailFromPhysics();
            if (simulationTime >= Math.max(0.1f, settings.simulationDuration)) {
                setPlayingInternal(false);
            }
            updateStatus();
        }
    }

    void setPositionsChangedCallback(Consumer<PhysicsSimulationSettings> callback) {
        positionsChangedCallback = callback;
    }

    void setStatusChangedCallback(Consumer<String> callback) {
        statusChangedCallback = callback;
    }

    void setPlaybackChangedCallback(Consumer<Boolean> callback) {
        playbackChangedCallback = callback;
    }

    void setSettings(PhysicsSimulationSettings next) {
        enqueue(() -> {
            boolean requiresBodyRefresh = Math.abs(settings.mass - next.mass) > 0.0001f
                    || Math.abs(settings.drag - next.drag) > 0.0001f
                    || Math.abs(settings.restitution - next.restitution) > 0.0001f
                    || Math.abs(settings.floorFriction - next.floorFriction) > 0.0001f;
            boolean positionsChanged = settings.objectPosition.distanceSquared(next.objectPosition) > 0.0001f
                    || settings.targetPosition.distanceSquared(next.targetPosition) > 0.0001f;
            settings = next.copy();
            if (requiresBodyRefresh) {
                recreatePhysicsBodies();
            }
            if (!playing || positionsChanged) {
                resetSimulationInternal();
            }
            updateStatus();
            return null;
        });
    }

    void setPlaying(boolean value) {
        enqueue(() -> {
            if (value) {
                resetSimulationInternal();
                preparePhysicsWorld();
                setPlayingInternal(true);
            } else {
                setPlayingInternal(false);
            }
            updateStatus();
            return null;
        });
    }

    void stopAndReset() {
        enqueue(() -> {
            setPlayingInternal(false);
            resetSimulationInternal();
            updateStatus();
            return null;
        });
    }

    void resetCamera() {
        enqueue(() -> {
            fitCamera();
            return null;
        });
    }

    private void buildLighting() {
        AmbientLight ambient = new AmbientLight();
        ambient.setColor(ColorRGBA.White.mult(1.15f));
        rootNode.addLight(ambient);

        DirectionalLight key = new DirectionalLight();
        key.setDirection(new Vector3f(-0.55f, -0.8f, -0.35f).normalizeLocal());
        key.setColor(ColorRGBA.White.mult(2.5f));
        rootNode.addLight(key);

        DirectionalLight fill = new DirectionalLight();
        fill.setDirection(new Vector3f(0.45f, -0.35f, 0.65f).normalizeLocal());
        fill.setColor(new ColorRGBA(0.7f, 0.83f, 1f, 1f).mult(1.25f));
        rootNode.addLight(fill);

        cameraLight = new PointLight();
        cameraLight.setColor(ColorRGBA.White.mult(1.8f));
        cameraLight.setRadius(100f);
        rootNode.addLight(cameraLight);
    }

    private void buildScene() {
        floor = new Geometry("PhysicsFloor", new Box(22f, 0.04f, 26f));
        floor.setMaterial(colorMaterial(new ColorRGBA(0.19f, 0.205f, 0.22f, 1f)));
        floor.setLocalTranslation(0f, -0.04f, 6f);
        sceneRoot.attachChild(floor);

        objectSphere = new Geometry("ThrowableObject", new Sphere(32, 32, SPHERE_RADIUS));
        objectSphere.setMaterial(colorMaterial(new ColorRGBA(0.95f, 0.57f, 0.18f, 1f)));
        objectSphere.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        objectRoot.attachChild(objectSphere);

        targetSphere = new Geometry("TargetObject", new Sphere(32, 32, SPHERE_RADIUS));
        targetSphere.setMaterial(colorMaterial(new ColorRGBA(0.28f, 0.74f, 1f, 1f)));
        targetSphere.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        targetRoot.attachChild(targetSphere);
    }

    private void buildGizmo() {
        gizmoRoot.attachChild(createArrow("X", new Vector3f(1.7f, 0f, 0f), ColorRGBA.Red));
        gizmoRoot.attachChild(createArrow("Y", new Vector3f(0f, 1.7f, 0f), ColorRGBA.Green));
        gizmoRoot.attachChild(createArrow("Z", new Vector3f(0f, 0f, 1.7f), ColorRGBA.Blue));
    }

    private Geometry createArrow(String axis, Vector3f extent, ColorRGBA color) {
        Arrow arrow = new Arrow(extent);
        arrow.setLineWidth(4f);
        Geometry geo = new Geometry("PhysicsGizmo_" + axis, arrow);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        mat.getAdditionalRenderState().setLineWidth(4f);
        geo.setMaterial(mat);
        return geo;
    }

    private void recreatePhysicsBodies() {
        PhysicsSpace space = physicsSpace();
        if (objectBody != null) {
            space.remove(objectBody);
            objectRoot.removeControl(objectBody);
        }
        if (targetBody != null) {
            space.remove(targetBody);
            targetRoot.removeControl(targetBody);
        }
        if (floorBody != null) {
            space.remove(floorBody);
            floor.removeControl(floorBody);
        }

        objectBody = new RigidBodyControl(new SphereCollisionShape(SPHERE_RADIUS), Math.max(0.001f, settings.mass));
        objectBody.setDamping(Math.max(0f, settings.drag), Math.max(0f, settings.drag) * 0.25f);
        objectBody.setRestitution(clamp01(settings.restitution));
        objectBody.setFriction(clamp01(settings.floorFriction));
        objectRoot.addControl(objectBody);
        space.add(objectBody);

        targetBody = new RigidBodyControl(new SphereCollisionShape(SPHERE_RADIUS), 0f);
        targetBody.setRestitution(clamp01(settings.restitution));
        targetBody.setFriction(clamp01(settings.floorFriction));
        targetRoot.addControl(targetBody);
        space.add(targetBody);

        floorBody = new RigidBodyControl(new BoxCollisionShape(new Vector3f(22f, 0.04f, 26f)), 0f);
        floorBody.setRestitution(clamp01(settings.restitution));
        floorBody.setFriction(clamp01(settings.floorFriction));
        floor.addControl(floorBody);
        space.add(floorBody);
    }

    private PhysicsSpace physicsSpace() {
        return bulletAppState.getPhysicsSpace();
    }

    private void setPlayingInternal(boolean value) {
        if (playing == value) {
            bulletAppState.setEnabled(value);
            return;
        }
        playing = value;
        bulletAppState.setEnabled(value);
        if (playbackChangedCallback != null) {
            playbackChangedCallback.accept(value);
        }
    }

    private void preparePhysicsWorld() {
        PhysicsSpace space = physicsSpace();
        space.setGravity(new Vector3f(0f, -Math.max(0f, settings.gravity), 0f));
        commandApplied = false;
        simulationTime = 0f;
        forceRemaining = settings.commandType == PhysicsSimulationSettings.CommandType.FORCE && settings.useForceDuration
                ? Math.max(0f, settings.forceDuration)
                : 0f;
        previousForceStepTime = 0f;
        lastTrailPosition = settings.objectPosition.clone();
        trailRoot.detachAllChildren();
    }

    private void resetSimulationInternal() {
        simulationTime = 0f;
        commandApplied = false;
        forceRemaining = 0f;
        previousForceStepTime = 0f;
        lastTrailPosition = settings.objectPosition.clone();
        trailRoot.detachAllChildren();

        objectRoot.setLocalRotation(Quaternion.IDENTITY);
        objectBody.setPhysicsLocation(settings.objectPosition);
        objectBody.setPhysicsRotation(Quaternion.IDENTITY);
        objectBody.setLinearVelocity(Vector3f.ZERO);
        objectBody.setAngularVelocity(Vector3f.ZERO);
        objectBody.clearForces();
        objectBody.activate();

        targetBody.setPhysicsLocation(settings.targetPosition);
        targetBody.setPhysicsRotation(Quaternion.IDENTITY);
        targetRoot.setLocalTranslation(settings.targetPosition);

        floorBody.setPhysicsLocation(floor.getWorldTranslation());
        updateGizmoPosition();
    }

    private void applyRuntimeCommandForFrame(float tpf) {
        if (objectBody == null) {
            return;
        }
        if (!commandApplied) {
            commandApplied = true;
            Vector3f vector = commandVector(settings);
            objectBody.activate();
            switch (settings.commandType) {
                case THROW:
                    objectBody.setLinearVelocity(vector);
                    if (settings.useSpin) {
                        objectBody.setAngularVelocity(settings.spin);
                    }
                    break;
                case IMPULSE:
                    objectBody.applyCentralImpulse(vector);
                    break;
                case FORCE:
                    objectBody.applyCentralForce(vector);
                    break;
                case VELOCITY:
                    objectBody.setLinearVelocity(vector);
                    break;
                case ANGULAR_VELOCITY:
                    objectBody.setAngularVelocity(vector);
                    break;
                case TORQUE:
                    if (settings.torqueImpulse) {
                        objectBody.applyTorqueImpulse(vector);
                    } else {
                        objectBody.applyTorque(vector);
                    }
                    break;
                case STOP:
                    objectBody.setLinearVelocity(Vector3f.ZERO);
                    objectBody.setAngularVelocity(Vector3f.ZERO);
                    objectBody.clearForces();
                    break;
                default:
                    break;
            }
            previousForceStepTime = simulationTime;
            return;
        }

        if (settings.commandType == PhysicsSimulationSettings.CommandType.FORCE && settings.useForceDuration && forceRemaining > 0f) {
            objectBody.applyCentralForce(commandVector(settings));
            float elapsedSinceForce = Math.max(tpf, simulationTime - previousForceStepTime);
            forceRemaining -= elapsedSinceForce;
            previousForceStepTime = simulationTime;
        } else if (settings.commandType == PhysicsSimulationSettings.CommandType.TORQUE && !settings.torqueImpulse) {
            objectBody.applyTorque(settings.vector);
        }
    }

    private Vector3f commandVector(PhysicsSimulationSettings current) {
        if (current.commandType == PhysicsSimulationSettings.CommandType.THROW) {
            return throwVector(current);
        }
        if (current.targetMode == PhysicsSimulationSettings.TargetMode.VECTOR
                || current.commandType == PhysicsSimulationSettings.CommandType.VELOCITY
                || current.commandType == PhysicsSimulationSettings.CommandType.ANGULAR_VELOCITY
                || current.commandType == PhysicsSimulationSettings.CommandType.TORQUE) {
            return current.vector.clone();
        }
        return directionVector(current).mult(current.power);
    }

    private Vector3f throwVector(PhysicsSimulationSettings current) {
        if (current.targetMode == PhysicsSimulationSettings.TargetMode.AT && !current.useAngle) {
            return calculateBallisticVelocity(current.objectPosition, current.targetPosition, current.power, arcBlend(current));
        }
        Vector3f direction = directionVector(current);
        if (current.useAngle || current.arcMode != PhysicsSimulationSettings.ArcMode.NONE) {
            float angle = current.useAngle ? current.angleDegrees : fallbackArcAngle(current);
            return applyLaunchAngle(direction, current.power, angle);
        }
        return direction.mult(current.power);
    }

    private Vector3f calculateBallisticVelocity(Vector3f source, Vector3f target, float speed, float arcBlend) {
        Vector3f delta = target.subtract(source);
        Vector3f horizontal = new Vector3f(delta.x, 0f, delta.z);
        float horizontalDistance = horizontal.length();
        if (horizontalDistance < 0.001f) {
            return new Vector3f(0f, speed, 0f);
        }

        float gravity = RUNTIME_THROW_GRAVITY;
        float speedSq = speed * speed;
        float root = speedSq * speedSq
                - gravity * (gravity * horizontalDistance * horizontalDistance + 2f * delta.y * speedSq);
        if (root < 0f) {
            return applyLaunchAngle(horizontal.normalizeLocal(), speed, 35f + arcBlend * 20f);
        }

        float sqrt = FastMath.sqrt(root);
        float lowAngle = FastMath.atan((speedSq - sqrt) / (gravity * horizontalDistance));
        float highAngle = FastMath.atan((speedSq + sqrt) / (gravity * horizontalDistance));
        float angle = FastMath.interpolateLinear(arcBlend, lowAngle, highAngle);
        Vector3f horizontalDir = horizontal.normalizeLocal();
        return horizontalDir.mult(speed * FastMath.cos(angle)).addLocal(0f, speed * FastMath.sin(angle), 0f);
    }

    private Vector3f applyLaunchAngle(Vector3f direction, float speed, float angleDegrees) {
        Vector3f horizontal = new Vector3f(direction.x, 0f, direction.z);
        if (horizontal.lengthSquared() < 0.0001f) {
            horizontal = Vector3f.UNIT_Z.clone();
        }
        horizontal.normalizeLocal();
        float angle = angleDegrees * FastMath.DEG_TO_RAD;
        return horizontal.mult(speed * FastMath.cos(angle)).addLocal(0f, speed * FastMath.sin(angle), 0f);
    }

    private Vector3f directionVector(PhysicsSimulationSettings current) {
        switch (current.targetMode) {
            case FORWARD:
                return Vector3f.UNIT_Z.clone();
            case BACKWARD:
                return Vector3f.UNIT_Z.negate();
            case LEFT:
                return Vector3f.UNIT_X.clone();
            case RIGHT:
                return Vector3f.UNIT_X.negate();
            case UP:
                return Vector3f.UNIT_Y.clone();
            case DOWN:
                return Vector3f.UNIT_Y.negate();
            case VECTOR:
                Vector3f vector = current.vector.clone();
                return vector.lengthSquared() < 0.0001f ? Vector3f.UNIT_Z.clone() : vector.normalizeLocal();
            case AT:
            case TOWARD:
            default:
                Vector3f dir = current.targetPosition.subtract(current.objectPosition);
                if (dir.lengthSquared() < 0.0001f) {
                    return Vector3f.UNIT_Z.clone();
                }
                return dir.normalizeLocal();
        }
    }

    private float arcBlend(PhysicsSimulationSettings current) {
        switch (current.arcMode) {
            case LOW:
                return 0f;
            case HIGH:
                return 1f;
            case CUSTOM:
                return clamp01(current.customArc);
            case MEDIUM:
            case NONE:
            default:
                return 0.5f;
        }
    }

    private float fallbackArcAngle(PhysicsSimulationSettings current) {
        switch (current.arcMode) {
            case LOW:
                return 20f;
            case HIGH:
                return 60f;
            case CUSTOM:
                return 20f + clamp01(current.customArc) * 45f;
            case MEDIUM:
            case NONE:
            default:
                return 35f;
        }
    }

    private void appendTrailFromPhysics() {
        Vector3f current = objectBody.getPhysicsLocation();
        if (lastTrailPosition == null) {
            lastTrailPosition = current.clone();
            return;
        }
        if (lastTrailPosition.distanceSquared(current) < 0.02f) {
            return;
        }
        addSegment(lastTrailPosition, current);
        lastTrailPosition = current.clone();
    }

    private void addSegment(Vector3f a, Vector3f b) {
        Mesh mesh = new Mesh();
        mesh.setMode(Mesh.Mode.Lines);
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(a, b));
        mesh.setBuffer(VertexBuffer.Type.Index, 2, BufferUtils.createShortBuffer((short) 0, (short) 1));
        mesh.updateBound();
        Geometry line = new Geometry("PhysicsTrailSegment", mesh);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", new ColorRGBA(1f, 0.9f, 0.35f, 0.92f));
        mat.getAdditionalRenderState().setLineWidth(2f);
        line.setMaterial(mat);
        trailRoot.attachChild(line);
    }

    private void registerInput() {
        inputManager.addMapping(ACTION_LEFT, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addMapping(ACTION_SCROLL_UP, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
        inputManager.addMapping(ACTION_SCROLL_DOWN, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));
        inputManager.addListener(actionListener, ACTION_LEFT);
        inputManager.addListener(analogListener, ACTION_SCROLL_UP, ACTION_SCROLL_DOWN);
    }

    private final ActionListener actionListener = (name, pressed, tpf) -> {
        if (!ACTION_LEFT.equals(name) || inputManager == null) {
            return;
        }
        if (pressed) {
            Vector2f cursor = inputManager.getCursorPosition();
            lastMouse.set(cursor);
            if (tryStartGizmoDrag(cursor)) {
                return;
            }
            if (tryStartDirectDrag(cursor)) {
                return;
            }
            orbiting = true;
        } else {
            if (dragAxis != null || directDragging) {
                dragAxis = null;
                directDragging = false;
                if (positionsChangedCallback != null) {
                    positionsChangedCallback.accept(settings.copy());
                }
            }
            orbiting = false;
        }
    };

    private final AnalogListener analogListener = (name, value, tpf) -> {
        if (ACTION_SCROLL_UP.equals(name)) {
            cameraDistance = Math.max(4f, cameraDistance - value * 12f);
            updateCamera(currentCenter());
        } else if (ACTION_SCROLL_DOWN.equals(name)) {
            cameraDistance = Math.min(60f, cameraDistance + value * 12f);
            updateCamera(currentCenter());
        }
    };

    private boolean tryStartDirectDrag(Vector2f cursor) {
        if (playing) {
            return false;
        }
        Ray ray = rayFromScreen(cursor);
        CollisionResults results = new CollisionResults();
        objectRoot.collideWith(ray, results);
        targetRoot.collideWith(ray, results);
        if (results.size() == 0) {
            return false;
        }
        CollisionResult closest = results.getClosestCollision();
        Spatial hit = closest.getGeometry();
        selectedHandle = hit != null && hit.getName().contains("Target") ? "target" : "object";
        updateGizmoPosition();
        dragStartPosition = selectedPosition().clone();
        dragAxisDir = null;
        dragAxis = null;
        directDragging = true;
        dragPlane = new Plane(Vector3f.UNIT_Y, dragStartPosition.y);
        Vector3f hitOnPlane = intersectDragPlane(cursor);
        directDragOffset = hitOnPlane == null ? Vector3f.ZERO.clone() : dragStartPosition.subtract(hitOnPlane);
        updateStatus();
        return true;
    }

    private boolean tryStartGizmoDrag(Vector2f screenPos) {
        if (playing) {
            return false;
        }
        Vector3f origin3D = gizmoRoot.getWorldTranslation();
        Vector3f originScreen = cam.getScreenCoordinates(origin3D);
        String bestAxis = null;
        float bestDist = Float.MAX_VALUE;
        for (String axis : new String[]{"X", "Y", "Z"}) {
            Vector3f endpoint3D = axisEndpoint(axis);
            Vector3f endScreen = cam.getScreenCoordinates(endpoint3D);
            float dist = distPointToSegment2D(screenPos.x, screenPos.y,
                    originScreen.x, originScreen.y, endScreen.x, endScreen.y);
            if (dist < bestDist) {
                bestDist = dist;
                bestAxis = axis;
            }
        }

        if (bestAxis == null || bestDist > PICK_THRESHOLD_PX) {
            return false;
        }

        dragAxis = bestAxis;
        dragAxisDir = axisDirection(bestAxis);
        dragStartPosition = selectedPosition().clone();
        dragPlane = buildDragPlane(origin3D, dragAxisDir, cam);
        dragStartT = computeAxisT(screenPos);
        return true;
    }

    private void updateGizmoDrag(Vector2f screenPos) {
        float currentT = computeAxisT(screenPos);
        Vector3f next = dragStartPosition.add(dragAxisDir.mult(currentT - dragStartT));
        moveSelected(next);
    }

    private void updateDirectDrag(Vector2f screenPos) {
        Vector3f hit = intersectDragPlane(screenPos);
        if (hit == null) {
            return;
        }
        moveSelected(hit.add(directDragOffset));
    }

    private void moveSelected(Vector3f next) {
        next.y = Math.max(SPHERE_RADIUS, next.y);
        if ("target".equals(selectedHandle)) {
            settings.targetPosition = next.clone();
        } else {
            settings.objectPosition = next.clone();
        }
        resetSimulationInternal();
        updateStatus();
    }

    private Vector3f intersectDragPlane(Vector2f screenPos) {
        Vector3f worldNear = cam.getWorldCoordinates(screenPos, 0f).clone();
        Vector3f worldFar = cam.getWorldCoordinates(screenPos, 1f).clone();
        Vector3f rayDir = worldFar.subtract(worldNear).normalizeLocal();
        float denom = dragPlane.getNormal().dot(rayDir);
        if (Math.abs(denom) < 1e-8f) {
            return null;
        }
        float rayT = dragPlane.pseudoDistance(worldNear) / -denom;
        return worldNear.add(rayDir.mult(rayT));
    }

    private void updateGizmoPosition() {
        gizmoRoot.setLocalTranslation(selectedPosition());
    }

    private Vector3f selectedPosition() {
        return "target".equals(selectedHandle) ? settings.targetPosition : settings.objectPosition;
    }

    private Vector3f axisEndpoint(String axis) {
        return gizmoRoot.localToWorld(axisDirection(axis).mult(1.7f), null);
    }

    private Vector3f axisDirection(String axis) {
        if ("X".equals(axis)) {
            return Vector3f.UNIT_X.clone();
        }
        if ("Y".equals(axis)) {
            return Vector3f.UNIT_Y.clone();
        }
        return Vector3f.UNIT_Z.clone();
    }

    private Plane buildDragPlane(Vector3f axisOrigin, Vector3f axisDir, Camera camera) {
        Vector3f toGizmo = axisOrigin.subtract(camera.getLocation());
        Vector3f planeNormal = toGizmo.subtract(axisDir.mult(toGizmo.dot(axisDir)));
        if (planeNormal.lengthSquared() < 0.0001f) {
            planeNormal = camera.getUp().subtract(axisDir.mult(camera.getUp().dot(axisDir)));
        }
        planeNormal.normalizeLocal();
        Plane plane = new Plane();
        plane.setOriginNormal(axisOrigin, planeNormal);
        return plane;
    }

    private float computeAxisT(Vector2f screenPos) {
        Vector3f worldNear = cam.getWorldCoordinates(screenPos, 0f).clone();
        Vector3f worldFar = cam.getWorldCoordinates(screenPos, 1f).clone();
        Vector3f rayDir = worldFar.subtract(worldNear).normalizeLocal();
        float denom = dragPlane.getNormal().dot(rayDir);
        if (Math.abs(denom) < 1e-8f) {
            return dragStartT;
        }
        float rayT = dragPlane.pseudoDistance(worldNear) / -denom;
        Vector3f hit = worldNear.add(rayDir.mult(rayT));
        return hit.subtract(gizmoRoot.getWorldTranslation()).dot(dragAxisDir);
    }

    private Ray rayFromScreen(Vector2f screenPos) {
        Vector3f origin = cam.getWorldCoordinates(screenPos, 0f).clone();
        Vector3f direction = cam.getWorldCoordinates(screenPos, 1f).subtractLocal(origin).normalizeLocal();
        return new Ray(origin, direction);
    }

    private float distPointToSegment2D(float px, float py, float ax, float ay, float bx, float by) {
        float dx = bx - ax;
        float dy = by - ay;
        float lenSq = dx * dx + dy * dy;
        if (lenSq < 0.0001f) {
            return FastMath.sqrt((px - ax) * (px - ax) + (py - ay) * (py - ay));
        }
        float t = FastMath.clamp(((px - ax) * dx + (py - ay) * dy) / lenSq, 0f, 1f);
        float cx = ax + t * dx;
        float cy = ay + t * dy;
        return FastMath.sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy));
    }

    private void fitCamera() {
        Vector3f center = currentCenter();
        cameraDistance = Math.max(10f, settings.objectPosition.distance(settings.targetPosition) * 1.35f + 5f);
        updateCamera(center);
    }

    private Vector3f currentCenter() {
        return settings.objectPosition.add(settings.targetPosition).multLocal(0.5f).addLocal(0f, 1.4f, 0f);
    }

    private void updateCamera(Vector3f center) {
        float cp = FastMath.cos(pitch);
        Vector3f offset = new Vector3f(
                FastMath.sin(yaw) * cp,
                FastMath.sin(pitch),
                FastMath.cos(yaw) * cp
        ).multLocal(cameraDistance);
        cam.setLocation(center.add(offset));
        cam.lookAt(center, Vector3f.UNIT_Y);
        if (cameraLight != null) {
            cameraLight.setPosition(cam.getLocation());
        }
    }

    private Material colorMaterial(ColorRGBA color) {
        Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        mat.setBoolean("UseMaterialColors", true);
        mat.setColor("Diffuse", color);
        mat.setColor("Ambient", color.mult(0.75f));
        mat.setColor("Specular", ColorRGBA.White.mult(0.25f));
        mat.setFloat("Shininess", 18f);
        return mat;
    }

    private float clamp01(float value) {
        return FastMath.clamp(value, 0f, 1f);
    }

    private void updateStatus() {
        if (statusChangedCallback == null) {
            return;
        }
        Vector3f liveObject = playing && objectBody != null ? objectBody.getPhysicsLocation() : settings.objectPosition;
        String status = String.format(Locale.US,
                "%s selected | Minie/Bullet | object (%.2f, %.2f, %.2f) target (%.2f, %.2f, %.2f)%s",
                "target".equals(selectedHandle) ? "Target" : "Object",
                liveObject.x, liveObject.y, liveObject.z,
                settings.targetPosition.x, settings.targetPosition.y, settings.targetPosition.z,
                playing ? String.format(Locale.US, " | %.2fs", simulationTime) : "");
        statusChangedCallback.accept(status);
    }
}
