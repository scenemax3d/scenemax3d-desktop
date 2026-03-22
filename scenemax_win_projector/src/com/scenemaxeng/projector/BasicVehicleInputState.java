package com.scenemaxeng.projector;

import com.jayfella.jme.vehicle.part.GearBox;
import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import jme3utilities.SignalTracker;

import java.util.HashMap;

public class BasicVehicleInputState extends BaseAppState {

    private final SceneMax3DGenericVehicle vehicle;
    private SignalTracker signalTracker=new SignalTracker();
    private Application app;

    private final String forwardSignalName = "forward";
    private final String leftSignalName = "left";
    private final String rightSignalName = "right";
    private final String reverseSignalName = "reverse";
    private final String resetSignalName = "reset";
    private final String hornSignalName = "horn";
    private final String breakSignalName = "break";

    //boolean turningLeft, turningRight;

    private float maxSteerForce = 1.0f;
    private float steeringForce = 0;
    private float turnSpeed = 0.5f;

    ActionListener listener;
    private HashMap<String, Integer> inputSource=new HashMap<>();

    public BasicVehicleInputState(SceneMax3DGenericVehicle vehicle) {
        this.vehicle = vehicle;
        vehicle.inputState = this;
    }

    @Override
    public void update(float tpf) {
        float strength;
        if (signalTracker.test(forwardSignalName)) {
            GearBox gearBox =vehicle.getGearBox();
            gearBox.setReversing(false);
            strength = 1f;
        } else if(signalTracker.test(reverseSignalName)) {
            GearBox gearBox =vehicle.getGearBox();
            gearBox.setReversing(true);
            strength=1f;
        } else {
            strength = 0f;
        }

        if(signalTracker.test(breakSignalName)) {
            vehicle.setBrakeSignals(1.0f,0);
        }

        if(signalTracker.test(resetSignalName)) {
            vehicle.reset();
        } else if(signalTracker.test(hornSignalName)) {

        }

        vehicle.setAccelerateSignal(strength);
        updateTurn(tpf);

    }

    private void updateTurn(float tpf) {

        if (signalTracker.test(leftSignalName)) {
            steeringForce = Math.min(steeringForce + (tpf * turnSpeed), maxSteerForce);

        }
        else if (signalTracker.test(rightSignalName)) {
            steeringForce = Math.max(steeringForce - (tpf * turnSpeed), -maxSteerForce);

        }
        else {
            steeringForce = 0;
        }

        vehicle.steer(steeringForce);
    }


    @Override
    protected void initialize(Application application) {

        this.app=application;

        inputSource.put(forwardSignalName,KeyInput.KEY_W);
        inputSource.put(leftSignalName,KeyInput.KEY_A);
        inputSource.put(rightSignalName,KeyInput.KEY_D);
        inputSource.put(breakSignalName,KeyInput.KEY_S);
        inputSource.put(reverseSignalName,KeyInput.KEY_E);
        inputSource.put(resetSignalName,KeyInput.KEY_R);
        inputSource.put(hornSignalName,KeyInput.KEY_H);

        setInputSource(inputSource);

    }

    /**
     * Add an input mapping that causes the SignalTracker to track the specified
     * key.
     *
     * @param key the key to be tracked
     * @param signalName name for the input signal (not null)
     */
    private void mapKeyToSignal(int key, String signalName) {
        signalTracker.add(signalName);

        KeyTrigger trigger = new KeyTrigger(key);
        app.getInputManager().addMapping(signalName, trigger);
    }

    @Override
    protected void cleanup(Application application) {

    }

    @Override
    protected void onEnable() {

    }

    @Override
    protected void onDisable() {

    }


    public void addStateListener() {

        signalTracker=new SignalTracker();

        listener = (action, keyPressed, tpf) -> {
            signalTracker.setActive(action, 0, keyPressed);
        };

        String[] srcKeys = new String[inputSource.size()];
        inputSource.keySet().toArray(srcKeys);
        app.getInputManager().addListener(listener, srcKeys);

    }

    public void removeStateListener() {
        if(listener!=null) {
            app.getInputManager().removeListener(listener);
            listener=null;
        }
    }

    public void setInputSource(HashMap<String, Integer> src) {

        src.forEach( (key,val)->{
            inputSource.put(key,val);
        });

        for(String key:inputSource.keySet()) {
            app.getInputManager().deleteMapping(key);
            mapKeyToSignal(inputSource.get(key),key);
        }

        removeStateListener();
        addStateListener();

    }
}
