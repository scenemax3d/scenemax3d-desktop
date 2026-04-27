package com.scenemaxeng.compiler;

import com.abware.scenemaxlang.parser.SceneMaxBaseVisitor;
import com.abware.scenemaxlang.parser.SceneMaxLexer;
import com.abware.scenemaxlang.parser.SceneMaxParser;
import com.abware.scenemaxlang.parser.SceneMaxParser.StatementContext;


import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SceneMaxLanguageParser implements IParser {

    public static MacroFilter macroFilter;
    public static boolean parseUsingResource = false;
    public static List<String> skyboxUsed = new ArrayList<>();
    public static List<String> terrainsUsed = new ArrayList<>();
    public static List<String> spriteSheetUsed = new ArrayList<>();
    public static List<String> modelsUsed = new ArrayList<>();
    public static List<String> effekseerUsed = new ArrayList<>();
    public static List<String> audioUsed = new ArrayList<>();
    public static List<String> fontsUsed = new ArrayList<>();
    public static List<String> filesUsed = new ArrayList<>();
    public static List<String> macroFilesUsed = new ArrayList<>();
    private static final HashMap<String, String> cinematicRigLocationCache = new HashMap<>();

    private ProgramDef prg = null;
    private String codePath="";

    private boolean isChildParser=false;
    private String _sourceFileName="";
    private static int foreachCounter=0; // implicit foreach function counter
    private ProgramDef previousProgramState;

    private static String trimQuotedString(String s) {
        if(s.length()>2) {
            s=s.substring(1,s.length()-1);
        }

        return s;
    }

    public static String readFile(File f) {

        String text = "";

        try {
            text = FileUtils.readFileToString(f,StandardCharsets.UTF_8);

        }catch(Exception ex) {

        }

        return text;
    }

    private static File resolveCodeDirectory(String codePath) {
        if (codePath == null || codePath.isBlank()) {
            return null;
        }
        File codeLocation = new File(codePath);
        File codeDir = codeLocation;
        if (codeLocation.exists()) {
            if (!codeLocation.isDirectory()) {
                codeDir = codeLocation.getParentFile();
            }
        } else if (codeLocation.getName().contains(".")) {
            codeDir = codeLocation.getParentFile();
        }
        return codeDir;
    }

    private static List<File> collectCinematicSearchRoots(String codePath) {
        LinkedHashSet<File> roots = new LinkedHashSet<>();
        File codeDir = resolveCodeDirectory(codePath);
        if (codeDir == null) {
            return new ArrayList<>();
        }

        File current = codeDir;
        while (current != null) {
            try {
                roots.add(current.getCanonicalFile());
            } catch (IOException ignored) {
                roots.add(current.getAbsoluteFile());
            }
            current = current.getParentFile();
        }

        return new ArrayList<>(roots);
    }

    private static String findCinematicRigInDirectory(File searchRoot, String runtimeId) {
        if (searchRoot == null || !searchRoot.exists() || runtimeId == null || runtimeId.isBlank()) {
            return null;
        }

        Collection<File> designerFiles = FileUtils.listFiles(searchRoot, new String[]{"smdesign"}, true);
        for (File designerFile : designerFiles) {
            try {
                String raw = FileUtils.readFileToString(designerFile, StandardCharsets.UTF_8);
                JSONObject root = new JSONObject(raw);
                if (containsCinematicRigRuntimeId(root.optJSONArray("entities"), runtimeId)) {
                    return designerFile.getAbsolutePath();
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private static String findCinematicRigSourceFile(String codePath, String runtimeId) {
        if (runtimeId == null || runtimeId.isBlank()) {
            return null;
        }
        String cacheKey = (codePath != null ? codePath : "") + "|" + runtimeId.toLowerCase();
        if (cinematicRigLocationCache.containsKey(cacheKey)) {
            return cinematicRigLocationCache.get(cacheKey);
        }

        List<File> searchRoots = collectCinematicSearchRoots(codePath);
        if (searchRoots.isEmpty()) {
            cinematicRigLocationCache.put(cacheKey, null);
            return null;
        }

        Set<String> visitedRoots = new LinkedHashSet<>();
        for (File root : searchRoots) {
            String canonicalPath;
            try {
                canonicalPath = root.getCanonicalPath();
            } catch (IOException e) {
                canonicalPath = root.getAbsolutePath();
            }
            if (!visitedRoots.add(canonicalPath)) {
                continue;
            }

            String resolved = findCinematicRigInDirectory(root, runtimeId);
            if (resolved != null) {
                cinematicRigLocationCache.put(cacheKey, resolved);
                return resolved;
            }
        }

        cinematicRigLocationCache.put(cacheKey, null);
        return null;
    }

    private static boolean containsCinematicRigRuntimeId(JSONArray entities, String runtimeId) {
        if (entities == null || runtimeId == null || runtimeId.isBlank()) {
            return false;
        }
        for (int i = 0; i < entities.length(); i++) {
            JSONObject entity = entities.optJSONObject(i);
            if (entity == null) {
                continue;
            }
            if ("CINEMATIC_RIG".equals(entity.optString("type", ""))) {
                String candidate = entity.optString("cinematicRuntimeId", entity.optString("id", ""));
                if (runtimeId.equalsIgnoreCase(candidate)) {
                    return true;
                }
            }
            if (containsCinematicRigRuntimeId(entity.optJSONArray("children"), runtimeId)) {
                return true;
            }
        }
        return false;
    }

    public SceneMaxLanguageParser(ProgramDef prg, String codePath) {
        super();
        this.prg = prg;
        this.codePath=codePath;
    }

    public SceneMaxLanguageParser(ProgramDef prg) {
        super();
        this.prg = prg;
    }

    public void enableChildParserMode(boolean enable) {
        this.isChildParser=enable;
    }

    public void setCurrentProgramState(ProgramDef prg) {
        this.previousProgramState = prg;
    }

    public ProgramDef parse(String code) {

        // Only main parser cleans the collections
        if(!isChildParser) {
            macroFilesUsed.clear();
            filesUsed.clear();
            fontsUsed.clear();
            audioUsed.clear();
            modelsUsed.clear();
            effekseerUsed.clear();
            spriteSheetUsed.clear();
            cinematicRigLocationCache.clear();
        }

        final List<String> errors = new ArrayList<>();

        if(macroFilter!=null) {
            ApplyMacroResults mr = macroFilter.apply(code);
            code=mr.finalPrg;

            for(String fileName:mr.macroFilesUsed) {
                if(!macroFilesUsed.contains(fileName)) {
                    macroFilesUsed.add(fileName);
                }
            }
        }

        CharStream charStream = new ANTLRInputStream(code);
        SceneMaxLexer lexer = new SceneMaxLexer(charStream);
        TokenStream tokens = new CommonTokenStream(lexer);
        SceneMaxParser parser = new SceneMaxParser(tokens);

        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(final Recognizer<?,?> recognizer, final Object offendingSymbol, final int line,
                                    final int charPositionInLine, final String msg, final RecognitionException e) {

                String err = "line: " + line + ", offset: " + charPositionInLine +
                        " " + msg;
                if(_sourceFileName.length()>0) {
                    err="File: " +_sourceFileName+", "+err;
                }
                errors.add(err);
            }
        });


        ProgramVisitor v = new ProgramVisitor(this.prg, this.codePath);
        ProgramDef prg = v.visit(parser.prog());

        if(errors.size()>0) {
            if(prg==null){
                prg=new ProgramDef();
            }
            prg.syntaxErrors.addAll(errors);
        }

        return prg;
    }

    public static void mergeExternalCode(ProgramDef prg, ProgramDef extPrg) {

        prg.syntaxErrors.addAll(extPrg.syntaxErrors);
        prg.groups.putAll(extPrg.groups);
        if (prg.screenMode == ProgramDef.ScreenMode.UNSPECIFIED
                && extPrg.screenMode != ProgramDef.ScreenMode.UNSPECIFIED) {
            prg.screenMode = extPrg.screenMode;
        }

        if(prg.inParams!=null && extPrg.inParams!=null) {
            prg.inParams.addAll(extPrg.inParams);
        }
        prg.vars.addAll(extPrg.vars);
        prg.vars_index.putAll(extPrg.vars_index);
        prg.functions.putAll(extPrg.functions);
        prg.models.putAll(extPrg.models);
        prg.sprites.putAll(extPrg.sprites);
        prg.actions.addAll(extPrg.actions);
    }

    public static void setMacroFilter(MacroFilter mf) {
        macroFilter=mf;
    }

    public static MacroFilter getMacroFilter() {
        return macroFilter;
    }

    private class ProgramVisitor extends SceneMaxBaseVisitor<ProgramDef> {

        private ProgramDef prg;
        private String codePath="";


        public ProgramVisitor(ProgramDef prg, String codePath) {
            this.prg = prg;
            if(codePath!=null) {
                this.codePath=codePath;
            }
        }

        @Override
        public ProgramDef visitProg(SceneMaxParser.ProgContext ctx) {

            if(this.prg!=null) {
                ProgramDef prg = new ProgramDef();
                prg.parent = this.prg;

                ProgramStatementsVisitor prgStatementsVisitor = new ProgramStatementsVisitor(prg, this.codePath);
                return prgStatementsVisitor.visit(ctx.program_statements());
            }


            ProgramDef prg = null;

            ProgramStatementsVisitor prgStatementsVisitor = new ProgramStatementsVisitor(null,this.codePath);
            prg = prgStatementsVisitor.visit(ctx.program_statements());

            return prg;

        }

        private class ProgramStatementsVisitor extends SceneMaxBaseVisitor<ProgramDef> {

            private ProgramDef prg;
            private String codePath="";

            public ProgramStatementsVisitor(ProgramDef prg, String codePath) {
                this.prg = prg;
                if(codePath!=null) {
                    this.codePath=codePath;
                }
            }

            @Override
            public ProgramDef visitProgram_statements(SceneMaxParser.Program_statementsContext ctx) {

                ProgramDef prg = new ProgramDef();
                prg.parent = this.prg;

                // root program should have a camera variable
                if(prg.parent==null) {
                    prg.addCameraVariableDef();
                    prg.copySharedEntities(SceneMaxLanguageParser.this.previousProgramState);
                }

                DefineStatementVisitor defineStatementVisitor = new DefineStatementVisitor(prg,this.codePath);

                List<StatementContext> statements = ctx.statement();
                for (StatementContext st : statements) {

                    StatementDef stDef = st.accept(defineStatementVisitor);
                    if(stDef==null) {

                        //return null;
                        continue;
                    }

                    if(stDef instanceof ForEachCommand) {
                        ForEachCommand fec = (ForEachCommand)stDef;
                        prg.functions.put(fec.funcDef.name,fec.funcDef);
                        prg.actions.add(fec);
                    } else if(stDef instanceof FunctionBlockDef) {
                        FunctionBlockDef fd = (FunctionBlockDef)stDef;
                        prg.functions.put(fd.name,fd);
                        //System.out.println("function def=" + fd.name);

                    } else if(stDef instanceof ModelDef) {
                        ModelDef md = (ModelDef)stDef;
                        prg.models.put(md.name, md);

                    } else if(stDef instanceof SpriteDef) {
                        SpriteDef def = (SpriteDef)stDef;

                        if(def.varName!=null) {

                            if(def.name!=null) {
                                prg.sprites.put(def.name, def);
                            }

                            VariableDef var = new VariableDef();
                            var.varType=VariableDef.VAR_TYPE_2D;
                            var.resName=def.name;
                            var.resNameExpr = def.nameExpr;
                            var.varName = def.varName;
                            var.xExpr=def.xExpr;
                            var.yExpr=def.yExpr;
                            var.zExpr=def.zExpr;
                            var.entityPos=def.entityPos;

                            prg.vars.add(var);
                            prg.vars_index.put(var.varName, var);

                            CreateSpriteCommand cmd = new CreateSpriteCommand(def,var);
                            prg.actions.add(cmd);

                        }

                    } else if(stDef instanceof VariableDef) {
                        VariableDef var = (VariableDef)stDef;
                        GraphicEntityCreationCommand cmd = new GraphicEntityCreationCommand(var);

                        if(var.varType == VariableDef.VAR_TYPE_EFFEKSEER) {
                            prg.vars.add(var);
                            prg.vars_index.put(var.varName, var);
                            prg.actions.add(cmd);
                        } else if (var.varType == VariableDef.VAR_TYPE_CINEMATIC_CAMERA) {
                            if (isChildParser) {
                                prg.vars.add(var);
                                prg.vars_index.put(var.varName, var);
                                prg.actions.add(cmd);
                            } else if (!var.validate(prg)) {
                                CinematicCameraVariableDef cinematicVar = (CinematicCameraVariableDef) var;
                                prg.syntaxErrors.add("Cinematic rig '" + cinematicVar.cinematicCameraId
                                        + "' was not found in any designer scene under the project");
                            } else {
                                prg.vars.add(var);
                                prg.vars_index.put(var.varName, var);
                                prg.actions.add(cmd);
                            }
                        } else if(!var.validate(prg)) {
                            // assume implicit declaration of a 3d model
                            var.varType=VariableDef.VAR_TYPE_3D;

                            if(var.resName!=null) {
                                ModelDef md = new ModelDef();
                                md.name = var.resName;
                                md.from = "";
                                md.isVehicle = var.isVehicle;
                                prg.models.put(md.name, md);

                                if (!modelsUsed.contains(md.name)) {
                                    modelsUsed.add(md.name);
                                }
                            }

                            prg.vars.add(var);
                            prg.vars_index.put(var.varName, var);
                            prg.actions.add(cmd);
                        } else {
                            prg.vars.add(var);
                            prg.vars_index.put(var.varName, var);
                            prg.actions.add(cmd);
                        }

                    } else if(stDef instanceof VariableDeclarationCommand) {

                        VariableDeclarationCommand varDecl = (VariableDeclarationCommand)stDef;
                        VariableAssignmentCommand vac = varDecl.toVarAssignment(prg);
                        vac.triggeredByDeclaration = true;
                        prg.actions.add(vac);

                    } else {
                        ActionStatementBase base = (ActionStatementBase)stDef;
                        if(base.validate(prg)) {
                            prg.actions.add(stDef);
                            if(stDef.requireResource) {
                                prg.requireResourceActions.add(stDef);
                            }
                        } else {
                            String err = "";
                            if(_sourceFileName.length()>0) {
                                err="Error: at file: "+_sourceFileName+": ";
                            }
                            prg.syntaxErrors.add(err+base.lastError+" at line: "+ctx.start.getLine());
                            //return null;
                        }
                    }

                }

                if(prg.parent!=null && prg.syntaxErrors.size()>0) {
                    prg.parent.syntaxErrors.addAll(prg.syntaxErrors);
                }
                return prg;

            }
        }


        private class DefineStatementVisitor extends SceneMaxBaseVisitor<StatementDef> {

            private final ProgramDef prg;
            private String codePath="";

            public DefineStatementVisitor(ProgramDef prg, String codePath) {
                this.prg=prg;
                if(codePath!=null) {
                    this.codePath=codePath;
                }
            }

            public ActionStatementBase visitPluginsAction(SceneMaxParser.PluginsActionContext ctx) {
                PluginActionCommand cmd = new PluginActionCommand();
                cmd.pluginName = ctx.plugins_actions().plugin_name().var_decl().getText();
                if (ctx.plugins_actions().plugin_action().Start() != null) {
                    cmd.action = PluginActionCommand.Actions.Start;
                } else if (ctx.plugins_actions().plugin_action().Stop() != null) {
                    cmd.action = PluginActionCommand.Actions.Stop;
                }
                return cmd;

            }

            public ActionStatementBase visitSwitchStatement(SceneMaxParser.SwitchStatementContext ctx) {
                SwitchStateCommand cmd = new SwitchStateCommand();
                cmd.pathExpr = ctx.switch_statement().logical_expression();
                return cmd;
            }

            public StatementDef visitUiStatement(SceneMaxParser.UiStatementContext ctx) {
                SceneMaxParser.Ui_statementContext uiCtx = ctx.ui_statement();

                if (uiCtx.ui_load() != null) {
                    UILoadCommand cmd = new UILoadCommand();
                    String quotedName = uiCtx.ui_load().QUOTED_STRING().getText();
                    cmd.uiName = stripQutes(quotedName);
                    // Resolve file path relative to the active script directory.
                    // In the runtime projector, codePath is usually the script folder,
                    // not the script file itself.
                    if (codePath != null && !codePath.isEmpty()) {
                        java.io.File codeLocation = new java.io.File(codePath);
                        java.io.File codeDir = codeLocation;
                        if (codeLocation.exists()) {
                            if (!codeLocation.isDirectory()) {
                                codeDir = codeLocation.getParentFile();
                            }
                        } else if (codeLocation.getName().contains(".")) {
                            codeDir = codeLocation.getParentFile();
                        }
                        if (codeDir != null) {
                            cmd.filePath = new java.io.File(codeDir, cmd.uiName + ".smui").getAbsolutePath();
                        }
                    }
                    return cmd;

                } else if (uiCtx.ui_show_hide() != null) {
                    SceneMaxParser.Ui_show_hideContext shCtx = uiCtx.ui_show_hide();
                    List<SceneMaxParser.Var_declContext> pathParts = shCtx.ui_dot_path().var_decl();
                    if (pathParts.isEmpty()) return null;

                    UIShowHideCommand cmd = new UIShowHideCommand();
                    cmd.show = shCtx.Show() != null;
                    int widgetStartIndex;

                    if (pathParts.size() == 1) {
                        cmd.uiName = null;
                        cmd.layerName = pathParts.get(0).getText();
                        widgetStartIndex = 1;
                    } else {
                        cmd.uiName = pathParts.get(0).getText();
                        cmd.layerName = pathParts.get(1).getText();
                        widgetStartIndex = 2;
                    }

                    // Build widget path from remaining segments after the layer name.
                    StringBuilder widgetPath = new StringBuilder();
                    for (int i = widgetStartIndex; i < pathParts.size(); i++) {
                        if (widgetPath.length() > 0) widgetPath.append(".");
                        widgetPath.append(pathParts.get(i).getText());
                    }
                    cmd.widgetPath = widgetPath.toString();
                    return cmd;

                } else if (uiCtx.ui_set_property() != null) {
                    SceneMaxParser.Ui_set_propertyContext propCtx = uiCtx.ui_set_property();
                    List<SceneMaxParser.Var_declContext> pathParts = propCtx.ui_dot_path().var_decl();
                    if (pathParts.size() < 2) return null;

                    UISetPropertyCommand cmd = new UISetPropertyCommand();
                    cmd.propertyName = propCtx.ui_property_name().var_decl().getText();
                    cmd.valueExpr = propCtx.logical_expression();
                    int widgetStartIndex;

                    if (pathParts.size() == 2) {
                        cmd.uiName = null;
                        cmd.layerName = pathParts.get(0).getText();
                        widgetStartIndex = 1;
                    } else {
                        cmd.uiName = pathParts.get(0).getText();
                        cmd.layerName = pathParts.get(1).getText();
                        widgetStartIndex = 2;
                    }

                    // Build widget path from remaining segments after the layer name.
                    StringBuilder widgetPath = new StringBuilder();
                    for (int i = widgetStartIndex; i < pathParts.size(); i++) {
                        if (widgetPath.length() > 0) widgetPath.append(".");
                        widgetPath.append(pathParts.get(i).getText());
                    }
                    cmd.widgetPath = widgetPath.toString();
                    return cmd;
                } else if (uiCtx.ui_set_default_property() != null) {
                    SceneMaxParser.Ui_set_default_propertyContext propCtx = uiCtx.ui_set_default_property();
                    List<SceneMaxParser.Var_declContext> pathParts = propCtx.ui_dot_path().var_decl();
                    if (pathParts.size() < 2) return null;

                    UISetPropertyCommand cmd = new UISetPropertyCommand();
                    cmd.implicitWidgetValue = true;
                    cmd.valueExpr = propCtx.logical_expression();
                    int widgetStartIndex;

                    if (pathParts.size() == 2) {
                        cmd.uiName = null;
                        cmd.layerName = pathParts.get(0).getText();
                        widgetStartIndex = 1;
                    } else {
                        cmd.uiName = pathParts.get(0).getText();
                        cmd.layerName = pathParts.get(1).getText();
                        widgetStartIndex = 2;
                    }

                    StringBuilder widgetPath = new StringBuilder();
                    for (int i = widgetStartIndex; i < pathParts.size(); i++) {
                        if (widgetPath.length() > 0) widgetPath.append(".");
                        widgetPath.append(pathParts.get(i).getText());
                    }
                    cmd.widgetPath = widgetPath.toString();
                    return cmd;
                } else if (uiCtx.ui_message() != null) {
                    SceneMaxParser.Ui_messageContext msgCtx = uiCtx.ui_message();
                    List<SceneMaxParser.Var_declContext> pathParts = msgCtx.ui_dot_path().var_decl();
                    if (pathParts.size() < 2) return null;

                    UIMessageCommand cmd = new UIMessageCommand();
                    cmd.messageExpr = msgCtx.logical_expression(0);
                    cmd.durationExpr = msgCtx.logical_expression(1);
                    cmd.isAsync = msgCtx.async_expr() != null;
                    for (SceneMaxParser.Ui_text_effect_flagContext effectCtx : msgCtx.ui_text_effect().ui_text_effect_flag()) {
                        cmd.effectNames.add(effectCtx.var_decl().getText());
                    }
                    int widgetStartIndex;

                    if (pathParts.size() == 2 || pathParts.get(0).getText().toLowerCase().startsWith("layer")) {
                        cmd.uiName = null;
                        cmd.layerName = pathParts.get(0).getText();
                        widgetStartIndex = 1;
                    } else {
                        cmd.uiName = pathParts.get(0).getText();
                        cmd.layerName = pathParts.get(1).getText();
                        widgetStartIndex = 2;
                    }

                    StringBuilder widgetPath = new StringBuilder();
                    for (int i = widgetStartIndex; i < pathParts.size(); i++) {
                        if (widgetPath.length() > 0) widgetPath.append(".");
                        widgetPath.append(pathParts.get(i).getText());
                    }
                    cmd.widgetPath = widgetPath.toString();
                    return cmd;
                } else if (uiCtx.ui_ease() != null) {
                    SceneMaxParser.Ui_easeContext easeCtx = uiCtx.ui_ease();
                    List<SceneMaxParser.Var_declContext> pathParts = easeCtx.ui_dot_path().var_decl();
                    if (pathParts.isEmpty()) return null;

                    UIEaseCommand cmd = new UIEaseCommand();
                    cmd.easingExpr = easeCtx.logical_expression(0);
                    cmd.durationExpr = easeCtx.logical_expression(1);
                    cmd.directionName = easeCtx.ui_ease_direction().getText();
                    int widgetStartIndex;

                    if (pathParts.size() == 1 || pathParts.get(0).getText().toLowerCase().startsWith("layer")) {
                        cmd.uiName = null;
                        cmd.layerName = pathParts.get(0).getText();
                        widgetStartIndex = 1;
                    } else {
                        cmd.uiName = pathParts.get(0).getText();
                        cmd.layerName = pathParts.get(1).getText();
                        widgetStartIndex = 2;
                    }

                    StringBuilder widgetPath = new StringBuilder();
                    for (int i = widgetStartIndex; i < pathParts.size(); i++) {
                        if (widgetPath.length() > 0) widgetPath.append(".");
                        widgetPath.append(pathParts.get(i).getText());
                    }
                    cmd.widgetPath = widgetPath.toString();
                    return cmd;
                }

                return null;
            }

            public ActionStatementBase visitWhenStatement(SceneMaxParser.WhenStatementContext ctx) {
                WhenStateCommand cmd = new WhenStateCommand();
                cmd.whenExpr.addAll(ctx.when_statement().logical_expression_sequence().logical_expression());
                DoBlockCommand doBlock = new DoBlockVisitor(prg, null).visit(ctx.when_statement().do_block());
                cmd.doBlock = doBlock;
                return cmd;
            }

            public ActionStatementBase visitHttpStatement(SceneMaxParser.HttpStatementContext ctx) {

                HttpCommand cmd = new HttpCommand();
                SceneMaxParser.Http_getContext getCtx = ctx.http_statement().http_action().http_get();
                if(getCtx!=null) {
                    cmd.verb = HttpCommand.VERB_TYPE_GET;
                    cmd.addressExp = getCtx.http_address().logical_expression();
                    cmd.callbackProcName = getCtx.res_var_decl().getText();
                    return cmd;
                }

                SceneMaxParser.Http_postContext postCtx = ctx.http_statement().http_action().http_post();
                if(postCtx!=null) {
                    cmd.verb = HttpCommand.VERB_TYPE_POST;
                    cmd.addressExp = postCtx.http_address().logical_expression();
                    cmd.bodyExp = postCtx.http_body().logical_expression();
                    cmd.callbackProcName = postCtx.res_var_decl().getText();
                    return cmd;
                }

                SceneMaxParser.Http_putContext putCtx = ctx.http_statement().http_action().http_put();
                if(putCtx!=null) {
                    cmd.verb = HttpCommand.VERB_TYPE_PUT;
                    cmd.addressExp = putCtx.http_address().logical_expression();
                    cmd.bodyExp = putCtx.http_body().logical_expression();
                    cmd.callbackProcName = putCtx.res_var_decl().getText();
                    return cmd;
                }

                return null;

            }

            public ActionStatementBase visitForStatement(SceneMaxParser.ForStatementContext ctx) {
                ForCommand cmd = new ForCommand();

                VariableDeclarationCommand vdc = new VariableDeclarationCommand();
                vdc.siblings = new ArrayList<>();
                for(SceneMaxParser.Variable_name_and_assignemtContext v : ctx.for_statement().declare_variable().variable_name_and_assignemt()) {
                    VariableDeclarationCommand var = new VariableDeclarationCommand();
                    var.varName = v.res_var_decl().getText();
                    var.valExpr = v.var_value_option().single_value_option().logical_expression();
                    vdc.siblings.add(var);
                }

                cmd.declareIndexCommand = vdc.toVarAssignment(prg);
                cmd.stopConditionExpr = ctx.for_statement().stop_condition();
                cmd.incrementIndexCommand = new ModifyVariableVisitor(prg).visitModify_variable (ctx.for_statement().modify_variable());
                cmd.doBlock = new DoBlockVisitor(prg, null).visit(ctx.for_statement().do_block());

                return cmd;
            }

            public ActionStatementBase visitForEachStatement(SceneMaxParser.ForEachStatementContext ctx) {

                ForEachCommand cmd = new ForEachCommand();
                String functionParamName = ctx.for_each_statement().var_decl().getText();
                List<String> params = new ArrayList<>();
                params.add(functionParamName);

                if (ctx.for_each_statement().for_each_in_target() != null) {
                    cmd.targetCollectionExpr = ctx.for_each_statement().for_each_in_target().logical_expression();
                }

                DoBlockCommand doBlock = new DoBlockVisitor(prg, params).visit(ctx.for_each_statement().do_block());

                FunctionBlockDef fdef = new FunctionBlockDef();
                fdef.doBlock = doBlock;
                fdef.name="foreach_"+ ++foreachCounter;
                cmd.funcDef = fdef;

                if(ctx.for_each_statement().entity_type()!=null) {
                    String type = ctx.for_each_statement().entity_type().getText().toLowerCase();
                    if(type.equals("model")) {
                        cmd.entityType = VariableDef.VAR_TYPE_3D;
                    } else if(type.equals("sprite")) {
                        cmd.entityType = VariableDef.VAR_TYPE_2D;
                    } else if(type.equals("box")) {
                        cmd.entityType = VariableDef.VAR_TYPE_BOX;
                    } else if(type.equals("sphere")) {
                        cmd.entityType = VariableDef.VAR_TYPE_SPHERE;
                    } else if(type.equals("cylinder")) {
                        cmd.entityType = VariableDef.VAR_TYPE_CYLINDER;
                    } else if(type.equals("hollowcylinder")) {
                        cmd.entityType = VariableDef.VAR_TYPE_HOLLOW_CYLINDER;
                    } else if(type.equals("quad")) {
                        cmd.entityType = VariableDef.VAR_TYPE_QUAD;
                    } else if(type.equals("wedge")) {
                        cmd.entityType = VariableDef.VAR_TYPE_WEDGE;
                    } else if(type.equals("cone")) {
                        cmd.entityType = VariableDef.VAR_TYPE_CONE;
                    } else if(type.equals("stairs")) {
                        cmd.entityType = VariableDef.VAR_TYPE_STAIRS;
                    } else if(type.equals("arch")) {
                        cmd.entityType = VariableDef.VAR_TYPE_ARCH;
                    }
                }

                if(ctx.for_each_statement().for_each_having_expr()!=null) {
                    for(SceneMaxParser.For_each_having_attrContext attr : ctx.for_each_statement().for_each_having_expr().for_each_having_attr()) {
                        if(attr.for_each_name_attr()!=null) {
                            cmd.name = attr.for_each_name_attr().QUOTED_STRING().getText();
                            if(cmd.name.length()>2) {
                                cmd.name = cmd.name.substring(1, cmd.name.length() - 1);
                            }

                            if(attr.for_each_name_attr().string_comparators()!=null) {
                                cmd.nameComparator = attr.for_each_name_attr().string_comparators().getText().toLowerCase();
                            }
                        }
                    }
                }

                return cmd;
            }

            public ActionStatementBase visitMiniMapActions(SceneMaxParser.MiniMapActionsContext ctx) {

                MiniMapCommand cmd = new MiniMapCommand();
                cmd.show = ctx.mini_map_actions().show_or_hide().Show()!=null;

                if(ctx.mini_map_actions().minimap_options()!=null) {
                    for (SceneMaxParser.Minimap_optionContext attr : ctx.mini_map_actions().minimap_options().minimap_option()) {
                        if (attr.height_attr() != null) {
                            cmd.heightExpr = attr.height_attr().logical_expression();
                        } else if (attr.unisize_attr() != null) {
                            cmd.sizeExpr = attr.unisize_attr().logical_expression();
                        } else if (attr.follow_entity()!=null) {
                            cmd.targetVar = attr.follow_entity().var_decl().getText();
                        }
                    }

                }
                return cmd;
            }

            public ActionStatementBase visitChannelDraw(SceneMaxParser.ChannelDrawContext ctx) {

                ChannelDrawCommand cmd = new ChannelDrawCommand();

                cmd.channelName = ctx.channel_draw_statement().res_var_decl().getText();//
                cmd.resourceName = ctx.channel_draw_statement().sprite_name().getText();

                if(ctx.channel_draw_statement().channel_draw_attrs()!=null) {
                    for(SceneMaxParser.Channel_draw_attrContext attr : ctx.channel_draw_statement().channel_draw_attrs().channel_draw_attr()) {
                        if(attr.frame_attr()!=null) {
                            cmd.frameNumExpr = attr.frame_attr().logical_expression();
                        } else if(attr.pos_2d_attr()!=null) {
                            cmd.posXExpr =attr.pos_2d_attr().pos_axes_2d().print_pos_x().logical_expression();
                            cmd.posYExpr =attr.pos_2d_attr().pos_axes_2d().print_pos_y().logical_expression();
                        } else if(attr.size_2d_attr()!=null) {
                            cmd.widthExpr = attr.size_2d_attr().width_size().logical_expression();
                            cmd.heightExpr = attr.size_2d_attr().height_size().logical_expression();
                        }
                    }
                }

                if(cmd.resourceName!=null && !cmd.resourceName.equalsIgnoreCase("clear")) {
                    if (!spriteSheetUsed.contains(cmd.resourceName)) {
                        spriteSheetUsed.add(cmd.resourceName);
                    }
                }

                return cmd;


            }

            public ActionStatementBase visitAddExternalCode(SceneMaxParser.AddExternalCodeContext ctx) {

                List<String> files = new ArrayList<>();
                for(SceneMaxParser.File_nameContext file:ctx.add_external_code().file_name()) {
                    String name = file.QUOTED_STRING().getText();
                    if(name.length()>0) {
                        name=name.substring(1,name.length()-1);
                    }

                    files.add(name);
                }

                for (String file: files) {

                    if(filesUsed.contains(file)) {
                        continue; // prevent cyclic dependency
                    }

                    // get code from file or resource
                    File resolvedFile = resolveExternalCodeFile(file);
                    String code = getExternalCode(file, resolvedFile);
                    if(code!=null) {
                        String childCodePath = resolvedFile != null ? resolvedFile.getAbsolutePath() : this.codePath;
                        SceneMaxLanguageParser parser = new SceneMaxLanguageParser(this.prg, childCodePath);
                        parser.setParserSourceFileName(file);
                        parser.enableChildParserMode(true);
                        //parser.setMacroFilter(SceneMaxLanguageParser.getMacroFilter());
                        ProgramDef prg = parser.parse(code);
                        filesUsed.add(file);

                        SceneMaxLanguageParser.mergeExternalCode(this.prg,prg);
                    }

                }

                return null;

            }

            private File resolveExternalCodeFile(String file) {
                File baseDir = resolveCodeDirectory(this.codePath);
                if (baseDir == null) {
                    return null;
                }
                File candidate = new File(baseDir, file);
                return candidate.exists() ? candidate : null;
            }

            private String getExternalCode(String file, File resolvedFile) {

                String code = null;
                // first, search code in file system
                if(resolvedFile != null && resolvedFile.exists()) {
                    code = readFile(resolvedFile);
                } else {
                    // code not exists in FS so search the in JAR itself (as a resource)
                    if (!file.startsWith("/")) {
                        file = "/" + file;
                    }
                    InputStream script = SceneMaxLanguageParser.class.getResourceAsStream("/running"+file);
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

            public StatementDef visitDeclareVariable(SceneMaxParser.DeclareVariableContext ctx) {

                VariableDeclarationCommand cmd = new VariableDeclarationCommand();
                cmd.siblings = new ArrayList<>();
                boolean isShared = ctx.declare_variable().Shared() != null;

                for(SceneMaxParser.Variable_name_and_assignemtContext v : ctx.declare_variable().variable_name_and_assignemt()) {
                    VariableDeclarationCommand var = new VariableDeclarationCommand();
                    var.isShared = isShared;
                    var.isExprPointer = v.Commat() != null;
                    var.varName = v.res_var_decl().getText();
                    if (v.var_range_option()!=null) {
                        if(v.var_range_option().min_num_value()!=null) {
                            var.minExpr = v.var_range_option().min_num_value().logical_expression();
                        }
                        if(v.var_range_option().max_num_value()!=null) {
                            var.maxExpr = v.var_range_option().max_num_value().logical_expression();
                        }
                    }
                    if(v.var_value_option().array_value()!=null) {
                        var.array = new ArrayList<>();
                        var.array.addAll(v.var_value_option().array_value().logical_expression());
                    } else {
                        var.valExpr = v.var_value_option().single_value_option().logical_expression();
                    }

                    cmd.siblings.add(var);
                }

                return cmd;
            }

            public ActionStatementBase visitDefineGroup(SceneMaxParser.DefineGroupContext ctx) {

                AddEntityToGroupCommand cmd = new AddEntityToGroupCommand();
                cmd.targetVar = ctx.define_group().res_var_decl().getText();
                cmd.targetGroup = ctx.define_group().group_name().getText();
                if(!prg.groups.containsKey(cmd.targetGroup)) {
                    prg.groups.put(cmd.targetGroup,new GroupDef(cmd.targetGroup));
                }
                return cmd;
            }

            public ActionStatementBase visitDebugStatement(SceneMaxParser.DebugStatementContext ctx) {
                ChangeDebugMode cmd = new ChangeDebugMode();
                if(ctx.debug_statement().debug_actions().debug_on()!=null) {
                    cmd.debugOn=true;
                } else {
                    cmd.debugOff=true;
                }

                return cmd;
            }

            public StatementDef visitAttachCameraActions(SceneMaxParser.AttachCameraActionsContext ctx) {
                FpsCameraCommand cmd = new FpsCameraCommand();

                cmd.command = ctx.attach_camera_actions().attach_camera_action().attach_camera_action_start() != null ?
                        FpsCameraCommand.START : FpsCameraCommand.STOP;

                String varName = null;

                if(cmd.command==FpsCameraCommand.START) {
                    SceneMaxParser.Attach_camera_action_startContext startCtx = ctx.attach_camera_actions().attach_camera_action().attach_camera_action_start();
                    varName = startCtx.var_decl().getText();

                    VariableDef vd = prg.getVar(varName);
                    if (vd == null) {
                        prg.syntaxErrors.add(_sourceFileName+": line " + ctx.start.getLine() + ", variable '" + varName + "' not exists");
                        return null;
                    }
                    cmd.varDef = vd;
                    cmd.targetVar = varName;

                    for(SceneMaxParser.Attach_camera_having_optionContext attr:startCtx.attach_camera_having_expr().attach_camera_having_options().attach_camera_having_option()) {
                        if(attr.camera_type_attr()!=null) {
                            cmd.cameraType = attr.camera_type_attr().camera_type().getText();
                        }  if(attr.damping_attr()!=null) {
                            cmd.dampingExpr = attr.damping_attr().logical_expression();
                        } else if(attr.replay_attr_offset()!=null) {
                            String axis = attr.replay_attr_offset().all_axes_names().getText();
                            if(axis.equalsIgnoreCase("x")) {
                                cmd.offsetXExpr = attr.replay_attr_offset().logical_expression();
                            } else if(axis.equalsIgnoreCase("y")) {
                                cmd.offsetYExpr = attr.replay_attr_offset().logical_expression();
                            } else if(axis.equalsIgnoreCase("z")) {
                                cmd.offsetZExpr = attr.replay_attr_offset().logical_expression();
                            } else if(axis.equalsIgnoreCase("rx")) {
                                cmd.offsetRXExpr = attr.replay_attr_offset().logical_expression();
                            } else if(axis.equalsIgnoreCase("ry")) {
                                cmd.offsetRYExpr = attr.replay_attr_offset().logical_expression();
                            } else if(axis.equalsIgnoreCase("rz")) {
                                cmd.offsetRZExpr = attr.replay_attr_offset().logical_expression();
                            }
                        }
                    }

                }

                return cmd;
            }

            public StatementDef visitChaseCameraActions(SceneMaxParser.ChaseCameraActionsContext ctx) {
                ChaseCameraCommand cmd = new ChaseCameraCommand();
                SceneMaxParser.Chase_camera_actionContext actionCtx = ctx.chase_camera_actions().chase_camera_action();
                SceneMaxParser.Chase_camera_action_chaseContext ctxChase = actionCtx.chase_camera_action_chase();

                if(ctxChase!=null) {

                    String varName = ctxChase.var_decl().getText();
                    VariableDef vd = prg.getVar(varName);
                    if(vd==null) {
                        prg.syntaxErrors.add(_sourceFileName+": line " + ctx.start.getLine() + ", variable '" + varName + "' not exists");
                        return null;
                    }

                    cmd.varDef=vd;
                    cmd.targetVar = vd.varName;
                    cmd.command = ChaseCameraCommand.CHASE;

                    SceneMaxParser.Chase_cam_having_exprContext having = ctxChase.chase_cam_having_expr();
                    if(having!=null) {
                        cmd.havingAttributesExists=true;
                        SceneMaxParser.Chase_cam_optionsContext options = having.chase_cam_options();
                        for(SceneMaxParser.Chase_cam_optionContext opt: options.chase_cam_option()) {
                            if(opt.chase_cam_option_trailing()!=null) {
                                if(opt.chase_cam_option_trailing().False()!=null) {
                                    cmd.trailing = false;
                                }
                            } else if(opt.chase_cam_option_rotation_speed()!=null) {
                                cmd.rotationSpeedExpr = opt.chase_cam_option_rotation_speed().logical_expression();
                            } else if(opt.chase_cam_option_vertical_rotation()!=null) {
                                cmd.verticalRotationExpr = opt.chase_cam_option_vertical_rotation().logical_expression();
                            } else if(opt.chase_cam_option_horizontal_rotation()!=null) {
                                cmd.horizontalRotationExpr = opt.chase_cam_option_horizontal_rotation().logical_expression();
                            } else if(opt.chase_cam_option_min_distance()!=null) {
                                cmd.minDistanceExpr = opt.chase_cam_option_min_distance().logical_expression();
                            } else if(opt.chase_cam_option_max_distance()!=null) {
                                cmd.maxDistanceExpr = opt.chase_cam_option_max_distance().logical_expression();
                            }
                        }
                    }

                    return cmd;
                }

                if(actionCtx.chase_camera_action_stop()!=null) {
                    cmd.command = ChaseCameraCommand.STOP;
                    return cmd;
                }


                return null;
            }

            public StatementDef visitTerrainActions(SceneMaxParser.TerrainActionsContext ctx) {

                TerrainCommand cmd = new TerrainCommand();
                SceneMaxParser.Terrain_actionContext ac = ctx.terrain_actions().terrain_action();
                if(ac.terrain_action_show()!=null) {
                    cmd.action = TerrainCommand.ACTION_SHOW;
                    cmd.terrainNameExprCtx = ac.terrain_action_show().logical_expression();
                } else if(ac.terrain_action_hide()!=null) {
                    cmd.action = TerrainCommand.ACTION_HIDE;
                }

                return cmd;
            }

            public StatementDef visitDefBox(SceneMaxParser.DefBoxContext ctx) {

                String varName = ctx.define_box().res_var_decl().getText();
                BoxVariableDef varDef = new BoxVariableDef();
                varDef.isShared = ctx.define_box().Shared() != null;
                varDef.varName = varName;
                varDef.resName="box";
                varDef.isStatic=ctx.define_box().Static()!=null;
                varDef.isCollider = ctx.define_box().Collider()!=null;

                if(ctx.define_box().box_having_expr()!=null) {
                    for(SceneMaxParser.Box_attrContext attr:ctx.define_box().box_having_expr().box_attributes().box_attr()) {

                        if(attr.model_attr()!=null) {
                            if(attr.model_attr().print_pos_attr()!=null) {
                                if(attr.model_attr().print_pos_attr().pos_axes()!=null) {

                                    if(attr.model_attr().print_pos_attr().pos_axes().exception!=null) {
                                        return null;
                                    }

                                    varDef.xExpr = attr.model_attr().print_pos_attr().pos_axes().print_pos_x().logical_expression();
                                    varDef.yExpr = attr.model_attr().print_pos_attr().pos_axes().print_pos_y().logical_expression();
                                    varDef.zExpr = attr.model_attr().print_pos_attr().pos_axes().print_pos_z().logical_expression();
                                } else {
                                    varDef.entityPos=new EntityPos();
                                    setEntityPos(varDef.entityPos,attr.model_attr().print_pos_attr().pos_entity());
                                }
                            } else if (attr.model_attr().init_rotate_attr()!=null) {
                                SceneMaxParser.Rot_axesContext rotAxes = attr.model_attr().init_rotate_attr().rot_axes();
                                if(rotAxes!=null) {
                                    varDef.rxExpr = rotAxes.rotate_x().logical_expression();//new ActionLogicalExpression(attr.init_rotate_attr().rotate_x().logical_expression(),prg);
                                    varDef.ryExpr = rotAxes.rotate_y().logical_expression();
                                    varDef.rzExpr = rotAxes.rotate_z().logical_expression();
                                } else {
                                    varDef.entityRot = attr.model_attr().init_rotate_attr().rot_entity().getText();
                                }
                            } else if(attr.model_attr().init_mass_attr()!=null) {
                                varDef.massExpr = attr.model_attr().init_mass_attr().logical_expression();
                            } else if(attr.model_attr().init_static_attr()!=null) {
                                varDef.isStatic=true;
                            } else if(attr.model_attr().init_hidden_attr()!=null) {
                                varDef.visible=false;
                            } else if(attr.model_attr().shadow_mode_attr()!=null) {
                                SceneMaxParser.Shadow_mode_optionsContext shadow_opts = attr.model_attr().shadow_mode_attr().shadow_mode_options();
                                if(shadow_opts.Cast()!=null) {
                                    varDef.shadowMode = 1;
                                } else if(shadow_opts.Receive()!=null) {
                                    varDef.shadowMode = 2;
                                } else {
                                    varDef.shadowMode = 3;
                                }
                            }
                        }


                        if(attr.box_specific_attr()!=null) {
                            if(attr.box_specific_attr().volume_size_attr()!=null) {
                                varDef.sizeX = attr.box_specific_attr().volume_size_attr().size_x().logical_expression();
                                varDef.sizeY = attr.box_specific_attr().volume_size_attr().size_y().logical_expression();
                                varDef.sizeZ = attr.box_specific_attr().volume_size_attr().size_z().logical_expression();
                            }

                            if(attr.box_specific_attr().material_attr()!=null) {
                                varDef.materialExpr = attr.box_specific_attr().material_attr().logical_expression();
                            }
                        }
                    }
                }

                return varDef;

            }

            public StatementDef visitDefCylinder(SceneMaxParser.DefCylinderContext ctx) {

                String varName = ctx.define_cylinder().res_var_decl().getText();
                CylinderVariableDef varDef = new CylinderVariableDef();
                varDef.isShared = ctx.define_cylinder().Shared() != null;
                varDef.varName = varName;
                varDef.resName="cylinder";
                varDef.isStatic=ctx.define_cylinder().Static()!=null;
                varDef.isCollider = ctx.define_cylinder().Collider()!=null;

                if(ctx.define_cylinder().cylinder_having_expr()!=null) {
                    for(SceneMaxParser.Cylinder_attrContext attr:ctx.define_cylinder().cylinder_having_expr().cylinder_attributes().cylinder_attr()) {
                        if(attr.model_attr()!=null) {
                            if(attr.model_attr().print_pos_attr()!=null) {
                                if(attr.model_attr().print_pos_attr().pos_axes()!=null) {
                                    if(attr.model_attr().print_pos_attr().pos_axes().exception!=null) {
                                        return null;
                                    }
                                    varDef.xExpr = attr.model_attr().print_pos_attr().pos_axes().print_pos_x().logical_expression();
                                    varDef.yExpr = attr.model_attr().print_pos_attr().pos_axes().print_pos_y().logical_expression();
                                    varDef.zExpr = attr.model_attr().print_pos_attr().pos_axes().print_pos_z().logical_expression();
                                } else {
                                    varDef.entityPos=new EntityPos();
                                    setEntityPos(varDef.entityPos,attr.model_attr().print_pos_attr().pos_entity());
                                }
                            } else if (attr.model_attr().init_rotate_attr()!=null) {
                                SceneMaxParser.Rot_axesContext rotAxes = attr.model_attr().init_rotate_attr().rot_axes();
                                if(rotAxes!=null) {
                                    varDef.rxExpr = rotAxes.rotate_x().logical_expression();
                                    varDef.ryExpr = rotAxes.rotate_y().logical_expression();
                                    varDef.rzExpr = rotAxes.rotate_z().logical_expression();
                                } else {
                                    varDef.entityRot = attr.model_attr().init_rotate_attr().rot_entity().getText();
                                }
                            } else if(attr.model_attr().init_mass_attr()!=null) {
                                varDef.massExpr = attr.model_attr().init_mass_attr().logical_expression();
                            } else if(attr.model_attr().init_static_attr()!=null) {
                                varDef.isStatic=true;
                            } else if(attr.model_attr().init_hidden_attr()!=null) {
                                varDef.visible=false;
                            } else if(attr.model_attr().shadow_mode_attr()!=null) {
                                SceneMaxParser.Shadow_mode_optionsContext shadow_opts = attr.model_attr().shadow_mode_attr().shadow_mode_options();
                                if(shadow_opts.Cast()!=null) {
                                    varDef.shadowMode = 1;
                                } else if(shadow_opts.Receive()!=null) {
                                    varDef.shadowMode = 2;
                                } else {
                                    varDef.shadowMode = 3;
                                }
                            }
                        }

                        if(attr.cylinder_specific_attr()!=null) {
                            if(attr.cylinder_specific_attr().cylinder_radius_attr()!=null) {
                                varDef.radiusTopExpr = attr.cylinder_specific_attr().cylinder_radius_attr().cylinder_radius_top().logical_expression();
                                varDef.radiusBottomExpr = attr.cylinder_specific_attr().cylinder_radius_attr().cylinder_radius_bottom().logical_expression();
                            }
                            if(attr.cylinder_specific_attr().cylinder_height_attr()!=null) {
                                varDef.heightExpr = attr.cylinder_specific_attr().cylinder_height_attr().logical_expression();
                            }
                            if(attr.cylinder_specific_attr().material_attr()!=null) {
                                varDef.materialExpr = attr.cylinder_specific_attr().material_attr().logical_expression();
                            }
                        }
                    }
                }

                return varDef;

            }

            public StatementDef visitDefHollowCylinder(SceneMaxParser.DefHollowCylinderContext ctx) {

                String varName = ctx.define_hollow_cylinder().res_var_decl().getText();
                HollowCylinderVariableDef varDef = new HollowCylinderVariableDef();
                varDef.isShared = ctx.define_hollow_cylinder().Shared() != null;
                varDef.varName = varName;
                varDef.resName="hollowcylinder";
                varDef.isStatic=ctx.define_hollow_cylinder().Static()!=null;
                varDef.isCollider = ctx.define_hollow_cylinder().Collider()!=null;

                if(ctx.define_hollow_cylinder().hollow_cylinder_having_expr()!=null) {
                    for(SceneMaxParser.Hollow_cylinder_attrContext attr:ctx.define_hollow_cylinder().hollow_cylinder_having_expr().hollow_cylinder_attributes().hollow_cylinder_attr()) {
                        if(attr.model_attr()!=null) {
                            if(attr.model_attr().print_pos_attr()!=null) {
                                if(attr.model_attr().print_pos_attr().pos_axes()!=null) {
                                    if(attr.model_attr().print_pos_attr().pos_axes().exception!=null) {
                                        return null;
                                    }
                                    varDef.xExpr = attr.model_attr().print_pos_attr().pos_axes().print_pos_x().logical_expression();
                                    varDef.yExpr = attr.model_attr().print_pos_attr().pos_axes().print_pos_y().logical_expression();
                                    varDef.zExpr = attr.model_attr().print_pos_attr().pos_axes().print_pos_z().logical_expression();
                                } else {
                                    varDef.entityPos=new EntityPos();
                                    setEntityPos(varDef.entityPos,attr.model_attr().print_pos_attr().pos_entity());
                                }
                            } else if (attr.model_attr().init_rotate_attr()!=null) {
                                SceneMaxParser.Rot_axesContext rotAxes = attr.model_attr().init_rotate_attr().rot_axes();
                                if(rotAxes!=null) {
                                    varDef.rxExpr = rotAxes.rotate_x().logical_expression();
                                    varDef.ryExpr = rotAxes.rotate_y().logical_expression();
                                    varDef.rzExpr = rotAxes.rotate_z().logical_expression();
                                } else {
                                    varDef.entityRot = attr.model_attr().init_rotate_attr().rot_entity().getText();
                                }
                            } else if(attr.model_attr().init_mass_attr()!=null) {
                                varDef.massExpr = attr.model_attr().init_mass_attr().logical_expression();
                            } else if(attr.model_attr().init_static_attr()!=null) {
                                varDef.isStatic=true;
                            } else if(attr.model_attr().init_hidden_attr()!=null) {
                                varDef.visible=false;
                            } else if(attr.model_attr().shadow_mode_attr()!=null) {
                                SceneMaxParser.Shadow_mode_optionsContext shadow_opts = attr.model_attr().shadow_mode_attr().shadow_mode_options();
                                if(shadow_opts.Cast()!=null) {
                                    varDef.shadowMode = 1;
                                } else if(shadow_opts.Receive()!=null) {
                                    varDef.shadowMode = 2;
                                } else {
                                    varDef.shadowMode = 3;
                                }
                            }
                        }

                        if(attr.hollow_cylinder_specific_attr()!=null) {
                            if(attr.hollow_cylinder_specific_attr().hollow_cylinder_radius_attr()!=null) {
                                varDef.radiusTopExpr = attr.hollow_cylinder_specific_attr().hollow_cylinder_radius_attr().cylinder_radius_top().logical_expression();
                                varDef.radiusBottomExpr = attr.hollow_cylinder_specific_attr().hollow_cylinder_radius_attr().cylinder_radius_bottom().logical_expression();
                            }
                            if(attr.hollow_cylinder_specific_attr().hollow_cylinder_inner_radius_attr()!=null) {
                                varDef.innerRadiusTopExpr = attr.hollow_cylinder_specific_attr().hollow_cylinder_inner_radius_attr().hollow_cylinder_inner_radius_top().logical_expression();
                                varDef.innerRadiusBottomExpr = attr.hollow_cylinder_specific_attr().hollow_cylinder_inner_radius_attr().hollow_cylinder_inner_radius_bottom().logical_expression();
                            }
                            if(attr.hollow_cylinder_specific_attr().cylinder_height_attr()!=null) {
                                varDef.heightExpr = attr.hollow_cylinder_specific_attr().cylinder_height_attr().logical_expression();
                            }
                            if(attr.hollow_cylinder_specific_attr().material_attr()!=null) {
                                varDef.materialExpr = attr.hollow_cylinder_specific_attr().material_attr().logical_expression();
                            }
                        }
                    }
                }

                return varDef;

            }

            public StatementDef visitDefQuad(SceneMaxParser.DefQuadContext ctx) {

                String varName = ctx.define_quad().res_var_decl().getText();
                QuadVariableDef varDef = new QuadVariableDef();
                varDef.isShared = ctx.define_quad().Shared() != null;
                varDef.varName = varName;
                varDef.resName="quad";
                varDef.isStatic=ctx.define_quad().Static()!=null;
                varDef.isCollider = ctx.define_quad().Collider()!=null;

                if(ctx.define_quad().quad_having_expr()!=null) {
                    for(SceneMaxParser.Quad_attrContext attr:ctx.define_quad().quad_having_expr().quad_attributes().quad_attr()) {
                        if(attr.model_attr()!=null) {
                            if(attr.model_attr().print_pos_attr()!=null) {
                                if(attr.model_attr().print_pos_attr().pos_axes()!=null) {
                                    if(attr.model_attr().print_pos_attr().pos_axes().exception!=null) {
                                        return null;
                                    }
                                    varDef.xExpr = attr.model_attr().print_pos_attr().pos_axes().print_pos_x().logical_expression();
                                    varDef.yExpr = attr.model_attr().print_pos_attr().pos_axes().print_pos_y().logical_expression();
                                    varDef.zExpr = attr.model_attr().print_pos_attr().pos_axes().print_pos_z().logical_expression();
                                } else {
                                    varDef.entityPos=new EntityPos();
                                    setEntityPos(varDef.entityPos,attr.model_attr().print_pos_attr().pos_entity());
                                }
                            } else if (attr.model_attr().init_rotate_attr()!=null) {
                                SceneMaxParser.Rot_axesContext rotAxes = attr.model_attr().init_rotate_attr().rot_axes();
                                if(rotAxes!=null) {
                                    varDef.rxExpr = rotAxes.rotate_x().logical_expression();
                                    varDef.ryExpr = rotAxes.rotate_y().logical_expression();
                                    varDef.rzExpr = rotAxes.rotate_z().logical_expression();
                                } else {
                                    varDef.entityRot = attr.model_attr().init_rotate_attr().rot_entity().getText();
                                }
                            } else if(attr.model_attr().init_mass_attr()!=null) {
                                varDef.massExpr = attr.model_attr().init_mass_attr().logical_expression();
                            } else if(attr.model_attr().init_static_attr()!=null) {
                                varDef.isStatic=true;
                            } else if(attr.model_attr().init_hidden_attr()!=null) {
                                varDef.visible=false;
                            } else if(attr.model_attr().shadow_mode_attr()!=null) {
                                SceneMaxParser.Shadow_mode_optionsContext shadow_opts = attr.model_attr().shadow_mode_attr().shadow_mode_options();
                                if(shadow_opts.Cast()!=null) {
                                    varDef.shadowMode = 1;
                                } else if(shadow_opts.Receive()!=null) {
                                    varDef.shadowMode = 2;
                                } else {
                                    varDef.shadowMode = 3;
                                }
                            }
                        }

                        if(attr.quad_specific_attr()!=null) {
                            if(attr.quad_specific_attr().quad_size_attr()!=null) {
                                varDef.widthExpr = attr.quad_specific_attr().quad_size_attr().quad_width().logical_expression();
                                varDef.heightExpr = attr.quad_specific_attr().quad_size_attr().quad_height().logical_expression();
                            }
                            if(attr.quad_specific_attr().material_attr()!=null) {
                                varDef.materialExpr = attr.quad_specific_attr().material_attr().logical_expression();
                            }
                        }
                    }
                }

                return varDef;

            }

            public StatementDef visitDefWedge(SceneMaxParser.DefWedgeContext ctx) {

                String varName = ctx.define_wedge().res_var_decl().getText();
                WedgeVariableDef varDef = new WedgeVariableDef();
                varDef.isShared = ctx.define_wedge().Shared() != null;
                varDef.varName = varName;
                varDef.resName="wedge";
                varDef.isStatic=ctx.define_wedge().Static()!=null;
                varDef.isCollider = ctx.define_wedge().Collider()!=null;

                if(ctx.define_wedge().wedge_having_expr()!=null) {
                    for(SceneMaxParser.Wedge_attrContext attr:ctx.define_wedge().wedge_having_expr().wedge_attributes().wedge_attr()) {
                        if(attr.model_attr()!=null) {
                            if(attr.model_attr().print_pos_attr()!=null) {
                                if(attr.model_attr().print_pos_attr().pos_axes()!=null) {
                                    if(attr.model_attr().print_pos_attr().pos_axes().exception!=null) {
                                        return null;
                                    }
                                    varDef.xExpr = attr.model_attr().print_pos_attr().pos_axes().print_pos_x().logical_expression();
                                    varDef.yExpr = attr.model_attr().print_pos_attr().pos_axes().print_pos_y().logical_expression();
                                    varDef.zExpr = attr.model_attr().print_pos_attr().pos_axes().print_pos_z().logical_expression();
                                } else {
                                    varDef.entityPos=new EntityPos();
                                    setEntityPos(varDef.entityPos,attr.model_attr().print_pos_attr().pos_entity());
                                }
                            } else if (attr.model_attr().init_rotate_attr()!=null) {
                                SceneMaxParser.Rot_axesContext rotAxes = attr.model_attr().init_rotate_attr().rot_axes();
                                if(rotAxes!=null) {
                                    varDef.rxExpr = rotAxes.rotate_x().logical_expression();
                                    varDef.ryExpr = rotAxes.rotate_y().logical_expression();
                                    varDef.rzExpr = rotAxes.rotate_z().logical_expression();
                                } else {
                                    varDef.entityRot = attr.model_attr().init_rotate_attr().rot_entity().getText();
                                }
                            } else if(attr.model_attr().init_mass_attr()!=null) {
                                varDef.massExpr = attr.model_attr().init_mass_attr().logical_expression();
                            } else if(attr.model_attr().init_static_attr()!=null) {
                                varDef.isStatic=true;
                            } else if(attr.model_attr().init_hidden_attr()!=null) {
                                varDef.visible=false;
                            } else if(attr.model_attr().shadow_mode_attr()!=null) {
                                SceneMaxParser.Shadow_mode_optionsContext shadowOpts = attr.model_attr().shadow_mode_attr().shadow_mode_options();
                                if(shadowOpts.Cast()!=null) {
                                    varDef.shadowMode = 1;
                                } else if(shadowOpts.Receive()!=null) {
                                    varDef.shadowMode = 2;
                                } else {
                                    varDef.shadowMode = 3;
                                }
                            }
                        }

                        if(attr.wedge_specific_attr()!=null) {
                            if(attr.wedge_specific_attr().volume_size_attr()!=null) {
                                varDef.sizeXExpr = attr.wedge_specific_attr().volume_size_attr().size_x().logical_expression();
                                varDef.sizeYExpr = attr.wedge_specific_attr().volume_size_attr().size_y().logical_expression();
                                varDef.sizeZExpr = attr.wedge_specific_attr().volume_size_attr().size_z().logical_expression();
                            }
                            if(attr.wedge_specific_attr().material_attr()!=null) {
                                varDef.materialExpr = attr.wedge_specific_attr().material_attr().logical_expression();
                            }
                        }
                    }
                }

                return varDef;

            }

            public StatementDef visitDefCone(SceneMaxParser.DefConeContext ctx) {

                String varName = ctx.define_cone().res_var_decl().getText();
                ConeVariableDef varDef = new ConeVariableDef();
                varDef.isShared = ctx.define_cone().Shared() != null;
                varDef.varName = varName;
                varDef.resName="cone";
                varDef.isStatic=ctx.define_cone().Static()!=null;
                varDef.isCollider = ctx.define_cone().Collider()!=null;

                if(ctx.define_cone().cone_having_expr()!=null) {
                    for(SceneMaxParser.Cone_attrContext attr:ctx.define_cone().cone_having_expr().cone_attributes().cone_attr()) {
                        if(attr.model_attr()!=null) {
                            if(attr.model_attr().print_pos_attr()!=null) {
                                if(attr.model_attr().print_pos_attr().pos_axes()!=null) {
                                    if(attr.model_attr().print_pos_attr().pos_axes().exception!=null) {
                                        return null;
                                    }
                                    varDef.xExpr = attr.model_attr().print_pos_attr().pos_axes().print_pos_x().logical_expression();
                                    varDef.yExpr = attr.model_attr().print_pos_attr().pos_axes().print_pos_y().logical_expression();
                                    varDef.zExpr = attr.model_attr().print_pos_attr().pos_axes().print_pos_z().logical_expression();
                                } else {
                                    varDef.entityPos=new EntityPos();
                                    setEntityPos(varDef.entityPos,attr.model_attr().print_pos_attr().pos_entity());
                                }
                            } else if (attr.model_attr().init_rotate_attr()!=null) {
                                SceneMaxParser.Rot_axesContext rotAxes = attr.model_attr().init_rotate_attr().rot_axes();
                                if(rotAxes!=null) {
                                    varDef.rxExpr = rotAxes.rotate_x().logical_expression();
                                    varDef.ryExpr = rotAxes.rotate_y().logical_expression();
                                    varDef.rzExpr = rotAxes.rotate_z().logical_expression();
                                } else {
                                    varDef.entityRot = attr.model_attr().init_rotate_attr().rot_entity().getText();
                                }
                            } else if(attr.model_attr().init_mass_attr()!=null) {
                                varDef.massExpr = attr.model_attr().init_mass_attr().logical_expression();
                            } else if(attr.model_attr().init_static_attr()!=null) {
                                varDef.isStatic=true;
                            } else if(attr.model_attr().init_hidden_attr()!=null) {
                                varDef.visible=false;
                            } else if(attr.model_attr().shadow_mode_attr()!=null) {
                                SceneMaxParser.Shadow_mode_optionsContext shadowOpts = attr.model_attr().shadow_mode_attr().shadow_mode_options();
                                if(shadowOpts.Cast()!=null) {
                                    varDef.shadowMode = 1;
                                } else if(shadowOpts.Receive()!=null) {
                                    varDef.shadowMode = 2;
                                } else {
                                    varDef.shadowMode = 3;
                                }
                            }
                        }

                        if(attr.cone_specific_attr()!=null) {
                            if(attr.cone_specific_attr().cylinder_radius_attr()!=null) {
                                varDef.radiusTopExpr = attr.cone_specific_attr().cylinder_radius_attr().cylinder_radius_top().logical_expression();
                                varDef.radiusBottomExpr = attr.cone_specific_attr().cylinder_radius_attr().cylinder_radius_bottom().logical_expression();
                            }
                            if(attr.cone_specific_attr().cylinder_height_attr()!=null) {
                                varDef.heightExpr = attr.cone_specific_attr().cylinder_height_attr().logical_expression();
                            }
                            if(attr.cone_specific_attr().material_attr()!=null) {
                                varDef.materialExpr = attr.cone_specific_attr().material_attr().logical_expression();
                            }
                        }
                    }
                }

                return varDef;

            }

            public StatementDef visitDefStairs(SceneMaxParser.DefStairsContext ctx) {

                String varName = ctx.define_stairs().res_var_decl().getText();
                StairsVariableDef varDef = new StairsVariableDef();
                varDef.isShared = ctx.define_stairs().Shared() != null;
                varDef.varName = varName;
                varDef.resName="stairs";
                varDef.isStatic=ctx.define_stairs().Static()!=null;
                varDef.isCollider = ctx.define_stairs().Collider()!=null;

                if(ctx.define_stairs().stairs_having_expr()!=null) {
                    for(SceneMaxParser.Stairs_attrContext attr:ctx.define_stairs().stairs_having_expr().stairs_attributes().stairs_attr()) {
                        if(attr.model_attr()!=null) {
                            if(attr.model_attr().print_pos_attr()!=null) {
                                if(attr.model_attr().print_pos_attr().pos_axes()!=null) {
                                    if(attr.model_attr().print_pos_attr().pos_axes().exception!=null) {
                                        return null;
                                    }
                                    varDef.xExpr = attr.model_attr().print_pos_attr().pos_axes().print_pos_x().logical_expression();
                                    varDef.yExpr = attr.model_attr().print_pos_attr().pos_axes().print_pos_y().logical_expression();
                                    varDef.zExpr = attr.model_attr().print_pos_attr().pos_axes().print_pos_z().logical_expression();
                                } else {
                                    varDef.entityPos=new EntityPos();
                                    setEntityPos(varDef.entityPos,attr.model_attr().print_pos_attr().pos_entity());
                                }
                            } else if (attr.model_attr().init_rotate_attr()!=null) {
                                SceneMaxParser.Rot_axesContext rotAxes = attr.model_attr().init_rotate_attr().rot_axes();
                                if(rotAxes!=null) {
                                    varDef.rxExpr = rotAxes.rotate_x().logical_expression();
                                    varDef.ryExpr = rotAxes.rotate_y().logical_expression();
                                    varDef.rzExpr = rotAxes.rotate_z().logical_expression();
                                } else {
                                    varDef.entityRot = attr.model_attr().init_rotate_attr().rot_entity().getText();
                                }
                            } else if(attr.model_attr().init_mass_attr()!=null) {
                                varDef.massExpr = attr.model_attr().init_mass_attr().logical_expression();
                            } else if(attr.model_attr().init_static_attr()!=null) {
                                varDef.isStatic=true;
                            } else if(attr.model_attr().init_hidden_attr()!=null) {
                                varDef.visible=false;
                            } else if(attr.model_attr().shadow_mode_attr()!=null) {
                                SceneMaxParser.Shadow_mode_optionsContext shadowOpts = attr.model_attr().shadow_mode_attr().shadow_mode_options();
                                if(shadowOpts.Cast()!=null) {
                                    varDef.shadowMode = 1;
                                } else if(shadowOpts.Receive()!=null) {
                                    varDef.shadowMode = 2;
                                } else {
                                    varDef.shadowMode = 3;
                                }
                            }
                        }

                        if(attr.stairs_specific_attr()!=null) {
                            if(attr.stairs_specific_attr().stairs_size_attr()!=null) {
                                varDef.widthExpr = attr.stairs_specific_attr().stairs_size_attr().stairs_width().logical_expression();
                                varDef.stepHeightExpr = attr.stairs_specific_attr().stairs_size_attr().stairs_step_height().logical_expression();
                                varDef.stepDepthExpr = attr.stairs_specific_attr().stairs_size_attr().stairs_step_depth().logical_expression();
                            }
                            if(attr.stairs_specific_attr().stairs_steps_attr()!=null) {
                                varDef.stepCountExpr = attr.stairs_specific_attr().stairs_steps_attr().logical_expression();
                            }
                            if(attr.stairs_specific_attr().material_attr()!=null) {
                                varDef.materialExpr = attr.stairs_specific_attr().material_attr().logical_expression();
                            }
                        }
                    }
                }

                return varDef;

            }

            public StatementDef visitDefArch(SceneMaxParser.DefArchContext ctx) {

                String varName = ctx.define_arch().res_var_decl().getText();
                ArchVariableDef varDef = new ArchVariableDef();
                varDef.isShared = ctx.define_arch().Shared() != null;
                varDef.varName = varName;
                varDef.resName="arch";
                varDef.isStatic=ctx.define_arch().Static()!=null;
                varDef.isCollider = ctx.define_arch().Collider()!=null;

                if(ctx.define_arch().arch_having_expr()!=null) {
                    for(SceneMaxParser.Arch_attrContext attr:ctx.define_arch().arch_having_expr().arch_attributes().arch_attr()) {
                        if(attr.model_attr()!=null) {
                            if(attr.model_attr().print_pos_attr()!=null) {
                                if(attr.model_attr().print_pos_attr().pos_axes()!=null) {
                                    if(attr.model_attr().print_pos_attr().pos_axes().exception!=null) {
                                        return null;
                                    }
                                    varDef.xExpr = attr.model_attr().print_pos_attr().pos_axes().print_pos_x().logical_expression();
                                    varDef.yExpr = attr.model_attr().print_pos_attr().pos_axes().print_pos_y().logical_expression();
                                    varDef.zExpr = attr.model_attr().print_pos_attr().pos_axes().print_pos_z().logical_expression();
                                } else {
                                    varDef.entityPos=new EntityPos();
                                    setEntityPos(varDef.entityPos,attr.model_attr().print_pos_attr().pos_entity());
                                }
                            } else if (attr.model_attr().init_rotate_attr()!=null) {
                                SceneMaxParser.Rot_axesContext rotAxes = attr.model_attr().init_rotate_attr().rot_axes();
                                if(rotAxes!=null) {
                                    varDef.rxExpr = rotAxes.rotate_x().logical_expression();
                                    varDef.ryExpr = rotAxes.rotate_y().logical_expression();
                                    varDef.rzExpr = rotAxes.rotate_z().logical_expression();
                                } else {
                                    varDef.entityRot = attr.model_attr().init_rotate_attr().rot_entity().getText();
                                }
                            } else if(attr.model_attr().init_mass_attr()!=null) {
                                varDef.massExpr = attr.model_attr().init_mass_attr().logical_expression();
                            } else if(attr.model_attr().init_static_attr()!=null) {
                                varDef.isStatic=true;
                            } else if(attr.model_attr().init_hidden_attr()!=null) {
                                varDef.visible=false;
                            } else if(attr.model_attr().shadow_mode_attr()!=null) {
                                SceneMaxParser.Shadow_mode_optionsContext shadowOpts = attr.model_attr().shadow_mode_attr().shadow_mode_options();
                                if(shadowOpts.Cast()!=null) {
                                    varDef.shadowMode = 1;
                                } else if(shadowOpts.Receive()!=null) {
                                    varDef.shadowMode = 2;
                                } else {
                                    varDef.shadowMode = 3;
                                }
                            }
                        }

                        if(attr.arch_specific_attr()!=null) {
                            if(attr.arch_specific_attr().arch_size_attr()!=null) {
                                varDef.widthExpr = attr.arch_specific_attr().arch_size_attr().arch_width().logical_expression();
                                varDef.heightExpr = attr.arch_specific_attr().arch_size_attr().arch_height().logical_expression();
                                varDef.depthExpr = attr.arch_specific_attr().arch_size_attr().arch_depth().logical_expression();
                            }
                            if(attr.arch_specific_attr().arch_thickness_attr()!=null) {
                                varDef.thicknessExpr = attr.arch_specific_attr().arch_thickness_attr().logical_expression();
                            }
                            if(attr.arch_specific_attr().arch_segments_attr()!=null) {
                                varDef.segmentsExpr = attr.arch_specific_attr().arch_segments_attr().logical_expression();
                            }
                            if(attr.arch_specific_attr().material_attr()!=null) {
                                varDef.materialExpr = attr.arch_specific_attr().material_attr().logical_expression();
                            }
                        }
                    }
                }

                return varDef;

            }

            public StatementDef visitSceneActions(SceneMaxParser.SceneActionsContext ctx) {
                if(ctx.scene_actions().exception!=null) {
                    return null;
                }

                if (ctx.scene_actions().scene_action().scene_environment_shader_action() != null) {
                    EnvironmentShaderCommand cmd = new EnvironmentShaderCommand();
                    cmd.shaderNameExpr = ctx.scene_actions().scene_action().scene_environment_shader_action().logical_expression();
                    return cmd;
                }

                SceneActionCommand cmd = new SceneActionCommand();
                if (ctx.scene_actions().scene_action().scene_action_pause()!=null) {
                    cmd.pause=true;
                } else {
                    cmd.resume=true;
                }
                return cmd;
            }

            public StatementDef visitScreenActions(SceneMaxParser.ScreenActionsContext ctx) {
                if (prg.screenMode != ProgramDef.ScreenMode.UNSPECIFIED) {
                    return null;
                }

                SceneMaxParser.Screen_actionContext screenAction = ctx.screen_actions().screen_action();
                if (screenAction.mode_full() != null) {
                    prg.screenMode = ProgramDef.ScreenMode.FULL;
                } else if (screenAction.mode_borderless() != null) {
                    prg.screenMode = ProgramDef.ScreenMode.BORDERLESS;
                } else if (screenAction.mode_window() != null) {
                    prg.screenMode = ProgramDef.ScreenMode.WINDOW;
                }

                return null;
            }

            public StatementDef visitDefLight(SceneMaxParser.DefLightContext ctx) {
                LightVariableDef varDef = new LightVariableDef();
                SceneMaxParser.Define_lightContext lightCtx = ctx.define_light();
                varDef.isShared = lightCtx.Shared() != null;
                varDef.varName = lightCtx.res_var_decl().getText();
                varDef.varLineNum = ctx.start.getLine();
                varDef.lightType = lightCtx.light_type().getText().toLowerCase();

                if (lightCtx.light_having_expr() != null) {
                    for (SceneMaxParser.Light_attrContext attr : lightCtx.light_having_expr().light_attributes().light_attr()) {
                        if (attr.light_color_attr() != null) {
                            varDef.color = readLightColor(attr.light_color_attr().light_color_value());
                        } else if (attr.light_intensity_attr() != null) {
                            varDef.intensityExpr = attr.light_intensity_attr().logical_expression();
                            if (attr.light_intensity_attr().light_intensity_unit() != null) {
                                varDef.intensityUnit = attr.light_intensity_attr().light_intensity_unit().getText().toLowerCase();
                            }
                        } else if (attr.light_direction_attr() != null) {
                            varDef.directionXExpr = attr.light_direction_attr().vector_x().logical_expression();
                            varDef.directionYExpr = attr.light_direction_attr().vector_y().logical_expression();
                            varDef.directionZExpr = attr.light_direction_attr().vector_z().logical_expression();
                        } else if (attr.print_pos_attr() != null) {
                            if (attr.print_pos_attr().pos_axes() != null) {
                                varDef.xExpr = attr.print_pos_attr().pos_axes().print_pos_x().logical_expression();
                                varDef.yExpr = attr.print_pos_attr().pos_axes().print_pos_y().logical_expression();
                                varDef.zExpr = attr.print_pos_attr().pos_axes().print_pos_z().logical_expression();
                            } else {
                                varDef.entityPos = new EntityPos();
                                setEntityPos(varDef.entityPos, attr.print_pos_attr().pos_entity());
                            }
                        } else if (attr.light_shadow_attr() != null) {
                            varDef.shadowMode = attr.light_shadow_attr().light_shadow_option().getText().toLowerCase();
                        } else if (attr.light_range_attr() != null) {
                            varDef.rangeExpr = attr.light_range_attr().logical_expression();
                        } else if (attr.light_look_at_attr() != null) {
                            varDef.lookAtTarget = attr.light_look_at_attr().res_var_decl().getText();
                        } else if (attr.light_angle_attr() != null) {
                            varDef.angleExpr = attr.light_angle_attr().logical_expression();
                        } else if (attr.light_preset_attr() != null) {
                            varDef.preset = stripQutes(attr.light_preset_attr().QUOTED_STRING().getText());
                        } else if (attr.light_exposure_attr() != null) {
                            varDef.exposureExpr = attr.light_exposure_attr().logical_expression();
                        } else if (attr.light_ambient_attr() != null) {
                            varDef.ambientColor = readLightColor(attr.light_ambient_attr().light_color_value());
                        }
                    }
                }

                return varDef;
            }

            public StatementDef visitLightActions(SceneMaxParser.LightActionsContext ctx) {

                LighsActionCommand cmd = new LighsActionCommand();
                SceneMaxParser.Light_probeContext probe = ctx.light_actions().light_options().light_probe();
                if(probe!=null) {
                    cmd.name = probe.QUOTED_STRING().getText();
                    cmd.name=cmd.name.substring(1,cmd.name.length()-1);
                    if(probe.print_pos_attr()!=null) {
                        if( probe.print_pos_attr().pos_axes()!=null) {

                            if(probe.print_pos_attr().pos_axes().exception!=null) {
                                return null;
                            }

                            cmd.xExpr = probe.print_pos_attr().pos_axes().print_pos_x().logical_expression();
                            cmd.yExpr = probe.print_pos_attr().pos_axes().print_pos_y().logical_expression();
                            cmd.zExpr = probe.print_pos_attr().pos_axes().print_pos_z().logical_expression();
                        } else {
                            cmd.entityPos=probe.print_pos_attr().pos_entity().getText();
                        }
                    }
                }

                return cmd;

            }

            public StatementDef visitAudioStop(SceneMaxParser.AudioStopContext ctx) {
                PlayStopSoundCommand cmd = new PlayStopSoundCommand();
                cmd.sound = ctx.audio_stop().string_expr().getText();
                if(cmd.sound.length()>2) {
                    cmd.sound=cmd.sound.substring(1,cmd.sound.length()-1);
                } else {
                    cmd.sound="";
                }

                cmd.stop = true;
                return cmd;
            }

            public StatementDef visitAudioPlay(SceneMaxParser.AudioPlayContext ctx) {
                PlayStopSoundCommand cmd = new PlayStopSoundCommand();
                if(ctx.audio_play().string_or_logical_expr().string_expr()!=null) {
                    cmd.sound = ctx.audio_play().string_or_logical_expr().string_expr().getText();
                    cmd.sound = stripQutes(cmd.sound);

                } else {
                    cmd.soundExpr = ctx.audio_play().string_or_logical_expr().logical_expression();
                }


                cmd.loop = ctx.audio_play().Loop()!=null;
                if(cmd.sound!=null && !audioUsed.contains(cmd.sound)) {
                    audioUsed.add(cmd.sound);
                }
                cmd.requireResource = true;

                if(ctx.audio_play().audio_play_options()!=null) {
                    for(SceneMaxParser.Audio_play_optionContext opt : ctx.audio_play().audio_play_options().audio_play_option()) {
                        if(opt.Volume()!=null) {
                            cmd.volumeExpr = opt.logical_expression();
                        }
                    }
                }

                return cmd;
            }

            public StatementDef visitPlaySound(SceneMaxParser.PlaySoundContext ctx) {
                PlayStopSoundCommand cmd = new PlayStopSoundCommand();
                cmd.sound = ctx.play_sound().res_var_decl().getText();
                cmd.loop = ctx.play_sound().Loop()!=null;
                if(!audioUsed.contains(cmd.sound)) {
                    audioUsed.add(cmd.sound);
                }
                cmd.requireResource = true;
                return cmd;
            }

            public StatementDef visitParticleSystemActions(SceneMaxParser.ParticleSystemActionsContext ctx) {

                ParticleSystemCommand cmd = new ParticleSystemCommand();
                cmd.isAsync = ctx.particle_system_actions().async_expr()!=null;

                SceneMaxParser.Particle_system_effectContext effectCtx = ctx.particle_system_actions().particle_system_effect();
                if (effectCtx.Debris()!=null) {
                    cmd.type=ParticleSystemCommand.DEBRIS;
                } else if(effectCtx.Explosion()!=null) {
                    cmd.type=ParticleSystemCommand.EXPLOSION;
                } else if(effectCtx.Flash()!=null) {
                    cmd.type=ParticleSystemCommand.FLASH;
                    cmd.startSizeVal = .1f;
                    cmd.endSizeVal = 3.0f;
                } else if(effectCtx.ShockWave()!=null) {
                    cmd.type=ParticleSystemCommand.SHOCK_WAVE;
                } else if(effectCtx.SmokeTrail()!=null) {
                    cmd.type=ParticleSystemCommand.SMOKE_TRAIL;
                } else if(effectCtx.Spark()!=null) {
                    cmd.type=ParticleSystemCommand.SPARK;
                } else if(effectCtx.TimeOrbit()!=null) {
                    cmd.type = ParticleSystemCommand.TIME_ORBIT ;
                } else if(effectCtx.Flame()!=null) {
                    cmd.type = ParticleSystemCommand.FLAME ;
                }


                SceneMaxParser.Particle_system_action_showContext showCtx = ctx.particle_system_actions().particle_system_action().particle_system_action_show();
                SceneMaxParser.Particle_system_having_exprContext havingCtx = showCtx.particle_system_having_expr();
                if(havingCtx!=null) {
                    for(SceneMaxParser.Particle_system_attrContext attr : havingCtx.particle_system_attributes().particle_system_attr()) {

                        ParserRuleContext attrCtx = attr.print_pos_attr();
                        if(attrCtx!=null) {

                            if(attr.print_pos_attr().pos_entity()!=null) {
                                cmd.entityPos=new EntityPos();
                                setEntityPos(cmd.entityPos, attr.print_pos_attr().pos_entity());
                            } else {
                                cmd.pos = (SceneMaxParser.Print_pos_attrContext) attrCtx;
                            }

                            continue;
                        }

                        attrCtx = attr.psys_attr_gravity();
                        if(attrCtx!=null) {
                            cmd.gravity = (SceneMaxParser.Psys_attr_gravityContext) attrCtx;
                            continue;
                        }

                        attrCtx = attr.psys_attr_start_size();
                        if(attrCtx!=null) {
                            cmd.startSize = (SceneMaxParser.Psys_attr_start_sizeContext) attrCtx;
                            continue;
                        }

                        attrCtx = attr.psys_attr_end_size();
                        if(attrCtx!=null) {
                            cmd.endSize = (SceneMaxParser.Psys_attr_end_sizeContext) attrCtx;
                            continue;
                        }

                        attrCtx = attr.psys_attr_duration();
                        if(attrCtx!=null) {
                            cmd.time = (SceneMaxParser.Psys_attr_durationContext) attrCtx;
                            continue;
                        }

                        if(attr.psys_attr_radius()!=null) {
                            cmd.radiusValExpr = attr.psys_attr_radius().logical_expression();
                            continue;
                        } else if(attr.psys_attr_emissions()!=null) {
                            cmd.emissionsPerSecExpr = attr.psys_attr_emissions().emissions_per_second().logical_expression();
                            cmd.particlesPerEmissionExpr = attr.psys_attr_emissions().particles_per_emission().logical_expression();
                        } else if(attr.psys_attr_attach_to()!=null) {
                            cmd.attachToEntity = attr.psys_attr_attach_to().var_decl().getText();
                        }

                    }

                }

                return cmd;
            }

            public StatementDef visitWaterActions(SceneMaxParser.WaterActionsContext ctx) {

                SceneMaxParser.Water_action_showContext show = ctx.water_actions().water_action().water_action_show();
                if(show!=null) {
                    WaterShowCommand cmd = new WaterShowCommand();
                    if(show.water_having_expr()!=null) {
                        for(SceneMaxParser.Water_attrContext attr : show.water_having_expr().water_attributes().water_attr()) {
                            ParserRuleContext attrCtx = attr.print_pos_attr();
                            if(attrCtx!=null) {
                                cmd.pos = (SceneMaxParser.Print_pos_attrContext) attrCtx;
                            }

                            attrCtx = attr.water_depth_attr();
                            if(attrCtx!=null) {
                                cmd.depth = (SceneMaxParser.Water_depth_attrContext) attrCtx;
                            }

                            attrCtx = attr.water_plane_size_attr();
                            if(attrCtx!=null) {
                                cmd.size = (SceneMaxParser.Water_plane_size_attrContext) attrCtx;
                            }

                            attrCtx = attr.water_strength_attr();
                            if(attrCtx!=null) {
                                cmd.strength = (SceneMaxParser.Water_strength_attrContext) attrCtx;
                            }

                            attrCtx = attr.water_wave_speed_attr();
                            if(attrCtx!=null) {
                                cmd.speed = (SceneMaxParser.Water_wave_speed_attrContext) attrCtx;
                            }

                        }
                    }

                    return cmd;

                }

                return null;

            }

            public StatementDef visitUsingResource(SceneMaxParser.UsingResourceContext ctx) {

                if(!parseUsingResource) {
                    return null;
                }

                for(SceneMaxParser.Resource_declarationContext res : ctx.using_resource().resource_declaration()) {
                    boolean isModel = res.Model()!=null;
                    boolean isSprite = !isModel && res.Sprite()!=null;
                    boolean isAudio = !isModel && !isSprite && res.Audio()!=null;

                    for(SceneMaxParser.Res_var_declContext resName : res.res_var_decl()) {
                        String name = resName.getText();
                        if(isModel) {
                            if(!modelsUsed.contains(name)) {
                                modelsUsed.add(name);
                            }
                        } else if(isSprite) {
                            if(!spriteSheetUsed.contains(name)) {
                                spriteSheetUsed.add(name);
                            }
                        } else if(isAudio) {
                            if(!audioUsed.contains(name)) {
                                audioUsed.add(name);
                                PlayStopSoundCommand cmd = new PlayStopSoundCommand();
                                cmd.sound = name;
                                prg.requireResourceActions.add(cmd);
                            }
                        }
                    }
                }
                return null;
            }

            public StatementDef visitSkyBoxActions(SceneMaxParser.SkyBoxActionsContext ctx) {
                SkyBoxCommand cmd = new SkyBoxCommand();
                SceneMaxParser.Skybox_action_showContext showCtx = ctx.skybox_actions().skybox_action().skybox_action_show();
                if(showCtx!=null) {
                    cmd.isShow = true;

                    if(showCtx.regular_skybox()!=null) {
                        cmd.showExpr = showCtx.regular_skybox().QUOTED_STRING().getText();
                        cmd.showExpr=cmd.showExpr.substring(1,cmd.showExpr.length()-1);
                        if(!skyboxUsed.contains(cmd.showExpr)) {
                            skyboxUsed.add(cmd.showExpr);
                        }
                    } else {
                        cmd.isShowSolarSystem = true;
                        SceneMaxParser.Solar_systemContext solarSysCtx = showCtx.solar_system();
                        if(solarSysCtx.solar_system_having_expr()!=null) {

                            setSolarSystemOptions(cmd,solarSysCtx.solar_system_having_expr().solar_system_having_options().solar_system_option());
                        }
                    }
                } else {
                    SceneMaxParser.Skybox_setupContext setupCtx = ctx.skybox_actions().skybox_action().skybox_setup();
                    if(setupCtx!=null) {
                        cmd.isSetup=true;
                        setSolarSystemOptions(cmd, setupCtx.solar_system_setup_options().solar_system_option());

                    }

                }

                return cmd;

            }

            private void setSolarSystemOptions(SkyBoxCommand cmd, List<SceneMaxParser.Solar_system_optionContext> solarSystemOptions) {

                for (SceneMaxParser.Solar_system_optionContext opt : solarSystemOptions) {
                    if (opt.cloud_flattening() != null) {
                        cmd.cloudFlatteningExpr = opt.cloud_flattening().logical_expression();
                    } else if (opt.cloudiness() != null) {
                        cmd.cloudinessExpr = opt.cloudiness().logical_expression();
                    } else if(opt.hour_of_day()!=null) {
                        cmd.hourOfDayExpr = opt.hour_of_day().logical_expression();
                    }
                }
            }


            public ActionStatementBase visitReturnStatement(SceneMaxParser.ReturnStatementContext var1) {
                StopBlockCommand cmd = new StopBlockCommand();
                cmd.returnAction=true;
                return cmd;
            }


            public StatementDef visitStop_statement(SceneMaxParser.Stop_statementContext ctx) {
                StopBlockCommand cmd = new StopBlockCommand();
                return cmd;
            }

            public StatementDef visitWaitForStatement(SceneMaxParser.WaitForStatementContext ctx) {
                WaitForCommand cmd = new WaitForCommand();
                if(ctx.wait_for_statement().wait_for_options().wait_for_expression()!=null) {
                    cmd.waitForExpr = ctx.wait_for_statement().wait_for_options().wait_for_expression().logical_expression();
                } else {
                    String src = ctx.wait_for_statement().wait_for_options().wait_for_input().input_source().getText().toLowerCase();
                    if(src.startsWith("key ")) {
                        cmd.inputType="key";
                        cmd.inputKey = src.replace("key ","");
                    }

                }
                return cmd;
            }

            public StatementDef visitWaitStatement(SceneMaxParser.WaitStatementContext ctx) {
                WaitStatementVisitor v = new WaitStatementVisitor(prg);
                return ctx.accept(v);
            }

            public StatementDef visitPrintStatement(SceneMaxParser.PrintStatementContext ctx) {
                PrintStatementVisitor v = new PrintStatementVisitor(prg);
                return ctx.accept(v);
            }

            public StatementDef visitModifyVar(SceneMaxParser.ModifyVarContext ctx) {

                ModifyVariableVisitor v = new ModifyVariableVisitor(prg);
                VariableAssignmentCommand cmd = ctx.accept(v);
                return cmd;
            }

            public StatementDef visitCameraSystemAssignment(SceneMaxParser.CameraSystemAssignmentContext ctx) {
                CameraSystemAssignmentCommand cmd = new CameraSystemAssignmentCommand();
                cmd.targetVar = "camera";
                if (ctx.camera_system_assignment().camera_system_assignment_value().camera_system_default() != null) {
                    cmd.resetToDefault = true;
                } else {
                    cmd.valueExpr = ctx.camera_system_assignment().camera_system_assignment_value().logical_expression();
                }
                return cmd;
            }

            public StatementDef visitInputStatement(SceneMaxParser.InputStatementContext ctx) {

                InputStatementCommand cmd = new InputStatementCommand();

                if(ctx.input().go_condition()!=null) {
                    cmd.goExpr = ctx.input().go_condition().logical_expression();
                }

                String src = ctx.input().input_source().getText().toLowerCase();
                if(src.startsWith("key ")) {
                    cmd.inputType="key";
                    cmd.inputKey = src.replace("key ","");
                } else if(src.startsWith("mouse ")) {
                    cmd.inputType="mouse";
                    cmd.inputKey = src.toLowerCase().trim();
                    if(ctx.input().on_entity()!=null) {
                        cmd.targetVar = ctx.input().on_entity().res_var_decl().getText();

                    }
                }

                if (ctx.input().input_action().is_released_action()!=null) {
                    cmd.once = true;
                    cmd.released = true;
                } else {
                    cmd.once = ctx.input().input_action().is_pressed_action().Once()!=null;
                }

                DoBlockCommand doBlock = new DoBlockVisitor(prg).visit(ctx.input().do_block());
                doBlock.isSecondLevelReturnPoint = true;
                cmd.doBlock = doBlock;

                return cmd;
            }

            public StatementDef visitCheckStaticStatement(SceneMaxParser.CheckStaticStatementContext ctx) {

                CheckIsStaticCommand cmd = new CheckIsStaticCommand();
                SceneMaxParser.Check_staticContext chk = ctx.check_static();
                DoBlockCommand doBlock = new DoBlockVisitor(prg).visit(chk.do_block());
                cmd.doBlock = doBlock;
                VariableDef vd = prg.getVar(chk.var_decl().getText());
                cmd.varDef=vd;
                cmd.timeExpr = chk.logical_expression();

                return cmd;
            }

            public StatementDef visitCollisionStatement(SceneMaxParser.CollisionStatementContext ctx) {
                CollisionStatementVisitor v = new CollisionStatementVisitor(prg);
                CollisionStatementCommand cmd = ctx.accept(v);
                return cmd;
            }

            public StatementDef visitIfStatement(SceneMaxParser.IfStatementContext ctx) {

                IfStatementVisitor v = new IfStatementVisitor(prg);
                IfStatementCommand cmd = ctx.accept(v);
                return cmd;
            }

            public StatementDef visitFunction_statement(SceneMaxParser.Function_statementContext ctx) {
                FunctionBlockVisitor v = new FunctionBlockVisitor(prg);
                FunctionBlockDef cmd = ctx.accept(v);

                return cmd;
            }

            public StatementDef visitFunctionInvocation(SceneMaxParser.FunctionInvocationContext ctx) {
                FunctionInvocationVisitor v = new FunctionInvocationVisitor();
                FunctionInvocationCommand cmd = ctx.accept(v);
                return cmd;
            }

            public StatementDef visitDo_block(SceneMaxParser.Do_blockContext ctx) {
                DoBlockVisitor v = new DoBlockVisitor(prg);
                DoBlockCommand cmd = ctx.accept(v);
                return cmd;
            }

            public StatementDef visitDefSpriteImplicit(SceneMaxParser.DefSpriteImplicitContext ctx) {

                SpriteDef def = new SpriteDef();

                try {
                    SceneMaxParser.Dynamic_model_typeContext dynamicDef = ctx.define_sprite_implicit().dynamic_model_type();    //res_var_decl().getText();
                    if(dynamicDef.res_var_decl()!=null) {
                        def.name = dynamicDef.res_var_decl().getText();
                    } else {
                        def.nameExpr = dynamicDef.dynamic_model_type_name().logical_expression();
                    }

                    def.varName = ctx.define_sprite_implicit().var_decl().getText();
                    def.isShared = ctx.define_sprite_implicit().Shared() != null;
                    if(ctx.define_sprite_implicit().sprite_having_expr()!=null) {

                        for(SceneMaxParser.Sprite_attrContext spriteAttr : ctx.define_sprite_implicit().sprite_having_expr().sprite_attributes().sprite_attr()) {

                            if(spriteAttr.billboard_attr()!=null) {
                                def.isBillboard = true;
                            } else if(spriteAttr.rows_def()!=null) {
                                def.rows = (int)Double.parseDouble(spriteAttr.rows_def().number().getText());
                            } else if(spriteAttr.cols_def()!=null) {
                                def.cols = (int)Double.parseDouble(spriteAttr.cols_def().number().getText());
                            } else if(spriteAttr.print_pos_attr()!=null) {
                                if(spriteAttr.print_pos_attr().pos_axes()!=null) {

                                    if(spriteAttr.print_pos_attr().pos_axes().exception!=null) {
                                        return null;
                                    }

                                    def.xExpr = spriteAttr.print_pos_attr().pos_axes().print_pos_x().logical_expression();
                                    def.yExpr = spriteAttr.print_pos_attr().pos_axes().print_pos_y().logical_expression();
                                    def.zExpr = spriteAttr.print_pos_attr().pos_axes().print_pos_z().logical_expression();
                                } else {
                                    //def.entityPos=spriteAttr.print_pos_attr().pos_entity().getText();
                                    def.entityPos=new EntityPos();
                                    setEntityPos(def.entityPos,spriteAttr.print_pos_attr().pos_entity());
                                }
                            } else if (spriteAttr.init_scale_attr()!=null) {
                                def.scaleExpr = spriteAttr.init_scale_attr().logical_expression();
                            } else if (spriteAttr.collision_shape_attr()!=null) {
                                def.hasCollisionShape = spriteAttr.collision_shape_attr().collision_shape_options().None()==null;
                            } else if (spriteAttr.init_hidden_attr()!=null) {
                                def.visible = false;
                            } else if (spriteAttr.size_2d_attr()!=null) {
                                def.widthExpr = spriteAttr.size_2d_attr().width_size().logical_expression();
                                def.heightExpr = spriteAttr.size_2d_attr().height_size().logical_expression();
                            }

                        }

                    }

                    if(!spriteSheetUsed.contains(def.name)) {
                        spriteSheetUsed.add(def.name);
                    }


                    return def;
                }catch(Exception e){
                    return null;
                }

            }

            public StatementDef visitDefSphere(SceneMaxParser.DefSphereContext ctx) {

                String varName = ctx.define_sphere().res_var_decl().getText();
                SphereVariableDef varDef = new SphereVariableDef();
                varDef.isShared = ctx.define_sphere().Shared() != null;
                varDef.varName = varName;
                varDef.resName="sphere";
                varDef.isStatic=ctx.define_sphere().Static()!=null;
                varDef.isCollider = ctx.define_sphere().Collider()!=null;

                if(ctx.define_sphere().sphere_having_expr()!=null) {

                    for(SceneMaxParser.Sphere_attrContext attr:ctx.define_sphere().sphere_having_expr().sphere_attributes().sphere_attr()) {
                        if(attr.model_attr()!=null) {
                            if(attr.model_attr().print_pos_attr()!=null) {
                                if(attr.model_attr().print_pos_attr().pos_axes()!=null) {
                                    if(attr.model_attr().print_pos_attr().pos_axes().exception!=null) {
                                        return null;
                                    }
                                    varDef.xExpr = attr.model_attr().print_pos_attr().pos_axes().print_pos_x().logical_expression();
                                    varDef.yExpr = attr.model_attr().print_pos_attr().pos_axes().print_pos_y().logical_expression();
                                    varDef.zExpr = attr.model_attr().print_pos_attr().pos_axes().print_pos_z().logical_expression();
                                } else {
                                    //varDef.entityPos=attr.model_attr().print_pos_attr().pos_entity().getText();
                                    varDef.entityPos=new EntityPos();
                                    setEntityPos(varDef.entityPos,attr.model_attr().print_pos_attr().pos_entity());
                                }
                            } else if (attr.model_attr().init_rotate_attr()!=null) {
                                SceneMaxParser.Rot_axesContext rotAxes = attr.model_attr().init_rotate_attr().rot_axes();
                                if(rotAxes!=null) {
                                    varDef.rxExpr = rotAxes.rotate_x().logical_expression();//new ActionLogicalExpression(attr.init_rotate_attr().rotate_x().logical_expression(),prg);
                                    varDef.ryExpr = rotAxes.rotate_y().logical_expression();
                                    varDef.rzExpr = rotAxes.rotate_z().logical_expression();
                                } else {
                                    varDef.entityRot = attr.model_attr().init_rotate_attr().rot_entity().getText();
                                }
                            } else if(attr.model_attr().init_mass_attr()!=null) {
                                varDef.massExpr = attr.model_attr().init_mass_attr().logical_expression();
                            } else if(attr.model_attr().init_static_attr()!=null) {
                                varDef.isStatic=true;
                            } else if(attr.model_attr().init_hidden_attr()!=null) {
                                varDef.visible=false;
                            } else if(attr.model_attr().shadow_mode_attr()!=null) {
                                SceneMaxParser.Shadow_mode_optionsContext shadow_opts = attr.model_attr().shadow_mode_attr().shadow_mode_options();
                                if(shadow_opts.Cast()!=null) {
                                    varDef.shadowMode = 1;
                                } else if(shadow_opts.Receive()!=null) {
                                    varDef.shadowMode = 2;
                                } else {
                                    varDef.shadowMode = 3;
                                }
                            }

                        } else if(attr.sphere_specific_attr()!=null) {
                            if(attr.sphere_specific_attr().radius_attr()!=null) {
                                varDef.radiusExpr = attr.sphere_specific_attr().radius_attr().logical_expression();
                            } else if(attr.sphere_specific_attr().material_attr()!=null) {
                                varDef.materialExpr = attr.sphere_specific_attr().material_attr().logical_expression();
                            }
                        }
                    }
                }

                return varDef;
            }

            @Override
            public StatementDef visitDefine_variable(SceneMaxParser.Define_variableContext ctx) {

                String varName = ctx.var_decl().getText();
                String resName = null;
                SceneMaxParser.Logical_expressionContext resNameExpr = null;
                boolean isCinematicCamera = false;
                if (ctx.dynamic_model_type().cinematic_resource_decl() != null) {
                    resName = "cinematic.camera." + ctx.dynamic_model_type().cinematic_resource_decl().res_var_decl().getText();
                    isCinematicCamera = true;
                } else if (ctx.dynamic_model_type().effect_resource_decl() != null) {
                    resName = "effects.effekseer." + ctx.dynamic_model_type().effect_resource_decl().res_var_decl().getText();
                } else if (ctx.dynamic_model_type().res_var_decl()!=null) {
                    resName = ctx.dynamic_model_type().res_var_decl().getText();
                } else {
                    resNameExpr = ctx.dynamic_model_type().dynamic_model_type_name().logical_expression();
                }

                VariableDef varDef = isCinematicCamera ? new CinematicCameraVariableDef() : new VariableDef();
                varDef.isShared = ctx.Shared() != null;
                varDef.isAsync = ctx.async_expr() != null;
                varDef.varName = varName;
                varDef.resName=resName;
                varDef.resNameExpr=resNameExpr;
                varDef.varLineNum = ctx.var_decl().getStart().getLine();
                varDef.isVehicle=ctx.Vehicle()!=null;
                varDef.isStatic = ctx.Static()!=null;
                varDef.isDynamic = ctx.Dynamic()!=null;
                if (resName != null && resName.toLowerCase().startsWith("effects.effekseer.")) {
                    varDef.varType = VariableDef.VAR_TYPE_EFFEKSEER;
                    if (!effekseerUsed.contains(resName)) {
                        effekseerUsed.add(resName);
                    }
                } else if (isCinematicCamera) {
                    varDef.varType = VariableDef.VAR_TYPE_CINEMATIC_CAMERA;
                    CinematicCameraVariableDef cinematicVar = (CinematicCameraVariableDef) varDef;
                    cinematicVar.cinematicCameraId =
                            ctx.dynamic_model_type().cinematic_resource_decl().res_var_decl().getText();
                    // Autocomplete reparses the live editor text on every keystroke, so skip
                    // the recursive rig lookup there and keep that work for full parses.
                    if (!isChildParser) {
                        cinematicVar.cinematicSourceFile = findCinematicRigSourceFile(this.codePath, cinematicVar.cinematicCameraId);
                    }
                }

                if(ctx.scene_entity_having_expr()!=null) {
                    for (SceneMaxParser.Model_attrContext attr : ctx.scene_entity_having_expr().model_attributes().model_attr()) {
                        if(attr.print_pos_attr()!=null) {
                            if(attr.print_pos_attr().pos_axes()!=null) {

                                if(attr.print_pos_attr().pos_axes().exception!=null) {
                                    return null;
                                }

                                varDef.xExpr = attr.print_pos_attr().pos_axes().print_pos_x().logical_expression();
                                varDef.yExpr = attr.print_pos_attr().pos_axes().print_pos_y().logical_expression();
                                varDef.zExpr = attr.print_pos_attr().pos_axes().print_pos_z().logical_expression();
                            } else {
                                //varDef.entityPos = attr.print_pos_attr().pos_entity().getText();
                                varDef.entityPos=new EntityPos();
                                setEntityPos(varDef.entityPos,attr.print_pos_attr().pos_entity());
                            }
                        } else if(attr.init_turn_attr()!=null ) {

                            varDef.useVerbalTurn = true;
                            SceneMaxParser.Turn_dirContext dirCtx = attr.init_turn_attr().turn_dir();
                            String dir = dirCtx==null?"left":dirCtx.getText();
                            if(dir.equalsIgnoreCase("left")) {
                                varDef.ryExpr = attr.init_turn_attr().turn_degrees().logical_expression();
                                varDef.rotDir=1;
                            } else if(dir.equalsIgnoreCase("right")) {
                                varDef.ryExpr = attr.init_turn_attr().turn_degrees().logical_expression();
                                varDef.rotDir=-1;
                            } else if(dir.equalsIgnoreCase("forward")) {
                                varDef.rxExpr = attr.init_turn_attr().turn_degrees().logical_expression();
                                varDef.rotDir=1;
                            } if(dir.equalsIgnoreCase("backward")) {
                                varDef.rotDir=-1;
                                varDef.rxExpr = attr.init_turn_attr().turn_degrees().logical_expression();
                            }

                        } else if(attr.init_rotate_attr()!=null) {
                            SceneMaxParser.Rot_axesContext rotAxes = attr.init_rotate_attr().rot_axes();
                            if(rotAxes!=null) {
                                varDef.rxExpr = rotAxes.rotate_x().logical_expression();
                                varDef.ryExpr = rotAxes.rotate_y().logical_expression();
                                varDef.rzExpr = rotAxes.rotate_z().logical_expression();
                            } else {
                                varDef.entityRot = attr.init_rotate_attr().rot_entity().getText();
                            }
                        } else if(attr.init_scale_attr()!=null) {
                            varDef.scaleExpr = attr.init_scale_attr().logical_expression();
                        } else if(attr.init_mass_attr()!=null) {
                            varDef.massExpr = attr.init_mass_attr().logical_expression();
                        } else if(attr.init_static_attr()!=null) {
                            varDef.isStatic=true;
                        } else if(attr.init_joints_attr()!=null) {

                            varDef.joints = new ArrayList<>();
                            for(TerminalNode str : attr.init_joints_attr().QUOTED_STRING()) {
                                varDef.joints.add(str.getText());
                            }

                        } else if(attr.init_hidden_attr()!=null) {
                            varDef.visible=false;
                        } else if(attr.shadow_mode_attr()!=null) {
                            SceneMaxParser.Shadow_mode_optionsContext shadow_opts = attr.shadow_mode_attr().shadow_mode_options();
                            if(shadow_opts.Cast()!=null) {
                                varDef.shadowMode = 1;
                            } else if(shadow_opts.Receive()!=null) {
                                varDef.shadowMode = 2;
                            } else {
                                varDef.shadowMode = 3;
                            }
                        } else if(attr.calibration_attr()!=null) {
                            varDef.calibration = new EntityPos(attr.calibration_attr().pos_axes().print_pos_x().logical_expression(),
                                    attr.calibration_attr().pos_axes().print_pos_y().logical_expression(),
                                    attr.calibration_attr().pos_axes().print_pos_z().logical_expression());
                        } else if(attr.collision_shape_attr()!=null) {
                            String shapeType = attr.collision_shape_attr().collision_shape_options().getText().toLowerCase();
                            if(shapeType.equals("box")) {
                                varDef.collisionShape = VariableDef.COLLISION_SHAPE_BOX;
                            } else if(shapeType.equals("boxes")) {
                                varDef.collisionShape = VariableDef.COLLISION_SHAPE_BOXES;
                            }
                        }
                    }
                }

                return varDef;
            }

            @Override
            public ActionStatementBase visitActionStatement(SceneMaxParser.ActionStatementContext ctx) {

                ActionStatementBase action = ctx.action_statement().action_operation().accept(new ActionStatementVisitor(prg));
                if(action!=null) {
                    action.isAsync = ctx.action_statement().async_expr() != null;
                }
                return action;
            }

        }


        private class WaitStatementVisitor extends SceneMaxBaseVisitor<WaitStatementCommand> {

            private final ProgramDef prg;

            public WaitStatementVisitor(ProgramDef prg) {
                this.prg=prg;

            }

            public WaitStatementCommand visitWaitStatement(SceneMaxParser.WaitStatementContext ctx) {

                WaitStatementCommand cmd=new WaitStatementCommand();
                cmd.waitExpr = ctx.wait_statement().logical_expression();//new ActionLogicalExpression(ctx.wait_statement().logical_expression(),prg);

                return cmd;
            }
        }

        private class PrintStatementVisitor extends SceneMaxBaseVisitor<PrintStatementCommand> {

            private final ProgramDef prg;

            public PrintStatementVisitor(ProgramDef prg) {
                this.prg=prg;

            }

            public PrintStatementCommand visitPrintStatement(SceneMaxParser.PrintStatementContext ctx) {
                PrintStatementCommand cmd = new PrintStatementCommand();
                cmd.printChannel = ctx.print_statement().res_var_decl().getText();
                cmd.text = ctx.print_statement().print_text_expr().logical_expression();// new ActionLogicalExpression(ctx.print_statement().print_text_expr().logical_expression(),prg);
                for(SceneMaxParser.Print_attrContext attr:ctx.print_statement().print_attr()) {
                    if(attr.print_pos_attr()!=null) {
                        if(attr.print_pos_attr().pos_axes()!=null) {

                            if(attr.print_pos_attr().pos_axes().exception!=null) {
                                return null;
                            }

                            cmd.x = attr.print_pos_attr().pos_axes().print_pos_x().logical_expression();//new ActionLogicalExpression(attr.print_pos_attr().print_pos_x().logical_expression(),prg);
                            cmd.y = attr.print_pos_attr().pos_axes().print_pos_y().logical_expression();//new ActionLogicalExpression(attr.print_pos_attr().print_pos_y().logical_expression(),prg);
                            cmd.z = attr.print_pos_attr().pos_axes().print_pos_z().logical_expression();//new ActionLogicalExpression(attr.print_pos_attr().print_pos_z().logical_expression(),prg);
                        }

                    } else if(attr.print_color_attr()!=null) {
                        cmd.color = attr.print_color_attr().SystemColor().getText();
                    } else if(attr.print_font_size_attr()!=null) {
                        cmd.fontSize=attr.print_font_size_attr().logical_expression();//new ActionLogicalExpression(attr.print_font_size_attr().logical_expression(),prg);
                    } else if(attr.print_append_attr()!=null) {
                        cmd.append=true;
                    } else if(attr.print_font_attr()!=null) {
                        cmd.font=attr.print_font_attr().QUOTED_STRING().getText();
                        if(cmd.font.length()>2) {
                            cmd.font=cmd.font.substring(1,cmd.font.length()-1);
                            if(!fontsUsed.contains(cmd.font)) {
                                fontsUsed.add(cmd.font);
                            }
                        }
                    }


                }

                return cmd;
            }


        }

        private class ModifyVariableVisitor extends SceneMaxBaseVisitor<VariableAssignmentCommand> {
            private final ProgramDef prg;

            public ModifyVariableVisitor(ProgramDef prg) {
                this.prg=prg;
            }

            public VariableAssignmentCommand visitModify_variable(SceneMaxParser.Modify_variableContext ctx) {
                VariableAssignmentCommand cmd = new VariableAssignmentCommand();
                ctx.variable_name_and_mandatory_assignemt().forEach(modify_variableContext -> {
                    String varName = modify_variableContext.res_var_decl().getText();
                    VariableDef variableDef = prg.getVar(varName);
                    if (variableDef == null) {
                        SceneMaxParser.Logical_expressionContext valueExpr = modify_variableContext.var_value_option().single_value_option() != null
                                ? modify_variableContext.var_value_option().single_value_option().logical_expression()
                                : null;
                        if (isCameraSystemValueExpression(valueExpr) || isCameraModifierValueExpression(valueExpr)) {
                            variableDef = createImplicitVariableDef(prg, varName);
                        } else {
                            prg.syntaxErrors.add(_sourceFileName+": line " + ctx.start.getLine() + ", variable '" + varName + "' not exists");
                        }
                    }

                    cmd.vars.add(variableDef);
                    if (variableDef != null && modify_variableContext.array_accessor() != null) {
                        variableDef.varType = VariableDef.VAR_TYPE_ARRAY;
                        cmd.arrayIndexes.put(variableDef, modify_variableContext.array_accessor().logical_expression());
                    }

                    if(modify_variableContext.var_value_option().array_value()!=null) {
                        cmd.array = modify_variableContext.var_value_option().array_value().logical_expression();
                    } else {
                        cmd.values.add(modify_variableContext.var_value_option().single_value_option().logical_expression());
                    }

                });

                return cmd;
            }

        }

        private class CollisionStatementVisitor extends SceneMaxBaseVisitor<CollisionStatementCommand> {
            private final ProgramDef prg;

            public CollisionStatementVisitor(ProgramDef prg) {
                this.prg=prg;
            }

            public CollisionStatementCommand visitCollisionStatement(SceneMaxParser.CollisionStatementContext ctx) {

                CollisionStatementCommand cmd = new CollisionStatementCommand();

                if(ctx.collision().go_condition()!=null) {
                    cmd.goExpr = ctx.collision().go_condition().logical_expression();
                }

                String destEntity = ctx.collision().collision_entity().var_decl().getText();
                String destJoint = null;
                if (ctx.collision().collision_entity().collision_joint_1()!=null) {
                    destJoint = ctx.collision().collision_entity().collision_joint_1().QUOTED_STRING().getText();
                    destJoint = stripQutes(destJoint);
                }
                VariableDef vd = prg.getVar(destEntity);
                if(vd==null) {
                    prg.syntaxErrors.add(_sourceFileName + ": Object '"+destEntity+"' not defined at line:"+ctx.collision().collision_entity().var_decl().getStart().getLine());
                    return null;
                }
                cmd.destEntity = vd;
                cmd.destJoint = destJoint;

                for (SceneMaxParser.Collision_entityContext collisionEntityContext : ctx.collision().source_collision_entities().collision_entity()) {
                    String sourceEntity = collisionEntityContext.var_decl().getText();
                    String sourceJoint = null;
                    if (collisionEntityContext.collision_joint_1()!=null) {
                        sourceJoint = collisionEntityContext.collision_joint_1().QUOTED_STRING().getText();
                        sourceJoint = stripQutes(sourceJoint);
                    }

                    vd = prg.getVar(sourceEntity);
                    if(vd==null) {
                        prg.syntaxErrors.add(_sourceFileName + ": Object '"+sourceEntity+"' not defined at line:"+collisionEntityContext.var_decl().getStart().getLine());
                        return null;
                    }

                    cmd.sourceEntities.add(vd);
                    cmd.sourceJoints.add(sourceJoint);
                }

                DoBlockCommand doBlock = new DoBlockVisitor(prg).visit(ctx.collision().do_block());
                doBlock.isSecondLevelReturnPoint=true;
                cmd.doBlock = doBlock;

                return cmd;
            }

        }

        private class IfStatementVisitor extends SceneMaxBaseVisitor<IfStatementCommand> {

            private final ProgramDef prg;

            public IfStatementVisitor(ProgramDef prg) {
                this.prg=prg;
            }

            public IfStatementCommand visitIfStatement(SceneMaxParser.IfStatementContext ctx) {

                IfStatementCommand cmd = new IfStatementCommand();
                DoBlockCommand doBlock = new DoBlockVisitor(prg).visit(ctx.if_statement().do_block());
                cmd.doBlock = doBlock;
                cmd.expression = ctx.if_statement().logical_expression();// new ActionLogicalExpression(ctx.if_statement().logical_expression(),prg);

                // Add the else block
                if(ctx.if_statement().else_expr()!=null) {
                    cmd.elseCmd=new DoBlockVisitor(prg).visit(ctx.if_statement().else_expr().do_block());

                }

                // Add the else-if block(s)
                if(ctx.if_statement().else_if_expr()!=null) {
                    cmd.elseIfCommands=new ArrayList<>();
                    for(int i=0;i<ctx.if_statement().else_if_expr().size();++i) {
                        SceneMaxParser.Else_if_exprContext elseIfCtx = ctx.if_statement().else_if_expr(i);

                        IfStatementCommand elseIfCmd = new IfStatementCommand();
                        elseIfCmd.expression = elseIfCtx.logical_expression();//new ActionLogicalExpression(elseIfCtx.logical_expression(),prg);
                        elseIfCmd.doBlock = new DoBlockVisitor(prg).visit(elseIfCtx.do_block());

                        cmd.elseIfCommands.add(elseIfCmd);
                    }
                }

                return cmd;
            }

        }

        private class FunctionBlockVisitor extends SceneMaxBaseVisitor<FunctionBlockDef> {
            private final ProgramDef prg;

            public FunctionBlockVisitor(ProgramDef prg) {
                this.prg=prg;
            }

            public FunctionBlockDef visitFunction_statement(SceneMaxParser.Function_statementContext ctx) {

                FunctionBlockDef cmd = new FunctionBlockDef();
                List<String> inParams = new ArrayList<>();
                if(ctx.func_variables()!=null) {
                    for(SceneMaxParser.Res_var_declContext varCtx: ctx.func_variables().res_var_decl()) {
                        String varName = varCtx.getText();
                        inParams.add(varName);
                    }
                }


                SceneMaxParser.Do_blockContext dbctx = ctx.do_block();
                if(dbctx==null) {
                    prg.syntaxErrors.add("Syntax error in 'Do' block at line:"+ctx.getStart().getLine());
                    return null;
                }
                DoBlockCommand doBlock = new DoBlockVisitor(prg,inParams).visit(dbctx);
                doBlock.isReturnPoint=true; // functions are always return points (when using "return" command)
                cmd.doBlock = doBlock;
                cmd.name = ctx.java_func_name().getText();

                if(ctx.go_condition()!=null) {
                    cmd.goExpr = ctx.go_condition().logical_expression();
                    doBlock.useGoExprEveryIteration = ctx.go_condition().Pound() != null;
                }

                return cmd;
            }

        }

        private class FunctionInvocationVisitor extends SceneMaxBaseVisitor<FunctionInvocationCommand> {

            public FunctionInvocationCommand visitFunctionInvocation(SceneMaxParser.FunctionInvocationContext ctx) {

                FunctionInvocationCommand cmd = new FunctionInvocationCommand();
                cmd.funcName=ctx.function_invocation().java_func_name().getText();
                if(ctx.function_invocation().func_invok_variables()!=null) {
                    cmd.params=ctx.function_invocation().func_invok_variables().logical_expression();

                }

                if(ctx.function_invocation().every_time_expr()!=null) {
                    cmd.intervalExpr = ctx.function_invocation().every_time_expr().logical_expression();
                }

                cmd.isAsync = ctx.function_invocation().async_expr()!=null;

                return cmd;
            }

        }

        private class DoBlockVisitor extends SceneMaxBaseVisitor<DoBlockCommand> {

            private final ProgramDef prg;
            private List<String> inParams=null;
            private final String codePath;

            public DoBlockVisitor(ProgramDef prg) {
                this(prg, null);
            }

            public DoBlockVisitor(ProgramDef prg, List<String> inParams) {
                this.prg=prg;
                this.inParams = inParams;
                this.codePath = ProgramVisitor.this.codePath;
            }

            @Override
            public DoBlockCommand visitDo_block(SceneMaxParser.Do_blockContext ctx) {

                DoBlockCommand doCmd = new DoBlockCommand();
                doCmd.inParams=this.inParams;

                if (ctx.end_do_block().while_expr()!=null) {
                    doCmd.loopExpr = ctx.end_do_block().while_expr().logical_expression();
                }

                if(ctx.go_condition()!=null) {
                    doCmd.goExpr = ctx.go_condition().logical_expression();
                    doCmd.useGoExprEveryIteration = ctx.go_condition().Pound()!=null;
                }
                if(ctx.amount_of_times_expr()!=null) {
                    doCmd.amountExpr = ctx.amount_of_times_expr().logical_expression();//new ActionLogicalExpression(ctx.amount_of_times_expr().logical_expression(),prg);
                    doCmd.loopType = ctx.amount_of_times_expr().times_or_seconds().getText();
                } else {
                    // defaults for do block
                    doCmd.amount="1";
                    doCmd.loopType="times";
                }

                if(ctx.program_statements()!=null) {
                    prg.inParams=inParams;
                    doCmd.prg = new ProgramStatementsVisitor(prg, this.codePath).visit(ctx.program_statements());
                    doCmd.isAsync = ctx.async_expr() != null;
                } else {
                    prg.syntaxErrors.add("Do block has invalid statements at: ");
                    return null;
                }
                
                return doCmd;
            }

        }

        private class ActionStatementVisitor extends SceneMaxBaseVisitor<ActionStatementBase> {

            private final ProgramDef prg;

            public ActionStatementVisitor(ProgramDef prg) {
                this.prg=prg;
            }

            public ActionStatementBase visitArrayPush(SceneMaxParser.ArrayPushContext ctx) {
                ArrayCommand cmd = new ArrayCommand();
                cmd.action = ArrayCommand.ArrayAction.Push;
                cmd.varName = ctx.array_push().var_decl().getText();
                cmd.expr = ctx.array_push().logical_expression();

                return cmd;
            }

            public ActionStatementBase visitArrayPop(SceneMaxParser.ArrayPopContext ctx) {
                ArrayCommand cmd = new ArrayCommand();
                cmd.action = ArrayCommand.ArrayAction.Pop;
                cmd.varName = ctx.array_pop().var_decl().getText();

                return cmd;
            }

            public ActionStatementBase visitArrayClear(SceneMaxParser.ArrayClearContext ctx) {
                ArrayCommand cmd = new ArrayCommand();
                cmd.action = ArrayCommand.ArrayAction.Clear;
                cmd.varName = ctx.array_clear().var_decl().getText();

                return cmd;
            }

            public ActionStatementBase visitCameraModifierApply(SceneMaxParser.CameraModifierApplyContext ctx) {
                CameraModifierApplyCommand cmd = new CameraModifierApplyCommand();
                cmd.targetVar = ctx.camera_modifier_apply().var_decl(0).getText();
                cmd.varDef = prg.getVar(cmd.targetVar);
                cmd.targetVarLine = ctx.camera_modifier_apply().var_decl(0).getStart().getLine();
                cmd.modifierVar = ctx.camera_modifier_apply().var_decl(1).getText();
                cmd.modifierVarDef = prg.getVar(cmd.modifierVar);

                if (ctx.camera_modifier_apply().camera_modifier_override_list() != null) {
                    for (SceneMaxParser.Camera_modifier_overrideContext overrideCtx
                            : ctx.camera_modifier_apply().camera_modifier_override_list().camera_modifier_override()) {
                        String key = overrideCtx.res_var_decl(0).getText().toLowerCase();
                        if (overrideCtx.res_var_decl().size() > 1) {
                            key = key + "_" + overrideCtx.res_var_decl(1).getText().toLowerCase();
                        }
                        cmd.overrideExprs.put(key, overrideCtx.logical_expression());
                    }
                }

                return cmd;
            }

            public ActionStatementBase visitReplayAction(SceneMaxParser.ReplayActionContext ctx) {

                ReplayCommand cmd = new ReplayCommand();
                cmd.targetVar = ctx.replay().var_decl().getText();

                SceneMaxParser.Replay_switch_toContext switchTo = ctx.replay().replay_options().replay_switch_to();
                if(switchTo!=null) {
                    cmd.option = ReplayCommand.SWITCH_TO;
                    cmd.dataArrayName = switchTo.replay_data().var_decl().getText();


                    if(switchTo.replay_attributes()!=null) {
                        for(SceneMaxParser.Replay_attributeContext attr:switchTo.replay_attributes().replay_attribute()) {
                            if(attr.replay_attr_offset()!=null) {
                                String axis = attr.replay_attr_offset().all_axes_names().getText();
                                if(axis.equalsIgnoreCase("x")) {
                                    cmd.offsetXExpr = attr.replay_attr_offset().logical_expression();
                                } else if(axis.equalsIgnoreCase("y")) {
                                    cmd.offsetYExpr = attr.replay_attr_offset().logical_expression();
                                } else if(axis.equalsIgnoreCase("z")) {
                                    cmd.offsetZExpr = attr.replay_attr_offset().logical_expression();
                                } else if(axis.equalsIgnoreCase("rx")) {
                                    cmd.offsetRXExpr = attr.replay_attr_offset().logical_expression();
                                } else if(axis.equalsIgnoreCase("ry")) {
                                    cmd.offsetRYExpr = attr.replay_attr_offset().logical_expression();
                                } else if(axis.equalsIgnoreCase("rz")) {
                                    cmd.offsetRZExpr = attr.replay_attr_offset().logical_expression();
                                }

                            }
                        }
                    }


                    return cmd;

                }

                if(ctx.replay().replay_options().replay_stop()!=null) {
                    cmd.option = ReplayCommand.STOP;
                    return cmd;
                }

                if(ctx.replay().replay_options().replay_pause()!=null) {
                    cmd.option = ReplayCommand.PAUSE;
                    return cmd;
                }

                if(ctx.replay().replay_options().replay_resume()!=null) {
                    cmd.option = ReplayCommand.RESUME;
                    return cmd;
                }

                if(ctx.replay().replay_options().replay_change_speed()!=null) {
                    cmd.option = ReplayCommand.CHANGE_SPEED;
                    cmd.speedExpr = ctx.replay().replay_options().replay_change_speed().speed_expr().logical_expression();
                    return cmd;
                }

                SceneMaxParser.Replay_commandContext replayCmdCtx = ctx.replay().replay_options().replay_command();
                if(replayCmdCtx!=null) {
                    cmd.dataArrayName = replayCmdCtx.replay_data().var_decl().getText();
                    if(replayCmdCtx.starting_at_expr()!=null) {
                        cmd.startAtExpr = replayCmdCtx.starting_at_expr().logical_expression();
                    }

                    cmd.speedExpr = replayCmdCtx.speed_expr().logical_expression();

                    if(replayCmdCtx.loop_expr()!=null) {
                        cmd.loopExpr = replayCmdCtx.loop_expr().logical_expression();
                    }

                }

                if(ctx.replay().replay_attributes()!=null) {
                    for(SceneMaxParser.Replay_attributeContext attr:ctx.replay().replay_attributes().replay_attribute()) {
                        if(attr.replay_attr_offset()!=null) {
                            String axis = attr.replay_attr_offset().all_axes_names().getText();
                            if(axis.equalsIgnoreCase("x")) {
                                cmd.offsetXExpr = attr.replay_attr_offset().logical_expression();
                            } else if(axis.equalsIgnoreCase("y")) {
                                cmd.offsetYExpr = attr.replay_attr_offset().logical_expression();
                            } else if(axis.equalsIgnoreCase("z")) {
                                cmd.offsetZExpr = attr.replay_attr_offset().logical_expression();
                            } else if(axis.equalsIgnoreCase("rx")) {
                                cmd.offsetRXExpr = attr.replay_attr_offset().logical_expression();
                            } else if(axis.equalsIgnoreCase("ry")) {
                                cmd.offsetRYExpr = attr.replay_attr_offset().logical_expression();
                            } else if(axis.equalsIgnoreCase("rz")) {
                                cmd.offsetRZExpr = attr.replay_attr_offset().logical_expression();
                            }

                        }
                    }
                }

                return cmd;
            }

            public ActionStatementBase visitDelete(SceneMaxParser.DeleteContext ctx) {
                KillEntityCommand cmd = new KillEntityCommand();
                cmd.targetVar = ctx.var_decl().getText();
                return cmd;
            }

            public ActionStatementBase visitSetMaterialAction(SceneMaxParser.SetMaterialActionContext ctx) {

                SetMaterialCommand cmd = new SetMaterialCommand();
                cmd.materialNameExpr = ctx.set_material_action().logical_expression();
                cmd.targetVar = ctx.set_material_action().var_decl().getText();
                return cmd;
            }

            public ActionStatementBase visitSetShaderAction(SceneMaxParser.SetShaderActionContext ctx) {

                SetShaderCommand cmd = new SetShaderCommand();
                cmd.shaderNameExpr = ctx.set_shader_action().logical_expression();
                cmd.targetVar = ctx.set_shader_action().var_decl().getText();
                return cmd;
            }

            public ActionStatementBase visitCharacterActions(SceneMaxParser.CharacterActionsContext ctx) {
                SceneMaxParser.Character_actionContext characterAction = ctx.character_actions().character_action();
                if(characterAction.character_action_jump()!=null) {
                    CharacterJumpCommand cmd = new CharacterJumpCommand();
                    cmd.targetVar = ctx.character_actions().var_decl().getText();

                    if(characterAction.character_action_jump().speed_of_expr()!=null) {
                        cmd.speedExpr = characterAction.character_action_jump().speed_of_expr().logical_expression();
                    }

                    return cmd;
                }

                if(characterAction.character_ignore()!=null) {
                    CharacterIgnoreCommand cmd = new CharacterIgnoreCommand();
                    cmd.targetVar = ctx.character_actions().var_decl().getText();
                    cmd.ignoreVar = characterAction.character_ignore().var_decl().getText();
                    return cmd;
                }

                return null;
            }

            public ActionStatementBase visitScaleStatement(SceneMaxParser.ScaleStatementContext ctx) {

                ActionScaleCommand cmd = new ActionScaleCommand();
                cmd.targetVar = ctx.scale().var_decl().getText();
                cmd.scaleExpr = ctx.scale().logical_expression();

                return cmd;
            }


            public ActionStatementBase visitDettachParent(SceneMaxParser.DettachParentContext ctx) {

                DettachFromParentCommand cmd = new DettachFromParentCommand();
                cmd.targetVar = ctx.detach_parent().var_decl().getText();
                return cmd;
            }

            public ActionStatementBase visitAttachTo(SceneMaxParser.AttachToContext ctx) {

                AttachToCommand cmd ;
                String tvName = ctx.attach_to().var_decl(0).getText();

                cmd = new AttachToCommand();
                cmd.entityNameToAttach = tvName;
                if(ctx.attach_to().source_joint_name()!=null) {
                    cmd.sourceJointName = stripQutes(ctx.attach_to().source_joint_name().joint_name().getText());
                }
                cmd.targetVar = ctx.attach_to().var_decl(1).getText();
                if(ctx.attach_to().dest_joint_name()!=null) {
                    cmd.jointName = stripQutes(ctx.attach_to().dest_joint_name().joint_name().getText());
                }

                SceneMaxParser.Attach_to_having_exprContext havingCtx = ctx.attach_to().attach_to_having_expr();
                if(havingCtx!=null) {
                    for(SceneMaxParser.Attach_to_having_optionContext opt:havingCtx.attach_to_options().attach_to_having_option()) {
                        if(opt.print_pos_attr()!=null) {
                            if(opt.print_pos_attr().pos_axes()!=null) {

                                if(opt.print_pos_attr().pos_axes().exception!=null) {
                                    return null;
                                }

                                cmd.xExpr = opt.print_pos_attr().pos_axes().print_pos_x().logical_expression();
                                cmd.yExpr = opt.print_pos_attr().pos_axes().print_pos_y().logical_expression();
                                cmd.zExpr = opt.print_pos_attr().pos_axes().print_pos_z().logical_expression();
                            }
                        }

                        if(opt.init_rotate_attr()!=null) {
                            SceneMaxParser.Rot_axesContext rotAxes = opt.init_rotate_attr().rot_axes();
                            if(rotAxes!=null) {
                                cmd.rxExpr = rotAxes.rotate_x().logical_expression();
                                cmd.ryExpr = rotAxes.rotate_y().logical_expression();
                                cmd.rzExpr = rotAxes.rotate_z().logical_expression();
                            } else {
                                cmd.entityRot = opt.init_rotate_attr().rot_entity().getText();
                            }
                        }
                    }
                }

                return cmd;
            }

            public ActionStatementBase visitTurnVerbalTo(SceneMaxParser.TurnVerbalToContext ctx) {

                LookAtCommand cmd = new LookAtCommand();

                if(ctx.turn_verbal_to().move_to_target().position_statement()!=null) {
                    SceneMaxParser.Position_statementContext posCtx = ctx.turn_verbal_to().move_to_target().position_statement();
                    cmd.posStatement = parsePositionStatement(posCtx);

                } else if(ctx.turn_verbal_to().move_to_target().ID()!=null) {
                    cmd.moveToTarget = ctx.turn_verbal_to().move_to_target().ID().getText();
                } else {
                    SceneMaxParser.Pos_axesContext posAxes = ctx.turn_verbal_to().move_to_target().pos_axes();
                    if(posAxes!=null) {
                        if(posAxes.exception==null) {
                            cmd.moveToTargetXExpr = posAxes.print_pos_x().logical_expression();
                            cmd.moveToTargetYExpr = posAxes.print_pos_y().logical_expression();
                            cmd.moveToTargetZExpr = posAxes.print_pos_z().logical_expression();
                        } else {

                            return null;
                        }
                    }
                }

                cmd.targetVar = ctx.turn_verbal_to().var_decl().getText();
                cmd.varLineNum = ctx.turn_verbal_to().var_decl().getStart().getLine();
                if(ctx.turn_verbal_to().speed_expr()!=null) {
                    cmd.speedExpr = ctx.turn_verbal_to().speed_expr().logical_expression();
                }
                cmd.validate(prg);

                return cmd;

            }


            public ActionStatementBase visitTurnStatement(SceneMaxParser.TurnStatementContext ctx) {

                ActionCommandRotate rotate = new ActionCommandRotate();
                String axis = "";
                String numSign = "";

                if(ctx.turn_verbal().loop_expr()!=null) {
                    rotate.loopExpr = ctx.turn_verbal().loop_expr().logical_expression();
                }

                SceneMaxParser.Turn_dirContext dirCtx = ctx.turn_verbal().turn_dir();
                String dir = dirCtx==null?"left":dirCtx.getText();
                if(dir.equalsIgnoreCase("left")) {
                    axis="y";
                    numSign="+";
                } else if(dir.equalsIgnoreCase("right")) {
                    axis="y";
                    numSign="-";
                } else if(dir.equalsIgnoreCase("forward")) {
                    axis="x";
                    numSign="+";
                } if(dir.equalsIgnoreCase("backward")) {
                    axis="x";
                    numSign="-";
                }

                String var = ctx.turn_verbal().var_decl().getText();
                ActionCommandRotate cmd = new ActionCommandRotate();
                VariableDef vd = prg.getVar(var);
                cmd.varDef=vd;
                cmd.targetVar = var;
                cmd.axis = axis;
                cmd.numSign = numSign;
                cmd.numExpr = ctx.turn_verbal().turn_degrees().logical_expression();
                cmd.speedExp = ctx.turn_verbal().speed_expr().logical_expression();
                cmd.motionEaseType = parseMotionEase(ctx.turn_verbal().motion_ease_attr());

                rotate.statements.add(cmd);
                return rotate;

            }

            public ActionStatementBase visitRollStatement(SceneMaxParser.RollStatementContext ctx) {

                ActionCommandRotate rotate = new ActionCommandRotate();
                String axis = "z";
                String numSign = "";

                SceneMaxParser.Turn_dirContext dirCtx = ctx.roll_verbal().turn_dir();
                String dir = dirCtx==null?"left":dirCtx.getText();

                if(ctx.roll_verbal().loop_expr()!=null) {
                    rotate.loopExpr = ctx.roll_verbal().loop_expr().logical_expression();
                }

                if(dir.equalsIgnoreCase("left")) {
                    numSign="-";
                } else if(dir.equalsIgnoreCase("right")) {
                    numSign="+";
                }

                String var = ctx.roll_verbal().var_decl().getText();
                ActionCommandRotate cmd = new ActionCommandRotate();
                VariableDef vd = prg.getVar(var);
                cmd.varDef=vd;
                cmd.targetVar = var;
                cmd.axis = axis;
                cmd.numSign = numSign;
                cmd.numExpr = ctx.roll_verbal().turn_degrees().logical_expression();
                cmd.speedExp = ctx.roll_verbal().speed_expr().logical_expression();
                cmd.motionEaseType = parseMotionEase(ctx.roll_verbal().motion_ease_attr());

                rotate.statements.add(cmd);
                return rotate;

            }

            public ActionStatementBase visitMoveVerbalStatement(SceneMaxParser.MoveVerbalStatementContext ctx) {

                String var = ctx.move_verbal().var_decl().getText();
                String axis = "z";
                String numSign = "";
                String dir = ctx.move_verbal().move_direction().getText();

                VariableDef vd = prg.getVar(var);
                int verbalCommand=0;

                if(dir.equalsIgnoreCase("left")) {
                    verbalCommand=ActionCommandMove.VERBAL_MOVE_LEFT;
                    axis="x";
                    numSign="+";
                } else if(dir.equalsIgnoreCase("right")) {
                    verbalCommand=ActionCommandMove.VERBAL_MOVE_RIGHT;
                    axis="x";
                    numSign="-";
                } else if(dir.equalsIgnoreCase("up")) {
                    verbalCommand=ActionCommandMove.VERBAL_MOVE_UP;
                    axis="y";
                    numSign="+";
                } else if(dir.equalsIgnoreCase("down")) {
                    verbalCommand=ActionCommandMove.VERBAL_MOVE_DOWN;
                    axis="y";
                    numSign="-";
                } else if(dir.equalsIgnoreCase("forward")) {
                    verbalCommand=ActionCommandMove.VERBAL_MOVE_FORWARD;
                    axis="z";
                    numSign="+";
                } else if(dir.equalsIgnoreCase("backward")) {
                    verbalCommand=ActionCommandMove.VERBAL_MOVE_BACKWARD;
                    axis="z";
                    numSign="-";
                }

                SceneMaxParser.Speed_exprContext speedExprCtx = ctx.move_verbal().speed_expr();
                if (speedExprCtx == null) {
                    return null;
                }

                ActionCommandMove move = new ActionCommandMove();
                ActionCommandMove cmd = new ActionCommandMove();

                cmd.verbalCommand=verbalCommand;
                cmd.varDef=vd;
                cmd.targetVar = var;

                cmd.axis = axis;
                cmd.numSign = numSign;
                cmd.numExpr = ctx.move_verbal().logical_expression();
                cmd.speedExpr=speedExprCtx.logical_expression();
                cmd.loopExpr = ctx.move_verbal().loop_expr();
                cmd.motionEaseType = parseMotionEase(ctx.move_verbal().motion_ease_attr());
                move.statements.add(cmd);

                return move;

            }

            public ActionStatementBase visitMoveTo(SceneMaxParser.MoveToContext ctx) {
                MoveToCommand cmd = new MoveToCommand();

                if(ctx.move_to().looking_at_expr()!=null) {
                    cmd.lookingAtStatement = parsePositionStatement(ctx.move_to().looking_at_expr().position_statement());
                }

                if(ctx.move_to().move_to_target().position_statement()!=null) {
                    SceneMaxParser.Position_statementContext posCtx = ctx.move_to().move_to_target().position_statement();
                    cmd.posStatement = parsePositionStatement(posCtx);

                } else if(ctx.move_to().move_to_target().ID()!=null) {
                    cmd.moveToTarget = ctx.move_to().move_to_target().ID().getText();
                } else {
                    SceneMaxParser.Pos_axesContext posAxes = ctx.move_to().move_to_target().pos_axes();
                    if(posAxes!=null) {
                        if(posAxes.exception==null) {
                            cmd.moveToTargetXExpr = posAxes.print_pos_x().logical_expression();
                            cmd.moveToTargetYExpr = posAxes.print_pos_y().logical_expression();
                            cmd.moveToTargetZExpr = posAxes.print_pos_z().logical_expression();
                        } else {
//                            String err="Unrecognized target definition in line: "+posAxes.exception.getOffendingToken().getLine();
//                            prg.syntaxErrors.add(err);
                            return null;
                        }
                    }
                }

                cmd.targetVar = ctx.move_to().var_decl().getText();
                cmd.varLineNum = ctx.move_to().var_decl().getStart().getLine();
                cmd.extraDistanceExpr = ctx.move_to().logical_expression();
                cmd.speedExpr = ctx.move_to().speed_expr().logical_expression();
                cmd.motionEaseType = parseMotionEase(ctx.move_to().motion_ease_attr());

                cmd.validate(prg);

                return cmd;
            }

            public ActionStatementBase visitRayCheckStatement(SceneMaxParser.RayCheckStatementContext ctx) {
                RayCheckCommand cmd = new RayCheckCommand();
                cmd.targetGroup = ctx.ray_check().var_decl().getText();

                SceneMaxParser.Ray_check_fromContext from = ctx.ray_check().ray_check_from();
                if(from!=null) {
                    SceneMaxParser.Pos_axesContext axes = from.pos_axes();
                    if(axes!=null) {
                        cmd.posX = axes.print_pos_x().logical_expression();
                        cmd.posY = axes.print_pos_y().logical_expression();
                        cmd.posZ = axes.print_pos_z().logical_expression();
                    } else {
                        cmd.entityPos = from.pos_entity().getText();
                    }
                }

                if(ctx.ray_check()!=null) {

                    if(ctx.ray_check().exception!=null) {
                        prg.syntaxErrors.add(ctx.ray_check().exception.getMessage());
                        return null;
                    }

                    DoBlockCommand doBlock = new DoBlockVisitor(prg).visit(ctx.ray_check().do_block());
                    cmd.doBlock = doBlock;
                }
                return cmd;
            }

            public ActionStatementBase visitUserDataStatement(SceneMaxParser.UserDataStatementContext ctx) {
                SetUserDataCommand cmd = new SetUserDataCommand();
                cmd.varName = ctx.user_data().var_decl().getText();
                cmd.fieldName = ctx.user_data().field_name().getText();
                cmd.dataExpr = ctx.user_data().logical_expression() ;

                return cmd;

            }

            public ActionStatementBase visitVelocityStatement(SceneMaxParser.VelocityStatementContext ctx) {
                ChangeVelocityCommand cmd = new ChangeVelocityCommand();
                cmd.targetVar=ctx.velocity().var_decl().getText();
                cmd.velocityExpr = ctx.velocity().logical_expression();
                return cmd;
            }

            public ActionStatementBase visitMassStatement(SceneMaxParser.MassStatementContext ctx) {
                ChangeMassCommand cmd = new ChangeMassCommand();
                cmd.varName = ctx.mass().var_decl().getText();
                cmd.massExpr = ctx.mass().logical_expression();

                return cmd;

            }

            public ActionStatementBase visitClearModes(SceneMaxParser.ClearModesContext ctx) {
                ClearModeCommand cmd = new ClearModeCommand();
                cmd.varName = ctx.clear_modes().var_decl().getText();
                for(SceneMaxParser.Clear_mode_optionContext mode : ctx.clear_modes().clear_modes_options().clear_mode_option()) {
                    if(mode.character_mode()!=null) {
                        cmd.modeToClear=SwitchModeCommand.CHARACTER;
                    }
                }
                return cmd;
            }

            public ActionStatementBase visitSwitch_mode(SceneMaxParser.Switch_modeContext ctx) {

                SwitchModeCommand cmd = new SwitchModeCommand();
                cmd.varName = ctx.var_decl().getText();
                if(ctx.switch_options().switch_to_character()!=null){
                    cmd.switchTo=SwitchModeCommand.CHARACTER;
                    SceneMaxParser.Character_mode_attributesContext charAttr = ctx.switch_options().switch_to_character().character_mode_attributes();
                    if(charAttr!=null) {
                        for(SceneMaxParser.Character_mode_attributeContext attr:charAttr.character_mode_attribute()) {
                            if(attr.scalar_gravity()!=null) {
                                cmd.gravityExpr = attr.scalar_gravity().logical_expression();
                            }
                        }
                    }


                } else if(ctx.switch_options().switch_to_rigid_body()!=null){
                    cmd.switchTo=SwitchModeCommand.RIGID_BODY;
               } else if(ctx.switch_options().switch_to_ragdoll()!=null) {
                    cmd.switchTo=SwitchModeCommand.RAGDOLL;
                } else if(ctx.switch_options().switch_to_kinematic()!=null) {
                    cmd.switchTo=SwitchModeCommand.KINEMATIC;
                } else if(ctx.switch_options().switch_to_floating()!=null) {
                    cmd.switchTo=SwitchModeCommand.FLOATING;
                }


                return cmd;
            }

            public ActionStatementBase visitPosStatement(SceneMaxParser.PosStatementContext ctx) {

                ActionCommandPos cmd = new ActionCommandPos();
                cmd.targetVar = ctx.pos().var_decl().getText();
                if(ctx.pos().position_statement()!=null) {
                    SceneMaxParser.Position_statementContext posCtx = ctx.pos().position_statement();
                    cmd.posStatement = parsePositionStatement(posCtx);
                } else if(ctx.pos().pos_axes()!=null) {

                    if(ctx.pos().pos_axes().exception!=null) {
                        return null;
                    }

                    cmd.x = ctx.pos().pos_axes().print_pos_x().logical_expression();
                    cmd.y = ctx.pos().pos_axes().print_pos_y().logical_expression();
                    cmd.z = ctx.pos().pos_axes().print_pos_z().logical_expression();
                } else {
                    cmd.entityPos=new EntityPos();
                    setEntityPos(cmd.entityPos, ctx.pos().pos_entity());
                }

                return cmd;
            }

            public ActionStatementBase visitVehicleEngineSetup(SceneMaxParser.VehicleEngineSetupContext ctx) {
                VehicleSetupCommand cmd = new VehicleSetupCommand();
                cmd.setupEngine=true;
                cmd.targetVar = ctx.vehicle_engine_setup().var_decl().getText();

                if(ctx.vehicle_engine_setup().engine_options().engine_power_option()!=null) {
                    cmd.enginePowerExp = ctx.vehicle_engine_setup().engine_options().engine_power_option().logical_expression();
                } else if(ctx.vehicle_engine_setup().engine_options().engine_breaking_option()!=null) {
                    cmd.engineBreakingExp = ctx.vehicle_engine_setup().engine_options().engine_breaking_option().logical_expression();
                } else if(ctx.vehicle_engine_setup().engine_options().engine_action_start_off()!=null) {
                    cmd.engineOnOff = ctx.vehicle_engine_setup().engine_options().engine_action_start_off().getText();
                }

                return cmd;
            }

            public ActionStatementBase visitVehicleInputSetup(SceneMaxParser.VehicleInputSetupContext ctx) {
                VehicleSetupCommand cmd = new VehicleSetupCommand();
                cmd.setupInput=true;
                cmd.targetVar = ctx.vehicle_input_setup().var_decl().getText();

                if(ctx.vehicle_input_setup().vehicle_input_setup_options()!=null) {
                    InputMapping im = new InputMapping();
                    for (SceneMaxParser.Vehicle_input_optionContext opt : ctx.vehicle_input_setup().vehicle_input_setup_options().vehicle_input_option()) {

                        String key = opt.input_source().getText();
                        key=key.replace("key ","").toLowerCase();
                        String action = opt.vehicle_action().getText();
                        cmd.addInputSource(action, im.getKeyVal(key));
                    }

                } else if(ctx.vehicle_input_setup().on_off_options()!=null) {
                    cmd.inputOnOffCommand = ctx.vehicle_input_setup().on_off_options().getText();
                }
                return cmd;
            }

            public ActionStatementBase visitVehicleSetup(SceneMaxParser.VehicleSetupContext ctx) {
                VehicleSetupCommand cmd = new VehicleSetupCommand();
                cmd.targetVar = ctx.vehicle_setup().var_decl().getText();
                cmd.setupFront = ctx.vehicle_setup().vehicle_side().getText().equalsIgnoreCase("front");

                for(SceneMaxParser.Vehicle_optionContext opt:ctx.vehicle_setup().vehicle_setup_options().vehicle_option()) {
                    if(opt.vehicle_friction_option()!=null) {
                        cmd.frictionExpr = opt.vehicle_friction_option().logical_expression();
                    } else if(opt.vehicle_suspension_option()!=null) {

                        for(SceneMaxParser.Specific_suspension_optionContext suspOpt:opt.vehicle_suspension_option().specific_suspension_options().specific_suspension_option()) {
                            if(suspOpt.specific_suspension_opt_compression()!=null) {
                                cmd.compressionExpr=suspOpt.specific_suspension_opt_compression().logical_expression();
                            } else if(suspOpt.specific_suspension_opt_damping()!=null) {
                                cmd.dampingExpr = suspOpt.specific_suspension_opt_damping().logical_expression();
                            } else if(suspOpt.specific_suspension_opt_stiffness()!=null) {
                                cmd.stiffnessExpr = suspOpt.specific_suspension_opt_stiffness().logical_expression();
                            } else if(suspOpt.specific_suspension_opt_length()!=null) {
                                cmd.lengthExpr = suspOpt.specific_suspension_opt_length().logical_expression();
                            }
                        }
                    }

                }
                return cmd;

            }

            public ActionStatementBase visitAccelerateStatement(SceneMaxParser.AccelerateStatementContext ctx) {
                AccelerateCommand cmd = new AccelerateCommand();
                cmd.targetVar=ctx.accelerate().var_decl().getText();
                cmd.accelerateExp=ctx.accelerate().logical_expression();
                return cmd;
            }

            public ActionStatementBase visitBrakeStatement(SceneMaxParser.BrakeStatementContext ctx) {
                CarBrakeCommand cmd = new CarBrakeCommand();
                cmd.targetVar=ctx.brake().var_decl().getText();
                cmd.brakeExp=ctx.brake().logical_expression();
                return cmd;
            }

            public ActionStatementBase visitTurboStatement(SceneMaxParser.TurboStatementContext ctx) {
                CarTurboCommand cmd = new CarTurboCommand();
                cmd.targetVar=ctx.turbo().var_decl().getText();
                cmd.xExpr = ctx.turbo().print_pos_x().logical_expression();
                cmd.yExpr = ctx.turbo().print_pos_y().logical_expression();
                cmd.zExpr = ctx.turbo().print_pos_z().logical_expression();

                return cmd;
            }

            public ActionStatementBase visitSteerStatement(SceneMaxParser.SteerStatementContext ctx) {
                CarSteerCommand cmd = new CarSteerCommand();
                cmd.targetVar=ctx.steer().var_decl().getText();
                cmd.steerExp=ctx.steer().logical_expression();
                return cmd;
            }

            public ActionStatementBase visitResetStatement(SceneMaxParser.ResetStatementContext ctx) {
                CarResetCommand cmd = new CarResetCommand();
                cmd.targetVar=ctx.reset_vehicle().var_decl().getText();
                return cmd;
            }

            public ActionStatementBase visitHideStatement(SceneMaxParser.HideStatementContext ctx) {
                ActionCommandShowHide cmd = new ActionCommandShowHide();
                cmd.varName = ctx.hide().var_decl().getText();
                cmd.show=false;
                if(ctx.hide().show_options()!=null) {
                    if(ctx.hide().show_options().show_info_option()!=null) {
                        cmd.info = true;
                    } else if(ctx.hide().show_options().Wireframe()!=null) {
                        cmd.wireframe = true;
                    } else if(ctx.hide().show_options().Speedo()!=null) {
                        cmd.speedo = true;
                    } else if(ctx.hide().show_options().Tacho()!=null) {
                        cmd.tacho = true;
                    }  else if(ctx.hide().show_options().show_joints_option()!=null) {
                        cmd.joints = true;
                    } else if(ctx.hide().show_options().Outline()!=null) {
                        cmd.outline = true;
                    }
                }
                return cmd;
            }

            public ActionStatementBase visitShowStatement(SceneMaxParser.ShowStatementContext ctx) {
                ActionCommandShowHide cmd = new ActionCommandShowHide();
                cmd.varName = ctx.show().var_decl().getText();
                cmd.show=true;
                if(ctx.show().show_options()!=null) {
                    if(ctx.show().show_options().show_info_option()!=null) {
                        cmd.info = true;
                        if(ctx.show().show_options().show_info_option().show_info_attributes()!=null) {
                            for(SceneMaxParser.Show_info_attributeContext attr: ctx.show().show_options().show_info_option().show_info_attributes().show_info_attribute()) {
                                if(attr.file_attr()!=null) {
                                    String txt = attr.file_attr().QUOTED_STRING().getText();
                                    if(txt.length()>0) {
                                        txt=txt.substring(1,txt.length()-1);
                                    }
                                    cmd.infoDumpFile = txt;
                                }
                            }
                        }
                    } else if(ctx.show().show_options().Wireframe()!=null) {
                        cmd.wireframe = true;
                    } else if(ctx.show().show_options().show_axis_option()!=null) {
                        cmd.axisX = ctx.show().show_options().show_axis_option().X()!=null;
                        cmd.axisY = ctx.show().show_options().show_axis_option().Y()!=null;
                        cmd.axisZ = ctx.show().show_options().show_axis_option().Z()!=null;
                    } else if(ctx.show().show_options().Speedo()!=null) {
                        cmd.speedo = true;
                    } else if(ctx.show().show_options().Tacho()!=null) {
                        cmd.tacho = true;
                    } else if(ctx.show().show_options().show_joints_option()!=null) {
                        cmd.joints = true;
                        SceneMaxParser.Show_joints_attributesContext joints_attr = ctx.show().show_options().show_joints_option().show_joints_attributes();
                        if(joints_attr!=null) {
                            for(SceneMaxParser.Show_joints_attributeContext attr:joints_attr.show_joints_attribute()) {
                                if(attr.scalar_size_attr()!=null) {
                                    cmd.showJointsSizeExpr = attr.scalar_size_attr().logical_expression();
                                }
                            }
                        }
                    } else if(ctx.show().show_options().Outline()!=null) {
                        cmd.outline = true;
                    }
                }

                return cmd;
            }

            public ActionStatementBase visitStopStatement(SceneMaxParser.StopStatementContext ctx) {
                String var=ctx.stop().var_decl().getText();
                ActionCommandStop cmd = new ActionCommandStop();
                VariableDef vd = prg.getVar(var);
                cmd.varDef=vd;
                cmd.targetVar = var;

                return cmd;
            }


            public ActionStatementBase visitDirectionalMove(SceneMaxParser.DirectionalMoveContext ctx) {

                DirectionalMoveCommand cmd = new DirectionalMoveCommand();

                String var = ctx.directional_move().var_decl().getText();
                VariableDef vd = prg.getVar(var);
                cmd.varDef=vd;
                cmd.targetVar = var;
                SceneMaxParser.Move_directionContext md = ctx.directional_move().move_direction();

                if(md.Forward()!=null) {
                    cmd.direction=DirectionalMoveCommand.FORWARD;
                } else if(md.Backward()!=null) {
                    cmd.direction=DirectionalMoveCommand.BACKWARD;
                } else if(md.Left()!=null) {
                    cmd.direction=DirectionalMoveCommand.LEFT;
                } else if(md.Right()!=null) {
                    cmd.direction=DirectionalMoveCommand.RIGHT;
                }

                cmd.distanceExpr = ctx.directional_move().logical_expression(0);
                if(ctx.directional_move().logical_expression().size()>1) {
                    cmd.timeExpr = ctx.directional_move().logical_expression(1);
                }

                cmd.loopExpr = ctx.directional_move().loop_expr();
                cmd.motionEaseType = parseMotionEase(ctx.directional_move().motion_ease_attr());
                return cmd;

            }

            public ActionStatementBase visitMoveStatement(SceneMaxParser.MoveStatementContext ctx) {
                try {
                    String var = ctx.move().var_decl().getText();

                    SceneMaxParser.Speed_exprContext speedExprCtx = ctx.move().speed_expr();
                    if (speedExprCtx == null) {
                        return null;
                    }

                    ActionCommandMove move = new ActionCommandMove();

                    for (int i = 0; i < ctx.move().axis_expr().size(); ++i) {
                        SceneMaxParser.Axis_exprContext actx = ctx.move().axis_expr().get(i);
                        String axis = actx.axis_id().getText();
                        String numSign = actx.number_sign().getText();

                        ActionCommandMove cmd = new ActionCommandMove();
                        VariableDef vd = prg.getVar(var);
                        cmd.varDef=vd;
                        cmd.targetVar = var;

                        cmd.axis = axis;
                        cmd.numSign = numSign;
                        cmd.numExpr = actx.logical_expression();//new ActionLogicalExpression(actx.logical_expression(),prg);
                        cmd.speedExpr=speedExprCtx.logical_expression();//speedExpr;
                        cmd.motionEaseType = parseMotionEase(ctx.move().motion_ease_attr());

                        move.statements.add(cmd);

                    }
                    return move;
                }catch(Exception e){
                    return null;
                }
            }


            public ActionStatementBase visitRotateReset(SceneMaxParser.RotateResetContext ctx) {
                RotateResetCommand cmd = new RotateResetCommand();
                cmd.xExpr = ctx.rotate_reset().print_pos_x().logical_expression();
                cmd.yExpr = ctx.rotate_reset().print_pos_y().logical_expression();
                cmd.zExpr = ctx.rotate_reset().print_pos_z().logical_expression();
                cmd.targetVar=ctx.rotate_reset().var_decl().getText();
                return cmd;
            }

            @Override
            public ActionStatementBase visitRotateToStatement(SceneMaxParser.RotateToStatementContext ctx) {

                ActionCommandRotateTo cmd = new ActionCommandRotateTo();
                cmd.targetVar = ctx.rotate_to().var_decl().getText();
                cmd.axis = ctx.rotate_to().axis_name().getText();
                cmd.speedExpr = ctx.rotate_to().speed_expr().logical_expression();
                cmd.rotateValExpr = ctx.rotate_to().logical_expression();
                cmd.motionEaseType = parseMotionEase(ctx.rotate_to().motion_ease_attr());
                return cmd;
            }

            public ActionStatementBase visitRecord(SceneMaxParser.RecordContext ctx) {
                ActionCommandRecord cmd = new ActionCommandRecord();
                cmd.targetVar = ctx.var_decl().getText();
                if(ctx.record_actions().record_transitions()!=null) {
                    cmd.recordType=ActionCommandRecord.RECORD_TYPE_TRANSITIONS;
                    cmd.everyTimeExpr = ctx.record_actions().record_transitions().every_time_expr().logical_expression();
                } else if(ctx.record_actions().record_save()!=null) {
                    cmd.recordType=ActionCommandRecord.RECORD_TYPE_SAVE;
                    cmd.savePath = ctx.record_actions().record_save().QUOTED_STRING().getText();
                    cmd.savePath = trimQuotedString(cmd.savePath);
                } else if(ctx.record_actions().record_stop()!=null) {
                    cmd.recordType=ActionCommandRecord.RECORD_TYPE_STOP;

                }

                return cmd;

            }

            @Override
            public ActionStatementBase visitRotate(SceneMaxParser.RotateContext ctx) {

                try {
                    String var = ctx.var_decl().getText();

                    SceneMaxParser.Speed_exprContext speedExprCtx = ctx.speed_expr();
                    if (speedExprCtx == null) {
                        return null;
                    }

                    ActionCommandRotate rotate = new ActionCommandRotate();

                    if(ctx.loop_expr()!=null) {
                        rotate.loopExpr = ctx.loop_expr().logical_expression();
                    }

                    for (int i = 0; i < ctx.axis_expr().size(); ++i) {
                        SceneMaxParser.Axis_exprContext actx = ctx.axis_expr().get(i);
                        String axis = actx.axis_id().getText();
                        String numSign = actx.number_sign() != null ? actx.number_sign().getText() : "+";

                        ActionCommandRotate cmd = new ActionCommandRotate();
                        VariableDef vd = prg.getVar(var);
                        cmd.varDef=vd;
                        cmd.targetVar = var;
                        cmd.axis = axis;
                        cmd.numSign = numSign;
                        cmd.numExpr = actx.logical_expression();//new ActionLogicalExpression(actx.logical_expression(),prg);

                        //cmd.speed = speed;
                        cmd.speedExp = speedExprCtx.logical_expression();//speedExp;
                        cmd.motionEaseType = parseMotionEase(ctx.motion_ease_attr());

                        rotate.statements.add(cmd);

                    }
                    return rotate;
                }catch(Exception e){
                    return null;
                }
            }

            @Override
            public ActionStatementBase visitPlay(SceneMaxParser.PlayContext ctx) {

                try {
                    if (ctx.cinematic_play() != null) {
                        CinematicCameraPlayCommand cmd = new CinematicCameraPlayCommand();
                        cmd.targetVar = ctx.cinematic_play().var_decl().getText();
                        cmd.varLineNum = ctx.cinematic_play().var_decl().getStart().getLine();

                        for (SceneMaxParser.Cinematic_play_optionContext option : ctx.cinematic_play().cinematic_play_options().cinematic_play_option()) {
                            if (option.cinematic_target_attr() != null) {
                                if (option.cinematic_target_attr().position_statement() != null) {
                                    cmd.lookAtPosStatement = parsePositionStatement(option.cinematic_target_attr().position_statement());
                                } else if (option.cinematic_target_attr().var_decl() != null) {
                                    cmd.lookAtTargetVar = option.cinematic_target_attr().var_decl().getText();
                                }
                            } else if (option.cinematic_duration_attr() != null) {
                                cmd.speedExpr = option.cinematic_duration_attr().logical_expression();
                            }
                        }

                        return cmd;
                    } else if (ctx.sprite_play() != null) {
                        String var = ctx.sprite_play().var_decl().getText();

                        ActionCommandPlay cmd = new ActionCommandPlay();
                        VariableDef vd = prg.getVar(var);
                        cmd.varDef=vd;
                        cmd.targetVar = var;

                        cmd.fromFrameExpr = ctx.sprite_play().frames_expr().from_frame().logical_expression();
                        cmd.toFrameExpr = ctx.sprite_play().frames_expr().to_frame().logical_expression();
                        cmd.speedExpr = ctx.sprite_play().speed_expr()==null?null:ctx.sprite_play().speed_expr().logical_expression();

                        if(ctx.sprite_play().play_duration_strategy()==null) {
                            cmd.durationStrategy=0;//once
                        } else if(ctx.sprite_play().play_duration_strategy().Once()!=null) {
                            cmd.durationStrategy=0;//once
                        } else if(ctx.sprite_play().play_duration_strategy().play_duration_loop_strategy()!=null) {
                            cmd.durationStrategy=2;//loop
                            if(ctx.sprite_play().play_duration_strategy().play_duration_loop_strategy().number()!=null) {
                                cmd.loopTimes = ctx.sprite_play().play_duration_strategy().play_duration_loop_strategy().number().getText();
                            } else {
                                cmd.loopTimes="-1";// endless loop
                            }
                        }
                        else {
                            cmd.durationStrategy=1;//time
                            cmd.forTimeExpr = ctx.sprite_play().play_duration_strategy().for_time_expr().logical_expression();
                        }

                        return cmd;
                    } else if (ctx.effect_play() != null) {
                        String var = ctx.effect_play().var_decl().getText();
                        EffekseerPlayCommand cmd = new EffekseerPlayCommand();
                        cmd.targetVar = var;
                        cmd.varDef = prg.getVar(var);

                        for (SceneMaxParser.Effect_play_optionContext opt : ctx.effect_play().effect_play_options().effect_play_option()) {
                            if (opt.print_pos_attr() != null) {
                                if (opt.print_pos_attr().pos_axes() != null) {
                                    if (opt.print_pos_attr().pos_axes().exception != null) {
                                        return null;
                                    }
                                    cmd.xExpr = opt.print_pos_attr().pos_axes().print_pos_x().logical_expression();
                                    cmd.yExpr = opt.print_pos_attr().pos_axes().print_pos_y().logical_expression();
                                    cmd.zExpr = opt.print_pos_attr().pos_axes().print_pos_z().logical_expression();
                                } else if (opt.print_pos_attr().pos_entity() != null) {
                                    cmd.entityPos = new EntityPos();
                                    setEntityPos(cmd.entityPos, opt.print_pos_attr().pos_entity());
                                }
                            } else if (opt.effect_play_attr_list() != null) {
                                for (SceneMaxParser.Effect_play_attrContext attr : opt.effect_play_attr_list().effect_play_attr()) {
                                    String key = attr.effect_play_attr_name().getText();
                                    if (key.startsWith("\"") && key.endsWith("\"")) {
                                        key = stripQutes(key);
                                    }
                                    cmd.attrExprs.put(key.toLowerCase(), attr.logical_expression());
                                }
                            }
                        }

                        return cmd;
                    }
                    return null;
                }catch(Exception e) {
                    return null;
                }
            }

            public ActionStatementBase visitAnimate(SceneMaxParser.AnimateContext ctx) {

                AnimateOptionsCommand cmd = new AnimateOptionsCommand();
                cmd.targetVar=ctx.var_decl().getText();

                for(SceneMaxParser.Animation_attrContext attr: ctx.animation_attr()) {
                    if(attr.anim_attr_speed()!=null) {

                        cmd.speedExpr = attr.anim_attr_speed().logical_expression();

                        if(attr.anim_attr_speed().speed_for_seconds()!=null) {
                            cmd.forTimeExpr = attr.anim_attr_speed().speed_for_seconds().logical_expression();
                        }

                        if(attr.anim_attr_speed().when_frames_above()!=null) {
                            cmd.aboveFramesExpr = attr.anim_attr_speed().when_frames_above().logical_expression();
                        }
                    }
                }

                return cmd;

            }

            public ActionStatementBase visitAnimate_short(SceneMaxParser.Animate_shortContext ctx) {
                String var = ctx.var_decl().getText();
                int varLineNum = ctx.var_decl().getStart().getLine();

                boolean firstAnim = true;

                ActionCommandAnimate animate = new ActionCommandAnimate();
                VariableDef vd = prg.getVar(var);
                animate.varDef=vd;

                animate.loop = ctx.Loop()!=null;
                if(ctx.go_condition()!=null) {
                    animate.goExpr = ctx.go_condition().logical_expression();
                }

                if (ctx.animate_short_having_expr()!=null) {
                    for (SceneMaxParser.Anim_short_attributeContext animShortAttributeContext : ctx.animate_short_having_expr().anim_short_attributes().anim_short_attribute()) {
                        if (animShortAttributeContext.protected_expr()!=null) {
                            animate.isProtected = true;
                        }
                    }
                }

                for (int i = 0; i < ctx.anim_expr().size(); ++i) {
                    SceneMaxParser.Anim_exprContext actx = ctx.anim_expr().get(i);

                    String anim = actx.animation_name().getText();
                    if(anim.startsWith("\"") && anim.length()>2) {
                        anim=anim.substring(1,anim.length()-1);
                    }

                    if (firstAnim) {
                        firstAnim = false;
                    }

                    ActionCommandAnimate cmd = new ActionCommandAnimate();
                    cmd.animationName = anim;
                    cmd.isProtected = animate.isProtected;
                    cmd.varDef=vd;
                    cmd.targetVar = var;
                    cmd.varLineNum=varLineNum;
                    cmd.speedExpr=actx.speed_of_expr()==null?null:actx.speed_of_expr().logical_expression();//speedExpr;
                    cmd.goExpr = animate.goExpr;
                    animate.statements.add(cmd);

                }

                return animate;
            }

        }


    }


    private List<DirectionVerb> parseDirectionVerbs(List<SceneMaxParser.Dir_statementContext> dirStatements) {

        List<DirectionVerb> directionVerbs = new ArrayList<>();
        for(SceneMaxParser.Dir_statementContext ds : dirStatements) {
            DirectionVerb dv = new DirectionVerb();
            if(ds.dir_verb().Forward()!=null) {
                dv.verb = DirectionVerb.FORWARD;
            } else if(ds.dir_verb().Backward()!=null) {
                dv.verb = DirectionVerb.BACKWARD;
            } else if(ds.dir_verb().Left()!=null) {
                dv.verb = DirectionVerb.LEFT;
            } else if(ds.dir_verb().Right()!=null) {
                dv.verb = DirectionVerb.RIGHT;
            } else if(ds.dir_verb().Up()!=null) {
                dv.verb = DirectionVerb.UP;
            } else if(ds.dir_verb().Down()!=null) {
                dv.verb = DirectionVerb.DOWN;
            }

            dv.valExp = ds.logical_expression();
            directionVerbs.add(dv);
        }

        return directionVerbs;
    }

    private PositionStatement parsePositionStatement(SceneMaxParser.Position_statementContext posCtx) {

        PositionStatement ps = new PositionStatement();
        ps.startEntity = posCtx.var_decl().getText();
        if(posCtx.dir_statement()!=null) {
            ps.directionVerbs=parseDirectionVerbs(posCtx.dir_statement());
        }

        return ps;

    }

    private int parseMotionEase(SceneMaxParser.Motion_ease_attrContext ctx) {

        if(ctx==null) {
            return MotionEaseType.LINEAR;
        }

        if(ctx.In()!=null && ctx.Out()!=null) {
            return MotionEaseType.EASE_IN_OUT;
        }

        if(ctx.Out()!=null) {
            return MotionEaseType.EASE_OUT;
        }

        return MotionEaseType.EASE_IN;

    }

    private void setEntityPos(EntityPos pos, SceneMaxParser.Pos_entityContext entityPos) {
        pos.entityName = entityPos.var_decl().getText();
        if(entityPos.collision_joint_1()!=null) {
            pos.entityJointName = entityPos.collision_joint_1().QUOTED_STRING().getText();
            if(pos.entityJointName.length()>2) {
                pos.entityJointName = pos.entityJointName.substring(1, pos.entityJointName.length() - 1);
            }
        }
    }

    private String stripQutes(String str) {
        if (str.length() > 2) {
            return str.substring(1, str.length() - 1);
        } else {
            return "";
        }
    }

    private String readLightColor(SceneMaxParser.Light_color_valueContext colorValue) {
        if (colorValue == null) {
            return null;
        }
        String text = colorValue.getText();
        if (text != null && text.startsWith("\"")) {
            return stripQutes(text);
        }
        return text;
    }

    private VariableDef createImplicitVariableDef(ProgramDef program, String varName) {
        VariableDef vd = new VariableDef();
        vd.resName = "var";
        vd.varName = varName;
        program.vars.add(vd);
        program.vars_index.put(vd.varName, vd);
        return vd;
    }

    private boolean isCameraSystemValueExpression(SceneMaxParser.Logical_expressionContext expr) {
        SceneMaxParser.ValueContext valueCtx = resolveSimpleValueExpression(expr);
        return valueCtx != null && valueCtx.camera_system_expr() != null;
    }

    private boolean isCameraModifierValueExpression(SceneMaxParser.Logical_expressionContext expr) {
        SceneMaxParser.ValueContext valueCtx = resolveSimpleValueExpression(expr);
        return valueCtx != null && valueCtx.camera_modifier_expr() != null;
    }

    private SceneMaxParser.ValueContext resolveSimpleValueExpression(SceneMaxParser.Logical_expressionContext expr) {
        if (expr == null || expr.booleanAndExpression().size() != 1) {
            return null;
        }
        SceneMaxParser.BooleanAndExpressionContext andExpr = expr.booleanAndExpression(0);
        if (andExpr.relationalExpression().size() != 1) {
            return null;
        }
        SceneMaxParser.RelationalExpressionContext relExpr = andExpr.relationalExpression(0);
        if (relExpr.additiveExpression().size() != 1) {
            return null;
        }
        SceneMaxParser.AdditiveExpressionContext addExpr = relExpr.additiveExpression(0);
        if (addExpr.multiplicativeExpression().size() != 1) {
            return null;
        }
        SceneMaxParser.MultiplicativeExpressionContext multExpr = addExpr.multiplicativeExpression(0);
        if (multExpr.unaryExpression().size() != 1) {
            return null;
        }
        SceneMaxParser.UnaryExpressionContext unaryExpr = multExpr.unaryExpression(0);
        if (unaryExpr.primaryExpression() == null || unaryExpr.primaryExpression().value() == null) {
            return null;
        }
        return unaryExpr.primaryExpression().value();
    }

    private void setParserSourceFileName(String file) {
        _sourceFileName=file;
    }

}
