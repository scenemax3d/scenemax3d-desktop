package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class DesignerGroupEntitiesTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "designer.group_entities";
    }

    @Override
    public String getDescription() {
        return "Groups entities into a SECTION node or ungroups an existing SECTION node.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject().put("type", "string"))
                        .put("base", new JSONObject().put("type", "string"))
                        .put("action", new JSONObject().put("type", "string"))
                        .put("entity_id", new JSONObject().put("type", "string"))
                        .put("entity_name", new JSONObject().put("type", "string"))
                        .put("entity_ids", new JSONObject().put("type", "array"))
                        .put("entity_names", new JSONObject().put("type", "array"))
                        .put("group_name", new JSONObject().put("type", "string"))
                        .put("group_id", new JSONObject().put("type", "string"))
                        .put("reload", new JSONObject().put("type", "boolean"))
                        .put("force", new JSONObject().put("type", "boolean")))
                .put("required", new JSONArray().put("action"));
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        Path path = DesignerAutomationSupport.resolvePathOrActive(context, arguments, "workspace");
        DesignerAutomationSupport.ensureDiskFileExists(path);
        DesignerAutomationSupport.ensureSceneDesigner(path);
        DesignerAutomationSupport.ensureNotDirtyInIde(context, path, optionalBoolean(arguments, "force", false));

        String action = optionalString(arguments, "action", "group").toLowerCase();
        JSONObject root = DesignerAutomationSupport.readJson(path);
        JSONArray entities = DesignerAutomationSupport.ensureEntitiesArray(root);
        JSONObject result = "ungroup".equals(action) ? ungroup(arguments, entities) : group(arguments, entities);

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
        data.put("result", result);
        data.put("reloaded", reloaded);
        data.put("validation", validation);
        return SceneMaxToolResult.success(("ungroup".equals(action) ? "Ungrouped" : "Grouped") + " scene entities.", data);
    }

    private JSONObject group(JSONObject arguments, JSONArray rootEntities) {
        Set<String> ids = DesignerAutomationSupport.collectStringSet(arguments, "entity_ids", "entity_id");
        Set<String> names = DesignerAutomationSupport.collectStringSet(arguments, "entity_names", "entity_name");
        if (ids.isEmpty() && names.isEmpty()) {
            throw new IllegalArgumentException("Provide entity_id/entity_ids or entity_name/entity_names.");
        }

        List<DesignerAutomationSupport.EntityRef> refs =
                DesignerAutomationSupport.collectEntityRefs(rootEntities, ids, names);
        if (refs.isEmpty()) {
            throw new IllegalArgumentException("Could not find any entities matching the provided ids/names.");
        }

        JSONObject group = DesignerAutomationSupport.createSectionEntity(
                optionalString(arguments, "group_name", "group"),
                optionalString(arguments, "group_id", UUID.randomUUID().toString()));
        JSONArray children = group.getJSONArray("children");
        for (DesignerAutomationSupport.EntityRef ref : refs) {
            children.put(DesignerAutomationSupport.deepCopyObject(ref.entity));
            ref.parentArray.remove(ref.index);
        }
        rootEntities.put(group);

        return new JSONObject()
                .put("action", "group")
                .put("group", group)
                .put("groupedCount", children.length());
    }

    private JSONObject ungroup(JSONObject arguments, JSONArray rootEntities) {
        String entityId = optionalString(arguments, "entity_id", "");
        String entityName = optionalString(arguments, "entity_name", "");
        if (entityId.isEmpty() && entityName.isEmpty()) {
            throw new IllegalArgumentException("Provide the group entity_id or entity_name to ungroup.");
        }

        DesignerAutomationSupport.EntityRef ref =
                DesignerAutomationSupport.findEntityRef(rootEntities, entityId, entityName);
        if (ref == null) {
            throw new IllegalArgumentException("Could not find the requested group entity.");
        }
        if (!"SECTION".equalsIgnoreCase(ref.entity.optString("type", ""))) {
            throw new IllegalArgumentException("Only SECTION entities can be ungrouped.");
        }

        JSONArray children = ref.entity.optJSONArray("children");
        JSONArray replacement = new JSONArray();
        for (int i = 0; i < ref.parentArray.length(); i++) {
            if (i == ref.index) {
                if (children != null) {
                    for (int j = 0; j < children.length(); j++) {
                        replacement.put(children.get(j));
                    }
                }
            } else {
                replacement.put(ref.parentArray.get(i));
            }
        }
        rewriteArray(ref.parentArray, replacement);

        return new JSONObject()
                .put("action", "ungroup")
                .put("groupName", DesignerAutomationSupport.entityLabel(ref.entity))
                .put("ungroupedCount", children != null ? children.length() : 0);
    }

    private void rewriteArray(JSONArray target, JSONArray replacement) {
        while (target.length() > 0) {
            target.remove(target.length() - 1);
        }
        for (int i = 0; i < replacement.length(); i++) {
            target.put(replacement.get(i));
        }
    }
}
