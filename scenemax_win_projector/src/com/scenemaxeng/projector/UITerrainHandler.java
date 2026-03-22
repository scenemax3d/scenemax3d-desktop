package com.scenemaxeng.projector;

import com.scenemaxeng.common.types.*;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.material.Material;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.terrain.geomipmap.TerrainLodControl;
import com.jme3.terrain.heightmap.AbstractHeightMap;
import com.jme3.terrain.geomipmap.TerrainQuad;
import com.jme3.terrain.heightmap.ImageBasedHeightMap;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture.WrapMode;


public class UITerrainHandler {

    private TerrainQuad terrain;
    private Material mat_terrain;
    public AssetManager assetManager;
    public Node rootNode;
    public Camera camera;


    public void init(TerrainResource terrainRes, BulletAppState bulletAppState) {


        mat_terrain = new Material(assetManager,
                "Common/MatDefs/Terrain/Terrain.j3md");

        /** 1.1) Add ALPHA map (for red-blue-green coded splat textures) */
        mat_terrain.setTexture("Alpha", assetManager.loadTexture(terrainRes.alphaMap));

        /** 1.2) Add GRASS texture into the red layer (Tex1). */
        Texture grass = assetManager.loadTexture(terrainRes.redTex);
        grass.setWrap(WrapMode.Repeat);
        mat_terrain.setTexture("Tex1", grass);
        mat_terrain.setFloat("Tex1Scale", 64f);

        /** 1.3) Add DIRT texture into the green layer (Tex2) */
        Texture dirt = assetManager.loadTexture(terrainRes.greenTex);
        dirt.setWrap(WrapMode.Repeat);
        mat_terrain.setTexture("Tex2", dirt);
        mat_terrain.setFloat("Tex2Scale", 32f);

        /** 1.4) Add ROAD texture into the blue layer (Tex3) */
        Texture rock = assetManager.loadTexture(terrainRes.blueTex);
        rock.setWrap(WrapMode.Repeat);
        mat_terrain.setTexture("Tex3", rock);
        mat_terrain.setFloat("Tex3Scale", 128f);

        /** 2. Create the height map */
        AbstractHeightMap heightmap = null;
        Texture heightMapImage = assetManager.loadTexture(terrainRes.heightMap);
        heightmap = new ImageBasedHeightMap(heightMapImage.getImage());
        heightmap.load();

        /** 3. We have prepared material and heightmap.
         * Now we create the actual terrain:
         * 3.1) Create a TerrainQuad and name it "my terrain".
         * 3.2) A good value for terrain tiles is 64x64 -- so we supply 64+1=65.
         * 3.3) We prepared a heightmap of size 512x512 -- so we supply 512+1=513.
         * 3.4) As LOD step scale we supply Vector3f(1,1,1).
         * 3.5) We supply the prepared heightmap itself.
         */
        int patchSize = 65;
        terrain = new TerrainQuad(terrainRes.name, patchSize, 513, heightmap.getHeightMap());

        /** 4. We give the terrain its material, position & scale it, and attach it. */
        terrain.setMaterial(mat_terrain);

        float posX=0,posY=0,posZ=0,scaleX=1,scaleY=1,scaleZ=1;
        posX=terrainRes.pos.getFloat("x");
        posY=terrainRes.pos.getFloat("y");
        posZ=terrainRes.pos.getFloat("z");

        scaleX=terrainRes.scale.getFloat("x");
        scaleY=terrainRes.scale.getFloat("y");
        scaleZ=terrainRes.scale.getFloat("z");

        terrain.setLocalTranslation(posX, posY, posZ);//0,-50,350
        terrain.setLocalScale(scaleX, scaleY, scaleZ);
        rootNode.attachChild(terrain);

        /** 5. The LOD (level of detail) depends on were the camera is: */
        TerrainLodControl control = new TerrainLodControl(terrain, this.camera);
        terrain.addControl(control);



        terrain.addControl(new RigidBodyControl(0));
        RigidBodyControl ctl = terrain.getControl(RigidBodyControl.class);
        ctl.setCollisionGroup(PhysicsCollisionObject.COLLISION_GROUP_08);
        ctl.setFriction(0.9f);// default high friction for terrain

        bulletAppState.getPhysicsSpace().add(terrain);



    }

}
