package com.scenemaxeng.common.types;

import com.jme3.audio.AudioData;
import com.jme3.math.Vector3f;
import com.scenemaxeng.common.motion.ThrowMotionDefinition;
import com.scenemaxeng.common.weapons.WeaponDefinition;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AssetsMapping {

    private HashMap<String, ResourceFont> _fontNodesRes = new HashMap<>();
    private HashMap<String, ResourceAudio> _audioNodesRes = new HashMap<>();
    private HashMap<String, ResourceSetup> _resources = new HashMap<>();
    private HashMap<String, ResourceSetup2D> _resources2D = new HashMap<>();
    private HashMap<String, ResourceShader> _shaders = new HashMap<>();
    private HashMap<String, ResourceMaterialAsset> _materials = new HashMap<>();
    private HashMap<String,TerrainResource> _terrains=new HashMap<>();
    private HashMap<String,SkyBoxResource> _skyboxes=new HashMap<>();
    private HashMap<String, ResourceCinematicRig> _cinematics = new HashMap<>();
    private HashMap<String, ResourceAnimation> _animations = new HashMap<>();
    private HashMap<String, WeaponDefinition> _weapons = new HashMap<>();
    private HashMap<String, ThrowMotionDefinition> _throwMotions = new HashMap<>();
    private LinkedHashSet<File> _weaponRoots = new LinkedHashSet<>();
    private LinkedHashSet<File> _throwMotionRoots = new LinkedHashSet<>();

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
        loadShadersRecursively(extPath, "shaders");
        loadShadersRecursively(extPath, "environment_shaders");

        res = getResourcesFolderIndex(extPath+"/material/materials-ext.json");
        loadMaterialsFromJson(res);

        res = getResourcesFolderIndex(extPath+"/animations/animations-ext.json");
        loadAnimationsFromJson(res);

        addWeaponRoot(new File(extPath, "weapons"));
        addWeaponRoot(new File(extPath, "Weapons"));
        addThrowMotionRoot(new File(extPath, "throw_motions"));
        addThrowMotionRoot(new File(extPath, "ThrowMotions"));

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

        res = getResourcesFolderIndex("./resources/material/materials.json");
        loadMaterialsFromJson(res);

        res = getResourcesFolderIndex("./resources/animations/animations.json");
        loadAnimationsFromJson(res);

        addWeaponRoot(new File("./resources/weapons"));
        addWeaponRoot(new File("./resources/Weapons"));
        addThrowMotionRoot(new File("./resources/throw_motions"));
        addThrowMotionRoot(new File("./resources/ThrowMotions"));

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
            loadMaterialsFromJson(res);
            loadCinematicsFromJson(res);
            loadAnimationsFromJson(res);
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

    private void loadShadersRecursively(String extPath, String folderName) {
        if (extPath == null || extPath.isBlank()) {
            return;
        }

        File resourcesRoot = new File(extPath);
        File shaderRoot = new File(resourcesRoot, folderName);
        if (!shaderRoot.isDirectory()) {
            return;
        }

        Collection<File> shaderFiles = org.apache.commons.io.FileUtils.listFiles(shaderRoot, new String[]{"j3md"}, true);
        if (shaderFiles.isEmpty()) {
            return;
        }

        List<File> sortedShaderFiles = new ArrayList<>(shaderFiles);
        sortedShaderFiles.sort(Comparator.comparing(File::getAbsolutePath, String.CASE_INSENSITIVE_ORDER));

        for (File shaderFile : sortedShaderFiles) {
            registerDiscoveredShader(resourcesRoot, shaderFile);
        }
    }

    private void registerDiscoveredShader(File resourcesRoot, File shaderFile) {
        if (resourcesRoot == null || shaderFile == null) {
            return;
        }

        String fileName = shaderFile.getName();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0) {
            return;
        }

        String shaderName = fileName.substring(0, dotIndex).trim();
        if (shaderName.isEmpty()) {
            return;
        }

        String relativePath = resourcesRoot.getAbsoluteFile().toURI()
                .relativize(shaderFile.getAbsoluteFile().toURI())
                .getPath();
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }

        String shaderKey = shaderName.toLowerCase();
        ResourceShader existingShader = _shaders.get(shaderKey);
        if (existingShader != null) {
            String existingPath = existingShader.path != null ? existingShader.path.replace('/', File.separatorChar) : "";
            if (!existingPath.isBlank() && new File(resourcesRoot, existingPath).isFile()) {
                return;
            }
        }

        _shaders.put(shaderKey, new ResourceShader(shaderName, relativePath.replace('\\', '/')));
    }

    private void loadMaterialsFromJson(JSONObject res) {
        if (res == null || !res.has("materials")) {
            return;
        }

        JSONArray materials = res.getJSONArray("materials");
        for (int i = 0; i < materials.length(); i++) {
            JSONObject material = materials.optJSONObject(i);
            if (material == null) {
                continue;
            }

            String name = material.optString("name", "").trim();
            String path = material.optString("path", "").trim();
            if (name.isEmpty() || path.isEmpty()) {
                continue;
            }

            _materials.put(name.toLowerCase(), new ResourceMaterialAsset(
                    name,
                    path,
                    material.optBoolean("transparent", false),
                    material.optBoolean("doubleSided", false)
            ));
        }
    }

    private void loadCinematicsFromJson(JSONObject res) {
        if (res == null) {
            return;
        }

        JSONArray cinematics = res.optJSONArray("cinematics");
        if (cinematics == null) {
            return;
        }

        for (int i = 0; i < cinematics.length(); i++) {
            JSONObject cinematic = cinematics.optJSONObject(i);
            if (cinematic == null) {
                continue;
            }

            String runtimeId = cinematic.optString("name", "").toLowerCase();
            if (runtimeId.isBlank()) {
                runtimeId = cinematic.optString("runtimeId", "").toLowerCase();
            }
            if (runtimeId.isBlank()) {
                continue;
            }

            String sourcePath = cinematic.optString("sourcePath", "");
            String jsonBuffer = cinematic.has("jsonBuffer")
                    ? cinematic.optJSONObject("jsonBuffer") != null
                        ? cinematic.getJSONObject("jsonBuffer").toString()
                        : cinematic.optString("jsonBuffer", "")
                    : "";
            String documentBuffer = cinematic.has("documentBuffer")
                    ? cinematic.optJSONObject("documentBuffer") != null
                        ? cinematic.getJSONObject("documentBuffer").toString()
                        : cinematic.optString("documentBuffer", "")
                    : "";

            if (jsonBuffer.isBlank()) {
                continue;
            }

            _cinematics.put(runtimeId, new ResourceCinematicRig(runtimeId, sourcePath, jsonBuffer, documentBuffer));
        }
    }

    private void loadAnimationsFromJson(JSONObject res) {
        if (res == null || !res.has("animations")) {
            return;
        }

        JSONArray animations = res.getJSONArray("animations");
        for (int i = 0; i < animations.length(); i++) {
            JSONObject animation = animations.optJSONObject(i);
            if (animation == null) {
                continue;
            }

            String name = animation.optString("name", "");
            String path = animation.optString("path", "");
            if (name.isBlank() || path.isBlank()) {
                continue;
            }

            String clipName = animation.optString("clipName", name);
            _animations.put(name.toLowerCase(), new ResourceAnimation(name, path, clipName));
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

    public HashMap<String, ResourceMaterialAsset> getMaterialsIndex() {
        return _materials;
    }

    public HashMap<String, ResourceCinematicRig> getCinematicsIndex() {
        return _cinematics;
    }

    public HashMap<String, ResourceAnimation> getAnimationsIndex() {
        return _animations;
    }

    public HashMap<String, WeaponDefinition> getWeaponsIndex() {
        return _weapons;
    }

    public HashMap<String, ThrowMotionDefinition> getThrowMotionsIndex() {
        return _throwMotions;
    }

    public ThrowMotionDefinition getThrowMotionDefinition(String motionNameOrId) {
        if (motionNameOrId == null || motionNameOrId.trim().isEmpty()) {
            return null;
        }
        String key = motionNameOrId.trim().toLowerCase(Locale.ROOT);
        ThrowMotionDefinition cached = _throwMotions.get(key);
        if (cached != null) {
            return cached;
        }

        ThrowMotionDefinition direct = loadThrowMotionByFileName(key);
        if (direct != null) {
            return direct;
        }

        return findAndCacheThrowMotionDefinition(key);
    }

    public WeaponDefinition getWeaponDefinition(String weaponNameOrId) {
        if (weaponNameOrId == null || weaponNameOrId.trim().isEmpty()) {
            return null;
        }
        String key = weaponNameOrId.trim().toLowerCase(Locale.ROOT);
        WeaponDefinition cached = _weapons.get(key);
        if (cached != null) {
            return cached;
        }

        WeaponDefinition direct = loadWeaponByFileName(key);
        if (direct != null) {
            return direct;
        }

        WeaponDefinition classpath = loadWeaponFromClasspath(buildWeaponCandidateNames(key));
        if (classpath != null) {
            return classpath;
        }

        return findAndCacheWeaponDefinition(key);
    }

    public void loadWeaponsFromProject(String projectRootPath) {
        if (projectRootPath == null || projectRootPath.isBlank()) {
            return;
        }
        File projectRoot = new File(projectRootPath);
        if (!projectRoot.exists()) {
            return;
        }
        for (File folder : collectProjectWeaponFolders(projectRoot)) {
            addWeaponRoot(folder);
        }
        for (File folder : collectProjectThrowMotionFolders(projectRoot)) {
            addThrowMotionRoot(folder);
        }
    }

    private List<File> collectProjectWeaponFolders(File projectRoot) {
        LinkedHashSet<File> folders = new LinkedHashSet<>();
        addExistingFolder(folders, new File(projectRoot, "resources/weapons"));
        addExistingFolder(folders, new File(projectRoot, "resources/Weapons"));
        addExistingFolder(folders, new File(projectRoot, "weapons"));
        addExistingFolder(folders, new File(projectRoot, "Weapons"));
        return new ArrayList<>(folders);
    }

    private List<File> collectProjectThrowMotionFolders(File projectRoot) {
        LinkedHashSet<File> folders = new LinkedHashSet<>();
        addExistingFolder(folders, new File(projectRoot, "resources/throw_motions"));
        addExistingFolder(folders, new File(projectRoot, "resources/ThrowMotions"));
        return new ArrayList<>(folders);
    }

    private void addExistingFolder(Set<File> folders, File folder) {
        if (folder == null || !folder.isDirectory()) {
            return;
        }
        try {
            folders.add(folder.getCanonicalFile());
        } catch (IOException ignored) {
            folders.add(folder.getAbsoluteFile());
        }
    }

    private void addWeaponRoot(File folder) {
        addExistingFolder(_weaponRoots, folder);
    }

    private void addThrowMotionRoot(File folder) {
        addExistingFolder(_throwMotionRoots, folder);
    }

    private WeaponDefinition loadWeaponByFileName(String key) {
        List<String> candidateNames = buildWeaponCandidateNames(key);
        for (File root : _weaponRoots) {
            for (String name : candidateNames) {
                File file = new File(root, name + WeaponDefinition.FILE_EXTENSION);
                if (file.isFile()) {
                    WeaponDefinition definition = loadAndCacheWeaponDefinition(file);
                    if (definition != null) {
                        return definition;
                    }
                }
            }
        }
        return null;
    }

    private List<String> buildWeaponCandidateNames(String key) {
        List<String> candidateNames = new ArrayList<>();
        String candidate = key;
        while (candidate != null && !candidate.isBlank()) {
            if (!candidateNames.contains(candidate)) {
                candidateNames.add(candidate);
            }
            if (!candidate.startsWith("weapon_") || candidate.length() <= "weapon_".length()) {
                break;
            }
            candidate = candidate.substring("weapon_".length());
        }
        return candidateNames;
    }

    private WeaponDefinition loadWeaponFromClasspath(List<String> candidateNames) {
        for (String name : candidateNames) {
            WeaponDefinition definition = loadWeaponFromClasspath("resources/weapons/"
                    + name + WeaponDefinition.FILE_EXTENSION, name);
            if (definition != null) {
                return definition;
            }
            definition = loadWeaponFromClasspath("resources/Weapons/"
                    + name + WeaponDefinition.FILE_EXTENSION, name);
            if (definition != null) {
                return definition;
            }
        }
        return null;
    }

    private WeaponDefinition loadWeaponFromClasspath(String resourcePath, String alias) {
        try (InputStream in = AssetsMapping.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            WeaponDefinition definition = WeaponDefinition.fromJSON(
                    new JSONObject(new String(Util.toByteArray(in), StandardCharsets.UTF_8)));
            cacheWeaponDefinition(alias, definition);
            return definition;
        } catch (Exception ignored) {
            return null;
        }
    }

    private WeaponDefinition findAndCacheWeaponDefinition(String key) {
        for (File root : _weaponRoots) {
            Collection<File> files = org.apache.commons.io.FileUtils.listFiles(root, new String[]{"smweapon"}, true);
            for (File file : files) {
                String fileKey = stripExtension(file.getName()).toLowerCase(Locale.ROOT);
                if (key.equals(fileKey)) {
                    WeaponDefinition definition = loadAndCacheWeaponDefinition(file);
                    if (definition != null) {
                        return definition;
                    }
                }
                try {
                    WeaponDefinition definition = WeaponDefinition.load(file);
                    cacheWeaponDefinition(file, definition);
                    if (definition.getId() != null && key.equals(definition.getId().trim().toLowerCase(Locale.ROOT))) {
                        return definition;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private WeaponDefinition loadAndCacheWeaponDefinition(File file) {
        try {
            WeaponDefinition definition = WeaponDefinition.load(file);
            cacheWeaponDefinition(file, definition);
            return definition;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void cacheWeaponDefinition(File file, WeaponDefinition definition) {
        if (definition == null || file == null) {
            return;
        }
        if (definition.getId() != null && !definition.getId().isBlank()) {
            _weapons.put(definition.getId().trim().toLowerCase(Locale.ROOT), definition);
        }
        _weapons.put(stripExtension(file.getName()).toLowerCase(Locale.ROOT), definition);
    }

    private void cacheWeaponDefinition(String alias, WeaponDefinition definition) {
        if (definition == null) {
            return;
        }
        if (definition.getId() != null && !definition.getId().isBlank()) {
            _weapons.put(definition.getId().trim().toLowerCase(Locale.ROOT), definition);
        }
        if (alias != null && !alias.isBlank()) {
            _weapons.put(alias.trim().toLowerCase(Locale.ROOT), definition);
        }
    }

    private ThrowMotionDefinition loadThrowMotionByFileName(String key) {
        List<String> candidateNames = new ArrayList<>();
        candidateNames.add(key);
        if (key.startsWith("motion_") && key.length() > "motion_".length()) {
            candidateNames.add(key.substring("motion_".length()));
        }
        for (File root : _throwMotionRoots) {
            for (String name : candidateNames) {
                File file = new File(root, name + ThrowMotionDefinition.FILE_EXTENSION);
                if (file.isFile()) {
                    ThrowMotionDefinition definition = loadAndCacheThrowMotionDefinition(file);
                    if (definition != null) {
                        return definition;
                    }
                }
            }
            ThrowMotionDefinition nested = loadThrowMotionByFileNameRecursive(root, candidateNames);
            if (nested != null) {
                return nested;
            }
            ThrowMotionDefinition matchingId = findAndCacheThrowMotionDefinitionInRoot(root, key);
            if (matchingId != null) {
                return matchingId;
            }
        }
        return loadThrowMotionFromClasspath(candidateNames);
    }

    private ThrowMotionDefinition loadThrowMotionFromClasspath(List<String> candidateNames) {
        for (String name : candidateNames) {
            ThrowMotionDefinition definition = loadThrowMotionFromClasspath("resources/throw_motions/"
                    + name + ThrowMotionDefinition.FILE_EXTENSION, name);
            if (definition != null) {
                return definition;
            }
            definition = loadThrowMotionFromClasspath("resources/ThrowMotions/"
                    + name + ThrowMotionDefinition.FILE_EXTENSION, name);
            if (definition != null) {
                return definition;
            }
        }
        return null;
    }

    private ThrowMotionDefinition loadThrowMotionFromClasspath(String resourcePath, String alias) {
        try (InputStream in = AssetsMapping.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            ThrowMotionDefinition definition = ThrowMotionDefinition.fromJSON(
                    new JSONObject(new String(Util.toByteArray(in), StandardCharsets.UTF_8)));
            cacheThrowMotionDefinition(alias, definition);
            return definition;
        } catch (Exception ignored) {
            return null;
        }
    }

    private ThrowMotionDefinition loadThrowMotionByFileNameRecursive(File root, List<String> candidateNames) {
        if (root == null || !root.isDirectory()) {
            return null;
        }
        Collection<File> files = org.apache.commons.io.FileUtils.listFiles(root, new String[]{"smmotion"}, true);
        for (String name : candidateNames) {
            for (File file : files) {
                if (name.equalsIgnoreCase(stripExtension(file.getName()))) {
                    ThrowMotionDefinition definition = loadAndCacheThrowMotionDefinition(file);
                    if (definition != null) {
                        return definition;
                    }
                }
            }
        }
        return null;
    }

    private ThrowMotionDefinition findAndCacheThrowMotionDefinition(String key) {
        for (File root : _throwMotionRoots) {
            ThrowMotionDefinition definition = findAndCacheThrowMotionDefinitionInRoot(root, key);
            if (definition != null) {
                return definition;
            }
        }
        return null;
    }

    private ThrowMotionDefinition findAndCacheThrowMotionDefinitionInRoot(File root, String key) {
        if (root == null || !root.isDirectory()) {
            return null;
        }
        Collection<File> files = org.apache.commons.io.FileUtils.listFiles(root, new String[]{"smmotion"}, true);
        for (File file : files) {
            String fileKey = stripExtension(file.getName()).toLowerCase(Locale.ROOT);
            if (key.equals(fileKey)) {
                ThrowMotionDefinition definition = loadAndCacheThrowMotionDefinition(file);
                if (definition != null) {
                    return definition;
                }
            }
            try {
                ThrowMotionDefinition definition = ThrowMotionDefinition.load(file);
                cacheThrowMotionDefinition(file, definition);
                if (definition.getId() != null && key.equals(definition.getId().trim().toLowerCase(Locale.ROOT))) {
                    return definition;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private ThrowMotionDefinition loadAndCacheThrowMotionDefinition(File file) {
        try {
            ThrowMotionDefinition definition = ThrowMotionDefinition.load(file);
            cacheThrowMotionDefinition(file, definition);
            return definition;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void cacheThrowMotionDefinition(File file, ThrowMotionDefinition definition) {
        if (definition == null || file == null) {
            return;
        }
        if (definition.getId() != null && !definition.getId().isBlank()) {
            _throwMotions.put(definition.getId().trim().toLowerCase(Locale.ROOT), definition);
        }
        _throwMotions.put(stripExtension(file.getName()).toLowerCase(Locale.ROOT), definition);
    }

    private void cacheThrowMotionDefinition(String alias, ThrowMotionDefinition definition) {
        if (definition == null) {
            return;
        }
        if (definition.getId() != null && !definition.getId().isBlank()) {
            _throwMotions.put(definition.getId().trim().toLowerCase(Locale.ROOT), definition);
        }
        if (alias != null && !alias.isBlank()) {
            _throwMotions.put(alias.trim().toLowerCase(Locale.ROOT), definition);
        }
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
                    _cinematics.put(runtimeId, new ResourceCinematicRig(runtimeId, relativePath, entity.toString(), rootDocumentBuffer(designerFile)));
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

    private String rootDocumentBuffer(File designerFile) {
        return designerFile == null ? null : Util.readFile(designerFile);
    }

    private static String stripExtension(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

}
