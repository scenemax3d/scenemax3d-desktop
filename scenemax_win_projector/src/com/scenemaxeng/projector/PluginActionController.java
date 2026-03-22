package com.scenemaxeng.projector;

import com.scenemaxeng.common.types.ISceneMaxPlugin;
import com.scenemaxeng.common.types.PluginsManager;
import com.scenemaxeng.compiler.PluginActionCommand;
import com.scenemaxeng.compiler.ProgramDef;

public class PluginActionController extends SceneMaxBaseController {

    public PluginActionController(SceneMaxApp app, ProgramDef prg, SceneMaxScope thread, PluginActionCommand cmd) {
        super(app, prg, thread, cmd);
    }

    public boolean run(float tpf) {
        PluginActionCommand cmd = (PluginActionCommand) this.cmd;
        ISceneMaxPlugin plugin = PluginsManager.loadPlugin(cmd.pluginName, this.app.pluginsCommunicationChannel, true);
        if (plugin==null) {
            return true;
        }

        if (cmd.action == PluginActionCommand.Actions.Start) {
            plugin.start();
        }
        return true;
    }

}
