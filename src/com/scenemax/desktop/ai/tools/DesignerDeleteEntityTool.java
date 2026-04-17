package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class DesignerDeleteEntityTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "designer.delete_entity";
    }

    @Override
    public String getDescription() {
        return "Deletes one or more scene entities from a .smdesign document by id or name.";
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

        JSONObject root = DesignerAutomationSupport.readJson(path);
        List<DesignerAutomationSupport.EntityRef> refs =
                DesignerAutomationSupport.collectEntityRefs(root.optJSONArray("entities"), ids, names);
        if (refs.isEmpty()) {
            throw new IllegalArgumentException("Could not find any entities matching the provided ids/names.");
        }

        JSONArray deleted = new JSONArray();
        for (DesignerAutomationSupport.EntityRef ref : refs) {
            deleted.put(DesignerAutomationSupport.entityLabel(ref.entity));
            ref.parentArray.remove(ref.index);
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
        data.put("deleted", deleted);
        data.put("deletedCount", deleted.length());
        data.put("reloaded", reloaded);
        data.put("validation", validation);
        return SceneMaxToolResult.success("Deleted " + deleted.length() + " scene entities.", data);
    }
}
