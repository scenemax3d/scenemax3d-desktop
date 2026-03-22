//package com.scenemaxeng.projector;
//
//import com.jayfella.jme.vehicle.BasicVehicleInputState;
//import com.jayfella.jme.vehicle.Vehicle;
//import com.jme3.app.Application;
//import com.jme3.input.KeyInput;
//import com.simsilica.lemur.GuiGlobals;
//import com.simsilica.lemur.input.InputState;
//
//import java.util.HashMap;
//
//public class SceneMaxVehicleInputState extends BasicVehicleInputState {
//
//    private final HashMap<String, Integer> inputSource;
//    private boolean hasStateListener = false;
//
//
//    public SceneMaxVehicleInputState(Vehicle vehicle, HashMap<String, Integer> inputSource) {
//        super(vehicle);
//        this.inputSource=inputSource;
//
//    }
//
//    private int getControlKey(String funcName, int defaultKey) {
//        if(inputSource!=null && inputSource.containsKey(funcName)) {
//            return inputSource.get(funcName);
//        }
//
//        return defaultKey;
//    }
//
//    @Override
//    protected void initialize(Application app) {
//
//        inputMapper = GuiGlobals.getInstance().getInputMapper();
//
//        inputMapper.map( F_START_ENGINE, getControlKey("start", KeyInput.KEY_Y) );
//        inputMapper.map( F_MOVE, getControlKey("move", KeyInput.KEY_W) );
//        inputMapper.map( F_MOVE, InputState.Negative, getControlKey("stop", KeyInput.KEY_S) );
//        inputMapper.map( F_TURN, getControlKey("left", KeyInput.KEY_A) );
//        inputMapper.map( F_TURN, InputState.Negative, getControlKey("right", KeyInput.KEY_D) );
//        inputMapper.map( F_REVERSE, getControlKey("reverse", KeyInput.KEY_E)  );
//        inputMapper.map( F_HANDBRAKE, getControlKey("handbrake", KeyInput.KEY_Q)  );
//        inputMapper.map( F_RESET, getControlKey("reset", KeyInput.KEY_R)  );
//        inputMapper.map( F_CAMVIEW, KeyInput.KEY_F5);
//        inputMapper.map( F_HORN, getControlKey("horn", KeyInput.KEY_H) );
//
//        addStateListener();
//
//    }
//
//    public void addStateListener() {
//        if(!this.hasStateListener) {
//            inputMapper.addStateListener(this,
//                    F_START_ENGINE, F_MOVE, F_TURN, F_REVERSE, F_HANDBRAKE, F_RESET, //F_LEAVE,
//                    F_HORN,
//                    F_CAMVIEW);
//
//            this.hasStateListener = true;
//        }
//    }
//
//    public void removeStateListener() {
//        if(this.hasStateListener) {
//            inputMapper.removeStateListener(this, F_START_ENGINE, F_MOVE, F_TURN, F_REVERSE, F_HANDBRAKE, F_RESET, //F_LEAVE,
//                    F_HORN,
//                    F_CAMVIEW);
//            this.hasStateListener = false;
//        }
//    }
//
//}
