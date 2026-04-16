package com.scenemax.desktop.ai;

import com.scenemax.desktop.ai.tools.AssetSearchSketchfabTool;
import com.scenemax.desktop.ai.tools.AssetImportSketchfabTool;
import com.scenemax.desktop.ai.tools.AppRestartTool;
import com.scenemax.desktop.ai.tools.DesignerAddEntitiesBatchTool;
import com.scenemax.desktop.ai.tools.DesignerCreateDocumentTool;
import com.scenemax.desktop.ai.tools.DesignerAddPrimitiveTool;
import com.scenemax.desktop.ai.tools.DesignerApplyMaterialTool;
import com.scenemax.desktop.ai.tools.DesignerCaptureThumbnailTool;
import com.scenemax.desktop.ai.tools.DesignerCreateCinematicRigTool;
import com.scenemax.desktop.ai.tools.DesignerGetDocumentJsonTool;
import com.scenemax.desktop.ai.tools.DesignerListAvailableMaterialsTool;
import com.scenemax.desktop.ai.tools.DesignerListEntitiesTool;
import com.scenemax.desktop.ai.tools.DesignerSetDocumentJsonTool;
import com.scenemax.desktop.ai.tools.DesignerValidateDocumentTool;
import com.scenemax.desktop.ai.tools.EditorGetActiveDocumentTool;
import com.scenemax.desktop.ai.tools.EditorOpenFileTool;
import com.scenemax.desktop.ai.tools.EditorReloadFromDiskTool;
import com.scenemax.desktop.ai.tools.EditorSaveActiveTool;
import com.scenemax.desktop.ai.tools.FileCreateTool;
import com.scenemax.desktop.ai.tools.FileModifyTool;
import com.scenemax.desktop.ai.tools.FileReadTool;
import com.scenemax.desktop.ai.tools.IdeGetRecentErrorsTool;
import com.scenemax.desktop.ai.tools.MaterialSaveExportTool;
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
        registry.register(new EditorReloadFromDiskTool());
        registry.register(new EditorSaveActiveTool());
        registry.register(new DesignerCreateDocumentTool());
        registry.register(new DesignerGetDocumentJsonTool());
        registry.register(new DesignerSetDocumentJsonTool());
        registry.register(new DesignerValidateDocumentTool());
        registry.register(new DesignerListEntitiesTool());
        registry.register(new DesignerAddPrimitiveTool());
        registry.register(new DesignerAddEntitiesBatchTool());
        registry.register(new DesignerApplyMaterialTool());
        registry.register(new DesignerListAvailableMaterialsTool());
        registry.register(new DesignerCreateCinematicRigTool());
        registry.register(new DesignerCaptureThumbnailTool());
        registry.register(new MaterialSaveExportTool());
        registry.register(new IdeGetRecentErrorsTool());
        registry.register(new AppRestartTool());
        registry.register(new AssetSearchSketchfabTool());
        registry.register(new AssetImportSketchfabTool());
        registry.register(new RunPreviewSceneTool());
        return registry;
    }
}
