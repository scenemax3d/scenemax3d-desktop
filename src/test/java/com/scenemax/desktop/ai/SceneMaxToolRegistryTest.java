package com.scenemax.desktop.ai;

import org.json.JSONObject;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SceneMaxToolRegistryTest {

    @Test
    public void fileToolsCreateReadModifyAndSearch() throws Exception {
        Path workspace = Files.createTempDirectory("scenemax-tools");
        try {
            SceneMaxToolRegistry registry = SceneMaxAutomationBootstrap.createDefaultRegistry();
            SceneMaxToolContext context = new SceneMaxToolContext(null, workspace);

            JSONObject createArgs = new JSONObject()
                    .put("path", "scripts/example.code")
                    .put("content", "hello world\nsecond line\n")
                    .put("base", "workspace")
                    .put("openInEditor", false);
            SceneMaxToolResult createResult = registry.call("file.create", context, createArgs);
            assertFalse(createResult.isError());
            assertTrue(Files.exists(workspace.resolve("scripts/example.code")));

            SceneMaxToolResult readResult = registry.call("file.read", context, new JSONObject()
                    .put("path", "scripts/example.code")
                    .put("base", "workspace"));
            assertFalse(readResult.isError());
            assertTrue(readResult.getData().getString("content").contains("hello world"));

            SceneMaxToolResult modifyResult = registry.call("file.modify", context, new JSONObject()
                    .put("path", "scripts/example.code")
                    .put("base", "workspace")
                    .put("expectedText", "hello world")
                    .put("replacementText", "hello agent")
                    .put("openInEditor", false));
            assertFalse(modifyResult.isError());

            String updated = Files.readString(workspace.resolve("scripts/example.code"));
            assertTrue(updated.contains("hello agent"));

            SceneMaxToolResult searchFilesResult = registry.call("project.search_files", context, new JSONObject()
                    .put("query", "example")
                    .put("base", "workspace"));
            assertFalse(searchFilesResult.isError());
            assertEquals(1, searchFilesResult.getData().getJSONArray("matches").length());

            SceneMaxToolResult listTreeResult = registry.call("project.list_tree", context, new JSONObject()
                    .put("base", "workspace")
                    .put("maxDepth", 3));
            assertFalse(listTreeResult.isError());
            assertTrue(listTreeResult.getData().getJSONArray("entries").length() >= 2);

            SceneMaxToolResult contextResult = registry.call("project.get_context", context, new JSONObject());
            assertFalse(contextResult.isError());
            assertTrue(contextResult.getData().getString("workspaceRoot").contains("scenemax-tools"));

            SceneMaxToolResult searchTextResult = registry.call("project.search_text", context, new JSONObject()
                    .put("query", "hello agent")
                    .put("base", "workspace"));
            assertFalse(searchTextResult.isError());
            assertEquals(1, searchTextResult.getData().getJSONArray("matches").length());
        } finally {
            deleteRecursively(workspace);
        }
    }

    @Test
    public void registryIncludesDesignerViewAndCodegenTools() {
        SceneMaxToolRegistry registry = SceneMaxAutomationBootstrap.createDefaultRegistry();
        assertNotNull(registry.get("designer.capture_canvas"));
        assertNotNull(registry.get("designer.capture_canvas_reliable"));
        assertNotNull(registry.get("designer.compare_to_reference"));
        assertNotNull(registry.get("designer.get_view_state"));
        assertNotNull(registry.get("designer.adjust_view"));
        assertNotNull(registry.get("designer.use_view_gizmo"));
        assertNotNull(registry.get("designer.get_entity"));
        assertNotNull(registry.get("designer.update_entity"));
        assertNotNull(registry.get("designer.delete_entity"));
        assertNotNull(registry.get("designer.duplicate_entity"));
        assertNotNull(registry.get("designer.mirror_entities"));
        assertNotNull(registry.get("designer.array_entities"));
        assertNotNull(registry.get("designer.group_entities"));
        assertNotNull(registry.get("designer.validate_resources"));
        assertNotNull(registry.get("designer.add_primitive_advanced"));
        assertNotNull(registry.get("designer.select_entity"));
        assertNotNull(registry.get("designer.overlay_reference_image"));
        assertNotNull(registry.get("designer.refresh_project_tree"));
        assertNotNull(registry.get("designer.generate_code"));
        assertNotNull(registry.get("run.preview_scene"));
    }

    private void deleteRecursively(Path path) throws Exception {
        if (path == null || !Files.exists(path)) {
            return;
        }
        Files.walk(path)
                .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(it -> {
                    try {
                        Files.deleteIfExists(it);
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                });
    }
}
