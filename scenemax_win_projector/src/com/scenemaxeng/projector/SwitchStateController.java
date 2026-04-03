package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import com.scenemaxeng.compiler.SwitchStateCommand;
import com.scenemaxeng.compiler.Utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class SwitchStateController extends SceneMaxBaseController {

    private SwitchStateCommand cmd;
    public SwitchStateController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, SwitchStateCommand cmd) {
        super(app, prg, scope, cmd);
        this.cmd = cmd;
    }

    public boolean run(float tpf) {
        String path = new ActionLogicalExpressionVm(this.cmd.pathExpr, this.scope).evaluate().toString();
        String level = "";
        if (!path.equals("main")) {
            level = "/" + path;
            path += "/main";
        }
        String code = this.getExternalCode(path);
        this.app.prepareToSwitchState(code, level);
        return true;
    }

    private String getExternalCode(String filePath) {

        String code = null;
        String codePath = this.app.getWorkingFolder();
        File runningFolder = new File(codePath);
        File f = new File(runningFolder, filePath);
        try {
            if(f.exists()) {
                code = SceneMaxLanguageParser.readFile(f);
            }
        } catch (SecurityException ex) {
            // Packaged Web Start launches may deny direct local reads; try bundled resources below.
            code = null;
        }

        if (code == null) {
            if (!filePath.startsWith("/")) {
                filePath = "/" + filePath;
            }
            InputStream script = SceneMaxLanguageParser.class.getResourceAsStream("/running"+filePath);
            try {
                if(script!=null) {
                    code = new String(Utils.toByteArray(script));
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return code;

    }


}
