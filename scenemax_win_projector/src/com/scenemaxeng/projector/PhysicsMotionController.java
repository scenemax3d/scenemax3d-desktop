package com.scenemaxeng.projector;

import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.scenemaxeng.compiler.DirectionVerb;
import com.scenemaxeng.compiler.PhysicsMotionCommand;
import com.scenemaxeng.compiler.PositionStatement;
import com.scenemaxeng.compiler.ProgramDef;

public class PhysicsMotionController extends SceneMaxBaseController {

    private static final float DEFAULT_GRAVITY = 9.81f;

    private boolean prepared;
    private float elapsed;
    private float duration;
    private Vector3f cachedVector;
    private RigidBodyControl body;
    private int velocitySettleFrames;

    public PhysicsMotionController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, PhysicsMotionCommand cmd) {
        super(app, prg, scope, cmd);
    }

    @Override
    public boolean run(float tpf) {
        if (forceStop) {
            return true;
        }

        PhysicsMotionCommand physics = (PhysicsMotionCommand) cmd;
        if (!prepared) {
            prepared = true;
            if (findTargetVar() != 0) {
                return true;
            }
            body = resolveRigidBody();
            if (body == null) {
                app.handleRuntimeError("Physics command target '" + physics.targetVar + "' has no rigid body control.");
                return true;
            }

            cachedVector = calculateVector(physics);
            duration = evalOptional(physics.durationExpr, 0f);

            if (physics.action != PhysicsMotionCommand.ACTION_FORCE
                    && !(physics.action == PhysicsMotionCommand.ACTION_TORQUE && !physics.impulseMode)) {
                applyVector(physics, cachedVector);
                if (needsNextFrameVelocitySettle(physics)) {
                    velocitySettleFrames = 1;
                    return false;
                }
                return true;
            }
        }

        if (velocitySettleFrames > 0) {
            applyVector(physics, cachedVector);
            velocitySettleFrames--;
            return velocitySettleFrames == 0;
        }

        applyVector(physics, cachedVector);
        if (duration <= 0f) {
            return true;
        }

        elapsed += tpf;
        return elapsed >= duration;
    }

    private RigidBodyControl resolveRigidBody() {
        if (targetVarDef != null && targetVarDef.varType == com.scenemaxeng.compiler.VariableDef.VAR_TYPE_3D) {
            AppModel model = app.getAppModel(targetVar);
            if (model != null && model.physicalControl instanceof RigidBodyControl) {
                return (RigidBodyControl) model.physicalControl;
            }
        }

        Spatial spatial = app.getEntitySpatial(targetVar, targetVarDef.varType);
        return spatial == null ? null : spatial.getControl(RigidBodyControl.class);
    }

    private void applyVector(PhysicsMotionCommand physics, Vector3f vector) {
        if (vector == null) {
            return;
        }

        body.activate();
        switch (physics.action) {
            case PhysicsMotionCommand.ACTION_IMPULSE:
                body.applyCentralImpulse(vector);
                break;
            case PhysicsMotionCommand.ACTION_FORCE:
                body.applyCentralForce(vector);
                break;
            case PhysicsMotionCommand.ACTION_VELOCITY:
                body.setLinearVelocity(vector);
                break;
            case PhysicsMotionCommand.ACTION_THROW:
                body.setLinearVelocity(vector);
                break;
            case PhysicsMotionCommand.ACTION_ANGULAR_VELOCITY:
                body.setAngularVelocity(vector);
                break;
            case PhysicsMotionCommand.ACTION_TORQUE:
                if (physics.impulseMode) {
                    body.applyTorqueImpulse(vector);
                } else {
                    body.applyTorque(vector);
                }
                break;
            case PhysicsMotionCommand.ACTION_STOP:
                body.setLinearVelocity(new Vector3f());
                body.setAngularVelocity(new Vector3f());
                body.clearForces();
                break;
            default:
                break;
        }
    }

    private boolean needsNextFrameVelocitySettle(PhysicsMotionCommand physics) {
        return physics.action == PhysicsMotionCommand.ACTION_THROW
                || physics.action == PhysicsMotionCommand.ACTION_VELOCITY
                || physics.action == PhysicsMotionCommand.ACTION_ANGULAR_VELOCITY;
    }

    private Vector3f calculateVector(PhysicsMotionCommand physics) {
        if (physics.action == PhysicsMotionCommand.ACTION_STOP) {
            return Vector3f.ZERO;
        }

        if (physics.action == PhysicsMotionCommand.ACTION_VELOCITY
                || physics.action == PhysicsMotionCommand.ACTION_ANGULAR_VELOCITY
                || physics.action == PhysicsMotionCommand.ACTION_TORQUE
                || physics.targetMode == PhysicsMotionCommand.TARGET_VECTOR) {
            return evalVector(physics.xExpr, physics.yExpr, physics.zExpr);
        }

        if (physics.action == PhysicsMotionCommand.ACTION_THROW) {
            return calculateThrowVector(physics);
        }

        Vector3f direction = resolveDirection(physics);
        float power = evalRequired(physics.powerExpr);
        return direction.mult(power);
    }

    private Vector3f calculateThrowVector(PhysicsMotionCommand physics) {
        float power = evalRequired(physics.powerExpr);
        Vector3f source = resolveSourcePosition();
        Vector3f target = resolveTargetPosition(physics);

        Vector3f launch;
        if (target != null && physics.targetMode == PhysicsMotionCommand.TARGET_AT && physics.angleExpr == null) {
            launch = calculateBallisticVelocity(source, target, power, resolveArcBlend(physics));
        } else {
            Vector3f direction = target == null ? resolveDirection(physics) : target.subtract(source).normalizeLocal();
            if (physics.angleExpr != null || physics.arcMode != null || physics.arcExpr != null) {
                float angle = physics.angleExpr != null
                        ? evalRequired(physics.angleExpr)
                        : fallbackArcAngle(physics);
                launch = applyLaunchAngle(direction, power, angle);
            } else {
                launch = direction.mult(power);
            }
        }

        if (hasSpin(physics)) {
            body.setAngularVelocity(evalVector(physics.spinXExpr, physics.spinYExpr, physics.spinZExpr));
        }
        return launch;
    }

    private Vector3f calculateBallisticVelocity(Vector3f source, Vector3f target, float speed, float arcBlend) {
        Vector3f delta = target.subtract(source);
        Vector3f horizontal = new Vector3f(delta.x, 0f, delta.z);
        float horizontalDistance = horizontal.length();
        if (horizontalDistance < 0.001f) {
            return new Vector3f(0f, speed, 0f);
        }

        float speedSq = speed * speed;
        float root = speedSq * speedSq
                - DEFAULT_GRAVITY * (DEFAULT_GRAVITY * horizontalDistance * horizontalDistance
                + 2f * delta.y * speedSq);

        if (root < 0f) {
            return applyLaunchAngle(horizontal.normalizeLocal(), speed, 35f + arcBlend * 20f);
        }

        float sqrt = FastMath.sqrt(root);
        float lowAngle = FastMath.atan((speedSq - sqrt) / (DEFAULT_GRAVITY * horizontalDistance));
        float highAngle = FastMath.atan((speedSq + sqrt) / (DEFAULT_GRAVITY * horizontalDistance));
        float angle = FastMath.interpolateLinear(arcBlend, lowAngle, highAngle);

        Vector3f horizontalDir = horizontal.normalizeLocal();
        return horizontalDir.mult(speed * FastMath.cos(angle)).addLocal(0f, speed * FastMath.sin(angle), 0f);
    }

    private Vector3f applyLaunchAngle(Vector3f direction, float speed, float angleDegrees) {
        Vector3f horizontal = new Vector3f(direction.x, 0f, direction.z);
        if (horizontal.lengthSquared() < 0.0001f) {
            horizontal = resolveSourceForward();
            horizontal.y = 0f;
        }
        horizontal.normalizeLocal();

        float angle = angleDegrees * FastMath.DEG_TO_RAD;
        return horizontal.mult(speed * FastMath.cos(angle)).addLocal(0f, speed * FastMath.sin(angle), 0f);
    }

    private Vector3f resolveDirection(PhysicsMotionCommand physics) {
        if (physics.targetMode == PhysicsMotionCommand.TARGET_DIRECTION) {
            return resolveNamedDirection(physics.direction);
        }

        Vector3f source = resolveSourcePosition();
        Vector3f target = resolveTargetPosition(physics);
        if (target == null) {
            return resolveSourceForward();
        }

        Vector3f direction = target.subtract(source);
        if (direction.lengthSquared() < 0.0001f) {
            return resolveSourceForward();
        }
        return direction.normalizeLocal();
    }

    private Vector3f resolveNamedDirection(int direction) {
        Spatial spatial = app.getEntitySpatial(targetVar, targetVarDef.varType);
        Quaternion rotation = spatial == null ? Quaternion.IDENTITY : spatial.getWorldRotation();

        if (direction == DirectionVerb.BACKWARD) {
            return rotation.mult(Vector3f.UNIT_Z).negateLocal().normalizeLocal();
        } else if (direction == DirectionVerb.LEFT) {
            return rotation.mult(Vector3f.UNIT_X).normalizeLocal();
        } else if (direction == DirectionVerb.RIGHT) {
            return rotation.mult(Vector3f.UNIT_X).negateLocal().normalizeLocal();
        } else if (direction == DirectionVerb.UP) {
            return rotation.mult(Vector3f.UNIT_Y).normalizeLocal();
        } else if (direction == DirectionVerb.DOWN) {
            return rotation.mult(Vector3f.UNIT_Y).negateLocal().normalizeLocal();
        }
        return rotation.mult(Vector3f.UNIT_Z).normalizeLocal();
    }

    private Vector3f resolveSourceForward() {
        return resolveNamedDirection(DirectionVerb.FORWARD);
    }

    private Vector3f resolveSourcePosition() {
        Spatial spatial = app.getEntitySpatial(targetVar, targetVarDef.varType);
        return spatial == null ? Vector3f.ZERO.clone() : spatial.getWorldTranslation().clone();
    }

    private Vector3f resolveTargetPosition(PhysicsMotionCommand physics) {
        if (physics.targetEntity != null) {
            RunTimeVarDef target = findTargetVar(physics.targetEntity);
            if (target == null) {
                return null;
            }
            Spatial spatial = app.getEntitySpatial(target.varName, target.varDef.varType);
            return spatial == null ? null : spatial.getWorldTranslation().clone();
        }

        if (physics.targetPositionStatement != null) {
            return resolvePositionStatement(physics.targetPositionStatement);
        }

        if (physics.xExpr != null && physics.yExpr != null && physics.zExpr != null) {
            return evalVector(physics.xExpr, physics.yExpr, physics.zExpr);
        }

        return null;
    }

    private Vector3f resolvePositionStatement(PositionStatement statement) {
        RunTimeVarDef target = findTargetVar(statement.startEntity);
        if (target == null) {
            return null;
        }

        Spatial spatial = app.getEntitySpatial(target.varName, target.varDef.varType);
        if (spatial == null) {
            return null;
        }

        Vector3f position = spatial.getWorldTranslation().clone();
        Util.calcPositionStatementVerbs(scope, statement, spatial.getWorldRotation(), position);
        return position;
    }

    private Vector3f evalVector(com.abware.scenemaxlang.parser.SceneMaxParser.Logical_expressionContext xExpr,
                                com.abware.scenemaxlang.parser.SceneMaxParser.Logical_expressionContext yExpr,
                                com.abware.scenemaxlang.parser.SceneMaxParser.Logical_expressionContext zExpr) {
        return new Vector3f(evalRequired(xExpr), evalRequired(yExpr), evalRequired(zExpr));
    }

    private float resolveArcBlend(PhysicsMotionCommand physics) {
        if (physics.arcExpr != null) {
            return FastMath.clamp(evalRequired(physics.arcExpr), 0f, 1f);
        }
        if ("low".equals(physics.arcMode)) {
            return 0f;
        }
        if ("high".equals(physics.arcMode)) {
            return 1f;
        }
        return 0.5f;
    }

    private float fallbackArcAngle(PhysicsMotionCommand physics) {
        if (physics.arcExpr != null) {
            return 20f + FastMath.clamp(evalRequired(physics.arcExpr), 0f, 1f) * 45f;
        }
        if ("low".equals(physics.arcMode)) {
            return 20f;
        }
        if ("high".equals(physics.arcMode)) {
            return 60f;
        }
        return 35f;
    }

    private boolean hasSpin(PhysicsMotionCommand physics) {
        return physics.spinXExpr != null && physics.spinYExpr != null && physics.spinZExpr != null;
    }

    private float evalRequired(com.abware.scenemaxlang.parser.SceneMaxParser.Logical_expressionContext expr) {
        if (expr == null) {
            return 0f;
        }
        Object value = new ActionLogicalExpressionVm(expr, scope).evaluate();
        return value instanceof Number ? ((Number) value).floatValue() : 0f;
    }

    private float evalOptional(com.abware.scenemaxlang.parser.SceneMaxParser.Logical_expressionContext expr, float fallback) {
        return expr == null ? fallback : evalRequired(expr);
    }
}
