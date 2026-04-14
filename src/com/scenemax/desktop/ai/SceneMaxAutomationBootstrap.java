package com.scenemax.desktop.ai;

import com.scenemax.desktop.ai.tools.DesignerCreateDocumentTool;
import com.scenemax.desktop.ai.tools.EditorGetActiveDocumentTool;
import com.scenemax.desktop.ai.tools.EditorOpenFileTool;
import com.scenemax.desktop.ai.tools.EditorSaveActiveTool;
import com.scenemax.desktop.ai.tools.FileCreateTool;
import com.scenemax.desktop.ai.tools.FileModifyTool;
import com.scenemax.desktop.ai.tools.FileReadTool;
import com.scenemax.desktop.ai.tools.ProjectSearchFilesTool;
import com.scenemax.desktop.ai.tools.ProjectSearchTextTool;

public final class SceneMaxAutomationBootstrap {

    private SceneMaxAutomationBootstrap() {
    }

    public static SceneMaxToolRegistry createDefaultRegistry() {
        SceneMaxToolRegistry registry = new SceneMaxToolRegistry();
        registry.register(new ProjectSearchFilesTool());
        registry.register(new ProjectSearchTextTool());
        registry.register(new FileReadTool());
        registry.register(new FileCreateTool());
        registry.register(new FileModifyTool());
        registry.register(new EditorGetActiveDocumentTool());
        registry.register(new EditorOpenFileTool());
        registry.register(new EditorSaveActiveTool());
        registry.register(new DesignerCreateDocumentTool());
        return registry;
    }
}
