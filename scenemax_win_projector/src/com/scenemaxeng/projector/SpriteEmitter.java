package com.scenemaxeng.projector;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.texture.Texture;

public class SpriteEmitter implements ISceneMax3dObjectWrapper {

    private final Node node;
    private final Geometry _geom;
    public EntityInstBase entityInst;
    String _name = "";
    private int _startFrame=0;
    private int _endFrame=2;
    private float _time=0;
    private float _targetTime=1;
    private transient Vector3f temp = new Vector3f();;
    private Material mat = null;
    private Node lastParent;

    public SpriteEmitter(String name, int rows, int cols, float width, float height, AssetManager assetManager, Texture texture) {

        _name=name;

        this.setStartFrame(0);
        this.setEndFrame(0);

        Box b = new Box(width, height, 0.01f); // create cube shape
        Geometry geom = new Geometry("Box", b);  // create cube geometry from the shape
        mat = new Material(assetManager, "MatDefs/sprite_sheet.j3md");
        mat.setTexture("ColorMap", texture);
        mat.setFloat("SizeX", cols);
        mat.setFloat("SizeY", rows);
        mat.setFloat("Position", 0f);
        mat.getAdditionalRenderState().setDepthWrite(false);
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.AlphaAdditive);
        geom.setQueueBucket(RenderQueue.Bucket.Transparent);
        geom.setMaterial(mat);
        _geom = geom;
        final Node parentNode = new Node();
        parentNode.attachChild(geom); // add it to the wrapper
        this.node = parentNode;
    }

    public void setStartFrame(int start) {
        _startFrame=start;
    }

    public void setEndFrame(int end) {
        _endFrame=end;
    }

    public void setFrame(float frame) {
        mat.setFloat("Position", frame);
    }

    public Spatial getSpatial() {
        return this.node;
    }

    public void hide() {
        this.lastParent = this.node.getParent();
        this.node.removeFromParent();
    }

    public void show(Node defaultParent) {
        if (this.lastParent!=null) {
            this.lastParent.attachChild(this.node);
        } else {
            defaultParent.attachChild(this.node);
        }

    }

    public void attachTo(Node parent) {
        parent.attachChild(this.node);
        this.lastParent = parent;
    }
}
