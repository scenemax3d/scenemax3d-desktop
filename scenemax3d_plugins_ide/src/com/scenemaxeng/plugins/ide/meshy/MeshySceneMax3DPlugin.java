package com.scenemaxeng.plugins.ide.meshy;

import com.scenemaxeng.common.types.PluginBase;
import com.scenemaxeng.common.types.SceneMaxAssetProvider;
import com.scenemaxeng.common.types.SceneMaxPluginAction;
import com.scenemaxeng.common.types.SceneMaxPluginContext;
import com.scenemaxeng.common.types.SceneMaxPluginManifest;
import com.scenemaxeng.common.types.SceneMaxPluginView;

import javax.swing.JComponent;
import java.util.Arrays;

public class MeshySceneMax3DPlugin extends PluginBase {
    static final String VIEW_ID = "meshy.ai.models";

    @Override
    public SceneMaxPluginManifest getManifest() {
        return new SceneMaxPluginManifest(
                "meshy.ai",
                "Meshy AI",
                "1.0.0",
                "Create, browse, refine, download and import Meshy AI 3D models.",
                Arrays.asList("menu", "toolbar", "view", "asset-provider", "settings", "asset-import"));
    }

    @Override
    public int start(Object... args) {
        context.registerView(new SceneMaxPluginView() {
            @Override
            public String getId() {
                return VIEW_ID;
            }

            @Override
            public String getTitle() {
                return "Meshy AI";
            }

            @Override
            public JComponent createComponent(SceneMaxPluginContext context) {
                return new MeshyViewPanel(context);
            }
        });

        context.registerAssetProvider(new SceneMaxAssetProvider() {
            @Override
            public String getId() {
                return "meshy.ai.asset-provider";
            }

            @Override
            public String getDisplayName() {
                return "Meshy AI";
            }

            @Override
            public JComponent createBrowserComponent(SceneMaxPluginContext context) {
                return new MeshyViewPanel(context);
            }
        });

        SceneMaxPluginAction openAction = new SceneMaxPluginAction() {
            @Override
            public String getId() {
                return "meshy.open";
            }

            @Override
            public String getLabel() {
                return "Meshy AI";
            }

            @Override
            public String getTooltip() {
                return "Create and import 3D models from Meshy AI";
            }

            @Override
            public void perform(SceneMaxPluginContext context) {
                context.openView(VIEW_ID);
            }
        };

        context.registerMenuAction("Assets", openAction);
        context.registerToolbarAction(openAction);
        return 0;
    }
}
