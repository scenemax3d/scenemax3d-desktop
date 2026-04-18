package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class DesignerDuplicateEntityTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "designer.duplicate_entity";
    }

    @Override
    public String getDescription() {
        return "Duplicates one or more scene entities with optional offset and multiple copies.";
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
                        .put("copies", new JSONObject().put("type", "integer"))
                        .put("offset", new JSONObject().put("type", "array"))
                        .put("name_suffix", new JSONObject().put("type", "string"))
                        .put("reload", new JSONObject().put("type", "boolean"))
                        .put("force", new JSONObject().put("type", "boolean")));
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

        int copies = Math.max(1, optionalInt(arguments, "copies", 1));
        JSONArray offset = arguments.optJSONArray("offset");
        double dx = offset != null ? offset.optDouble(0, 0d) : 0d;
        double dy = offset != null ? offset.optDouble(1, 0d) : 0d;
        double dz = offset != null ? offset.optDouble(2, 0d) : 0d;
        String suffixBase = optionalString(arguments, "name_suffix", "_copy");

        JSONObject root = DesignerAutomationSupport.readJson(path);
        List<DesignerAutomationSupport.EntityRef> refs =
                DesignerAutomationSupport.collectEntityRefs(root.optJSONArray("entities"), ids, names);
        if (refs.isEmpty()) {
            throw new IllegalArgumentException("Could not find any entities matching the provided ids/names.");
        }

        JSONArray created = new JSONArray();
        for (DesignerAutomationSupport.EntityRef ref : refs) {
            for (int copyIndex = 1; copyIndex <= copies; copyIndex++) {
                JSONObject clone = DesignerAutomationSupport.deepCopyObject(ref.entity);
                DesignerAutomationSupport.regenerateEntityIds(clone);
                DesignerAutomationSupport.applyNameSuffix(clone, suffixBase + copyIndex);
                DesignerAutomationSupport.offsetEntity(clone, dx * copyIndex, dy * copyIndex, dz * copyIndex);
                ref.parentArray.put(clone);
                created.put(new JSONObject()
                        .put("source", DesignerAutomationSupport.entityLabel(ref.entity))
                        .put("copyIndex", copyIndex)
                        .put("entity", clone));
            }
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
        data.put("created", created);
        data.put("createdCount", created.length());
        data.put("reloaded", reloaded);
        data.put("validation", validation);
        return SceneMaxToolResult.success("Duplicated " + refs.size() + " entity selection(s).", data);
    }
}
