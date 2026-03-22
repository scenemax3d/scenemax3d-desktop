package com.scenemaxeng.projector;

import com.scenemaxeng.common.types.*;

import com.jayfella.jme.vehicle.Sound;
import com.jayfella.jme.vehicle.SpeedUnit;
import com.jayfella.jme.vehicle.Vehicle;
import com.jayfella.jme.vehicle.WheelModel;
import com.jayfella.jme.vehicle.gui.SpeedometerState;
import com.jayfella.jme.vehicle.gui.TachometerState;
import com.jayfella.jme.vehicle.part.Engine;
import com.jayfella.jme.vehicle.part.GearBox;
import com.jayfella.jme.vehicle.part.Wheel;
import com.jme3.app.state.AppStateManager;
import com.jayfella.jme.vehicle.Steering;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;

import java.io.File;
import java.util.logging.Level;

public class SceneMax3DGenericVehicle extends Vehicle {

    private final ResourceSetup resource;
    public BasicVehicleInputState inputState;
    private TachometerState tachoState;
    private SpeedometerState speedoState;

    public void load(AssetManager assetManager) {
        if (getVehicleControl() != null) {
            logger.log(Level.SEVERE, "The model is already loaded.");
            return;
        }
        /*
         * Load the C-G model with everything except the wheels.
         * Bullet refers to this as the "chassis".
         */
        float mass = resource.mass;// 525f; // in kilos
        float linearDamping = 0.02f;

        File f=new File(resource.path);
        String folderName = f.getParentFile().getName();
        String fileName = f.getName();
        int extIndex=fileName.lastIndexOf(".");
        fileName=fileName.substring(0,extIndex);


        Spatial chassis = assetManager.loadModel(resource.path);
        chassis.setLocalScale(resource.localScale);
        CollisionShape shape = CollisionShapeFactory.createDynamicMeshShape(chassis);


//        String assetPath = "/Models/" + folderName + "/shapes/chassis-shape.j3o";
//        CollisionShape shape;
//        try {
//            shape = (CollisionShape)assetManager.loadAsset(assetPath);
//            Vector3f scale = chassis.getWorldScale();
//            shape.setScale(scale);
//        } catch (AssetNotFoundException var10) {
//            shape = CollisionShapeFactory.createDynamicMeshShape(cgmRoot);
//        }


        setChassis(chassis, shape, mass,linearDamping);

        float rearDiameter = resource.backWheel.diameter*resource.localScale;
        float frontDiameter = resource.frontWheel.diameter*resource.localScale;
        WheelModel wheel_fl = new SceneMax3DWheel(assetManager,resource.wheelModel,frontDiameter, resource.frontWheel.scale);
        WheelModel wheel_fr = new SceneMax3DWheel(assetManager,resource.wheelModel,frontDiameter, resource.frontWheel.scale);
        WheelModel wheel_rl = new SceneMax3DWheel(assetManager,resource.rearWheelModel,rearDiameter,resource.backWheel.scale);
        WheelModel wheel_rr = new SceneMax3DWheel(assetManager,resource.rearWheelModel,rearDiameter,resource.backWheel.scale);

        /*
         * By convention, wheels are modeled for the left side, so
         * wheel models for the right side require a 180-degree rotation.
         */
        wheel_fr.flip();
        wheel_rr.flip();
        /*
         * Add the wheels to the vehicle.
         * For rear-wheel steering, it will be necessary to "flip" the steering.
         */
        float frontX = resource.frontWheel.offset.getX();//  0.9f; // half of the axle track
        float rearX = resource.backWheel.offset.getX();//  0.9f; // half of the axle track
        float frontY = resource.frontWheel.offset.getY();//0.48f; // height of front axle relative to vehicle's CoG
        float rearY = resource.backWheel.offset.getY();//0.6f; // height of rear axle relative to vehicle's CoG
        float frontZ = resource.frontWheel.offset.getZ();//1.12f;
        float rearZ = resource.backWheel.offset.getZ();//-1.33f;
        float frontMainBrake = resource.frontWheel.brake;//  10000; // in front only
        float frontParkingBrake = resource.frontWheel.brake; //3_000f; // in front only

        float rearMainBrake = resource.backWheel.brake;//  10000; // in front only
        float rearParkingBrake = resource.backWheel.brake; //3_000f; // in front only

        float damping = 0.09f; // extra linear damping
        addWheel(wheel_fl, new Vector3f(+frontX, frontY, frontZ),
                Steering.DIRECT, frontMainBrake, frontParkingBrake, damping);
        addWheel(wheel_fr, new Vector3f(-frontX, frontY, frontZ),
                Steering.DIRECT, frontMainBrake, frontParkingBrake, damping);
        addWheel(wheel_rl, new Vector3f(+rearX, rearY, rearZ),
                Steering.UNUSED, rearMainBrake, rearParkingBrake, damping);
        addWheel(wheel_rr, new Vector3f(-rearX, rearY, rearZ),
                Steering.UNUSED, rearMainBrake, rearParkingBrake, damping);
        /*
         * Configure the suspension.
         *
         * This vehicle applies the same settings to each wheel,
         * but that isn't required.
         */
        int index=0;
        for (Wheel wheel : listWheels()) {
            SceneMaxWheel w = index<2?resource.frontWheel:resource.backWheel;
            setupWheel(wheel,w);
            index++;
        }

        /*
         * Distribute drive power across the wheels:
         *  0 = no power, 1 = all of the power
         *
        /*
         * Specify the name and speed range for each gear.
         * The min-max speeds of successive gears should overlap.
         * The "min" speed of low gear should be zero.
         * The "max" speed of high gear determines the top speed.
         * The "red" speed of each gear is used to calculate its ratio.
         */
        GearBox gearBox = new GearBox(resource.gearBox.gears.size(), 1);
        gearBox.getGear(-1).setName("reverse").setMinMaxRedKph(0f, -40f, -45f);
        for(int i=0;i<resource.gearBox.gears.size();++i) {
            SceneMaxGearBox.SceneMaxGear g =resource.gearBox.gears.get(i);
            gearBox.getGear(i+1).setName("gear"+(i+1)).setMinMaxRedKph(g.start,g.end-5,g.end);
        }
        setGearBox(gearBox);

        Engine engine = new SceneMax3DGenericEngine(resource.engine);
        setEngine(engine);

        Sound engineSound = new SceneMax3DGenericEngineSound(Integer.valueOf(resource.engine.audio));
        engineSound.load(assetManager);
        engine.setSound(engineSound);

        Sound hornSound = new SceneMax3DGenericSound("audio/horn-1.wav", 823f);
        hornSound.load(assetManager);
        setHornSound(hornSound);
        /*
         * build() must be invoked last, to complete the Vehicle
         */
        build();

    }

    private void setupWheel(Wheel w, SceneMaxWheel setup) {
        // how much weight the suspension can take before it bottoms out
        // Setting this too low will make the wheels sink into the ground.
        w.getSuspension().setMaxForce(setup.suspension.maxForce);// 12_000f

        // the stiffness of the suspension
        // Setting this too low can cause odd behavior.
        w.getSuspension().setStiffness(setup.suspension.stiffness);// 24f

        // how fast the suspension will compress
        // 1 = slow, 0 = fast.
        w.getSuspension().setCompressDamping(setup.suspension.compression);//  0.5f

        // how quickly the suspension will rebound back to height
        // 1 = slow, 0 = fast.
        w.getSuspension().setRelaxDamping(setup.suspension.damping);//  0.65f

        w.setTireModel(new SceneMaxTire1());
        w.setFriction(setup.friction);//  1.3f

        w.setPowerFraction(setup.accelerationForce);
    }


    public SceneMax3DGenericVehicle(ResourceSetup resource) {
        super(resource.name);
        this.resource=resource;
    }

    public void reset() {

        float[] angles = new float[3];
        this.getVehicleControl().getPhysicsRotation().toAngles(angles);

        Quaternion newRotation = new Quaternion().fromAngles(0, angles[1], 0);
        this.getVehicleControl().setPhysicsRotation(newRotation);

        this.getVehicleControl().setAngularVelocity(new Vector3f());
        this.getVehicleControl().setLinearVelocity(new Vector3f());

    }


    @Override
    public void locateDashCam(Vector3f storeResult) {

    }

    @Override
    protected void locateTarget(Vector3f storeResult) {

    }

    public void showTacho(AppStateManager stateManager) {

        if(this.tachoState==null) {
            this.tachoState = new TachometerState(this.getEngine());
            //stateManager.attach(new SpeedometerState(this, SpeedUnit.KPH));
            stateManager.attach(tachoState);
        }
    }

    public void removeTacho(AppStateManager stateManager) {

        if(tachoState!=null) {
            stateManager.detach(tachoState);
            tachoState=null;
        }
    }

    public void showSpeedo(AppStateManager stateManager) {

        if(this.speedoState==null) {
            this.speedoState = new SpeedometerState(this, SpeedUnit.KPH);
            stateManager.attach(speedoState);
        }
    }

    public void removeSpeedo(AppStateManager stateManager) {

        if(speedoState!=null) {
            stateManager.detach(speedoState);
            speedoState=null;
        }
    }

}
