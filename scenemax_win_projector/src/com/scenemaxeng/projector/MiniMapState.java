package com.scenemaxeng.projector;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;

public class MiniMapState extends BaseAppState {

    private final int minimapSize;
    private final float height;

    private Camera mapCam;
    private ViewPort mapViewport;
    private Geometry minimap;

    private Node mapRoot;
    private Node guiNode;
    private Spatial followNode;

    /**
     * Creates a new MiniMap and displays the scene specified.
     * @param mapRoot   The scene to display in the minimap, for example the rootNode of your game.
     * @param height    The height the minimap will display from. Generally slightly higher than your maximum world
     *                  height.
     */
    public MiniMapState(Node mapRoot, float height, int size, Spatial followNode) {
        this.mapRoot = mapRoot;
        this.height = height;
        this.minimapSize = size;
        this.followNode = followNode;
    }

    public Node getMapRoot() {
        return mapRoot;
    }

    public ViewPort getViewport() {
        return mapViewport;
    }

    @Override
    protected void initialize(Application app) {

        mapCam = new Camera(minimapSize, minimapSize);

        mapViewport = app.getRenderManager().createMainView("Offscreen View", mapCam);
        mapViewport.setClearFlags(true, true, true);
        mapViewport.setBackgroundColor(ColorRGBA.DarkGray);

        FrameBuffer offBuffer = new FrameBuffer(minimapSize, minimapSize, 1);

        mapCam.setFrustumPerspective(45, 1f, 1f, 300);
        mapCam.setParallelProjection(true);
        setMapHeight(mapCam, height);
        mapCam.setLocation(new Vector3f(0, height, 0));
        mapCam.lookAt(new Vector3f(0, -1, 0), Vector3f.UNIT_Y);

        Texture2D offTex = new Texture2D(minimapSize, minimapSize, Image.Format.RGBA8);
        offTex.setMinFilter(Texture.MinFilter.Trilinear);
        offTex.setMagFilter(Texture.MagFilter.Bilinear);

        offBuffer.setDepthBuffer(Image.Format.Depth);
        offBuffer.setColorTexture(offTex);

        mapViewport.setOutputFrameBuffer(offBuffer);

        mapViewport.attachScene(mapRoot);

        minimap = new Geometry("MiniMap", new Quad(minimapSize, minimapSize));

        minimap.setMaterial(new Material(app.getAssetManager(), "MatDefs/MiniMap/MiniMap.j3md"));
        minimap.getMaterial().setTexture("ColorMap", offTex);
        minimap.getMaterial().setTexture("Mask", app.getAssetManager().loadTexture("Textures/MiniMap/circle-mask.png"));
        minimap.getMaterial().setTexture("Overlay", app.getAssetManager().loadTexture("Textures/MiniMap/circle-overlay.png"));

        minimap.setLocalTranslation(
                app.getCamera().getWidth() - minimapSize - 20,
                app.getCamera().getHeight() - minimapSize - 20,
                1
        );

        guiNode = ((SimpleApplication)app).getGuiNode();
        guiNode.attachChild(minimap);
    }


    @Override
    protected void cleanup(Application app) {
        getApplication().getRenderManager().removeMainView(mapViewport);
    }

    @Override
    protected void onEnable() {
        guiNode.attachChild(minimap);
    }

    @Override
    protected void onDisable() {
        minimap.removeFromParent();
    }

    private final Quaternion mapRot = new Quaternion();
    private final float[] angles = new float[3];

    @Override
    public void update(float tpf) {

        getApplication().getCamera().getRotation().toAngles(angles);

        mapRot.fromAngles(FastMath.HALF_PI, angles[1], 0);

        mapCam.setRotation(mapRot);

        Vector3f pos = this.followNode!=null?this.followNode.getWorldTranslation():getApplication().getCamera().getLocation();

        mapCam.setLocation(new Vector3f(
                pos.x,
                height,
                pos.z
        ));

    }

    private void setMapHeight(Camera camera, float factor) {
        float bottom = camera.getFrustumBottom();
        camera.setFrustumBottom(bottom * factor);
        float left = camera.getFrustumLeft();
        camera.setFrustumLeft(left * factor);
        float right = camera.getFrustumRight();
        camera.setFrustumRight(right * factor);
        float top = camera.getFrustumTop();
        camera.setFrustumTop(top * factor);
    }

}