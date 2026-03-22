package com.scenemaxeng.projector;

import jme3utilities.DecalManager;
import com.jayfella.jme.vehicle.VehicleWorld;
import com.jayfella.jme.vehicle.World;
import com.jme3.app.Application;
import com.jme3.app.state.AppStateManager;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

import java.util.logging.Logger;

public class DefaultVehicleWorld implements VehicleWorld {

    final private DecalManager decalManager = new DecalManager();
    private final Node parentNode;
    private Application application;

    public DefaultVehicleWorld(Application app, Node parentNode) {
        this.application=app;
        this.parentNode=parentNode;
    }

    @Override
    public float dropYRotation() {
        return 0;
    }

    @Override
    public AssetManager getAssetManager() {
        return application.getAssetManager();
    }

    @Override
    public DecalManager getDecalManager() {
        return decalManager;
    }

    @Override
    public Node getParentNode() {
        return this.parentNode;
    }

    @Override
    public PhysicsSpace getPhysicsSpace() {
        BulletAppState bulletAppState
                = getStateManager().getState(BulletAppState.class);
        if (bulletAppState == null) {
            throw new IllegalStateException("BulletAppState not found.");
        }
        PhysicsSpace result = bulletAppState.getPhysicsSpace();

        assert result != null;
        return result;
    }

    @Override
    public AppStateManager getStateManager() {
        AppStateManager result = application.getStateManager();
        assert result != null;
        return result;
    }

    /**
     * Locate the drop point, which lies directly above the preferred initial
     * location for vehicles.
     *
     * @param storeResult storage for the result (not null)
     */
    @Override
    public void locateDrop(Vector3f storeResult) {

    }


}
