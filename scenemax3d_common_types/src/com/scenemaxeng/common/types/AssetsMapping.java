package com.scenemaxeng.common.types;

import com.jme3.audio.AudioData;
import com.jme3.math.Vector3f;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;

public class AssetsMapping {

    private HashMap<String, ResourceFont> _fontNodesRes = new HashMap<>();
    private HashMap<String, ResourceAudio> _audioNodesRes = new HashMap<>();
    private HashMap<String, ResourceSetup> _resources = new HashMap<>();
    private HashMap<String, ResourceSetup2D> _resources2D = new HashMap<>();
    private HashMap<String, ResourceShader> _shaders = new HashMap<>();
    private HashMap<String,TerrainResource> _terrains=new HashMap<>();
    private HashMap<String,SkyBoxResource> _skyboxes=new HashMap<>();
    private HashMap<String, ResourceCinematicRig> _cinematics = new HashMap<>();

    private JSONObject getResourcesIndex() {
        String json = "";
        InputStream script = AssetsMapping.class.getClassLoader().getResourceAsStream("resources.json");
        if(script==null) {
            return null;
        }

        try {
            json = new String(Util.toByteArray(script));

        } catch (IOException e) {
            e.printStackTrace();
        }

        return new JSONObject(json);

    }

    public AssetsMapping(String extPath) {
        this();
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(AssetsMapping.class.getName());
        logger.info("loading assets from: "+extPath);
        JSONObject res = getResourcesFolderIndex(extPath+"/models/models-ext.json");
        loadModelsFromJson(res);

        res = getResourcesFolderIndex(extPath+"/sprites/sprites-ext.json");
        loadSpritesFromJson(res);

        res = getResourcesFolderIndex(extPath+"/audio/audio-ext.json");
        loadAudioFromJson(res);

        res = getResourcesFolderIndex(extPath+"/fonts/fonts-ext.json");
        loadFontsFromJson(res);

        res = getResourcesFolderIndex(extPath+"/skyboxes/skyboxes-ext.json");
        loadSkyBoxesFromJson(res);

        res = getResourcesFolderIndex(extPath+"/shaders/shaders-ext.json");
        loadShadersFromJson(res);

        res = getResourcesFolderIndex(extPath+"/environment_shaders/environment-shaders-ext.json");
        loadShadersFromJson(res);

    }

    public AssetsMapping() {

        ///////////// LOAD MODELS ///////////
        JSONObject res = getResourcesFolderIndex( "./resources/models/models.json");
        loadModelsFromJson(res);

        ///////////// LOAD SPRITES //////////
        res = getResourcesFolderIndex("./resources/sprites/sprites.json");
        loadSpritesFromJson(res);

        ///////////// LOAD TERRAIN //////////
        res = getResourcesFolderIndex("./resources/terrain/terrains.json");
        loadTerrainsFromJson(res);

        ///////////// LOAD SOUNDS ///////////
        res = getResourcesFolderIndex("./resources/audio/audio.json");
        loadAudioFromJson(res);

        ///////////// LOAD SOUNDS ///////////
        res = getResourcesFolderIndex("./resources/fonts/fonts.json");
        loadFontsFromJson(res);

        ///////////// LOAD SkyBoxes ///////////
        res = getResourcesFolderIndex("./resources/skyboxes/skyboxes.json");
        loadSkyBoxesFromJson(res);

        res = getResourcesFolderIndex("./resources/shaders/shaders.json");
        loadShadersFromJson(res);

        res = getResourcesFolderIndex("./resources/environment_shaders/environment-shaders.json");
        loadShadersFromJson(res);

        /////////////////////////////// READ SELF - CONTAINED ASSETS /////////////////////////////
        // self contained exec will read from embedded class-path resource file
        res = getResourcesIndex();
        if(res!=null) {
            loadSpritesFromJson(res);
            loadModelsFromJson(res);
            loadTerrainsFromJson(res);
            loadAudioFromJson(res);
            loadFontsFromJson(res);
            loadSkyBoxesFromJson(res);
            loadShadersFromJson(res);
        }
    }

    private void loadModelsFromJson(JSONObject res) {

        if(res==null || !res.has("models")) return;
        JSONArray models = res.getJSONArray("models");
        for(int i=0;i<models.length();++i) {
            JSONObject spr = models.getJSONObject(i);
            String name = spr.getString("name");
            String path=spr.getString("path");
            float scaleX = spr.getFloat("scaleX");
            float scaleY = spr.getFloat("scaleY");
            float scaleZ = spr.getFloat("scaleZ");
            float transX = spr.getFloat("transX");
            float transY = spr.getFloat("transY");
            float transZ = spr.getFloat("transZ");
            float rotateY = spr.getFloat("rotateY");

            ResourceSetup res3D = new ResourceSetup(name,path,scaleX,scaleY,scaleZ,transX,transY,transZ,rotateY);
            res3D.setJsonBuffer(spr.toString());
            if(spr.has("isStatic")) {
                res3D.isStatic = spr.getBoolean("isStatic");
            }

            if(spr.has("physics")) {
                JSONObject physics = spr.getJSONObject("physics");
                if(physics.has("character")) {
                    JSONObject character = physics.getJSONObject("character");

                    float ratio = 1.0f/scaleX;
                    res3D.calibrateX = character.getFloat("calibrateX")*ratio;
                    res3D.calibrateY = character.getFloat("calibrateY")*ratio;
                    res3D.calibrateZ = character.getFloat("calibrateZ")*ratio;
                    //res3D.calibrateRatio = 1.0f/scaleX;

                    res3D.capsuleRadius = character.getFloat("capsuleRadius");
                    res3D.capsuleHeight = character.getFloat("capsuleHeight");
                    res3D.stepHeight = character.getFloat("stepHeight");
                }

                if(physics.has("vehicle")) {
                    res3D.isVehicle=true;
                    JSONObject vehicle = physics.getJSONObject("vehicle");

                    if(vehicle.has("chassisMaterial")) {
                        res3D.chassisMaterial = vehicle.getString("chassisMaterial");
                    }

                    if(vehicle.has("localScale")) {
                        res3D.localScale = vehicle.getFloat("localScale");
                    }

                    res3D.wheelModel=vehicle.getString("wheelModel");
                    if(vehicle.has("rearWheelModel")) {
                        res3D.rearWheelModel = vehicle.getString("rearWheelModel");
                    } else {
                        res3D.rearWheelModel = res3D.wheelModel;
                    }

                    if(vehicle.has("wheelMaterial")) {
                        res3D.wheelMaterial = vehicle.getString("wheelMaterial");
                    }

                    res3D.frontWheel = loadWheel(vehicle.getJSONObject("frontWheel"));
                    res3D.backWheel = loadWheel(vehicle.getJSONObject("backWheel"));

                    res3D.gearBox = loadGearBox(vehicle.getJSONObject("gearBox"));
                    res3D.engine = loadEngine(vehicle.getJSONObject("engine"));

                    res3D.mass = vehicle.getFloat("mass");
                    res3D.horn = vehicle.getString("horn");

                }

            }

            name=name.toLowerCase();
            _resources.put(name,res3D);

        }
    }

    private SceneMaxWheel loadWheel(JSONObject data) {
        SceneMaxWheel wheel = new SceneMaxWheel();
        wheel.scale=data.getFloat("scale");

        JSONObject offset = data.getJSONObject("offset");
        wheel.offset=new Vector3f(offset.getFloat("x"),offset.getFloat("y"),offset.getFloat("z"));
        wheel.steering=data.getBoolean("steering");
        wheel.brake=data.getFloat("brake");
        wheel.friction=data.getFloat("friction");
        wheel.diameter=data.getFloat("diameter");

        JSONObject suspension = data.getJSONObject("suspension");
        wheel.suspension.stiffness=suspension.getFloat("stiffness");
        wheel.suspension.compression=suspension.getFloat("compression");
        wheel.suspension.damping=suspension.getFloat("damping");
        wheel.suspension.length=suspension.getFloat("length");
        wheel.suspension.maxForce=suspension.getFloat("maxForce");

        wheel.accelerationForce=data.getFloat("accelerationForce");

        return wheel;
    }

    private SceneMaxGearBox loadGearBox(JSONObject data) {

        SceneMaxGearBox gb = new SceneMaxGearBox();
        JSONArray gears = data.getJSONArray("gears");
        for(int i=0;i<gears.length();++i) {
            JSONObject gear = gears.getJSONObject(i);
            gb.gears.add(new SceneMaxGearBox.SceneMaxGear(gear.getFloat("start"), gear.getFloat("end")));
        }

        return gb;

    }

    private SceneMaxEngine loadEngine(JSONObject data) {
        SceneMaxEngine en = new SceneMaxEngine();
        en.name=data.getString("name");
        en.audio=data.getString("audio");
        en.power=data.getFloat("power");
        en.maxRevs=data.getFloat("maxRevs");
        en.braking=data.getFloat("braking");

        return en;
    }

    private void loadFontsFromJson(JSONObject res) {
        if(res==null || !res.has("fonts")) return;
        JSONArray fonts = res.getJSONArray("fonts");
        for(int i=0;i<fonts.length();++i) {
            JSONObject snd = fonts.getJSONObject(i);
            String fontName = snd.getString("name");
            String path=snd.getString("path");
            _fontNodesRes.put(fontName.toLowerCase(), new ResourceFont(fontName,path));//
        }

    }

    private void loadAudioFromJson(JSONObject res) {
        if(res==null || !res.has("sounds")) return;
        JSONArray sounds = res.getJSONArray("sounds");
        for(int i=0;i<sounds.length();++i) {
            JSONObject snd = sounds.getJSONObject(i);
            String soundName = snd.getString("name");
            String path=snd.getString("path");
            _audioNodesRes.put(soundName.toLowerCase(), new ResourceAudio(soundName,path, AudioData.DataType.Buffer));//
        }

    }

    private void loadSpritesFromJson(JSONObject res) {

        if(res==null || !res.has("sprites")) return;

        JSONArray sprites = res.getJSONArray("sprites");
        for(int i=0;i<sprites.length();++i) {
            JSONObject spr = sprites.getJSONObject(i);
            String spriteName = spr.getString("name");
            String path=spr.getString("path");
            int rows=spr.getInt("rows");
            int cols=spr.getInt("cols");
            _resources2D.put(spriteName.toLowerCase(), new ResourceSetup2D(spriteName,path,rows,cols));//
        }
    }

    private void loadSkyBoxesFromJson(JSONObject res) {

        if(res==null || !res.has("skyboxes")) return;

        JSONArray skyboxes = res.getJSONArray("skyboxes");
        for(int i=0;i<skyboxes.length();++i) {
            JSONObject skybox = skyboxes.getJSONObject(i);
            String name = skybox.getString("name");
            String up=skybox.getString("up");
            String down=skybox.getString("down");
            String left=skybox.getString("left");
            String right=skybox.getString("right");
            String front=skybox.getString("front");
            String back=skybox.getString("back");

            SkyBoxResource sr = new SkyBoxResource(name,up,down,left,right,front,back);
            sr.buff=skybox.toString();
            _skyboxes.put(name.toLowerCase(), sr);//
        }

    }

    private void loadShadersFromJson(JSONObject res) {
        if(res==null) return;

        JSONArray shaders = res.optJSONArray("shaders");
        if (shaders != null) {
            loadShaderArray(shaders);
        }

        JSONArray environmentShaders = res.optJSONArray("environmentShaders");
        if (environmentShaders != null) {
            loadShaderArray(environmentShaders);
        }
    }

    private void loadShaderArray(JSONArray shaders) {
        for (int i = 0; i < shaders.length(); ++i) {
            JSONObject shader = shaders.getJSONObject(i);
            String name = shader.getString("name");
            String path = shader.getString("path");
            _shaders.put(name.toLowerCase(), new ResourceShader(name, path));
        }
    }

    private void loadTerrainsFromJson(JSONObject res) {
        if(res==null || !res.has("terrains")) return;

        JSONArray terrains = res.getJSONArray("terrains");
        for(int i=0;i<terrains.length();++i) {
            JSONObject terr = terrains.getJSONObject(i);
            String name = terr.getString("name");
            String alphaMap=terr.getString("Alpha");
            String redTex=terr.getString("Red");
            String greenTex=terr.getString("Green");
            String blueTex=terr.getString("Blue");
            String heightMap=terr.getString("HeightMap");
            JSONObject pos=terr.getJSONObject("pos");
            JSONObject scale=terr.getJSONObject("scale");

            TerrainResource tr = new TerrainResource(name,alphaMap,redTex,greenTex,blueTex,heightMap,pos,scale);
            tr.buff=terr.toString();
            _terrains.put(name.toLowerCase(), tr);//
        }
    }

    public HashMap<String, SkyBoxResource> getSkyboxesIndex () { return _skyboxes; }
    public HashMap<String, TerrainResource> getTerrainsIndex () {
        return _terrains;
    }

    public HashMap<String, ResourceSetup> get3DModelsIndex () {
        return _resources;
    }

    public HashMap<String, ResourceSetup2D> getSpriteSheetsIndex () {
        return _resources2D;
    }

    public HashMap<String, ResourceAudio> getAudioIndex() {
        return _audioNodesRes;
    }

    public HashMap<String, ResourceFont> getFontsIndex() {
        return _fontNodesRes;
    }

    public HashMap<String, ResourceShader> getShadersIndex() {
        return _shaders;
    }

    public HashMap<String, ResourceCinematicRig> getCinematicsIndex() {
        return _cinematics;
    }

    public void loadCinematicsFromProject(String projectRootPath) {
        if (projectRootPath == null || projectRootPath.isBlank()) {
            return;
        }

        File projectRoot = new File(projectRootPath);
        if (!projectRoot.exists()) {
            return;
        }

        Collection<File> designerFiles = org.apache.commons.io.FileUtils.listFiles(projectRoot, new String[]{"smdesign"}, true);
        for (File designerFile : designerFiles) {
            loadCinematicsFromDesignerFile(projectRoot, designerFile);
        }
    }

    private void loadCinematicsFromDesignerFile(File projectRoot, File designerFile) {
        String raw = Util.readFile(designerFile);
        if (raw == null || raw.isBlank()) {
            return;
        }

        try {
            JSONObject root = new JSONObject(raw);
            loadCinematicsRecursive(projectRoot, designerFile, root.optJSONArray("entities"));
        } catch (Exception ignored) {
        }
    }

    private void loadCinematicsRecursive(File projectRoot, File designerFile, JSONArray entities) {
        if (entities == null) {
            return;
        }

        for (int i = 0; i < entities.length(); i++) {
            JSONObject entity = entities.optJSONObject(i);
            if (entity == null) {
                continue;
            }

            if ("CINEMATIC_RIG".equals(entity.optString("type", ""))) {
                String runtimeId = entity.optString("cinematicRuntimeId", entity.optString("id", "")).toLowerCase();
                if (!runtimeId.isBlank()) {
                    String relativePath = designerFile.getAbsolutePath();
                    if (projectRoot != null) {
                        relativePath = projectRoot.toURI().relativize(designerFile.toURI()).getPath();
                    }
                    _cinematics.put(runtimeId, new ResourceCinematicRig(runtimeId, relativePath, entity.toString()));
                }
            }

            loadCinematicsRecursive(projectRoot, designerFile, entity.optJSONArray("children"));
        }
    }

    private JSONObject getResourcesFolderIndex(String path) {
        File f = new File(path);
        if(!f.exists()) return null;

        String s = Util.readFile(f);
        if(s==null || s.length()==0) return null;
        return new JSONObject(s);
    }


}
