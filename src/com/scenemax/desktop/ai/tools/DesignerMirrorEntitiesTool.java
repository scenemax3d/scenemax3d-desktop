package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DesignerMirrorEntitiesTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "designer.mirror_entities";
    }

    @Override
    public String getDescription() {
        return "Mirrors one or more scene entities across an axis, either in place or by creating mirrored copies.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject().put("type", "string"))
                        .put("base", new JSONObject().put("type", "string"))
                        .put("entity_id", new JSONObject().put("type", "string"))
                        .put("entity_name", new JSONObject().put("type", "string"))
                        .put("entity_ids", new JSONObject().put("type", "array"))
                        .put("entity_names", new JSONObject().put("type", "array"))
                        .put("axis", new JSONObject().put("type", "string"))
                        .put("pivot", new JSONObject().put("type", "number"))
                        .put("mode", new JSONObject().put("type", "string"))
                        .put("name_suffix", new JSONObject().put("type", "string"))
                        .put("reload", new JSONObject().put("type", "boolean"))
                        .put("force", new JSONObject().put("type", "boolean")))
                .put("required", new JSONArray().put("axis"));
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        Path path = DesignerAutomationSupport.resolvePathOrActive(context, arguments, "workspace");
        DesignerAutomationSupport.ensureDiskFileExists(path);
        DesignerAutomationSupport.ensureSceneDesigner(path);
        DesignerAutomationSupport.ensureNotDirtyInIde(context, path, optionalBoolean(arguments, "force", false));

        Set<String> ids = DesignerAutomationSupport.collectStringSet(arguments, "entity_ids", "entity_id");
        Set<String> names = DesignerAutomationSupport.collectStringSet(arguments, "entity_names", "entity_name");
        if (ids.isEmpty() && names.isEmpty()) {
            throw new IllegalArgumentException("Provide entity_id/entity_ids or entity_name/entity_names.");
        }

        String axis = optionalString(arguments, "axis", "x").toLowerCase(Locale.ROOT);
        double pivot = arguments.has("pivot") ? arguments.optDouble("pivot", 0d) : 0d;
        String mode = optionalString(arguments, "mode", "duplicate").toLowerCase(Locale.ROOT);
        boolean duplicate = !"update".equals(mode);
        String suffix = optionalString(arguments, "name_suffix", "_mirror_" + axis);

        JSONObject root = DesignerAutomationSupport.readJson(path);
        List<DesignerAutomationSupport.EntityRef> refs =
                DesignerAutomationSupport.collectEntityRefs(root.optJSONArray("entities"), ids, names);
        if (refs.isEmpty()) {
            throw new IllegalArgumentException("Could not find any entities matching the provided ids/names.");
        }

        JSONArray affected = new JSONArray();
        for (DesignerAutomationSupport.EntityRef ref : refs) {
            JSONObject target = duplicate ? DesignerAutomationSupport.deepCopyObject(ref.entity) : ref.entity;
            if (duplicate) {
                DesignerAutomationSupport.regenerateEntityIds(target);
                DesignerAutomationSupport.applyNameSuffix(target, suffix);
            }
            DesignerAutomationSupport.mirrorEntity(target, axis, pivot);
            if (duplicate) {
                ref.parentArray.put(target);
            }
            affected.put(new JSONObject()
                    .put("mode", duplicate ? "duplicate" : "update")
                    .put("entity", target));
        }

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
        data.put("axis", axis);
        data.put("pivot", pivot);
        data.put("mode", duplicate ? "duplicate" : "update");
        data.put("affected", affected);
        data.put("affectedCount", affected.length());
        data.put("reloaded", reloaded);
        data.put("validation", validation);
        return SceneMaxToolResult.success("Mirrored " + affected.length() + " entity instance(s).", data);
    }
}
