package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

public class DesignerAddPrimitiveAdvancedTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "designer.add_primitive_advanced";
    }

    @Override
    public String getDescription() {
        return "Creates a primitive entity with full initial properties in a .smdesign document.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject().put("type", "string"))
                        .put("base", new JSONObject().put("type", "string"))
                        .put("primitive", new JSONObject().put("type", "string"))
                        .put("name", new JSONObject().put("type", "string"))
                        .put("id", new JSONObject().put("type", "string"))
                        .put("position", new JSONObject().put("type", "array"))
                        .put("rotation", new JSONObject().put("type", "array"))
                        .put("scale", new JSONObject().put("type", "array"))
                        .put("material", new JSONObject().put("type", "string"))
                        .put("shader", new JSONObject().put("type", "string"))
                        .put("hidden", new JSONObject().put("type", "boolean"))
                        .put("shadowMode", new JSONObject().put("type", "string"))
                        .put("staticEntity", new JSONObject().put("type", "boolean"))
                        .put("colliderEntity", new JSONObject().put("type", "boolean"))
                        .put("radius", new JSONObject().put("type", "number"))
                        .put("sizeX", new JSONObject().put("type", "number"))
                        .put("sizeY", new JSONObject().put("type", "number"))
                        .put("sizeZ", new JSONObject().put("type", "number"))
                        .put("wedgeWidth", new JSONObject().put("type", "number"))
                        .put("wedgeHeight", new JSONObject().put("type", "number"))
                        .put("wedgeDepth", new JSONObject().put("type", "number"))
                        .put("radiusTop", new JSONObject().put("type", "number"))
                        .put("radiusBottom", new JSONObject().put("type", "number"))
                        .put("height", new JSONObject().put("type", "number"))
                        .put("innerRadiusTop", new JSONObject().put("type", "number"))
                        .put("innerRadiusBottom", new JSONObject().put("type", "number"))
                        .put("quadWidth", new JSONObject().put("type", "number"))
                        .put("quadHeight", new JSONObject().put("type", "number"))
                        .put("stairsWidth", new JSONObject().put("type", "number"))
                        .put("stairsStepHeight", new JSONObject().put("type", "number"))
                        .put("stairsStepDepth", new JSONObject().put("type", "number"))
                        .put("stairsStepCount", new JSONObject().put("type", "integer"))
                        .put("archWidth", new JSONObject().put("type", "number"))
                        .put("archHeight", new JSONObject().put("type", "number"))
                        .put("archDepth", new JSONObject().put("type", "number"))
                        .put("archThickness", new JSONObject().put("type", "number"))
                        .put("archSegments", new JSONObject().put("type", "integer"))
                        .put("reload", new JSONObject().put("type", "boolean"))
                        .put("force", new JSONObject().put("type", "boolean")))
                .put("required", new JSONArray().put("primitive"));
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        Path path = DesignerAutomationSupport.resolvePathOrActive(context, arguments, "workspace");
        DesignerAutomationSupport.ensureDiskFileExists(path);
        DesignerAutomationSupport.ensureSceneDesigner(path);
        DesignerAutomationSupport.ensureNotDirtyInIde(context, path, optionalBoolean(arguments, "force", false));

        JSONObject root = DesignerAutomationSupport.readJson(path);
        JSONArray entities = DesignerAutomationSupport.ensureEntitiesArray(root);
        JSONObject entity = buildEntity(arguments);
        entities.put(entity);

        DesignerAutomationSupport.writeJson(path, root);
        JSONObject validation = DesignerAutomationSupport.validateDocument(context, path);

        boolean reloaded = false;
        if (optionalBoolean(arguments, "reload", true)) {
            MainApp host = context.getHost();
            if (host != null) {
                reloaded = host.reloadFileFromDiskForAutomation(path.toFile());
            }
        }

        JSONObject data = new JSONObject();
        data.put("path", path.toString());
        data.put("entity", entity);
        data.put("reloaded", reloaded);
        data.put("validation", validation);
        return SceneMaxToolResult.success("Added primitive entity " + DesignerAutomationSupport.entityLabel(entity) + ".", data);
    }

    private JSONObject buildEntity(JSONObject arguments) {
        String primitive = normalizePrimitive(requireString(arguments, "primitive"));
        String id = optionalString(arguments, "id", UUID.randomUUID().toString());
        JSONObject entity = new JSONObject();
        entity.put("id", id);
        entity.put("name", optionalString(arguments, "name", primitive + "_" + id.substring(0, 8)));
        entity.put("type", primitiveType(primitive));
        entity.put("position", copyArray(arguments.optJSONArray("position"), defaultPosition(primitive)));
        entity.put("rotation", copyArray(arguments.optJSONArray("rotation"), new JSONArray().put(0).put(0).put(0).put(1)));
        entity.put("scale", copyArray(arguments.optJSONArray("scale"), new JSONArray().put(1).put(1).put(1)));
        entity.put("material", optionalString(arguments, "material", ""));
        entity.put("shader", optionalString(arguments, "shader", ""));
        entity.put("hidden", optionalBoolean(arguments, "hidden", false));
        entity.put("shadowMode", optionalString(arguments, "shadowMode", "none"));
        entity.put("staticEntity", optionalBoolean(arguments, "staticEntity", false));
        entity.put("colliderEntity", optionalBoolean(arguments, "colliderEntity", false));

        switch (primitive) {
            case "sphere":
                entity.put("radius", arguments.has("radius") ? arguments.optDouble("radius", 0.5d) : 0.5d);
                break;
            case "box":
                entity.put("sizeX", arguments.has("sizeX") ? arguments.optDouble("sizeX", 0.5d) : 0.5d);
                entity.put("sizeY", arguments.has("sizeY") ? arguments.optDouble("sizeY", 0.5d) : 0.5d);
                entity.put("sizeZ", arguments.has("sizeZ") ? arguments.optDouble("sizeZ", 0.5d) : 0.5d);
                break;
            case "wedge":
                entity.put("wedgeWidth", arguments.has("wedgeWidth") ? arguments.optDouble("wedgeWidth", 1d) : 1d);
                entity.put("wedgeHeight", arguments.has("wedgeHeight") ? arguments.optDouble("wedgeHeight", 1d) : 1d);
                entity.put("wedgeDepth", arguments.has("wedgeDepth") ? arguments.optDouble("wedgeDepth", 1d) : 1d);
                break;
            case "cylinder":
            case "cone":
                entity.put("radiusTop", arguments.has("radiusTop") ? arguments.optDouble("radiusTop", primitive.equals("cone") ? 0d : 1d) : (primitive.equals("cone") ? 0d : 1d));
                entity.put("radiusBottom", arguments.has("radiusBottom") ? arguments.optDouble("radiusBottom", 1d) : 1d);
                entity.put("height", arguments.has("height") ? arguments.optDouble("height", 2d) : 2d);
                break;
            case "hollow_cylinder":
                entity.put("radiusTop", arguments.has("radiusTop") ? arguments.optDouble("radiusTop", 1d) : 1d);
                entity.put("radiusBottom", arguments.has("radiusBottom") ? arguments.optDouble("radiusBottom", 1d) : 1d);
                entity.put("innerRadiusTop", arguments.has("innerRadiusTop") ? arguments.optDouble("innerRadiusTop", 0.5d) : 0.5d);
                entity.put("innerRadiusBottom", arguments.has("innerRadiusBottom") ? arguments.optDouble("innerRadiusBottom", 0.5d) : 0.5d);
                entity.put("height", arguments.has("height") ? arguments.optDouble("height", 2d) : 2d);
                break;
            case "quad":
                entity.put("quadWidth", arguments.has("quadWidth") ? arguments.optDouble("quadWidth", 1d) : 1d);
                entity.put("quadHeight", arguments.has("quadHeight") ? arguments.optDouble("quadHeight", 1d) : 1d);
                break;
            case "stairs":
                entity.put("stairsWidth", arguments.has("stairsWidth") ? arguments.optDouble("stairsWidth", 2d) : 2d);
                entity.put("stairsStepHeight", arguments.has("stairsStepHeight") ? arguments.optDouble("stairsStepHeight", 0.25d) : 0.25d);
                entity.put("stairsStepDepth", arguments.has("stairsStepDepth") ? arguments.optDouble("stairsStepDepth", 0.4d) : 0.4d);
                entity.put("stairsStepCount", arguments.has("stairsStepCount") ? arguments.optInt("stairsStepCount", 6) : 6);
                break;
            case "arch":
                entity.put("archWidth", arguments.has("archWidth") ? arguments.optDouble("archWidth", 2d) : 2d);
                entity.put("archHeight", arguments.has("archHeight") ? arguments.optDouble("archHeight", 2.5d) : 2.5d);
                entity.put("archDepth", arguments.has("archDepth") ? arguments.optDouble("archDepth", 0.5d) : 0.5d);
                entity.put("archThickness", arguments.has("archThickness") ? arguments.optDouble("archThickness", 0.35d) : 0.35d);
                entity.put("archSegments", arguments.has("archSegments") ? arguments.optInt("archSegments", 12) : 12);
                break;
            default:
                throw new IllegalArgumentException("Unsupported primitive type: " + primitive);
        }

        return entity;
    }

    private String normalizePrimitive(String primitive) {
        return primitive.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private String primitiveType(String primitive) {
        switch (primitive) {
            case "sphere": return "SPHERE";
            case "box": return "BOX";
            case "wedge": return "WEDGE";
            case "cylinder": return "CYLINDER";
            case "cone": return "CONE";
            case "hollow_cylinder": return "HOLLOW_CYLINDER";
            case "quad": return "QUAD";
            case "stairs": return "STAIRS";
            case "arch": return "ARCH";
            default: throw new IllegalArgumentException("Unsupported primitive type: " + primitive);
        }
    }

    private JSONArray defaultPosition(String primitive) {
        switch (primitive) {
            case "sphere":
            case "box":
            case "quad":
            case "wedge":
                return new JSONArray().put(0).put(0.5).put(0);
            case "cylinder":
            case "cone":
            case "hollow_cylinder":
                return new JSONArray().put(0).put(1).put(0);
            case "stairs":
                return new JSONArray().put(0).put(0.75).put(0);
            case "arch":
                return new JSONArray().put(0).put(1.25).put(0);
            default:
                return new JSONArray().put(0).put(0).put(0);
        }
    }

    private JSONArray copyArray(JSONArray source, JSONArray fallback) {
        return source != null ? new JSONArray(source.toString()) : new JSONArray(fallback.toString());
    }
}
