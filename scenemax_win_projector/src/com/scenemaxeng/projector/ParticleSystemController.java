package com.scenemaxeng.projector;

import com.jme3.effect.ParticleEmitter;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.scenemaxeng.compiler.ParticleSystemCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class ParticleSystemController extends SceneMaxBaseController {

    private final SceneMaxApp app;
    private final SceneMaxScope scope;
    private final ParticleSystemCommand cmd;
    private final ProgramDef prg;
    private boolean particleCreated = false;
    private float passedTime = 0;
    private ParticleEmitter particleEmitter;
    private Node newParticleEmitter;


    public ParticleSystemController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, ParticleSystemCommand cmd) {
        this.app=app;
        this.scope=scope;
        this.cmd=cmd;
        this.prg=prg;

    }

    public boolean run(float tpf) {

        if (forceStop) return true;

        if(particleCreated) {
            return checkTimer(tpf);
        }

        particleCreated=true;

        if(cmd.time!=null) {
            cmd.timeVal = ((Double) new ActionLogicalExpression(cmd.time.logical_expression(), scope).evaluate()).floatValue();
        }

        if (cmd.entityPos!=null) {
            RunTimeVarDef entityForPos = app.findVarRuntime(prg,scope,cmd.entityPos.entityName);
            if(entityForPos!=null) {
                Spatial sp = null;
                if(cmd.entityPos.entityJointName!=null) {
                    AppModel am = app.getAppModel(entityForPos.varName);
                    sp = am.getJointAttachementNode(cmd.entityPos.entityJointName);

                } else {
                    sp = app.getEntitySpatial(entityForPos.varName, entityForPos.varDef.varType);
                }

                if (sp != null) {
                    Vector3f pos = sp.getWorldTranslation();// sp.getLocalTranslation();
                    cmd.posX = pos.getX();
                    cmd.posY = pos.getY();
                    cmd.posZ = pos.getZ();
                }
            }

        } else if (cmd.pos != null) {
            if(cmd.pos.pos_axes()!=null) {
                cmd.posX = ((Double) new ActionLogicalExpression(cmd.pos.pos_axes().print_pos_x().logical_expression(), scope).evaluate()).floatValue();
                cmd.posY = ((Double) new ActionLogicalExpression(cmd.pos.pos_axes().print_pos_y().logical_expression(), scope).evaluate()).floatValue();
                cmd.posZ = ((Double) new ActionLogicalExpression(cmd.pos.pos_axes().print_pos_z().logical_expression(), scope).evaluate()).floatValue();
            }
        }

        if(cmd.gravity!=null) {
            cmd.gravityX = ((Double) new ActionLogicalExpression(cmd.gravity.vector_x().logical_expression(),scope).evaluate()).floatValue();
            cmd.gravityY = ((Double) new ActionLogicalExpression(cmd.gravity.vector_y().logical_expression(),scope).evaluate()).floatValue();
            cmd.gravityZ = ((Double) new ActionLogicalExpression(cmd.gravity.vector_z().logical_expression(),scope).evaluate()).floatValue();
        }

        if(cmd.startSize!=null) {
            cmd.startSizeVal = ((Double) new ActionLogicalExpression(cmd.startSize.logical_expression(),scope).evaluate()).floatValue();
        }

        if(cmd.endSize!=null) {
            cmd.endSizeVal = ((Double) new ActionLogicalExpression(cmd.endSize.logical_expression(),scope).evaluate()).floatValue();
        }

        // new particle system
        if(cmd.type>=100) {
            if(cmd.radiusValExpr!=null) {
                cmd.radiusVal=((Double) new ActionLogicalExpression(cmd.radiusValExpr,scope).evaluate()).floatValue();
            }

            if(cmd.emissionsPerSecExpr!=null) {
                cmd.emissionsPerSecondVal = ((Double) new ActionLogicalExpression(cmd.emissionsPerSecExpr,scope).evaluate()).floatValue();
                cmd.particlesPerEmissionVal = ((Double) new ActionLogicalExpression(cmd.particlesPerEmissionExpr,scope).evaluate()).floatValue();
            }

        }

        if(cmd.attachToEntity!=null) {
            cmd.attachToEntityVarDef = app.findVarRuntime(prg,scope,cmd.attachToEntity);
        }

        if(cmd.type==ParticleSystemCommand.FLASH) {
            particleEmitter = app.createFlash(cmd);
        } else if (cmd.type==ParticleSystemCommand.DEBRIS) {
            particleEmitter = app.createDebris(cmd);
        } else if (cmd.type==ParticleSystemCommand.SPARK) {
            particleEmitter = app.createSpark(cmd);
        } else if (cmd.type==ParticleSystemCommand.SHOCK_WAVE) {
            particleEmitter = app.createShockwave(cmd);
        } else if (cmd.type==ParticleSystemCommand.SMOKE_TRAIL) {
            particleEmitter = app.createSmokeTrail(cmd);
        } else if (cmd.type==ParticleSystemCommand.EXPLOSION) {
            particleEmitter = app.createExplosion(cmd);
        } else if (cmd.type==ParticleSystemCommand.TIME_ORBIT) {
            newParticleEmitter = app.createParticleTimeORB(cmd);
        } else if (cmd.type==ParticleSystemCommand.FLAME) {
            newParticleEmitter = app.createParticleFlame(cmd);
        }

        if(particleEmitter!=null || newParticleEmitter!=null) {
            return false;
        }

        return true;

    }

    private boolean checkTimer(float tpf) {
        passedTime+=tpf;
        boolean finish = (passedTime>=cmd.timeVal);

        if(finish) {
            if(cmd.type>=100) {
                app.removeNewParticleEmitter(newParticleEmitter);
            } else {
                app.removeParticleEmitter(particleEmitter);
            }
        }

        return finish;
    }


}
