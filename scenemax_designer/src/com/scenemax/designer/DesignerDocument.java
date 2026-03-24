package com.scenemax.designer;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles loading and saving of .smdesign files (JSON format).
 */
public class DesignerDocument {

    private static final int FORMAT_VERSION = 1;

    private String filePath;
    private Vector3f cameraPosition = new Vector3f(0, 5, -10);
    private Quaternion cameraRotation = new Quaternion(0, 1, 0, 0);
    private List<JSONObject> entityDefs = new ArrayList<>();

    // Game camera (the user-placed camera entity that sets the initial in-game camera posture)
    private Vector3f gameCameraPos = new Vector3f(0, 2, 10);
    private Quaternion gameCameraRot = new Quaternion(0, 1, 0, 0);

    public DesignerDocument(String filePath) {
        this.filePath = filePath;
    }

    public String getFilePath() {
        return filePath;
    }

    public Vector3f getCameraPosition() {
        return cameraPosition;
    }

    public void setCameraPosition(Vector3f pos) {
        this.cameraPosition = pos;
    }

    public Quaternion getCameraRotation() {
        return cameraRotation;
    }

    public void setCameraRotation(Quaternion rot) {
        this.cameraRotation = rot;
    }

    public List<JSONObject> getEntityDefs() {
        return entityDefs;
    }

    public Vector3f getGameCameraPos() {
        return gameCameraPos;
    }

    public void setGameCameraPos(Vector3f pos) {
        this.gameCameraPos = pos;
    }

    public Quaternion getGameCameraRot() {
        return gameCameraRot;
    }

    public void setGameCameraRot(Quaternion rot) {
        this.gameCameraRot = rot;
    }

    public static DesignerDocument load(File file) throws IOException {
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        JSONObject root = new JSONObject(content);

        DesignerDocument doc = new DesignerDocument(file.getAbsolutePath());

        if (root.has("camera")) {
            JSONObject cam = root.getJSONObject("camera");
            if (cam.has("position")) {
                JSONArray pos = cam.getJSONArray("position");
                doc.cameraPosition = new Vector3f(
                        (float) pos.getDouble(0), (float) pos.getDouble(1), (float) pos.getDouble(2));
            }
            if (cam.has("rotation")) {
                JSONArray rot = cam.getJSONArray("rotation");
                doc.cameraRotation = new Quaternion(
                        (float) rot.getDouble(0), (float) rot.getDouble(1),
                        (float) rot.getDouble(2), (float) rot.getDouble(3));
            }
        }

        if (root.has("gameCamera")) {
            JSONObject gc = root.getJSONObject("gameCamera");
            if (gc.has("position")) {
                JSONArray pos = gc.getJSONArray("position");
                doc.gameCameraPos = new Vector3f(
                        (float) pos.getDouble(0), (float) pos.getDouble(1), (float) pos.getDouble(2));
            }
            if (gc.has("rotation")) {
                JSONArray rot = gc.getJSONArray("rotation");
                doc.gameCameraRot = new Quaternion(
                        (float) rot.getDouble(0), (float) rot.getDouble(1),
                        (float) rot.getDouble(2), (float) rot.getDouble(3));
            }
        }

        if (root.has("entities")) {
            JSONArray entities = root.getJSONArray("entities");
            for (int i = 0; i < entities.length(); i++) {
                doc.entityDefs.add(entities.getJSONObject(i));
            }
        }

        return doc;
    }

    public void save(File file, List<DesignerEntity> entities, Vector3f camPos, Quaternion camRot,
                     Vector3f gameCamPos, Quaternion gameCamRot) throws IOException {
        JSONObject root = new JSONObject();
        root.put("version", FORMAT_VERSION);

        JSONObject camera = new JSONObject();
        camera.put("position", new float[]{camPos.x, camPos.y, camPos.z});
        camera.put("rotation", new float[]{camRot.getX(), camRot.getY(), camRot.getZ(), camRot.getW()});
        root.put("camera", camera);

        JSONObject gameCamera = new JSONObject();
        gameCamera.put("position", new float[]{gameCamPos.x, gameCamPos.y, gameCamPos.z});
        gameCamera.put("rotation", new float[]{gameCamRot.getX(), gameCamRot.getY(),
                gameCamRot.getZ(), gameCamRot.getW()});
        root.put("gameCamera", gameCamera);

        JSONArray entitiesArray = new JSONArray();
        for (DesignerEntity entity : entities) {
            entitiesArray.put(entity.toJSON());
        }
        root.put("entities", entitiesArray);

        Files.write(file.toPath(), root.toString(2).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generates and saves a companion .code file alongside the .smdesign file.
     * The .code file contains the SceneMax3D script representing all entities
     * in the designer document with their current transforms.
     *
     * @return true if the .code file was newly created (did not exist before)
     */
    public static boolean saveCodeFile(File smdesignFile, List<DesignerEntity> entities,
                                       Vector3f gameCamPos, Quaternion gameCamRot) throws IOException {
        File codeFile = getCodeFile(smdesignFile);
        boolean isNew = !codeFile.exists();

        StringBuilder sb = new StringBuilder();
        sb.append("// Auto-generated by SceneMax Designer from: ").append(smdesignFile.getName()).append("\n");
        sb.append("// Do not edit manually - changes will be overwritten on save.\n\n");

        // Include user's init code at the very beginning
        File initFile = getInitCodeFile(smdesignFile);
        if (initFile.exists()) {
            String initContent = new String(Files.readAllBytes(initFile.toPath()), StandardCharsets.UTF_8).trim();
            if (!initContent.isEmpty()) {
                sb.append(initContent).append("\n\n");
            }
        }

        // Game camera setup
        sb.append("camera.pos(").append(gameCamPos.x).append(",")
          .append(gameCamPos.y).append(",").append(gameCamPos.z).append(")\n");

        // Convert entity rotation directly to euler angles for camera.rotate()
        // JME cameras with identity rotation look along +Z, matching the gizmo visual.
        float[] camAngles = new float[3];
        gameCamRot.toAngles(camAngles);
        float camDegX = (float) Math.toDegrees(camAngles[0]);
        float camDegY = (float) Math.toDegrees(camAngles[1]);
        float camDegZ = (float) Math.toDegrees(camAngles[2]);
        // Always emit camera.rotate() to override SceneMaxApp's initial rotation
        sb.append("camera.rotate(").append(camDegX).append(",")
          .append(camDegY).append(",").append(camDegZ).append(")\n");
        sb.append("\n");

        for (DesignerEntity entity : entities) {
            if (entity.getType() == DesignerEntityType.CODE) {
                String codeText = entity.getCodeText();
                if (codeText != null && !codeText.trim().isEmpty()) {
                    sb.append(codeText.trim()).append("\n");
                }
                continue;
            }
            String code = generateEntityCode(entity);
            if (!code.isEmpty()) {
                sb.append(code).append("\n");
            }
        }

        // Include user's end code at the very end
        File endFile = getEndCodeFile(smdesignFile);
        if (endFile.exists()) {
            String endContent = new String(Files.readAllBytes(endFile.toPath()), StandardCharsets.UTF_8).trim();
            if (!endContent.isEmpty()) {
                sb.append("\n").append(endContent).append("\n");
            }
        }

        Files.write(codeFile.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        return isNew;
    }

    /**
     * Returns the companion .code file for a given .smdesign file.
     */
    public static File getCodeFile(File smdesignFile) {
        String baseName = getBaseName(smdesignFile);
        return new File(smdesignFile.getParentFile(), baseName + ".code");
    }

    /**
     * Returns the _init.code file for a given .smdesign file.
     */
    public static File getInitCodeFile(File smdesignFile) {
        String baseName = getBaseName(smdesignFile);
        return new File(smdesignFile.getParentFile(), baseName + "_init.code");
    }

    /**
     * Returns the _end.code file for a given .smdesign file.
     */
    public static File getEndCodeFile(File smdesignFile) {
        String baseName = getBaseName(smdesignFile);
        return new File(smdesignFile.getParentFile(), baseName + "_end.code");
    }

    private static String getBaseName(File smdesignFile) {
        String smdesignName = smdesignFile.getName();
        return smdesignName.substring(0, smdesignName.length() - ".smdesign".length());
    }

    /**
     * Generates a SceneMax3D code line for an entity using its current
     * position and rotation.
     */
    private static String generateEntityCode(DesignerEntity entity) {
        String name = entity.getName();
        Vector3f pos = entity.getPosition();
        String rotateSuffix = buildRotateSuffix(entity.getRotation());
        String scaleSuffix = buildScaleSuffix(entity.getScale());
        String materialSuffix = buildMaterialSuffix(entity.getMaterial());
        switch (entity.getType()) {
            case SPHERE:
                String spherePrefix = (entity.isStaticEntity() ? "static " : "") + (entity.isColliderEntity() ? "collider " : "");
                return name + " => " + spherePrefix + "sphere : pos (" + pos.x + "," + pos.y + "," + pos.z +
                       "), radius " + entity.getRadius() + materialSuffix + scaleSuffix + rotateSuffix;
            case BOX:
                String boxPrefix = (entity.isStaticEntity() ? "static " : "") + (entity.isColliderEntity() ? "collider " : "");
                return name + " => " + boxPrefix + "box : size (" +
                       (entity.getSizeX() * 2) + "," + (entity.getSizeY() * 2) + "," + (entity.getSizeZ() * 2) +
                       "), pos (" + pos.x + "," + pos.y + "," + pos.z + ")" + materialSuffix + scaleSuffix + rotateSuffix;
            case MODEL:
                String modelPrefix = entity.isStaticModel() ? "static " : entity.isDynamicModel() ? "dynamic " : "";
                String vehicleSuffix = entity.isVehicleModel() ? " vehicle" : "";
                return name + " => " + modelPrefix + entity.getResourcePath() + vehicleSuffix +
                       ": pos (" + pos.x + "," + pos.y + "," + pos.z + ")" + scaleSuffix + rotateSuffix + " async";
            default:
                return "";
        }
    }

    /**
     * Builds the ", scale N" suffix from a Vector3f scale (using the X component).
     * Returns an empty string when the scale is effectively 1.0.
     */
    private static String buildScaleSuffix(Vector3f scale) {
        float s = scale.x;
        if (Math.abs(s - 1.0f) < 0.001f) {
            return "";
        }
        return ", scale " + s;
    }

    /**
     * Builds the ", material 'name'" suffix.
     * Returns an empty string when no material is set.
     */
    private static String buildMaterialSuffix(String material) {
        if (material == null || material.isEmpty()) {
            return "";
        }
        return ", material \"" + material + "\"";
    }

    private static String buildRotateSuffix(Quaternion rotation) {
        float[] angles = new float[3];
        rotation.toAngles(angles);
        float degX = (float) Math.toDegrees(angles[0]);
        float degY = (float) Math.toDegrees(angles[1]);
        float degZ = (float) Math.toDegrees(angles[2]);

        if (Math.abs(degX) < 0.01f && Math.abs(degY) < 0.01f && Math.abs(degZ) < 0.01f) {
            return "";
        }
        return ", rotate(" + degX + "," + degY + "," + degZ + ")";
    }

    /**
     * Regenerates the companion .code file by re-reading the .smdesign from disk.
     * Used when the _init.code or _end.code files are edited outside the designer.
     */
    public static void regenerateCodeFileFromDisk(File smdesignFile) throws IOException {
        if (!smdesignFile.exists()) return;
        DesignerDocument doc = load(smdesignFile);

        // Reconstruct entities with transforms from the saved JSON
        List<DesignerEntity> entities = new ArrayList<>();
        for (JSONObject json : doc.entityDefs) {
            DesignerEntity entity = DesignerEntity.fromJSON(json);
            if (entity.getType() == DesignerEntityType.CODE) {
                // Code nodes don't need a scene node
                entities.add(entity);
                continue;
            }
            // Create a temporary scene node to hold the transform so getPosition() etc. work
            Node tempNode = new Node(entity.getName());
            tempNode.setLocalTranslation(DesignerEntity.positionFromJSON(json));
            tempNode.setLocalRotation(DesignerEntity.rotationFromJSON(json));
            tempNode.setLocalScale(DesignerEntity.scaleFromJSON(json));
            entity.setSceneNode(tempNode);
            if (entity.getType() != DesignerEntityType.CAMERA) {
                entities.add(entity);
            }
        }

        saveCodeFile(smdesignFile, entities, doc.gameCameraPos, doc.gameCameraRot);
    }

    public static DesignerDocument createEmpty(String filePath) {
        return new DesignerDocument(filePath);
    }

    public static void writeEmptyFile(File file) throws IOException {
        JSONObject root = new JSONObject();
        root.put("version", FORMAT_VERSION);
        root.put("camera", new JSONObject()
                .put("position", new float[]{0, 5, -10})
                .put("rotation", new float[]{0, 1, 0, 0}));
        root.put("gameCamera", new JSONObject()
                .put("position", new float[]{0, 2, 10})
                .put("rotation", new float[]{0, 1, 0, 0}));
        root.put("entities", new JSONArray());
        Files.write(file.toPath(), root.toString(2).getBytes(StandardCharsets.UTF_8));

        // Create the companion empty .code file
        File codeFile = getCodeFile(file);
        String header = "// Auto-generated by SceneMax Designer from: " + file.getName() + "\n"
                       + "// Do not edit manually - changes will be overwritten on save.\n";
        Files.write(codeFile.toPath(), header.getBytes(StandardCharsets.UTF_8));

        // Create the _init.code and _end.code files for user custom code
        File initFile = getInitCodeFile(file);
        if (!initFile.exists()) {
            Files.write(initFile.toPath(), "// Write your initialization code here.\n// This code will be included at the beginning of the generated .code file.\n".getBytes(StandardCharsets.UTF_8));
        }
        File endFile = getEndCodeFile(file);
        if (!endFile.exists()) {
            Files.write(endFile.toPath(), "// Write your finalization code here.\n// This code will be included at the end of the generated .code file.\n".getBytes(StandardCharsets.UTF_8));
        }
    }
}
