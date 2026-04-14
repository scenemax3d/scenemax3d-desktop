package com.scenemax.desktop.ai;

import com.scenemax.desktop.ai.tools.AssetSearchSketchfabTool;
import com.scenemax.desktop.ai.tools.DesignerCreateDocumentTool;
import com.scenemax.desktop.ai.tools.DesignerAddPrimitiveTool;
import com.scenemax.desktop.ai.tools.DesignerCreateCinematicRigTool;
import com.scenemax.desktop.ai.tools.DesignerListEntitiesTool;
import com.scenemax.desktop.ai.tools.EditorGetActiveDocumentTool;
import com.scenemax.desktop.ai.tools.EditorOpenFileTool;
import com.scenemax.desktop.ai.tools.EditorSaveActiveTool;
import com.scenemax.desktop.ai.tools.FileCreateTool;
import com.scenemax.desktop.ai.tools.FileModifyTool;
import com.scenemax.desktop.ai.tools.FileReadTool;
import com.scenemax.desktop.ai.tools.ProjectGetContextTool;
import com.scenemax.desktop.ai.tools.ProjectListTreeTool;
import com.scenemax.desktop.ai.tools.ProjectSearchFilesTool;
import com.scenemax.desktop.ai.tools.ProjectSearchTextTool;
import com.scenemax.desktop.ai.tools.RunPreviewSceneTool;

public final class SceneMaxAutomationBootstrap {

    private SceneMaxAutomationBootstrap() {
    }

    public static SceneMaxToolRegistry createDefaultRegistry() {
        SceneMaxToolRegistry registry = new SceneMaxToolRegistry();
        registry.register(new ProjectGetContextTool());
        registry.register(new ProjectListTreeTool());
        registry.register(new ProjectSearchFilesTool());
        registry.register(new ProjectSearchTextTool());
        registry.register(new FileReadTool());
        registry.register(new FileCreateTool());
        registry.register(new FileModifyTool());
        registry.register(new EditorGetActiveDocumentTool());
        registry.register(new EditorOpenFileTool());
        registry.register(new EditorSaveActiveTool());
        registry.register(new DesignerCreateDocumentTool());
        registry.register(new DesignerListEntitiesTool());
        registry.register(new DesignerAddPrimitiveTool());
        registry.register(new DesignerCreateCinematicRigTool());
        registry.register(new AssetSearchSketchfabTool());
        registry.register(new RunPreviewSceneTool());
        return registry;
    }
}
