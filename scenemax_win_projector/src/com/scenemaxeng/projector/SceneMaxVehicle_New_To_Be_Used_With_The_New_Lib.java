//package com.scenemaxeng.projector;
//
//import com.jayfella.jme.vehicle.Car;
//import com.jayfella.jme.vehicle.Main;
//import com.jayfella.jme.vehicle.Sound;
//import com.jayfella.jme.vehicle.examples.engines.Engine180HP;
//import com.jayfella.jme.vehicle.examples.engines.Engine250HP;
//import com.jayfella.jme.vehicle.examples.engines.Engine450HP;
//import com.jayfella.jme.vehicle.examples.engines.Engine600HP;
//import com.jayfella.jme.vehicle.examples.sounds.EngineSound5;
//import com.jayfella.jme.vehicle.examples.tires.Tire_01;
//import com.jayfella.jme.vehicle.examples.wheels.WheelModel;
//import com.jayfella.jme.vehicle.part.GearBox;
//import com.jayfella.jme.vehicle.part.Suspension;
//import com.jayfella.jme.vehicle.part.Wheel;
//import com.jme3.app.Application;
//import com.jme3.asset.AssetManager;
//import com.jme3.math.Quaternion;
//import com.jme3.math.Vector3f;
//import com.jme3.scene.Spatial;
//
//import java.io.File;
//import java.util.logging.Level;
//
////import com.jayfella.jme.vehicle.engine.Engine;
////import com.jayfella.jme.vehicle.examples.engines.Engine180HP;
////import com.jayfella.jme.vehicle.examples.tyres.Tyre_01;
////import com.jayfella.jme.vehicle.examples.wheels.BuggyFrontWheel;
////import com.jayfella.jme.vehicle.examples.wheels.BuggyRearWheel;
////import com.jme3.material.Material;
////import com.jme3.math.FastMath;
////import com.jme3.scene.Node;
////import com.scenemaxeng.projector.AppModel;
////import com.scenemaxeng.common.types.ResourceSetup;
////import com.scenemaxeng.common.types.SceneMaxGearBox;
////import com.scenemaxeng.common.types.SceneMaxWheel;
//
//public class SceneMaxVehicle_New_To_Be_Used_With_The_New_Lib extends Car {
//
//
//    private final ResourceSetup resource;
//
//    public void load() {
//        if (getVehicleControl() != null) {
//            logger.log(Level.SEVERE, "The model is already loaded.");
//            return;
//        }
//
//        AssetManager assetManager = Main.getApplication().getAssetManager();
//        String assetPath = resource.path;// "Models/hcr2_buggy/dune-buggy.j3o";
//        Spatial chassis = assetManager.loadModel(assetPath);
//
//
//        float mass = resource.mass;// 525f; // in kilos
//        float linearDamping = 0.02f;
//        String assetFolderName = new File(assetPath).getParentFile().getName();
//        setChassis("Vehicles/"+assetFolderName, chassis, mass, linearDamping);
//        /*
//         * By convention, wheels are modeled for the left side, so
//         * wheel models for the right side require a 180-degree rotation.
//         */
//        float rearDiameter = resource.backWheel.diameter;// 0.944f;
//        float frontDiameter = resource.frontWheel.diameter;// 0.77f;
//        WheelModel wheel_fl = new SceneMax3DWheel(resource.frontWheel.path, frontDiameter);// BuggyFrontWheel(frontDiameter);
//        WheelModel wheel_fr = new SceneMax3DWheel(resource.frontWheel.path, frontDiameter).flip(); // BuggyFrontWheel(frontDiameter).flip();
//        WheelModel wheel_rl = new SceneMax3DWheel(resource.backWheel.path, rearDiameter); // BuggyRearWheel(rearDiameter);
//        WheelModel wheel_rr = new SceneMax3DWheel(resource.backWheel.path, rearDiameter).flip(); // BuggyRearWheel(rearDiameter).flip();
//        /*
//         * Add the wheels to the vehicle.
//         * For rear-wheel steering, it will be necessary to "flip" the steering.
//         */
//        float wheelX = resource.frontWheel.offset.x;//     0.92f; // half of the axle track
//        float frontY = resource.frontWheel.offset.y;//    0.53f; // height of front axle relative to vehicle's CoG
//        float rearY = resource.backWheel.offset.y;//      0.63f; // height of rear axle relative to vehicle's CoG
//        float frontZ = resource.frontWheel.offset.z;// 1.12f;
//        float rearZ =  resource.backWheel.offset.z;// -1.33f;
//        boolean front = true; // Front wheels are for steering.
//        boolean rear = false; // Rear wheels do not steer.
//        boolean steeringFlipped = false;
//        float mainBrake = resource.frontWheel.brake;// 3_000f; // in front only
//        float parkingBrake = resource.frontWheel.brake; //3_000f; // in front only
//        float damping = 0.09f; // extra linear damping
//        addWheel(wheel_fl, new Vector3f(+wheelX, frontY, frontZ), front,
//                steeringFlipped, mainBrake, parkingBrake, damping);
//        addWheel(wheel_fr, new Vector3f(-wheelX, frontY, frontZ), front,
//                steeringFlipped, mainBrake, parkingBrake, damping);
//        addWheel(wheel_rl, new Vector3f(+wheelX, rearY, rearZ), rear,
//                steeringFlipped, 0f, 0f, damping);
//        addWheel(wheel_rr, new Vector3f(-wheelX, rearY, rearZ), rear,
//                steeringFlipped, 0f, 0f, damping);
//        /*
//         * Configure the suspension.
//         *
//         * This vehicle applies the same settings to each wheel,
//         * but that isn't required.
//         */
//        int index=0;
//        for (Wheel wheel : listWheels()) {
//            SceneMaxWheel.Suspension sp = index<2?resource.frontWheel.suspension:resource.backWheel.suspension;
//            Suspension suspension = wheel.getSuspension();
//
//            // the rest-length or "height" of the suspension
//            suspension.setRestLength(sp.length); //0.25f
//
//            // how much weight the suspension can take before it bottoms out
//            // Setting this too low will make the wheels sink into the ground.
//            suspension.setMaxForce(sp.maxForce);// 12_000f
//
//            // the stiffness of the suspension
//            // Setting this too low can cause odd behavior.
//            suspension.setStiffness(sp.stiffness);// 24f
//
//            // how fast the suspension will compress
//            // 1 = slow, 0 = fast.
//            suspension.setCompressDamping(sp.compression  );//0.5f
//
//            // how quickly the suspension will rebound back to height
//            // 1 = slow, 0 = fast.
//            suspension.setRelaxDamping(sp.damping  );//0.65f
//
//            index++;
//        }
//        /*
//         * Give each wheel a tire with friction.
//         */
//        index=0;
//        for (Wheel wheel : listWheels()) {
//            wheel.setTireModel(new Tire_01());
//            wheel.setFriction(index<2?resource.frontWheel.friction:resource.backWheel.friction);// 1.3f
//            index++;
//        }
//        /*
//         * Distribute drive power across the wheels:
//         *  0 = no power, 1 = all of the power
//         *
//         * This vehicle has rear-wheel drive.
//         *
//         * 4-wheel drive would be problematic here because
//         * the diameters of the front wheels differ from those of the rear ones.
//         */
//        getWheel(0).setPowerFraction(resource.frontWheel.accelerationForce );//0f
//        getWheel(1).setPowerFraction(resource.frontWheel.accelerationForce);// 0f
//        getWheel(2).setPowerFraction(resource.backWheel.accelerationForce);// 0.4f
//        getWheel(3).setPowerFraction(resource.backWheel.accelerationForce);// 0.4f
//        /*
//         * Specify the name and speed range for each gear.
//         * The min-max speeds of successive gears should overlap.
//         * The "min" speed of low gear should be zero.
//         * The "max" speed of high gear determines the top speed.
//         * The "red" speed of each gear is used to calculate its ratio.
//         */
////        GearBox gearBox = new GearBox(4, 1);
////        gearBox.getGear(-1).setName("reverse").setMinMaxRedKph(0f, -40f, -40f);
////        gearBox.getGear(1).setName("low").setMinMaxRedKph(0f, 15f, 20f);
////        gearBox.getGear(2).setName("2nd").setMinMaxRedKph(5f, 30f, 35f);
////        gearBox.getGear(3).setName("3rd").setMinMaxRedKph(25f, 50f, 60f);
////        gearBox.getGear(4).setName("high").setMinMaxRedKph(45f, 90f, 90f);
////        setGearBox(gearBox);
//        GearBox gearBox = new GearBox(resource.gearBox.gears.size(),1);
//        gearBox.getGear(-1).setName("reverse").setMinMaxRedKph(0f, -40f, -40f);
//        for(int i=0;i<resource.gearBox.gears.size();++i) {
//            SceneMaxGearBox.SceneMaxGear g = resource.gearBox.gears.get(i);
//            gearBox.getGear(i).setName(String.valueOf(i)).setMinMaxRedKph(g.start,g.end,g.end+5);
//        }
//
//        setGearBox(gearBox);
//
//        Engine engine;
//        if(resource.engine.name.equals("Basic 180")) {  // 180,250,450,600
//            engine = new Engine180HP();
//        } else if(resource.engine.name.equals("Basic 250")) {
//            engine = new Engine250HP();
//        } else if(resource.engine.name.equals("Basic 450")) {
//            engine = new Engine450HP();
//        } else if(resource.engine.name.equals("Basic 600")) {
//            engine = new Engine600HP();
//        } else {
//            engine = new Engine600HP(); // default strongest engine
//        }
//
//        setEngine(engine);
//
//
//        Sound engineSound = new EngineSound5();
//        setEngineSound(engineSound);
//
//        setHornAudio("/Audio/horn-1.ogg");
//        /*
//         * build() must be invoked last, to complete the Vehicle
//         */
//        build();
//    }
//
//
//    public SceneMaxVehicle_New_To_Be_Used_With_The_New_Lib(Application app, ResourceSetup resource, Quaternion initRotate) {
//        super(resource.name);
//
//        this.resource=resource;
//
////        AssetManager assetManager = app.getAssetManager();
////
////
////        Spatial chassis = assetManager.loadModel(resource.path);//  "Models/Vehicles/Chassis/Pickup2/pickup.j3o"
////        if(resource.localScale!=0.0f) {
////            chassis.setLocalScale(resource.localScale);
////        }
////        if(resource.chassisMaterial!=null) {
////            Material chassisMaterial = assetManager.loadMaterial(resource.chassisMaterial);//"Materials/Vehicles/Pickup.j3m"
////            chassis.setMaterial(chassisMaterial);
////        }
////
////        // Set the mass of the chassis. This is the overall weight.
////        setChassis(chassis, resource.mass );
////
////        Spatial wheel = assetManager.loadModel( resource.wheelModel);// "Models/Vehicles/Wheel/Wheel_1/wheel.j3o"
////
////
////        Material wheelMaterial = assetManager.loadMaterial(resource.wheelMaterial);//"Materials/Vehicles/Wheel_1.j3m"
////        wheel.setMaterial(wheelMaterial);
////
////        Node w_fl_node = new Node("Wheel FL Node");
////        Spatial w_fl = wheel.clone(false);
////        w_fl.setMaterial(wheelMaterial);
////        w_fl.rotate(0, 0, 0);
////        w_fl_node.attachChild(w_fl);
////
////        Node w_fr_node = new Node("Wheel FR Node");
////        Spatial w_fr = wheel.clone(false);
////        w_fr.setMaterial(wheelMaterial);
////        w_fr.rotate(0, FastMath.PI, 0);
////        w_fr_node.attachChild(w_fr);
////
////        Node w_rl_node = new Node("Wheel RL Node");
////        Spatial w_rl = wheel.clone(false);
////        w_rl.setMaterial(wheelMaterial);
////        w_rl.rotate(0, 0, 0);
////        w_rl_node.attachChild(w_rl);
////
////        Node w_rr_node = new Node("Wheel RR Node");
////        Spatial w_rr = wheel.clone(false);
////        w_rr.setMaterial(wheelMaterial);
////        w_rr.rotate(0, FastMath.PI, 0);
////        w_rr_node.attachChild(w_rr);
////
////        // set the scale of the wheels.
////        w_fr_node.setLocalScale(resource.frontWheel.scale );//1.1f
////        w_fl_node.setLocalScale(resource.frontWheel.scale );
////        w_rr_node.setLocalScale(resource.backWheel.scale );
////        w_rl_node.setLocalScale(resource.backWheel.scale );
////
////        // add the wheels, setting the position, whether or not they steer, and a brake with force.
////        // if you want rear-wheel steering, you will also want to "flip" the steering.
////
////        addWheel(w_fl_node, resource.frontWheel.offset, resource.frontWheel.steering, false, new Brake(resource.frontWheel.brake));
////        addWheel(w_fr_node, resource.frontWheel.offset.clone().setX(resource.frontWheel.offset.getX()*-1), resource.frontWheel.steering, false, new Brake(resource.frontWheel.brake));
////
////        addWheel(w_rl_node, resource.backWheel.offset, resource.backWheel.steering, false, new Brake(resource.backWheel.brake));
////        addWheel(w_rr_node, resource.backWheel.offset.clone().setX(resource.backWheel.offset.getX()*-1), resource.backWheel.steering, false, new Brake(resource.backWheel.brake));
////
////        // configure the suspension.
////        // In this car we're setting the same settings for each wheel, but you don't have to.
////        for (int i = 0; i < getNumWheels(); i++) {
////
////            // the rest-length or "height" of the suspension.
////            SceneMaxWheel.Suspension sp = i<2?resource.frontWheel.suspension:resource.backWheel.suspension;
////            getWheel(i).getSuspension().setRestLength(sp.length);
////
////            getWheel(i).getVehicleWheel().setMaxSuspensionTravelCm(1000);
////
////            // how much force the suspension can take before it bottoms out.
////            // setting this too low will make the wheels sink into the ground.
////            getWheel(i).getSuspension().setMaxForce(sp.maxForce);
////
////            // the stiffness of the suspension.
////            // setting this too soft can cause odd behavior.
////            getWheel(i).getSuspension().setStiffness(sp.stiffness);
////
////            // how fast the suspension will compress.
////            // 1 = slow, 0 = fast.
////            getWheel(i).getSuspension().setCompression(sp.compression);
////
////            // how quickly the suspension will rebound back to height.
////            // 1 = slow, 0 = fast.
////            getWheel(i).getSuspension().setDampness(sp.damping);
////        }
////
////        // give each wheel a tyre.
////        getWheel(0).setTireModel(new Tyre_01());
////        getWheel(1).setTireModel(new Tyre_01());
////        getWheel(2).setTireModel(new Tyre_01());
////        getWheel(3).setTireModel(new Tyre_01());
////
////        getWheel(0).setFriction(resource.frontWheel.friction);
////        getWheel(1).setFriction(resource.frontWheel.friction);
////        getWheel(2).setFriction(resource.backWheel.friction);
////        getWheel(3).setFriction(resource.backWheel.friction);
////
////        getWheel(0).setAccelerationForce(resource.frontWheel.accelerationForce);
////        getWheel(1).setAccelerationForce(resource.frontWheel.accelerationForce);
////        getWheel(2).setAccelerationForce(resource.backWheel.accelerationForce);
////        getWheel(3).setAccelerationForce(resource.backWheel.accelerationForce);
////
////        GearBox gearBox = new GearBox(resource.gearBox.gears.size());
////
////        for(int i=0;i<resource.gearBox.gears.size();++i) {
////            SceneMaxGearBox.SceneMaxGear g =resource.gearBox.gears.get(i);
////            gearBox.setGear(i,g.start,g.end);
////        }
////
////        setGearBox(gearBox);
////
////        Engine engine;
////        if(resource.engine.name.equals("Basic 600")) {
////            engine = new Engine600HP(this);
////        } else {
////            engine = new Engine450HP(this);
////        }
////        engine.setEngineAudio(assetManager, resource.engine.audio);
////        setEngine(engine);
////
////        setHornAudio(assetManager, resource.horn);
////
////        build();
//
//    }
//
//    public void reset() {
//
//        float[] angles = new float[3];
//        this.getVehicleControl().getPhysicsRotation().toAngles(angles);
//
//        Quaternion newRotation = new Quaternion().fromAngles(0, angles[1], 0);
//        this.getVehicleControl().setPhysicsRotation(newRotation);
//
//        this.getVehicleControl().setAngularVelocity(new Vector3f());
//        this.getVehicleControl().setLinearVelocity(new Vector3f());
//
//    }
//
//
//    @Override
//    public Vector3f dashCamOffset() {
//        return null;
//    }
//
//    @Override
//    protected Vector3f targetOffset() {
//        return null;
//    }
//
//    public Vector3f getLocation() {
//        return this.getNode().getLocalTranslation();
//    }
//
//    public void setLocation(Vector3f loc) {
//        this.getVehicleControl().setPhysicsLocation(loc);
//    }
//
//    public void setRotation(Quaternion rotation) {
//        this.getVehicleControl().setPhysicsRotation(rotation);
//    }
//
//
//}
