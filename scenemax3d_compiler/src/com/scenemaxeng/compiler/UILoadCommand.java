package com.scenemaxeng.compiler;

/**
 * Compiler command for: UI.load "ui_system_name"
 *
 * Loads a .smui document and creates the JME node hierarchy.
 * The UI name is used as the key for subsequent UI access commands.
 */
public class UILoadCommand extends ActionStatementBase {

    public String uiName;       // the quoted name from UI.load "name"
    public String filePath;     // resolved file path (set during compilation)

    @Override
    public boolean validate(ProgramDef prg) {
        return uiName != null && !uiName.isEmpty();
    }
}
