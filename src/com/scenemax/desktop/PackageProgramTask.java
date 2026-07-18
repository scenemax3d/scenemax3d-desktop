package com.scenemax.desktop;

import com.scenemaxeng.common.ui.model.UIDocument;
import com.scenemaxeng.common.ui.model.UILayerDef;
import com.scenemaxeng.common.ui.model.UIWidgetDef;
import com.scenemaxeng.compiler.ApplyMacroResults;
import com.scenemaxeng.compiler.MacroFilter;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;

//import com.scenemaxeng.projector.*;
import com.scenemaxeng.common.types.*;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.scenemaxeng.compiler.SceneMaxLanguageParser.macroFilter;

public class PackageProgramTask extends SwingWorker<Integer, String> {

    private static final String SCENE_JAR_NAME = "scenemax3d_scene.jar";
    private static final String EMBEDDED_RUNTIME_DIR_NAME = "runtime";
    private static final String SELF_EXTRACT_PAYLOAD_JAR_NAME = "scenemax3d_scene.jar";
    private static final byte[] SELF_EXTRACT_PAYLOAD_MAGIC = "SMXPKG1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SELF_EXTRACT_FOOTER_MAGIC = "SCENEMAX_PAYLOAD".getBytes(StandardCharsets.US_ASCII);
    private static final Set<String> SCENEMAX_RUNTIME_GUARD_MODULES = new LinkedHashSet<>();

    static {
        SCENEMAX_RUNTIME_GUARD_MODULES.add("java.desktop");
        SCENEMAX_RUNTIME_GUARD_MODULES.add("java.logging");
        SCENEMAX_RUNTIME_GUARD_MODULES.add("jdk.charsets");
        SCENEMAX_RUNTIME_GUARD_MODULES.add("jdk.unsupported");
    }

    private File scriptFolder=null;
    private String prg;
    private Runnable finish;
    private Runnable canceled;
    private int globalCounter=0;
    private int progressPhaseBase = 0;
    private int progressPhaseSpan = 1;
    private int progressPhaseDone = 0;
    private int progressPhaseTotal = 1;
    private final EnumSet<PackageTarget> targets;
    private final List<File> producedArtifacts = new ArrayList<>();
    private final Set<String> addedJarEntries = new LinkedHashSet<>();
    private final Set<String> uiReferencedSpriteNames = new LinkedHashSet<>();
    private final Set<String> uiReferencedFontNames = new LinkedHashSet<>();
    private final Set<String> uiReferencedImagePaths = new LinkedHashSet<>();
    private final Set<String> animationNamesUsed = new LinkedHashSet<>();
    private final Set<String> shaderNamesUsed = new LinkedHashSet<>();
    private final Set<String> environmentShaderNamesUsed = new LinkedHashSet<>();
    private final Set<String> materialNamesUsed = new LinkedHashSet<>();
    private final Set<String> builtInMaterialNamesUsed = new LinkedHashSet<>();
    private final Set<String> weaponAssetNamesUsed = new LinkedHashSet<>();
    private final Set<String> throwMotionAssetNamesUsed = new LinkedHashSet<>();
    private final Set<String> ikAssetNamesUsed = new LinkedHashSet<>();
    private final List<String> scannedScriptFiles = new ArrayList<>();
    private final List<String> scannedDesignerFiles = new ArrayList<>();
    private final List<String> reachableUiFiles = new ArrayList<>();
    private final PackageOptions options;
    private File outputFolder;
    private final StringBuilder completionNotes = new StringBuilder();
    private volatile String statusNote = "";
    private volatile String failureMessage = "";
    private String packagingInventoryJson = "";
    private File packageLogFile;
    private JavaExtensionBuildTool.BuildResult javaExtensionBuildResult = new JavaExtensionBuildTool.BuildResult();

    public enum PackageTarget {
        WINDOWS,
        LINUX,
        MAC_OSX,
        WEB_START
    }

    public static class PackageOptions {
        public final File windowsIcon;
        public final File linuxIcon;
        public final File macIcon;
        public final String webBaseUrl;
        public final String webVendor;
        public final String webHomepage;
        public final String webRemoteFolder;
        public final boolean uploadWebStart;
        public final boolean signWebStart;
        public final boolean generateSelfSignedCertificate;
        public final File keystoreFile;
        public final String keystoreAlias;
        public final String keystorePassword;
        public final String keyPassword;
        public final boolean uploadToItch;
        public final boolean embedMinimalJavaRuntime;
        public final String itchButlerPath;
        public final String itchGameTarget;
        public final String itchApiKey;
        public final String itchWindowsChannel;
        public final String itchLinuxChannel;
        public final String itchMacChannel;

        public PackageOptions(File windowsIcon, File linuxIcon, File macIcon,
                              String webBaseUrl, String webVendor, String webHomepage,
                              String webRemoteFolder, boolean uploadWebStart, boolean signWebStart,
                              boolean generateSelfSignedCertificate, File keystoreFile,
                              String keystoreAlias, String keystorePassword, String keyPassword,
                              boolean uploadToItch, boolean embedMinimalJavaRuntime,
                              String itchButlerPath, String itchGameTarget,
                              String itchApiKey, String itchWindowsChannel, String itchLinuxChannel,
                              String itchMacChannel) {
            this.windowsIcon = windowsIcon;
            this.linuxIcon = linuxIcon;
            this.macIcon = macIcon;
            this.webBaseUrl = webBaseUrl == null ? "" : webBaseUrl.trim();
            this.webVendor = webVendor == null ? "" : webVendor.trim();
            this.webHomepage = webHomepage == null ? "" : webHomepage.trim();
            this.webRemoteFolder = webRemoteFolder == null ? "" : webRemoteFolder.trim();
            this.uploadWebStart = uploadWebStart;
            this.signWebStart = signWebStart;
            this.generateSelfSignedCertificate = generateSelfSignedCertificate;
            this.keystoreFile = keystoreFile;
            this.keystoreAlias = keystoreAlias == null ? "" : keystoreAlias.trim();
            this.keystorePassword = keystorePassword == null ? "" : keystorePassword;
            this.keyPassword = keyPassword == null || keyPassword.trim().length() == 0 ? this.keystorePassword : keyPassword;
            this.uploadToItch = uploadToItch;
            this.embedMinimalJavaRuntime = embedMinimalJavaRuntime;
            this.itchButlerPath = itchButlerPath == null ? "" : itchButlerPath.trim();
            this.itchGameTarget = itchGameTarget == null ? "" : itchGameTarget.trim();
            this.itchApiKey = itchApiKey == null ? "" : itchApiKey.trim();
            this.itchWindowsChannel = itchWindowsChannel == null ? "" : itchWindowsChannel.trim();
            this.itchLinuxChannel = itchLinuxChannel == null ? "" : itchLinuxChannel.trim();
            this.itchMacChannel = itchMacChannel == null ? "" : itchMacChannel.trim();
        }
    }

    public PackageProgramTask(String scriptFilePath, String prg, List<PackageTarget> targets, PackageOptions options, Runnable finish, Runnable canceled) {
        this.prg=prg;
        this.finish=finish;
        this.canceled=canceled;
        this.options = options == null ? new PackageOptions(null, null, null, "", "", "", "", false, false, false, null, "", "", "", false, false, "", "", "", "", "", "") : options;
        this.targets = targets == null || targets.isEmpty()
                ? EnumSet.of(PackageTarget.WINDOWS)
                : EnumSet.copyOf(targets);

        if(scriptFilePath!=null) {
            File f = new File(scriptFilePath);
            if(f.isFile()) {
                f=f.getParentFile();
            }

            this.scriptFolder = f;
        }

    }


    private void setPackageProgress(int progress) {
        setProgress(Math.max(0, Math.min(100, progress)));
    }

    private void setRunningProgress(int progress) {
        setPackageProgress(Math.min(99, progress));
    }

    private void beginProgressPhase(int base, int span, int totalWork) {
        progressPhaseBase = Math.max(0, Math.min(99, base));
        progressPhaseSpan = Math.max(1, Math.min(99 - progressPhaseBase, span));
        progressPhaseDone = 0;
        progressPhaseTotal = Math.max(1, totalWork);
        setRunningProgress(progressPhaseBase);
    }

    private void finishProgressPhase() {
        setRunningProgress(progressPhaseBase + progressPhaseSpan);
    }

    private void advanceProgressUnit() {
        globalCounter += 1;
        progressPhaseDone += 1;
        int phaseProgress = Math.min(progressPhaseSpan,
                Math.round(progressPhaseDone * progressPhaseSpan / (float) progressPhaseTotal));
        setRunningProgress(progressPhaseBase + phaseProgress);
    }

    private int scaledProgress(int base, int span, int percent) {
        int boundedPercent = Math.max(0, Math.min(100, percent));
        return base + Math.round(span * boundedPercent / 100f);
    }

    @Override
    protected Integer doInBackground() throws Exception {

        setPackageProgress(0);
        normalizePackageRootAndProgram();
        String gameName = getGameName();
        initializePackageOutput(gameName);
        logPackage("Packaging started.");
        logPackage("Script root: " + (scriptFolder == null ? "(none)" : scriptFolder.getAbsolutePath()));
        logPackage("Targets: " + targets);
        updateStatus("Preparing package...");
        SceneMaxLanguageParser.modelsUsed = new ArrayList<>();
        SceneMaxLanguageParser.effekseerUsed = new ArrayList<>();
        SceneMaxLanguageParser.videoUsed = new ArrayList<>();
        SceneMaxLanguageParser.lightProbesUsed = new ArrayList<>();
        SceneMaxLanguageParser.spriteSheetUsed = new ArrayList<>();
        SceneMaxLanguageParser.audioUsed = new ArrayList<>();
        SceneMaxLanguageParser.fontsUsed = new ArrayList<>();
        SceneMaxLanguageParser.skyboxUsed = new ArrayList<>();
        SceneMaxLanguageParser.terrainsUsed = new ArrayList<>();
        addedJarEntries.clear();
        uiReferencedSpriteNames.clear();
        uiReferencedFontNames.clear();
        uiReferencedImagePaths.clear();
        animationNamesUsed.clear();
        shaderNamesUsed.clear();
        environmentShaderNamesUsed.clear();
        materialNamesUsed.clear();
        builtInMaterialNamesUsed.clear();
        weaponAssetNamesUsed.clear();
        throwMotionAssetNamesUsed.clear();
        ikAssetNamesUsed.clear();
        scannedScriptFiles.clear();
        scannedDesignerFiles.clear();
        reachableUiFiles.clear();
        packagingInventoryJson = "";
        SceneMaxLanguageParser.parseUsingResource = true; // look for manual resource declarations
        MacroFilter macroFilter = new MacroFilter();
        macroFilter.loadMacroRulesFromMacroFolder(new File("macro"));
        SceneMaxLanguageParser.setMacroFilter(macroFilter);
        logPackage("Parsing main script.");
        setRunningProgress(3);
        ScriptTreeResourceCollector.CollectionResult reachableSources =
                ScriptTreeResourceCollector.collectReachableResources(this.scriptFolder, macroFilter);
        scannedScriptFiles.addAll(reachableSources.scriptFiles);
        scannedDesignerFiles.addAll(DesignerDocumentResourceCollector.collectResources(reachableSources.designerFiles, macroFilter));
        reachableUiFiles.addAll(reachableSources.uiFiles);
        logPackage("Collected script files: " + scannedScriptFiles.size());
        logPackage("Collected designer files: " + scannedDesignerFiles.size());
        logPackage("Collected UI files: " + reachableUiFiles.size());
        AssetsMapping assetsMapping = new AssetsMapping(Util.getResourcesFolder());
        collectReferencedAuxiliaryAssets(assetsMapping);
        logPackage("Referenced models: " + SceneMaxLanguageParser.modelsUsed.size());
        logPackage("Referenced effects: " + SceneMaxLanguageParser.effekseerUsed.size());
        logPackage("Referenced sprites: " + SceneMaxLanguageParser.spriteSheetUsed.size());
        logPackage("Referenced audio: " + SceneMaxLanguageParser.audioUsed.size());
        setRunningProgress(12);

        JSONObject resources = new JSONObject("{ skyboxes:[], terrains:[], sprites:[],models:[],sounds:[], fonts:[], shaders:[], environmentShaders:[], materials:[], cinematics:[], animations:[], videos:[] }");

        File deployFolder = new File("deploy");
        logPackage("Resetting deploy folder: " + deployFolder.getAbsolutePath());
        FileUtils.deleteDirectory(deployFolder);

        File texDir = new File(deployFolder, "Textures");
        FileUtils.forceMkdir(texDir);
        File effekseerDir = new File(deployFolder, "resources/effects");
        FileUtils.forceMkdir(effekseerDir);

        try {
//
//            for (File f : texDir.listFiles()) {
//                if(f.isFile()) {
//                    f.delete();
//                } else {
//                    String name=f.getName();
//                    if(!(name.equals("skies") || name.equals("shapes"))) {
//                        FileUtils.deleteDirectory(f);
//                    }
//                }
//            }

            File modelsDir = new File(deployFolder, "Models");
            FileUtils.forceMkdir(modelsDir);

            File audioDir = new File(deployFolder, "audio");
            FileUtils.forceMkdir(audioDir);

            File fontsDir = new File(deployFolder, "fonts");
            FileUtils.forceMkdir(fontsDir);

            File skyboxesDir = new File(deployFolder, "skyboxes");
            FileUtils.forceMkdir(skyboxesDir);

            File matDir = new File(deployFolder, "Materials");
            FileUtils.forceMkdir(matDir);

        } catch (Exception ex) {

        }

        JSONArray models = resources.getJSONArray("models");
        for (String modelName : SceneMaxLanguageParser.modelsUsed) {
            ResourceSetup res = assetsMapping.get3DModelsIndex().get(modelName.toLowerCase());
            if (res != null) {

                if (res.path.startsWith("Models/")) {
                    copyModelResourceToDeploy(new File("./deploy"), res);

                    // for vehicles , we need to copy the wheel model as well
                    if (res.isVehicle) {

                        // add engine audio file
                        List<String> soundFiles = getSoundFiles(Integer.parseInt(res.engine.audio));
                        for (String f : soundFiles) {
                            File src = new File(Util.getResourcePath(f));
                            File dest = new File("./deploy/" + f);
                            Files.copy(src.toPath(), dest.toPath());

                        }

                        // add horn audio file
                        File src = new File(Util.getResourcePath(res.horn));
                        File dest = new File("./deploy/" + res.horn);
                        Files.copy(src.toPath(), dest.toPath());

                    }


                    JSONObject model = res.toJson();
                    models.put(model);
                }
            } else {
                throw failPackaging("Missing 3D model resource '" + modelName + "'. Packaging cannot continue.");
            }
        }

        copyEffekseerResourcesToDeploy(deployFolder);
        copyLightProbeResourcesToDeploy(deployFolder);
        copyAnimationResourcesToDeploy(deployFolder, resources.getJSONArray("animations"));
        copyWeaponResourcesToDeploy(deployFolder);
        copyThrowMotionResourcesToDeploy(deployFolder);
        copyIKResourcesToDeploy(deployFolder);


        collectUiDocumentReferences(reachableUiFiles);

        for (String spriteName : uiReferencedSpriteNames) {
            if (!SceneMaxLanguageParser.spriteSheetUsed.contains(spriteName)) {
                SceneMaxLanguageParser.spriteSheetUsed.add(spriteName);
            }
        }
        for (String fontName : uiReferencedFontNames) {
            if (!SceneMaxLanguageParser.fontsUsed.contains(fontName)) {
                SceneMaxLanguageParser.fontsUsed.add(fontName);
            }
        }

        for (String imagePath : uiReferencedImagePaths) {
            copyUiImageResource(imagePath);
        }

        JSONArray sprites = resources.getJSONArray("sprites");
        for (String spriteSheet : SceneMaxLanguageParser.spriteSheetUsed) {
            ResourceSetup2D res = assetsMapping.getSpriteSheetsIndex().get(spriteSheet.toLowerCase());
            if (res != null) {
                if (res.path.startsWith("sprites/")) {
                    File src = new File(Util.getResourcePath(res.path));
                    File dest = new File("./deploy/Textures/" + src.getName());

                    try {
                        Files.copy(src.toPath(), dest.toPath());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    JSONObject sprite = createSpriteResourceEntity(spriteSheet.toLowerCase(), src.getName(), "Textures", res);
                    sprites.put(sprite);
                }
            }
        }


        JSONArray terrains = resources.getJSONArray("terrains");
        for (String terrain : SceneMaxLanguageParser.terrainsUsed) {
            TerrainResource res = assetsMapping.getTerrainsIndex().get(terrain.toLowerCase());
            if (res != null) {

                copyFile(Util.getResourcePath(res.alphaMap), "./deploy");
                copyFile(Util.getResourcePath(res.redTex), "./deploy");
                copyFile(Util.getResourcePath(res.greenTex), "./deploy");
                copyFile(Util.getResourcePath(res.blueTex), "./deploy");
                copyFile(Util.getResourcePath(res.heightMap), "./deploy");

                JSONObject tr = new JSONObject(res.buff);
                terrains.put(tr);

            }
        }


        JSONArray audioFiles = resources.getJSONArray("sounds");
        for (String audio : SceneMaxLanguageParser.audioUsed) {
            ResourceAudio res = assetsMapping.getAudioIndex().get(audio.toLowerCase());
            if (res != null) {

                File src = new File(Util.getResourcePath(res.path));
                File dest = new File("./deploy/" + res.path);
                Files.copy(src.toPath(), dest.toPath());

                JSONObject audioObj = createAudioResourceEntity(res.name, res.path);
                audioFiles.put(audioObj);

            }
        }

        JSONArray fontsFiles = resources.getJSONArray("fonts");
        for (String font : SceneMaxLanguageParser.fontsUsed) {
            ResourceFont res = assetsMapping.getFontsIndex().get(font.toLowerCase());
            if (res != null) {

                File src = new File(Util.getResourcePath(res.path));
                File dest = new File("./deploy/" + res.path);
                Files.copy(src.toPath(), dest.toPath());

                src = new File(Util.getResourcePath(res.path.replace(".fnt", ".png")));
                dest = new File("./deploy/" + res.path.replace(".fnt", ".png"));
                Files.copy(src.toPath(), dest.toPath());

                JSONObject fontObj = createFontResourceEntity(res.name, res.path);
                fontsFiles.put(fontObj);

            }
        }

        appendUsedShaderResources(assetsMapping, resources.getJSONArray("shaders"), resources.getJSONArray("environmentShaders"));
        appendUsedMaterialResources(assetsMapping, resources.getJSONArray("materials"));
        copyUsedBuiltInMaterialTextures();
        appendUsedVideoResources(deployFolder, resources.getJSONArray("videos"));

        appendCinematicResources(resources.getJSONArray("cinematics"));


        JSONArray skyboxFiles = resources.getJSONArray("skyboxes");
        for (String sb : SceneMaxLanguageParser.skyboxUsed) {
            SkyBoxResource res = assetsMapping.getSkyboxesIndex().get(sb.toLowerCase());
            if (res != null) {

                copyFileFromResourceToDeploy(Util.getResourcePath(res.up));
                copyFileFromResourceToDeploy(Util.getResourcePath(res.down));
                copyFileFromResourceToDeploy(Util.getResourcePath(res.left));
                copyFileFromResourceToDeploy(Util.getResourcePath(res.right));
                copyFileFromResourceToDeploy(Util.getResourcePath(res.back));
                copyFileFromResourceToDeploy(Util.getResourcePath(res.front));

                //JSONObject skyboxObj = createSkyBoxResourceEntity(res);
                skyboxFiles.put(new JSONObject(res.buff));

            }
        }


        packagingInventoryJson = buildPackagingInventory(resources, assetsMapping).toString(2);
        FileUtils.writeStringToFile(new File(deployFolder, "packaging-inventory.json"), packagingInventoryJson, StandardCharsets.UTF_8);
        logPackage("Wrote deploy packaging inventory.");
        setRunningProgress(18);

        File scriptFolderCopy = copyAndApplyMacro(scriptFolder);
        FileUtils.moveDirectory(scriptFolderCopy, new File(deployFolder, "running")); // rename
        injectProjectMetadata(new File(deployFolder, "running"));
        logPackage("Copied packaged scripts into deploy/running.");
        try {
            javaExtensionBuildResult = JavaExtensionBuildTool.buildExtensions(
                    scriptFolder,
                    new File(deployFolder, JavaExtensionBuildTool.EXTENSIONS_FOLDER_NAME),
                    this::logPackage);
        } catch (IOException e) {
            String logText = formatExceptionMessage(e);
            File javaLog = new File(outputFolder == null ? new File("build_games") : outputFolder,
                    JavaExtensionBuildTool.COMPILE_LOG_NAME);
            FileUtils.writeStringToFile(javaLog, logText, StandardCharsets.UTF_8);
            failureMessage = appendPackageLogPath(logText + System.lineSeparator()
                    + System.lineSeparator()
                    + "Java compile log: " + javaLog.getAbsolutePath());
            throw e;
        }
        if (javaExtensionBuildResult.hasExtensions()) {
            logPackage("Built Java extensions: " + javaExtensionBuildResult.extensions.size());
        }
        setRunningProgress(24);
        prepareTargetPackages(resources);
        writeSizeReportAnalysis(resources);
        setRunningProgress(92);
        uploadToItchIfRequested();
        logPackage("Packaging finished successfully.");
        setPackageProgress(100);

        return globalCounter;
    }

    private static String formatExceptionMessage(Throwable e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.toString() : message;
    }

    private void initializePackageOutput(String gameName) throws IOException {
        outputFolder = new File("build_games", gameName);
        if (outputFolder.exists()) {
            FileUtils.deleteDirectory(outputFolder);
        }
        FileUtils.forceMkdir(outputFolder);
        packageLogFile = new File(outputFolder, "package.log");
    }

    private void normalizePackageRootAndProgram() throws IOException {
        if (scriptFolder == null) {
            return;
        }

        scriptFolder = resolvePackageScriptRoot(scriptFolder);
        File mainFile = new File(scriptFolder, "main");
        if (mainFile.isFile()) {
            prg = FileUtils.readFileToString(mainFile, StandardCharsets.UTF_8);
        } else if (prg == null) {
            prg = "";
        }
    }

    private File resolvePackageScriptRoot(File selectedScriptFolder) {
        if (selectedScriptFolder == null) {
            return null;
        }

        File current = selectedScriptFolder;
        if (current.isFile()) {
            current = current.getParentFile();
        }

        File nearestGameRoot = null;
        File walker = current;
        while (walker != null) {
            File parent = walker.getParentFile();
            if (parent != null && "scripts".equalsIgnoreCase(parent.getName())) {
                nearestGameRoot = walker;
                break;
            }
            walker = parent;
        }

        return nearestGameRoot == null ? current : nearestGameRoot;
    }

    private File copyAndApplyMacro(File folder) {
        try {
            File deployFolder = new File("deploy");
            File createdFolder = new File(deployFolder, folder.getName());
            if (createdFolder.exists()) {
                FileUtils.deleteDirectory(createdFolder);
            }
            FileUtils.forceMkdir(createdFolder);

            LinkedHashSet<String> filesToCopy = new LinkedHashSet<>();
            filesToCopy.addAll(scannedScriptFiles);
            filesToCopy.addAll(scannedDesignerFiles);
            filesToCopy.addAll(reachableUiFiles);

            if (filesToCopy.isEmpty()) {
                FileUtils.copyDirectoryToDirectory(folder, deployFolder);
                Iterator<File> files = FileUtils.iterateFiles(createdFolder,null, true);
                while (files.hasNext()) {
                    applyMacroIfScript(files.next());
                }
            } else {
                for (String path : filesToCopy) {
                    copyReachableSourceFile(folder, createdFolder, path);
                }
            }

            return createdFolder;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void copyReachableSourceFile(File sourceRoot, File targetRoot, String sourcePath) throws IOException {
        if (sourcePath == null || sourcePath.isBlank()) {
            return;
        }
        File sourceFile = new File(sourcePath);
        if (!sourceFile.isFile()) {
            return;
        }

        File targetFile = new File(targetRoot, toRelativePath(sourceFile.getAbsolutePath(), sourceRoot));
        File parent = targetFile.getParentFile();
        if (parent != null) {
            FileUtils.forceMkdir(parent);
        }
        FileUtils.copyFile(sourceFile, targetFile);
        applyMacroIfScript(targetFile);
    }

    private void applyMacroIfScript(File file) throws IOException {
        if (!ScriptTreeResourceCollector.isSceneMaxScriptFile(file)) {
            return;
        }
        String code = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        ApplyMacroResults mr = macroFilter.apply(code);
        FileUtils.write(file, mr.finalPrg, StandardCharsets.UTF_8);
    }

    private void injectProjectMetadata(File runningFolder) throws IOException {
        if (runningFolder == null) {
            return;
        }
        File mainFile = new File(runningFolder, "main");
        if (!mainFile.isFile()) {
            return;
        }
        String code = FileUtils.readFileToString(mainFile, StandardCharsets.UTF_8);
        StringBuilder metadata = new StringBuilder();
        SceneMaxProject project = Util.getActiveProject();

        if (project != null && project.name != null && !project.name.isBlank() && !code.contains("//$[project]=")) {
            metadata.append("//$[project]=").append(sanitizeMetadataValue(project.name)).append(";");
        }

        String guid = resolveActiveProjectGuid();
        if (!guid.isBlank() && !code.contains("//$[project_guid]=")) {
            metadata.append("//$[project_guid]=").append(sanitizeMetadataValue(guid)).append(";");
        }

        String packagedSourceText = readScannedSourceText();
        if (programUsesMultiplayer(packagedSourceText)) {
            appendMultiplayerMetadata(metadata, code, project);
        }

        if (metadata.length() == 0) {
            return;
        }
        FileUtils.write(mainFile, metadata + code, StandardCharsets.UTF_8);
    }

    private void appendMultiplayerMetadata(StringBuilder metadata, String code, SceneMaxProject project) {
        String serverIp = project != null && project.multiplayerServerIp != null && !project.multiplayerServerIp.isBlank()
                ? project.multiplayerServerIp.trim()
                : "127.0.0.1";
        int serverPort = project != null && project.multiplayerServerPort > 0
                ? project.multiplayerServerPort
                : SceneMaxProject.DEFAULT_MULTIPLAYER_PORT;
        String sessionName = project != null && project.name != null && !project.name.isBlank()
                ? project.name.trim()
                : "local";

        appendMetadataIfMissing(metadata, code, "multiplayer_server", serverIp);
        appendMetadataIfMissing(metadata, code, "multiplayer_port", Integer.toString(serverPort));
        if (project != null && project.multiplayerPassword != null && !project.multiplayerPassword.isBlank()) {
            appendMetadataIfMissing(metadata, code, "multiplayer_password", project.multiplayerPassword);
        }
        appendMetadataIfMissing(metadata, code, "multiplayer_session_id", "1000");
        appendMetadataIfMissing(metadata, code, "multiplayer_create_session", "false");
        appendMetadataIfMissing(metadata, code, "multiplayer_session_name", sessionName);
        appendMetadataIfMissing(metadata, code, "multiplayer_scene", "main");
    }

    private void appendMetadataIfMissing(StringBuilder metadata, String code, String key, String value) {
        if (value == null || value.isBlank() || code.contains("//$[" + key + "]=")) {
            return;
        }
        metadata.append("//$[").append(key).append("]=").append(sanitizeMetadataValue(value)).append(";");
    }

    private boolean programUsesMultiplayer(String code) {
        return code != null && Pattern.compile("\\bmultiplayer\\b", Pattern.CASE_INSENSITIVE).matcher(code).find();
    }

    private String sanitizeMetadataValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\r', ' ')
                .replace('\n', ' ')
                .replace(';', ' ')
                .trim();
    }

    private String resolveActiveProjectGuid() {
        SceneMaxProject project = Util.getActiveProject();
        if (project == null) {
            return "";
        }
        if (project.projectGuid == null || project.projectGuid.trim().isEmpty()) {
            project.projectGuid = java.util.UUID.randomUUID().toString();
            Util.saveProjectSettings(project);
        }
        return project.projectGuid == null ? "" : project.projectGuid.trim();
    }

    private void prepareTargetPackages(JSONObject resources) throws IOException {
        verifyEffekseerNativeResourcesForSelectedTargets();
        String gameName = getGameName();
        if (outputFolder == null) {
            outputFolder = new File("build_games", gameName);
            FileUtils.forceMkdir(outputFolder);
        }
        producedArtifacts.clear();
        logPackage("Preparing target packages in: " + outputFolder.getAbsolutePath());
        int targetCount = Math.max(1, targets.size());
        int targetIndex = 0;

        if (targets.contains(PackageTarget.WINDOWS)) {
            int phaseBase = targetProgressBase(targetIndex, targetCount);
            int phaseEnd = targetProgressBase(targetIndex + 1, targetCount);
            int archiveSpan = targetArchiveSpan(phaseEnd - phaseBase);
            logPackage("Creating Windows scene JAR.");
            updateStatus("Creating Windows game archive...");
            beginProgressPhase(phaseBase, archiveSpan, estimateSceneJarWorkUnits(PackageTarget.WINDOWS));
            writeSceneJarForTarget(PackageTarget.WINDOWS, resources);
            finishProgressPhase();
            updateStatus("Creating Windows package...");
            File windowsArtifact = prepareWindowsExecutable(gameName);
            if (windowsArtifact != null && windowsArtifact.exists()) {
                producedArtifacts.add(windowsArtifact);
                logPackage("Windows artifact: " + windowsArtifact.getAbsolutePath() + " (" + windowsArtifact.length() + " bytes)");
            }
            setRunningProgress(phaseEnd);
            targetIndex += 1;
        }

        if (targets.contains(PackageTarget.LINUX)) {
            int phaseBase = targetProgressBase(targetIndex, targetCount);
            int phaseEnd = targetProgressBase(targetIndex + 1, targetCount);
            int archiveSpan = targetArchiveSpan(phaseEnd - phaseBase);
            logPackage("Creating Linux scene JAR.");
            updateStatus("Creating Linux game archive...");
            beginProgressPhase(phaseBase, archiveSpan, estimateSceneJarWorkUnits(PackageTarget.LINUX));
            writeSceneJarForTarget(PackageTarget.LINUX, resources);
            finishProgressPhase();
            updateStatus("Creating Linux package...");
            if (options.embedMinimalJavaRuntime) {
                File linuxArtifact = prepareSelfExtractingPackage(PackageTarget.LINUX, gameName, "linux", gameName, true);
                if (linuxArtifact != null && linuxArtifact.exists()) {
                    producedArtifacts.add(linuxArtifact);
                    logPackage("Linux artifact: " + linuxArtifact.getAbsolutePath() + " (" + linuxArtifact.length() + " bytes)");
                }
            } else {
                File linuxFolder = prepareScriptPackage(PackageTarget.LINUX, gameName, "linux", gameName + ".sh", false);
                File linuxZip = createPlatformZip(linuxFolder, gameName + "_linux.zip");
                if (linuxZip != null && linuxZip.exists()) {
                    producedArtifacts.add(linuxZip);
                    logPackage("Linux artifact: " + linuxZip.getAbsolutePath() + " (" + linuxZip.length() + " bytes)");
                }
                deletePlatformArtifactsExceptZip(linuxFolder, linuxZip);
            }
            setRunningProgress(phaseEnd);
            targetIndex += 1;
        }

        if (targets.contains(PackageTarget.MAC_OSX)) {
            int phaseBase = targetProgressBase(targetIndex, targetCount);
            int phaseEnd = targetProgressBase(targetIndex + 1, targetCount);
            int archiveSpan = targetArchiveSpan(phaseEnd - phaseBase);
            logPackage("Creating macOS scene JAR.");
            updateStatus("Creating macOS game archive...");
            beginProgressPhase(phaseBase, archiveSpan, estimateSceneJarWorkUnits(PackageTarget.MAC_OSX));
            writeSceneJarForTarget(PackageTarget.MAC_OSX, resources);
            finishProgressPhase();
            updateStatus("Creating macOS package...");
            if (options.embedMinimalJavaRuntime) {
                File macArtifact = prepareSelfExtractingPackage(PackageTarget.MAC_OSX, gameName, "macos", gameName, true);
                if (macArtifact != null && macArtifact.exists()) {
                    producedArtifacts.add(macArtifact);
                    logPackage("macOS artifact: " + macArtifact.getAbsolutePath() + " (" + macArtifact.length() + " bytes)");
                }
            } else {
                File macFolder = prepareScriptPackage(PackageTarget.MAC_OSX, gameName, "macos", gameName + ".command", true);
                File macZip = createPlatformZip(macFolder, gameName + "_macos.zip");
                if (macZip != null && macZip.exists()) {
                    producedArtifacts.add(macZip);
                    logPackage("macOS artifact: " + macZip.getAbsolutePath() + " (" + macZip.length() + " bytes)");
                }
                deletePlatformArtifactsExceptZip(macFolder, macZip);
            }
            setRunningProgress(phaseEnd);
            targetIndex += 1;
        }

        if (targets.contains(PackageTarget.WEB_START)) {
            int phaseBase = targetProgressBase(targetIndex, targetCount);
            int phaseEnd = targetProgressBase(targetIndex + 1, targetCount);
            int archiveSpan = targetArchiveSpan(phaseEnd - phaseBase);
            logPackage("Creating Web Start package.");
            updateStatus("Creating Web Start game archive...");
            beginProgressPhase(phaseBase, archiveSpan, estimateSceneJarWorkUnits(PackageTarget.WEB_START));
            writeSceneJarForTarget(PackageTarget.WEB_START, resources);
            finishProgressPhase();
            updateStatus("Creating Web Start package...");
            File webFolder = prepareWebStartPackage(gameName);
            if (webFolder != null && webFolder.exists()) {
                producedArtifacts.add(webFolder);
                logPackage("Web Start artifact folder: " + webFolder.getAbsolutePath());
            }
            setRunningProgress(phaseEnd);
        }

        writePackagingInventoryArtifact();
    }

    private int targetProgressBase(int targetIndex, int targetCount) {
        return 25 + Math.round(targetIndex * 65f / Math.max(1, targetCount));
    }

    private int targetArchiveSpan(int targetSpan) {
        return Math.max(1, Math.round(Math.max(1, targetSpan) * 0.70f));
    }

    private void writeSceneJarForTarget(PackageTarget target, JSONObject resources) throws IOException {
        addedJarEntries.clear();
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "com.scenemaxeng.projector.SceneMaxLauncher");//SceneMaxLauncher.class.getName()
        manifest.getMainAttributes().put(new Attributes.Name("SceneMax-Package-Target"), target.name());

        try (JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(SCENE_JAR_NAME), manifest)) {
            File projectorFile = resolveProjectorJar(target);
            logPackage("Using projector for " + target + ": " + projectorFile.getAbsolutePath() + " (" + projectorFile.length() + " bytes)");
            JarUtils.addJar(jarOutputStream, "", projectorFile, new Runnable() {
                @Override
                public void run() {
                    advanceProgressUnit();
                }
            }, addedJarEntries);

            File deployRoot = new File("deploy");
            File[] deployFiles = deployRoot.listFiles();
            if (deployFiles != null) {
                for (File nestedFile : deployFiles) {
                    add(nestedFile, jarOutputStream);
                }
            }

            addJavaExtensionClasses(jarOutputStream);

            if (addedJarEntries.add("resources.json")) {
                jarOutputStream.putNextEntry(new JarEntry("resources.json"));
                jarOutputStream.write(resources.toString().getBytes(StandardCharsets.UTF_8));
                jarOutputStream.closeEntry();
                advanceProgressUnit();
            }
        }
        File sceneJar = new File(SCENE_JAR_NAME);
        logPackage("Created scene JAR for " + target + ": " + sceneJar.getAbsolutePath() + " (" + sceneJar.length() + " bytes)");
    }

    private int estimateSceneJarWorkUnits(PackageTarget target) throws IOException {
        int total = 1; // resources.json
        total += countJarFileEntries(resolveProjectorJar(target));
        total += countRegularFiles(new File("deploy"));
        if (javaExtensionBuildResult != null && javaExtensionBuildResult.hasExtensions()) {
            for (JavaExtensionBuildTool.ExtensionBuild extension : javaExtensionBuildResult.extensions) {
                if (extension.jarFile != null && extension.jarFile.isFile()) {
                    total += countJarFileEntries(extension.jarFile);
                }
            }
        }
        return Math.max(1, total);
    }

    private int countJarFileEntries(File jarFile) throws IOException {
        if (jarFile == null || !jarFile.isFile()) {
            return 0;
        }
        int count = 0;
        try (JarFile jar = new JarFile(jarFile)) {
            java.util.Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    count += 1;
                }
            }
        }
        return count;
    }

    private int countRegularFiles(File folder) {
        if (folder == null || !folder.exists()) {
            return 0;
        }
        if (folder.isFile()) {
            return 1;
        }
        File[] children = folder.listFiles();
        if (children == null) {
            return 0;
        }
        int count = 0;
        for (File child : children) {
            count += countRegularFiles(child);
        }
        return count;
    }

    private void addJavaExtensionClasses(JarOutputStream jarOutputStream) {
        if (javaExtensionBuildResult == null || !javaExtensionBuildResult.hasExtensions()) {
            return;
        }
        for (JavaExtensionBuildTool.ExtensionBuild extension : javaExtensionBuildResult.extensions) {
            if (extension.jarFile == null || !extension.jarFile.isFile()) {
                continue;
            }
            logPackage("Merging Java extension classes: " + extension.name);
            JarUtils.addJar(jarOutputStream, "", extension.jarFile, new Runnable() {
                @Override
                public void run() {
                    advanceProgressUnit();
                }
            }, addedJarEntries);
        }
    }

    private File resolveProjectorJar(PackageTarget target) throws IOException {
        String artifactName;
        switch (target) {
            case LINUX:
                artifactName = "scenemax_projector-linux.jar";
                break;
            case MAC_OSX:
                artifactName = "scenemax_projector-macos.jar";
                break;
            case WINDOWS:
            case WEB_START:
            default:
                artifactName = "scenemax_projector-windows.jar";
                break;
        }

        File projectorFile = new File(Util.getWorkingDir() + "/out/artifacts/" + artifactName);
        if (!projectorFile.isFile() && target == PackageTarget.WINDOWS) {
            projectorFile = new File(Util.getWorkingDir() + "/out/artifacts/scenemax_win_projector.jar");
        }
        if (!projectorFile.isFile()) {
            throw new IOException("Missing projector artifact for " + target + ": " + projectorFile.getAbsolutePath());
        }
        return projectorFile;
    }

    private void uploadToItchIfRequested() throws IOException {
        if (!options.uploadToItch) {
            return;
        }

        setRunningProgress(93);
        updateStatus("Preparing itch.io upload...");
        List<String> butlerCommand = resolveButlerCommand();
        ensureButlerAvailable(butlerCommand);

        boolean uploadedAny = false;
        String gameName = getGameName();
        int uploadableTargets = 0;
        if (targets.contains(PackageTarget.WINDOWS)) {
            uploadableTargets += 1;
        }
        if (targets.contains(PackageTarget.LINUX)) {
            uploadableTargets += 1;
        }
        if (targets.contains(PackageTarget.MAC_OSX)) {
            uploadableTargets += 1;
        }
        int uploadedTargets = 0;

        if (targets.contains(PackageTarget.WINDOWS)) {
            File windowsFolder = new File(outputFolder, "windows");
            File windowsArtifact = new File(windowsFolder, gameName + ".exe");
            uploadArtifactToItch(butlerCommand, windowsArtifact, ItchIoHelper.defaultChannel("windows", options.itchWindowsChannel), "Windows");
            uploadedAny = true;
            uploadedTargets += 1;
            setRunningProgress(scaledProgress(93, 6, uploadableTargets == 0 ? 100 : uploadedTargets * 100 / uploadableTargets));
        }

        if (targets.contains(PackageTarget.LINUX)) {
            File linuxArtifact = options.embedMinimalJavaRuntime
                    ? new File(new File(outputFolder, "linux"), gameName)
                    : new File(new File(outputFolder, "linux"), gameName + "_linux.zip");
            uploadArtifactToItch(butlerCommand, linuxArtifact, ItchIoHelper.defaultChannel("linux", options.itchLinuxChannel), "Linux");
            uploadedAny = true;
            uploadedTargets += 1;
            setRunningProgress(scaledProgress(93, 6, uploadableTargets == 0 ? 100 : uploadedTargets * 100 / uploadableTargets));
        }

        if (targets.contains(PackageTarget.MAC_OSX)) {
            File macArtifact = options.embedMinimalJavaRuntime
                    ? new File(new File(outputFolder, "macos"), gameName)
                    : new File(new File(outputFolder, "macos"), gameName + "_macos.zip");
            uploadArtifactToItch(butlerCommand, macArtifact, ItchIoHelper.defaultChannel("macos", options.itchMacChannel), "macOS");
            uploadedAny = true;
            uploadedTargets += 1;
            setRunningProgress(scaledProgress(93, 6, uploadableTargets == 0 ? 100 : uploadedTargets * 100 / uploadableTargets));
        }

        if (targets.contains(PackageTarget.WEB_START)) {
            appendCompletionNote("Web Start packaging completed locally. Automatic itch.io upload currently skips Web Start artifacts.");
        }

        if (uploadedAny) {
            appendCompletionNote("Updated itch.io page " + options.itchGameTarget + " using butler.");
        }
    }

    private List<String> resolveButlerCommand() throws IOException {
        if (options.itchButlerPath.length() > 0) {
            File configured = new File(options.itchButlerPath);
            if (!configured.isFile()) {
                throw new IOException("Configured butler executable was not found: " + configured.getAbsolutePath());
            }
            return Collections.singletonList(configured.getAbsolutePath());
        }

        String bundledButler = ItchIoHelper.findBundledButlerExecutable();
        if (bundledButler != null && bundledButler.trim().length() > 0) {
            return Collections.singletonList(bundledButler);
        }

        return Collections.singletonList("butler");
    }

    private void ensureButlerAvailable(List<String> butlerCommand) throws IOException {
        List<String> versionCommand = new ArrayList<>(butlerCommand);
        versionCommand.add("version");
        try {
            runCommand(versionCommand, null, "butler");
        } catch (IOException e) {
            if (butlerCommand.size() == 1 && "butler".equalsIgnoreCase(butlerCommand.get(0))) {
                throw new IOException(ItchIoHelper.buildButlerInstallInstructions(), e);
            }
            throw new IOException("Unable to run butler from " + butlerCommand.get(0) + ". " + e.getMessage(), e);
        }
    }

    private void uploadArtifactToItch(List<String> butlerCommand, File artifact, String channel, String platformLabel) throws IOException {
        if (artifact == null || !artifact.exists()) {
            throw new IOException(platformLabel + " artifact was not found for itch.io upload: " + (artifact == null ? "(missing)" : artifact.getAbsolutePath()));
        }

        updateStatus("Uploading " + platformLabel + " build to itch.io...");
        List<String> command = new ArrayList<>(butlerCommand);
        command.add("push");
        command.add(artifact.getAbsolutePath());
        command.add(options.itchGameTarget + ":" + channel);

        Map<String, String> env = Collections.emptyMap();
        if (options.itchApiKey.length() > 0) {
            env = new LinkedHashMap<>();
            env.put("BUTLER_API_KEY", options.itchApiKey);
        }

        runCommand(command, artifact.getParentFile(), "butler", env, "butler:");
        appendCompletionNote("Uploaded " + platformLabel + " build to " + options.itchGameTarget + ":" + channel + ".");
    }

    private File prepareWindowsExecutable(String gameName) throws IOException {
        File exePath = prepareSelfExtractingPackage(PackageTarget.WINDOWS, gameName, "windows", gameName + ".exe", options.embedMinimalJavaRuntime);
        File windowsFolder = exePath.getParentFile();
        copyPlatformIcon(options.windowsIcon, windowsFolder, "icon");
        return exePath;
    }

    private void writePackagingInventoryArtifact() {
        if (outputFolder == null || packagingInventoryJson == null || packagingInventoryJson.isBlank()) {
            return;
        }

        try {
            FileUtils.writeStringToFile(new File(outputFolder, "packaging-inventory.json"), packagingInventoryJson, StandardCharsets.UTF_8);
            logPackage("Wrote packaging inventory: " + new File(outputFolder, "packaging-inventory.json").getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeSizeReportAnalysis(JSONObject packagedResources) {
        if (outputFolder == null) {
            return;
        }

        File deployRoot = new File("deploy");
        if (!deployRoot.isDirectory()) {
            return;
        }

        try {
            List<SizeReportRow> categories = buildSizeReportCategories(deployRoot, packagedResources);
            List<SizeReportRow> topFiles = collectTopContributors(deployRoot, 40);
            long deployTotal = sizeOf(deployRoot);

            JSONObject json = new JSONObject();
            json.put("deployTotalBytes", deployTotal);
            json.put("deployTotalMiB", roundMiB(deployTotal));
            JSONArray categoryArray = new JSONArray();
            for (SizeReportRow row : categories) {
                categoryArray.put(row.toJson());
            }
            json.put("categories", categoryArray);
            JSONArray topArray = new JSONArray();
            for (SizeReportRow row : topFiles) {
                topArray.put(row.toJson());
            }
            json.put("topContributors", topArray);

            String text = buildSizeReportText(deployTotal, categories, topFiles);
            File textFile = new File(outputFolder, "size-report-analysis.txt");
            File jsonFile = new File(outputFolder, "size-report-analysis.json");
            FileUtils.writeStringToFile(textFile, text, StandardCharsets.UTF_8);
            FileUtils.writeStringToFile(jsonFile, json.toString(2), StandardCharsets.UTF_8);
            logPackage("Wrote size report analysis: " + textFile.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<SizeReportRow> buildSizeReportCategories(File deployRoot, JSONObject packagedResources) {
        List<SizeReportRow> rows = new ArrayList<>();
        addCategory(rows, deployRoot, "Models", "Models", resourceCount(packagedResources, "models"));
        addCategory(rows, deployRoot, "audio", "audio", resourceCount(packagedResources, "sounds"));
        addCategory(rows, deployRoot, "videos", "videos", resourceCount(packagedResources, "videos"));
        addCategory(rows, deployRoot, "Textures / sprites", "Textures", resourceCount(packagedResources, "sprites"));
        addCategory(rows, deployRoot, "Effekseer effects", "resources/effects", SceneMaxLanguageParser.effekseerUsed.size());
        addCategory(rows, deployRoot, "materials", "material", resourceCount(packagedResources, "materials"));
        addCategory(rows, deployRoot, "Materials", "Materials", 0);
        addCategory(rows, deployRoot, "fonts", "fonts", resourceCount(packagedResources, "fonts"));
        addCategory(rows, deployRoot, "animations", "animations", resourceCount(packagedResources, "animations"));
        addCategory(rows, deployRoot, "light probes", "probes", SceneMaxLanguageParser.lightProbesUsed.size());
        addCategory(rows, deployRoot, "runtime scripts", "running", scannedScriptFiles.size());
        addCategory(rows, deployRoot, "shaders", "shaders", resourceCount(packagedResources, "shaders"));
        addCategory(rows, deployRoot, "environment shaders", "environment_shaders", resourceCount(packagedResources, "environmentShaders"));
        addCategory(rows, deployRoot, "skyboxes", "skyboxes", resourceCount(packagedResources, "skyboxes"));
        addCategory(rows, deployRoot, "IK", "resources/IK", ikAssetNamesUsed.size());
        addCategory(rows, deployRoot, "throw motions", "resources/throw_motions", throwMotionAssetNamesUsed.size());
        addCategory(rows, deployRoot, "weapons", "resources/weapons", weaponAssetNamesUsed.size());

        long resourceTotal = sizeOf(new File(deployRoot, "resources"));
        long knownResourceTotal = sizeOf(new File(deployRoot, "resources/effects"))
                + sizeOf(new File(deployRoot, "resources/IK"))
                + sizeOf(new File(deployRoot, "resources/throw_motions"))
                + sizeOf(new File(deployRoot, "resources/weapons"));
        long resourceOther = Math.max(0, resourceTotal - knownResourceTotal);
        if (resourceOther > 0) {
            rows.add(new SizeReportRow("resources / other", "resources", resourceOther, 0));
        }

        rows.sort(Comparator.comparingLong((SizeReportRow row) -> row.bytes).reversed());
        return rows;
    }

    private void addCategory(List<SizeReportRow> rows, File deployRoot, String label, String relativePath, int count) {
        long bytes = sizeOf(new File(deployRoot, relativePath));
        if (bytes > 0 || count > 0) {
            rows.add(new SizeReportRow(label, relativePath, bytes, count));
        }
    }

    private int resourceCount(JSONObject packagedResources, String key) {
        if (packagedResources == null) {
            return 0;
        }
        JSONArray array = packagedResources.optJSONArray(key);
        return array == null ? 0 : array.length();
    }

    private List<SizeReportRow> collectTopContributors(File root, int limit) throws IOException {
        List<SizeReportRow> files = new ArrayList<>();
        collectFileRows(root, root, files);
        files.sort(Comparator.comparingLong((SizeReportRow row) -> row.bytes).reversed());
        if (files.size() <= limit) {
            return files;
        }
        return new ArrayList<>(files.subList(0, limit));
    }

    private void collectFileRows(File root, File current, List<SizeReportRow> rows) throws IOException {
        File[] children = current.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectFileRows(root, child, rows);
            } else if (child.isFile()) {
                rows.add(new SizeReportRow(
                        child.getName(),
                        toRelativePath(child.getAbsolutePath(), root),
                        child.length(),
                        0
                ));
            }
        }
    }

    private long sizeOf(File file) {
        if (file == null || !file.exists()) {
            return 0L;
        }
        if (file.isFile()) {
            return file.length();
        }
        long total = 0L;
        File[] children = file.listFiles();
        if (children == null) {
            return 0L;
        }
        for (File child : children) {
            total += sizeOf(child);
        }
        return total;
    }

    private String buildSizeReportText(long deployTotal, List<SizeReportRow> categories, List<SizeReportRow> topFiles) {
        String lineBreak = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("SceneMax Packaging Size Report").append(lineBreak);
        sb.append("Generated: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append(lineBreak);
        sb.append("Deploy content total: ").append(formatSize(deployTotal)).append(lineBreak).append(lineBreak);

        sb.append("Asset Categories").append(lineBreak);
        for (SizeReportRow row : categories) {
            sb.append(String.format(Locale.ROOT, "%10s  %-24s  count=%-4d  %s",
                    formatMiB(row.bytes), row.label, row.count, row.path)).append(lineBreak);
        }

        sb.append(lineBreak).append("Top Contributors").append(lineBreak);
        for (SizeReportRow row : topFiles) {
            sb.append(String.format(Locale.ROOT, "%10s  %s",
                    formatMiB(row.bytes), row.path)).append(lineBreak);
        }

        sb.append(lineBreak)
                .append("Notes").append(lineBreak)
                .append("- This report measures the staged deploy folder before it is embedded into target packages.").append(lineBreak)
                .append("- Large files here are the best candidates for asset compression, LOD reduction, or removal.").append(lineBreak)
                .append("- The final executable or JAR also includes the SceneMax projector/runtime.").append(lineBreak);
        return sb.toString();
    }

    private String formatSize(long bytes) {
        return bytes + " bytes (" + formatMiB(bytes) + ")";
    }

    private String formatMiB(long bytes) {
        return String.format(Locale.ROOT, "%.2f MiB", bytes / 1024.0 / 1024.0);
    }

    private double roundMiB(long bytes) {
        return Math.round((bytes / 1024.0 / 1024.0) * 100.0) / 100.0;
    }

    private File prepareScriptPackage(PackageTarget target, String gameName, String platformFolderName, String launcherFileName, boolean macLauncher) throws IOException {
        File platformFolder = new File(outputFolder, platformFolderName);
        FileUtils.forceMkdir(platformFolder);

        File targetJar = new File(platformFolder, gameName + ".jar");
        FileUtils.copyFile(new File(SCENE_JAR_NAME), targetJar);

        if (options.embedMinimalJavaRuntime) {
            createMinimalJavaRuntime(target, targetJar, new File(platformFolder, EMBEDDED_RUNTIME_DIR_NAME));
        }

        File launcherFile = new File(platformFolder, launcherFileName);
        String launcherText = createLauncherScript(gameName, macLauncher);
        FileUtils.writeStringToFile(launcherFile, launcherText, StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(
                new File(platformFolder, "README.txt"),
                createLauncherReadme(launcherFileName, macLauncher),
                StandardCharsets.UTF_8
        );
        if ("linux".equals(platformFolderName)) {
            copyPlatformIcon(options.linuxIcon, platformFolder, "icon");
        } else if ("macos".equals(platformFolderName)) {
            copyPlatformIcon(options.macIcon, platformFolder, "icon");
        }

        return platformFolder;
    }

    private String createLauncherScript(String gameName, boolean macLauncher) {
        String lineBreak = "\n";
        StringBuilder sb = new StringBuilder();
        sb.append("#!/bin/sh").append(lineBreak);
        sb.append("SCRIPT_DIR=\"$(cd \"$(dirname \"$0\")\" && pwd)\"").append(lineBreak);
        sb.append("cd \"$SCRIPT_DIR\"").append(lineBreak);
        if (options.embedMinimalJavaRuntime) {
            sb.append("JAVA_CMD=\"$SCRIPT_DIR/").append(EMBEDDED_RUNTIME_DIR_NAME).append("/bin/java\"").append(lineBreak);
            sb.append("\"$JAVA_CMD\" -XX:MaxDirectMemorySize=1024m -jar \"").append(gameName).append(".jar\"").append(lineBreak);
        } else {
            sb.append("java -XX:MaxDirectMemorySize=1024m -jar \"").append(gameName).append(".jar\"").append(lineBreak);
        }
        if (macLauncher) {
            sb.append("exit $?").append(lineBreak);
        }
        return sb.toString();
    }

    private String createLauncherReadme(String launcherFileName, boolean macLauncher) {
        StringBuilder sb = new StringBuilder();
        sb.append("SceneMax packaged game").append("\n\n");
        if (options.embedMinimalJavaRuntime) {
            sb.append("Requirements: none. This package includes a minimal Java runtime generated with jlink.").append("\n\n");
        } else {
            sb.append("Requirements: Java 11 or newer installed on the target machine.").append("\n\n");
        }
        if (launcherFileName.toLowerCase(Locale.ROOT).endsWith(".exe")) {
            sb.append("Run: ").append(launcherFileName).append("\n");
            sb.append("Keep the runtime folder beside the executable when sharing this package.\n");
            return sb.toString();
        }
        if (macLauncher) {
            sb.append("Run: ./").append(launcherFileName).append("\n");
            sb.append("If needed, make it executable first with: chmod +x ").append(launcherFileName).append("\n");
        } else {
            sb.append("Run: ./").append(launcherFileName).append("\n");
            sb.append("If needed, make it executable first with: chmod +x ").append(launcherFileName).append("\n");
        }
        return sb.toString();
    }

    private File prepareSelfExtractingPackage(PackageTarget target, String gameName, String platformFolderName, String outputFileName, boolean includeRuntime) throws IOException {
        File platformFolder = new File(outputFolder, platformFolderName);
        FileUtils.forceMkdir(platformFolder);

        File payloadRoot = new File(platformFolder, "selfextract-payload");
        if (payloadRoot.exists()) {
            FileUtils.deleteDirectory(payloadRoot);
        }
        FileUtils.forceMkdir(payloadRoot);

        File payloadSceneJar = new File(payloadRoot, SELF_EXTRACT_PAYLOAD_JAR_NAME);
        FileUtils.copyFile(new File(SCENE_JAR_NAME), payloadSceneJar);
        if (includeRuntime) {
            createMinimalJavaRuntime(target, payloadSceneJar, new File(payloadRoot, EMBEDDED_RUNTIME_DIR_NAME));
        }

        File outputFile = new File(platformFolder, outputFileName);
        createSelfExtractingExecutable(target, payloadRoot, outputFile);
        if (target != PackageTarget.WINDOWS) {
            outputFile.setExecutable(true, false);
        }

        FileUtils.deleteDirectory(payloadRoot);
        appendCompletionNote("Created single-file " + target + " executable: " + outputFile.getAbsolutePath()
                + (includeRuntime ? "." : " (requires Java 11 or newer on the target machine)."));
        return outputFile;
    }

    private void createSelfExtractingExecutable(PackageTarget target, File payloadRoot, File outputFile) throws IOException {
        File stub = resolveSelfExtractingLauncherStub(target);
        File payloadFile = File.createTempFile("scenemax-selfextract-", ".smxp", outputFolder == null ? new File("build_games") : outputFolder);
        try {
            writeSelfExtractPayload(payloadRoot, payloadFile);
            byte[] payloadHash = sha256(payloadFile);
            File parent = outputFile.getParentFile();
            if (parent != null) {
                FileUtils.forceMkdir(parent);
            }
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(outputFile))) {
                copyFileToStream(stub, out);
                copyFileToStream(payloadFile, out);
                writeLongLE(out, payloadFile.length());
                out.write(payloadHash);
                out.write(SELF_EXTRACT_FOOTER_MAGIC);
            }
            logPackage("Self-extracting launcher: " + outputFile.getAbsolutePath()
                    + " (stub=" + stub.length() + " bytes, payload=" + payloadFile.length() + " bytes)");
        } finally {
            if (payloadFile.exists()) {
                payloadFile.delete();
            }
        }
    }

    private File resolveSelfExtractingLauncherStub(PackageTarget target) throws IOException {
        File stub = configuredSelfExtractingStub(target);
        if (stub.isFile()) {
            return stub;
        }

        File generated = new File("build/native-launcher/" + nativeLauncherStubName(target));
        if (generated.isFile()) {
            return generated;
        }

        File zigSource = new File("tools/native-launcher/zig/scenemax_launcher.zig");
        if (!zigSource.isFile()) {
            throw failPackaging("Missing SceneMax native launcher source: " + zigSource.getAbsolutePath());
        }

        File zig = findToolOnPath("zig" + (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? ".exe" : ""));
        if (zig == null) {
            throw failPackaging("Missing native launcher stub for " + target + ": " + stub.getAbsolutePath()
                    + ". Install Zig or bundle a prebuilt stub in tools/native-launcher/bin.");
        }

        File parent = generated.getParentFile();
        if (parent != null) {
            FileUtils.forceMkdir(parent);
        }
        List<String> command = new ArrayList<>();
        command.add(zig.getAbsolutePath());
        command.add("build-exe");
        command.add("-O");
        command.add("ReleaseSmall");
        command.add("-target");
        command.add(zigTarget(target));
        if (target == PackageTarget.WINDOWS) {
            command.add("--subsystem");
            command.add("windows");
        }
        command.add("-femit-bin=" + generated.getAbsolutePath());
        command.add(zigSource.getAbsolutePath());
        runCommand(command, null, "zig", Collections.emptyMap(), "zig:");
        if (!generated.isFile()) {
            throw failPackaging("Zig did not create native launcher stub: " + generated.getAbsolutePath());
        }
        return generated;
    }

    private File configuredSelfExtractingStub(PackageTarget target) {
        String configured = AppConfig.get(nativeLauncherConfigKey(target), "").trim();
        if (configured.length() > 0) {
            return new File(configured);
        }
        return new File("tools/native-launcher/bin/" + nativeLauncherStubName(target));
    }

    private String nativeLauncherConfigKey(PackageTarget target) {
        switch (target) {
            case WINDOWS:
                return "package_native_launcher_stub_windows";
            case LINUX:
                return "package_native_launcher_stub_linux";
            case MAC_OSX:
                return "package_native_launcher_stub_macos";
            case WEB_START:
            default:
                return "package_native_launcher_stub_windows";
        }
    }

    private String nativeLauncherStubName(PackageTarget target) {
        switch (target) {
            case WINDOWS:
                return "windows-x64/scenemax-selfextract.exe";
            case LINUX:
                return "linux-x64/scenemax-selfextract";
            case MAC_OSX:
                return "macos-x64/scenemax-selfextract";
            case WEB_START:
            default:
                return "windows-x64/scenemax-selfextract.exe";
        }
    }

    private String zigTarget(PackageTarget target) {
        switch (target) {
            case WINDOWS:
                return "x86_64-windows";
            case LINUX:
                return "x86_64-linux";
            case MAC_OSX:
                return "x86_64-macos";
            case WEB_START:
            default:
                return "x86_64-windows";
        }
    }

    private void writeSelfExtractPayload(File payloadRoot, File payloadFile) throws IOException {
        List<PayloadEntry> entries = collectPayloadEntries(payloadRoot);
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(payloadFile))) {
            out.write(SELF_EXTRACT_PAYLOAD_MAGIC);
            writeIntLE(out, entries.size());
            for (PayloadEntry entry : entries) {
                byte[] pathBytes = entry.relativePath.getBytes(StandardCharsets.UTF_8);
                out.write(entry.directory ? 1 : 0);
                out.write(entry.executable ? 1 : 0);
                writeIntLE(out, pathBytes.length);
                writeLongLE(out, entry.directory ? 0L : entry.file.length());
                out.write(pathBytes);
                if (!entry.directory) {
                    copyFileToStream(entry.file, out);
                }
            }
        }
    }

    private List<PayloadEntry> collectPayloadEntries(File root) throws IOException {
        List<PayloadEntry> entries = new ArrayList<>();
        collectPayloadEntries(root, root, entries);
        entries.sort(Comparator.comparing((PayloadEntry entry) -> entry.relativePath));
        return entries;
    }

    private void collectPayloadEntries(File root, File current, List<PayloadEntry> entries) throws IOException {
        File[] children = current.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            String relativePath = toRelativePath(child.getCanonicalPath(), root.getCanonicalFile()).replace('\\', '/');
            if (relativePath.length() == 0) {
                continue;
            }
            if (child.isDirectory()) {
                entries.add(new PayloadEntry(child, relativePath, true, false));
                collectPayloadEntries(root, child, entries);
            } else if (child.isFile()) {
                boolean executable = child.canExecute()
                        || relativePath.endsWith("/bin/java")
                        || relativePath.endsWith("/bin/java.exe")
                        || relativePath.endsWith("/bin/javaw.exe");
                entries.add(new PayloadEntry(child, relativePath, false, executable));
            }
        }
    }

    private byte[] sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new DigestInputStream(new BufferedInputStream(new FileInputStream(file)), digest)) {
                byte[] buffer = new byte[64 * 1024];
                while (in.read(buffer) != -1) {
                    // DigestInputStream updates the digest.
                }
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is not available in this Java runtime.", e);
        }
    }

    private void copyFileToStream(File file, OutputStream out) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    private void writeIntLE(OutputStream out, int value) throws IOException {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
        out.write((value >>> 16) & 0xff);
        out.write((value >>> 24) & 0xff);
    }

    private void writeLongLE(OutputStream out, long value) throws IOException {
        for (int i = 0; i < 8; i++) {
            out.write((int) ((value >>> (i * 8)) & 0xff));
        }
    }

    private static final class PayloadEntry {
        final File file;
        final String relativePath;
        final boolean directory;
        final boolean executable;

        PayloadEntry(File file, String relativePath, boolean directory, boolean executable) {
            this.file = file;
            this.relativePath = relativePath;
            this.directory = directory;
            this.executable = executable;
        }
    }

    private File createMinimalJavaRuntime(PackageTarget target, File appJar, File runtimeDir) throws IOException {
        if (appJar == null || !appJar.isFile()) {
            throw failPackaging("Cannot build a minimal Java runtime because the packaged JAR is missing.");
        }

        File jdkHome = resolveJdkHomeForTarget(target);
        File jmodsDir = new File(jdkHome, "jmods");
        if (!jmodsDir.isDirectory()) {
            throw failPackaging("Cannot build a minimal Java runtime for " + target + ". The configured JDK has no jmods folder: " + jdkHome.getAbsolutePath());
        }

        File jlink = resolveCurrentJdkTool("jlink");
        if (jlink == null) {
            throw failPackaging("Cannot build a minimal Java runtime because jlink was not found. Run SceneMax with a JDK, not a JRE.");
        }

        if (runtimeDir.exists()) {
            FileUtils.deleteDirectory(runtimeDir);
        }
        File parent = runtimeDir.getParentFile();
        if (parent != null) {
            FileUtils.forceMkdir(parent);
        }

        updateStatus("Analyzing Java runtime modules for " + target + "...");
        LinkedHashSet<String> modules = inferRuntimeModules(appJar);
        String moduleCsv = String.join(",", modules);
        logPackage("Minimal Java modules for " + target + ": " + moduleCsv);

        updateStatus("Building minimal Java runtime for " + target + "...");
        List<String> command = new ArrayList<>();
        command.add(jlink.getAbsolutePath());
        command.add("--module-path");
        command.add(jmodsDir.getAbsolutePath());
        command.add("--add-modules");
        command.add(moduleCsv);
        command.add("--output");
        command.add(runtimeDir.getAbsolutePath());
        command.add("--strip-debug");
        command.add("--no-header-files");
        command.add("--no-man-pages");
        command.add("--compress=2");
        runCommand(command, null, "jlink", Collections.emptyMap(), "jlink:");

        File javaLauncher = new File(runtimeDir, "bin/java" + windowsExecutableSuffix(target));
        if (!javaLauncher.isFile()) {
            throw failPackaging("jlink finished, but the embedded runtime is missing " + javaLauncher.getAbsolutePath());
        }
        if (target == PackageTarget.WINDOWS) {
            File javawLauncher = new File(runtimeDir, "bin/javaw.exe");
            if (!javawLauncher.isFile()) {
                throw failPackaging("jlink finished, but the embedded runtime is missing " + javawLauncher.getAbsolutePath());
            }
        }

        appendCompletionNote("Embedded minimal Java runtime for " + target + ": "
                + runtimeDir.getAbsolutePath() + " (" + formatSize(sizeOf(runtimeDir)) + ").");
        return runtimeDir;
    }

    private LinkedHashSet<String> inferRuntimeModules(File appJar) throws IOException {
        File jdeps = resolveCurrentJdkTool("jdeps");
        if (jdeps == null) {
            throw failPackaging("Cannot infer Java runtime modules because jdeps was not found. Run SceneMax with a JDK, not a JRE.");
        }

        List<String> command = new ArrayList<>();
        command.add(jdeps.getAbsolutePath());
        command.add("--multi-release");
        command.add("11");
        command.add("--ignore-missing-deps");
        command.add("--print-module-deps");
        command.add(appJar.getAbsolutePath());

        String output = runCommandCapture(command, null, "jdeps");
        LinkedHashSet<String> modules = buildRuntimeModuleSet(output);
        if (modules.isEmpty()) {
            throw failPackaging("jdeps did not report any Java modules for " + appJar.getAbsolutePath());
        }
        return modules;
    }

    static LinkedHashSet<String> buildRuntimeModuleSet(String jdepsOutput) {
        LinkedHashSet<String> modules = new LinkedHashSet<>();
        String detected = extractJdepsModuleCsv(jdepsOutput);
        if (detected.length() > 0) {
            for (String module : detected.split(",")) {
                String trimmed = module.trim();
                if (trimmed.length() > 0) {
                    modules.add(trimmed);
                }
            }
        }
        modules.addAll(SCENEMAX_RUNTIME_GUARD_MODULES);
        modules.add("java.base");
        return modules;
    }

    static String extractJdepsModuleCsv(String jdepsOutput) {
        if (jdepsOutput == null) {
            return "";
        }
        String[] lines = jdepsOutput.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.length() == 0 || line.startsWith("Warning:") || line.startsWith("Error:")) {
                continue;
            }
            if (line.matches("[A-Za-z0-9_.]+(,[A-Za-z0-9_.]+)*")) {
                return line;
            }
        }
        return "";
    }

    private File resolveJdkHomeForTarget(PackageTarget target) throws IOException {
        String configured = readTargetJdkHome(target);
        if (configured.length() > 0) {
            return validateJdkHome(new File(configured), target);
        }

        File current = resolveCurrentJdkHome();
        if (target == getCurrentDesktopTarget()) {
            return validateJdkHome(current, target);
        }

        throw failPackaging("Embedding a Java runtime for " + target + " requires a matching JDK home. "
                + "Set " + jdkHomeConfigKey(target) + " in config.properties to a JDK for that platform, or package this target on that OS.");
    }

    private String readTargetJdkHome(PackageTarget target) {
        String envName = "SCENEMAX_JLINK_JDK_HOME_" + target.name();
        String envValue = System.getenv(envName);
        if (envValue != null && envValue.trim().length() > 0) {
            return envValue.trim();
        }
        return AppConfig.get(jdkHomeConfigKey(target), "").trim();
    }

    private String jdkHomeConfigKey(PackageTarget target) {
        switch (target) {
            case WINDOWS:
                return "package_jlink_jdk_home_windows";
            case LINUX:
                return "package_jlink_jdk_home_linux";
            case MAC_OSX:
                return "package_jlink_jdk_home_macos";
            case WEB_START:
            default:
                return "package_jlink_jdk_home_windows";
        }
    }

    private File validateJdkHome(File jdkHome, PackageTarget target) throws IOException {
        if (jdkHome == null || !jdkHome.isDirectory()) {
            throw failPackaging("Configured JDK home for " + target + " was not found: " + (jdkHome == null ? "(none)" : jdkHome.getAbsolutePath()));
        }
        if (!new File(jdkHome, "jmods").isDirectory()) {
            throw failPackaging("Configured JDK home for " + target + " does not contain jmods: " + jdkHome.getAbsolutePath());
        }
        return jdkHome;
    }

    private File resolveCurrentJdkHome() {
        File javaHome = new File(System.getProperty("java.home", ""));
        if (new File(javaHome, "jmods").isDirectory()) {
            return javaHome;
        }
        File parent = javaHome.getParentFile();
        if (parent != null && new File(parent, "jmods").isDirectory()) {
            return parent;
        }
        return javaHome;
    }

    private File resolveCurrentJdkTool(String toolName) {
        String executable = toolName + (isWindowsHost() ? ".exe" : "");
        File jdkHome = resolveCurrentJdkHome();
        File tool = new File(new File(jdkHome, "bin"), executable);
        if (tool.isFile()) {
            return tool;
        }
        File javaHomeTool = new File(new File(System.getProperty("java.home", ""), "bin"), executable);
        if (javaHomeTool.isFile()) {
            return javaHomeTool;
        }
        return findToolOnPath(executable);
    }

    private File findToolOnPath(String executable) {
        String path = System.getenv("PATH");
        if (path == null || path.trim().length() == 0) {
            return null;
        }
        for (String entry : path.split(Pattern.quote(File.pathSeparator))) {
            if (entry == null || entry.trim().length() == 0) {
                continue;
            }
            File candidate = new File(entry.trim(), executable);
            if (candidate.isFile()) {
                return candidate;
            }
        }
        return null;
    }

    private PackageTarget getCurrentDesktopTarget() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return PackageTarget.WINDOWS;
        }
        if (os.contains("mac")) {
            return PackageTarget.MAC_OSX;
        }
        return PackageTarget.LINUX;
    }

    private boolean isWindowsHost() {
        return getCurrentDesktopTarget() == PackageTarget.WINDOWS;
    }

    private String windowsExecutableSuffix(PackageTarget target) {
        return target == PackageTarget.WINDOWS ? ".exe" : "";
    }

    private File prepareWebStartPackage(String gameName) throws IOException {
        File webFolder = new File(outputFolder, "webstart");
        FileUtils.forceMkdir(webFolder);

        File webJar = new File(webFolder, gameName + ".jar");
        FileUtils.copyFile(new File(SCENE_JAR_NAME), webJar);
        if (options.signWebStart) {
            signWebStartJar(webJar, options.webVendor.length() == 0 ? "SceneMax3D" : options.webVendor);
        }
        copyWebStartIcon(webFolder);

        String normalizedBaseUrl = normalizeBaseUrl(options.webBaseUrl, gameName);
        String vendor = options.webVendor.length() == 0 ? "SceneMax3D" : options.webVendor;
        String homepage = options.webHomepage.length() == 0 ? normalizedBaseUrl + "/index.html" : options.webHomepage;

        FileUtils.writeStringToFile(
                new File(webFolder, "launch.jnlp"),
                createJnlp(gameName, normalizedBaseUrl, vendor, homepage),
                StandardCharsets.UTF_8
        );
        FileUtils.writeStringToFile(
                new File(webFolder, "index.html"),
                createWebLandingPage(gameName, vendor),
                StandardCharsets.UTF_8
        );
        FileUtils.writeStringToFile(
                new File(webFolder, "README.txt"),
                createWebStartReadme(gameName, normalizedBaseUrl),
                StandardCharsets.UTF_8
        );

        if (options.uploadWebStart) {
            uploadWebStartFiles(webFolder, normalizedBaseUrl);
        }

        return webFolder;
    }

    private void copyWebStartIcon(File webFolder) throws IOException {
        File preferred = options.windowsIcon;
        if ((preferred == null || !preferred.exists()) && options.linuxIcon != null && options.linuxIcon.exists()) {
            preferred = options.linuxIcon;
        }
        if ((preferred == null || !preferred.exists()) && options.macIcon != null && options.macIcon.exists()) {
            preferred = options.macIcon;
        }

        File targetIcon = new File(webFolder, "icon.png");
        if (preferred != null && preferred.exists() && preferred.isFile() && preferred.getName().toLowerCase().endsWith(".png")) {
            FileUtils.copyFile(preferred, targetIcon);
            return;
        }

        File defaultIcon = new File("assets/images/scenemax_icon.png");
        if (defaultIcon.exists()) {
            FileUtils.copyFile(defaultIcon, targetIcon);
        }
    }

    private String normalizeBaseUrl(String baseUrl, String gameName) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.length() == 0) {
            normalized = "https://example.com/" + gameName;
        }
        return normalized;
    }

    private String createJnlp(String gameName, String codebase, String vendor, String homepage) {
        String title = escapeXml(gameName.replace("_", " "));
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<jnlp spec=\"1.0+\" codebase=\"" + escapeXml(codebase) + "\" href=\"launch.jnlp\">\n" +
                "  <information>\n" +
                "    <title>" + title + "</title>\n" +
                "    <vendor>" + escapeXml(vendor) + "</vendor>\n" +
                "    <homepage href=\"" + escapeXml(homepage) + "\"/>\n" +
                "    <description>" + title + " for SceneMax Web Start deployment.</description>\n" +
                "    <description kind=\"short\">Launch " + title + " with OpenWebStart.</description>\n" +
                "    <icon href=\"icon.png\" kind=\"default\"/>\n" +
                "    <offline-allowed/>\n" +
                "    <shortcut online=\"true\">\n" +
                "      <desktop/>\n" +
                "      <menu submenu=\"" + title + "\"/>\n" +
                "    </shortcut>\n" +
                "  </information>\n" +
                "  <resources>\n" +
                "    <j2se version=\"11+\"/>\n" +
                "    <jar href=\"" + escapeXml(gameName) + ".jar\" main=\"true\" download=\"eager\"/>\n" +
                "  </resources>\n" +
                "  <application-desc main-class=\"com.scenemaxeng.projector.SceneMaxLauncher\"/>\n" +
                "</jnlp>\n";
    }

    private String createWebLandingPage(String gameName, String vendor) {
        String title = escapeHtml(gameName.replace("_", " "));
        String safeVendor = escapeHtml(vendor);
        return "<!doctype html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "  <meta charset=\"utf-8\" />\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />\n" +
                "  <title>" + title + " | Web Start</title>\n" +
                "  <style>\n" +
                "    :root { --bg: #f7f2e9; --ink: #10221c; --muted: #5f6d68; --card: rgba(255,255,255,0.82); --accent: #0d8f63; --accent-dark: #0b6849; --line: rgba(16,34,28,0.12); }\n" +
                "    * { box-sizing: border-box; }\n" +
                "    body { margin: 0; font-family: 'Segoe UI', Tahoma, sans-serif; color: var(--ink); background: radial-gradient(circle at top left, #fff6d8 0%, rgba(255,246,216,0) 35%), linear-gradient(135deg, #f8f4ec 0%, #dbe7df 100%); min-height: 100vh; }\n" +
                "    .shell { max-width: 1100px; margin: 0 auto; padding: 32px 20px 48px; }\n" +
                "    .hero { display: grid; grid-template-columns: 1.3fr 0.9fr; gap: 24px; align-items: stretch; }\n" +
                "    .panel { background: var(--card); backdrop-filter: blur(12px); border: 1px solid var(--line); border-radius: 28px; box-shadow: 0 24px 60px rgba(16,34,28,0.12); }\n" +
                "    .lead { padding: 34px; }\n" +
                "    .eyebrow { display: inline-block; padding: 7px 12px; border-radius: 999px; background: rgba(13,143,99,0.12); color: var(--accent-dark); font-size: 12px; letter-spacing: 0.08em; text-transform: uppercase; font-weight: 700; }\n" +
                "    h1 { margin: 18px 0 14px; font-size: clamp(32px, 5vw, 60px); line-height: 0.96; }\n" +
                "    .sub { font-size: 18px; line-height: 1.6; color: var(--muted); max-width: 40rem; }\n" +
                "    .actions { display: flex; flex-wrap: wrap; gap: 14px; margin-top: 28px; }\n" +
                "    .btn { display: inline-flex; align-items: center; justify-content: center; min-height: 52px; padding: 0 22px; border-radius: 16px; text-decoration: none; font-weight: 700; transition: transform 120ms ease, box-shadow 120ms ease; }\n" +
                "    .btn:hover { transform: translateY(-1px); }\n" +
                "    .primary { background: linear-gradient(135deg, var(--accent) 0%, #22b07d 100%); color: white; box-shadow: 0 18px 30px rgba(13,143,99,0.22); }\n" +
                "    .secondary { background: white; color: var(--ink); border: 1px solid var(--line); }\n" +
                "    .side { padding: 26px; display: flex; flex-direction: column; justify-content: space-between; background: linear-gradient(180deg, rgba(255,255,255,0.92) 0%, rgba(243,249,246,0.92) 100%); }\n" +
                "    .steps { display: grid; gap: 14px; margin-top: 20px; }\n" +
                "    .step { border: 1px solid var(--line); border-radius: 18px; padding: 16px; background: rgba(255,255,255,0.78); }\n" +
                "    .step strong { display: block; margin-bottom: 6px; }\n" +
                "    .note { margin-top: 16px; font-size: 14px; line-height: 1.6; color: var(--muted); }\n" +
                "    .footer { margin-top: 22px; font-size: 13px; color: var(--muted); }\n" +
                "    @media (max-width: 860px) { .hero { grid-template-columns: 1fr; } .lead, .side { padding: 24px; } }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <main class=\"shell\">\n" +
                "    <section class=\"hero\">\n" +
                "      <div class=\"panel lead\">\n" +
                "        <span class=\"eyebrow\">SceneMax Web Start</span>\n" +
                "        <h1>" + title + "</h1>\n" +
                "        <p class=\"sub\">Launch the desktop game straight from your browser using OpenWebStart. Click the green button, allow the <code>.jnlp</code> file to open, and the runtime will handle the rest.</p>\n" +
                "        <div class=\"actions\">\n" +
                "          <a class=\"btn primary\" href=\"launch.jnlp\">Launch Game</a>\n" +
                "          <a class=\"btn secondary\" href=\"https://openwebstart.com/download/\" target=\"_blank\" rel=\"noreferrer\">Install OpenWebStart</a>\n" +
                "        </div>\n" +
                "        <p class=\"footer\">Published by " + safeVendor + "</p>\n" +
                "      </div>\n" +
                "      <aside class=\"panel side\">\n" +
                "        <div>\n" +
                "          <h2>First time?</h2>\n" +
                "          <div class=\"steps\">\n" +
                "            <div class=\"step\"><strong>1. Install OpenWebStart</strong>Use the installer once on the target machine.</div>\n" +
                "            <div class=\"step\"><strong>2. Click Launch Game</strong>Your browser downloads <code>launch.jnlp</code>.</div>\n" +
                "            <div class=\"step\"><strong>3. Open the file</strong>OpenWebStart downloads the game JAR and starts it.</div>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        <p class=\"note\">For the smoothest experience, host these files over HTTPS and configure your web server to return the MIME type <code>application/x-java-jnlp-file</code> for <code>.jnlp</code> files.</p>\n" +
                "      </aside>\n" +
                "    </section>\n" +
                "  </main>\n" +
                "</body>\n" +
                "</html>\n";
    }

    private String createWebStartReadme(String gameName, String baseUrl) {
        return "SceneMax Web Start package\n\n" +
                "Files in this folder:\n" +
                "- index.html: browser landing page for players\n" +
                "- launch.jnlp: OpenWebStart launcher descriptor\n" +
                "- " + gameName + ".jar: packaged game runtime\n" +
                "- icon.png: launcher icon\n\n" +
                "Deploy steps:\n" +
                "1. Upload every file in this folder to: " + baseUrl + "\n" +
                "2. Serve launch.jnlp with MIME type application/x-java-jnlp-file\n" +
                "3. Link players to index.html\n" +
                "4. Prefer HTTPS for both the landing page and the JNLP/JAR files\n\n" +
                "Signing note:\n" +
                "- OpenWebStart can launch JNLP applications, but unsigned or self-signed JARs may show trust prompts.\n" +
                "- For the most user-friendly production flow, sign the JAR with a certificate trusted by end-user machines.\n";
    }

    private void signWebStartJar(File webJar, String vendor) throws IOException {
        File keystore = resolveKeystoreFile(webJar);
        if (!keystore.exists()) {
            if (!options.generateSelfSignedCertificate) {
                throw new IOException("Web Start signing was requested, but the keystore file does not exist: " + keystore.getAbsolutePath());
            }
            generateSelfSignedCertificate(keystore, vendor);
            appendCompletionNote("Created self-signed test certificate: " + keystore.getAbsolutePath());
        }

        List<String> command = new ArrayList<>();
        command.add("jarsigner");
        command.add("-keystore");
        command.add(keystore.getAbsolutePath());
        command.add("-storepass");
        command.add(options.keystorePassword);
        command.add("-keypass");
        command.add(options.keyPassword);
        command.add(webJar.getAbsolutePath());
        command.add(options.keystoreAlias);
        runCommand(command, webJar.getParentFile(), "jarsigner");
        appendCompletionNote("Signed Web Start JAR with alias '" + options.keystoreAlias + "'.");
    }

    private File resolveKeystoreFile(File webJar) {
        if (options.keystoreFile != null && options.keystoreFile.getPath().trim().length() > 0) {
            return options.keystoreFile;
        }
        return new File(webJar.getParentFile(), "webstart-selfsigned.p12");
    }

    private void generateSelfSignedCertificate(File keystore, String vendor) throws IOException {
        File parent = keystore.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        List<String> command = new ArrayList<>();
        command.add("keytool");
        command.add("-genkeypair");
        command.add("-noprompt");
        command.add("-storetype");
        command.add("PKCS12");
        command.add("-keystore");
        command.add(keystore.getAbsolutePath());
        command.add("-storepass");
        command.add(options.keystorePassword);
        command.add("-keypass");
        command.add(options.keyPassword);
        command.add("-alias");
        command.add(options.keystoreAlias);
        command.add("-dname");
        command.add("CN=" + vendor + ", OU=SceneMax3D, O=" + vendor + ", L=Jerusalem, ST=Jerusalem, C=IL");
        command.add("-validity");
        command.add("3650");
        command.add("-keyalg");
        command.add("RSA");
        command.add("-keysize");
        command.add("2048");
        runCommand(command, keystore.getParentFile(), "keytool");
    }

    private void uploadWebStartFiles(File webFolder, String normalizedBaseUrl) throws IOException {
        if (options.webRemoteFolder.length() == 0) {
            throw new IOException("Web Start upload was requested, but no FTP remote folder was provided.");
        }
        File[] children = webFolder.listFiles();
        if (children == null || children.length == 0) {
            return;
        }
        List<File> files = new ArrayList<>();
        for (File child : children) {
            if (child.isFile()) {
                files.add(child);
            }
        }
        updateStatus("Preparing upload...");
        final int uploadBase = Math.max(getProgress(), progressPhaseBase);
        final int uploadSpan = Math.max(1, Math.min(99, progressPhaseBase + progressPhaseSpan) - uploadBase);
        setRunningProgress(uploadBase);
        try {
            Util.ftpUploadFiles(files, options.webRemoteFolder, new IMonitor() {
                @Override
                public void setNote(String note) {
                    updateStatus(note);
                }

                @Override
                public void setProgress(int progress) {
                    PackageProgramTask.this.setRunningProgress(scaledProgress(uploadBase, uploadSpan, progress));
                }

                @Override
                public void onEnd() {
                    updateStatus("Upload completed.");
                    PackageProgramTask.this.setRunningProgress(uploadBase + uploadSpan);
                }
            });
        } catch (Exception e) {
            throw new IOException(buildUploadErrorMessage(e), e);
        }
        appendCompletionNote("Uploaded Web Start files to " + options.webRemoteFolder + ".");
        appendCompletionNote("Public launch page: " + normalizedBaseUrl + "/index.html");
    }

    private String buildUploadErrorMessage(Exception e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage() == null ? e.getMessage() : root.getMessage();
        if (root instanceof java.net.NoRouteToHostException) {
            return "Cannot reach the server. Check the host/IP, port, firewall rules, and whether SFTP is exposed from your current network.";
        }
        if (root instanceof java.net.ConnectException) {
            return "Connection refused or timed out. Check the host/IP, port, and whether the server is accepting " + Util.FILE_TRANSFER_PROTOCOL + " connections.";
        }
        if (message == null || message.trim().length() == 0) {
            message = e.toString();
        }
        return "Web Start upload failed: " + message;
    }

    private void runCommand(List<String> command, File workingDir, String toolName) throws IOException {
        runCommand(command, workingDir, toolName, Collections.emptyMap(), null);
    }

    private void runCommand(List<String> command, File workingDir, String toolName, Map<String, String> extraEnvironment, String statusPrefix) throws IOException {
        logPackage("Running " + toolName + ": " + String.join(" ", command));
        if (workingDir != null) {
            logPackage(toolName + " working directory: " + workingDir.getAbsolutePath());
        }
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (workingDir != null) {
            processBuilder.directory(workingDir);
        }
        if (extraEnvironment != null && !extraEnvironment.isEmpty()) {
            processBuilder.environment().putAll(extraEnvironment);
        }
        processBuilder.redirectErrorStream(true);
        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException e) {
            logPackageException("Failed to start " + toolName + ".", e);
            throw new IOException("Failed to start " + toolName + ". Make sure it is available in PATH.", e);
        }

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
                if (line.trim().length() > 0) {
                    logPackage(toolName + ": " + line);
                }
                if (statusPrefix != null && line.trim().length() > 0) {
                    updateStatus(statusPrefix + " " + line.trim());
                }
            }
        }

        try {
            int exitCode = process.waitFor();
            logPackage(toolName + " exited with code " + exitCode + ".");
            if (exitCode != 0) {
                String text = output.toString().trim();
                throw new IOException(toolName + " failed with exit code " + exitCode + (text.length() == 0 ? "" : ": " + text));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(toolName + " was interrupted.", e);
        }
    }

    private String runCommandCapture(List<String> command, File workingDir, String toolName) throws IOException {
        logPackage("Running " + toolName + ": " + String.join(" ", command));
        if (workingDir != null) {
            logPackage(toolName + " working directory: " + workingDir.getAbsolutePath());
        }
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (workingDir != null) {
            processBuilder.directory(workingDir);
        }
        processBuilder.redirectErrorStream(true);
        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException e) {
            logPackageException("Failed to start " + toolName + ".", e);
            throw new IOException("Failed to start " + toolName + ". Make sure it is available in PATH.", e);
        }

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
                if (line.trim().length() > 0) {
                    logPackage(toolName + ": " + line);
                }
            }
        }

        try {
            int exitCode = process.waitFor();
            logPackage(toolName + " exited with code " + exitCode + ".");
            if (exitCode != 0) {
                String text = output.toString().trim();
                throw new IOException(toolName + " failed with exit code " + exitCode + (text.length() == 0 ? "" : ": " + text));
            }
            return output.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(toolName + " was interrupted.", e);
        }
    }

    private void appendCompletionNote(String note) {
        if (note == null || note.trim().length() == 0) {
            return;
        }
        if (completionNotes.length() > 0) {
            completionNotes.append("\r\n");
        }
        completionNotes.append(note.trim());
    }

    private void updateStatus(String note) {
        String newValue = note == null ? "" : note.trim();
        String oldValue = this.statusNote;
        this.statusNote = newValue;
        firePropertyChange("statusNote", oldValue, newValue);
        if (newValue.length() > 0) {
            logPackage("Status: " + newValue);
        }
    }

    private IOException failPackaging(String message) {
        return failPackaging(message, null);
    }

    private IOException failPackaging(String message, Throwable cause) {
        String normalized = message == null || message.trim().length() == 0 ? "Packaging failed." : message.trim();
        failureMessage = appendPackageLogPath(normalized);
        if (cause == null) {
            logPackage("ERROR: " + normalized);
            return new IOException(normalized);
        }
        logPackageException("ERROR: " + normalized, cause);
        return new IOException(normalized, cause);
    }

    private String appendPackageLogPath(String message) {
        if (packageLogFile == null) {
            return message;
        }
        return message + "\r\n\r\nPackage log:\r\n" + packageLogFile.getAbsolutePath();
    }

    private void logPackage(String message) {
        String normalized = message == null ? "" : message;
        String line = "[" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "] " + normalized + System.lineSeparator();
        System.out.print("[package] " + normalized + System.lineSeparator());
        if (packageLogFile == null) {
            return;
        }
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(packageLogFile, true), StandardCharsets.UTF_8)) {
            writer.write(line);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void logPackageException(String message, Throwable throwable) {
        logPackage(message);
        if (throwable == null || packageLogFile == null) {
            return;
        }
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(packageLogFile, true), StandardCharsets.UTF_8))) {
            throwable.printStackTrace(writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getGameName() {
        String gameName = scriptFolder == null ? "scenemax_game" : scriptFolder.getName();
        gameName = gameName.replace(" ", "_").trim();
        if (gameName.length() == 0) {
            gameName = "scenemax_game";
        }
        return gameName;
    }

    private File getPackagedProjectRoot() {
        File current = scriptFolder;
        while (current != null) {
            File resourcesDir = new File(current, "resources");
            if (resourcesDir.isDirectory()) {
                return current;
            }

            if ("scripts".equalsIgnoreCase(current.getName())) {
                File candidate = current.getParentFile();
                if (candidate != null && new File(candidate, "resources").isDirectory()) {
                    return candidate;
                }
            }

            current = current.getParentFile();
        }

        return null;
    }

    private File getPackagedProjectResourcesFolder() {
        File projectRoot = getPackagedProjectRoot();
        if (projectRoot != null) {
            File resourcesDir = new File(projectRoot, "resources");
            if (resourcesDir.isDirectory()) {
                return resourcesDir;
            }
        }

        String activeResources = Util.getResourcesFolder();
        return activeResources == null ? null : new File(activeResources);
    }

    private void copyEffekseerResourcesToDeploy(File deployFolder) throws IOException {
        File deployEffectsDir = new File(deployFolder, "resources/effects");

        for (String effectName : SceneMaxLanguageParser.effekseerUsed) {
            String assetId = effectName;
            String prefix = "effects.effekseer.";
            if (assetId.toLowerCase().startsWith(prefix)) {
                assetId = assetId.substring(prefix.length());
            }

            File targetDir = new File(deployEffectsDir, assetId);
            if (targetDir.isDirectory()) {
                continue;
            }

            File sourceDir = resolveEffekseerEffectSource(assetId);
            if (!sourceDir.isDirectory()) {
                throw failPackaging(
                        "Missing Effekseer effect resource '" + effectName + "'. Expected folder: " + sourceDir.getAbsolutePath()
                );
            }

            try {
                FileUtils.copyDirectory(sourceDir, targetDir);
                logPackage("Copied Effekseer effect '" + effectName + "' from " + sourceDir.getAbsolutePath());
            } catch (IOException e) {
                throw failPackaging("Failed to copy Effekseer effect '" + effectName + "'.", e);
            }
        }
    }

    private void copyLightProbeResourcesToDeploy(File deployFolder) {
        if (SceneMaxLanguageParser.lightProbesUsed.isEmpty()) {
            return;
        }

        File deployProbeDir = new File(deployFolder, "probes");
        for (String probeName : SceneMaxLanguageParser.lightProbesUsed) {
            String fileName = resolveBuiltInProbeFileName(probeName);
            if (fileName == null) {
                continue;
            }

            File source = new File(Util.getResourcePath("probes/" + fileName));
            if (!source.isFile()) {
                throw new RuntimeException("Missing built-in light probe resource: " + source.getAbsolutePath());
            }

            try {
                FileUtils.forceMkdir(deployProbeDir);
                FileUtils.copyFile(source, new File(deployProbeDir, fileName));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private String resolveBuiltInProbeFileName(String probeName) {
        if (probeName == null) {
            return null;
        }
        switch (probeName.trim()) {
            case "1":
                return "1.j3o";
            case "2":
                return "2.j3o";
            case "3":
                return "3.j3o";
            case "4":
                return "4.j3o";
            case "5":
                return "5.j3o";
            default:
                return null;
        }
    }

    private void copyAnimationResourcesToDeploy(File deployFolder, JSONArray targetArray) {
        appendUsedIndexedResources(
                deployFolder,
                "animations",
                targetArray,
                animationNamesUsed,
                new File("./resources/animations/animations.json"),
                getProjectResourceIndexFile("animations/animations-ext.json")
        );
    }

    private void copyThrowMotionResourcesToDeploy(File deployFolder) {
        File deployThrowMotionsDir = new File(deployFolder, "resources/throw_motions");
        copyReferencedStandaloneAssetsToDeploy(
                collectStandaloneAssetFiles("smmotion", "throw_motions", "ThrowMotions"),
                throwMotionAssetNamesUsed,
                deployThrowMotionsDir,
                "smmotion"
        );
    }

    private void copyWeaponResourcesToDeploy(File deployFolder) {
        File deployWeaponsDir = new File(deployFolder, "resources/weapons");
        copyReferencedStandaloneAssetsToDeploy(
                collectStandaloneAssetFiles("smweapon", "weapons", "Weapons"),
                weaponAssetNamesUsed,
                deployWeaponsDir,
                "smweapon"
        );
    }

    private void copyIKResourcesToDeploy(File deployFolder) {
        File deployIKDir = new File(deployFolder, "resources/IK");
        copyReferencedStandaloneAssetsToDeploy(
                collectIKStandaloneAssetFiles(),
                ikAssetNamesUsed,
                deployIKDir,
                "smik"
        );
    }

    private File resolveEffekseerEffectSource(String assetId) {
        File projectResources = getPackagedProjectResourcesFolder();
        if (projectResources != null) {
            File projectEffect = new File(projectResources, "effects/" + assetId);
            if (projectEffect.isDirectory()) {
                return projectEffect;
            }
        }

        return new File(Util.getDefaultResourcesFolder(), "effects/" + assetId);
    }

    private void verifyEffekseerNativeResourcesForSelectedTargets() throws IOException {
        LinkedHashMap<String, String> requiredPlatforms = new LinkedHashMap<>();
        if (targets.contains(PackageTarget.WINDOWS)) {
            requiredPlatforms.put("windows-x86_64", "scenemax_effekseer_jni.dll");
        }
        if (targets.contains(PackageTarget.LINUX)) {
            requiredPlatforms.put("linux-x86_64", "libscenemax_effekseer_jni.so");
        }
        if (targets.contains(PackageTarget.MAC_OSX)) {
            requiredPlatforms.put("macos-x86_64", "libscenemax_effekseer_jni.dylib");
            requiredPlatforms.put("macos-aarch64", "libscenemax_effekseer_jni.dylib");
        }
        if (targets.contains(PackageTarget.WEB_START)) {
            requiredPlatforms.put("windows-x86_64", "scenemax_effekseer_jni.dll");
            requiredPlatforms.put("linux-x86_64", "libscenemax_effekseer_jni.so");
            requiredPlatforms.put("macos-x86_64", "libscenemax_effekseer_jni.dylib");
            requiredPlatforms.put("macos-aarch64", "libscenemax_effekseer_jni.dylib");
        }

        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, String> entry : requiredPlatforms.entrySet()) {
            File nativeLib = new File("scenemax_effekseer_runtime/assets/native/" + entry.getKey() + "/" + entry.getValue());
            if (!nativeLib.isFile()) {
                missing.add(entry.getKey() + " -> " + nativeLib.getPath());
            }
        }

        if (!missing.isEmpty()) {
            throw new IOException("Missing Effekseer native runtime libraries for the selected package targets:\n - "
                    + String.join("\n - ", missing));
        }
    }

    private void collectReferencedAuxiliaryAssets(AssetsMapping assetsMapping) {
        String sourceText = readScannedSourceText();
        if (sourceText.length() == 0) {
            return;
        }

        collectNamedReferences(sourceText, assetsMapping.getAnimationsIndex().keySet(), animationNamesUsed);
        collectNamedReferences(sourceText, assetsMapping.getMaterialsIndex().keySet(), materialNamesUsed);
        collectNamedReferences(sourceText, builtInMaterialTexturePaths().keySet(), builtInMaterialNamesUsed);

        for (ResourceShader shader : assetsMapping.getShadersIndex().values()) {
            if (shader == null || shader.name == null || !containsAssetName(sourceText, shader.name)) {
                continue;
            }
            if (isEnvironmentShaderPath(shader.path)) {
                environmentShaderNamesUsed.add(shader.name);
            } else {
                shaderNamesUsed.add(shader.name);
            }
        }

        collectNamedReferences(sourceText, collectStandaloneAssetCandidateNames("smweapon", "weapons", "Weapons"), weaponAssetNamesUsed);
        collectNamedReferences(sourceText, collectStandaloneAssetCandidateNames("smmotion", "throw_motions", "ThrowMotions"), throwMotionAssetNamesUsed);
        collectNamedReferences(sourceText, collectIKStandaloneAssetCandidateNames(), ikAssetNamesUsed);
    }

    private String readScannedSourceText() {
        StringBuilder combined = new StringBuilder();
        if (prg != null && !prg.isBlank()) {
            combined.append(prg);
        }
        appendSourceFiles(combined, scannedScriptFiles);
        appendSourceFiles(combined, scannedDesignerFiles);
        return combined.toString();
    }

    private void appendSourceFiles(StringBuilder combined, List<String> paths) {
        for (String path : paths) {
            if (path == null || path.isBlank()) {
                continue;
            }
            try {
                combined.append('\n')
                        .append(FileUtils.readFileToString(new File(path), StandardCharsets.UTF_8));
            } catch (IOException ignored) {
            }
        }
    }

    private void collectNamedReferences(String sourceText, Collection<String> names, Set<String> target) {
        if (names == null || names.isEmpty()) {
            return;
        }
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            if (containsAssetName(sourceText, name)) {
                target.add(name);
            }
        }
    }

    private boolean containsAssetName(String sourceText, String assetName) {
        if (sourceText == null || sourceText.length() == 0 || assetName == null || assetName.isBlank()) {
            return false;
        }
        String pattern = "(?i)(^|[^A-Za-z0-9_\\-])" + Pattern.quote(assetName.trim()) + "($|[^A-Za-z0-9_\\-])";
        return Pattern.compile(pattern).matcher(sourceText).find();
    }

    private void appendUsedShaderResources(AssetsMapping assetsMapping, JSONArray shadersArray, JSONArray environmentShadersArray) {
        for (String shaderName : shaderNamesUsed) {
            ResourceShader shader = assetsMapping.getShadersIndex().get(shaderName.toLowerCase(Locale.ROOT));
            appendUsedShaderResource(shader, shadersArray);
        }
        for (String shaderName : environmentShaderNamesUsed) {
            ResourceShader shader = assetsMapping.getShadersIndex().get(shaderName.toLowerCase(Locale.ROOT));
            appendUsedShaderResource(shader, environmentShadersArray);
        }
    }

    private void appendUsedShaderResource(ResourceShader shader, JSONArray targetArray) {
        if (shader == null || shader.path == null || shader.path.isBlank()) {
            return;
        }
        copyIndexedResourcePathToDeploy(new File("./deploy"), shader.path);
        JSONObject resource = findIndexedResourceByName(
                isEnvironmentShaderPath(shader.path) ? "environmentShaders" : "shaders",
                shader.name,
                isEnvironmentShaderPath(shader.path)
                        ? new File("./resources/environment_shaders/environment-shaders.json")
                        : new File("./resources/shaders/shaders.json"),
                isEnvironmentShaderPath(shader.path)
                        ? getProjectResourceIndexFile("environment_shaders/environment-shaders-ext.json")
                        : getProjectResourceIndexFile("shaders/shaders-ext.json")
        );
        if (resource == null) {
            resource = new JSONObject();
            resource.put("name", shader.name);
            resource.put("path", shader.path);
        }
        upsertIndexedResource(targetArray, resource);
    }

    private boolean isEnvironmentShaderPath(String path) {
        return path != null && path.replace("\\", "/").toLowerCase(Locale.ROOT).startsWith("environment_shaders/");
    }

    private void appendUsedMaterialResources(AssetsMapping assetsMapping, JSONArray targetArray) {
        for (String materialName : materialNamesUsed) {
            ResourceMaterialAsset material = assetsMapping.getMaterialsIndex().get(materialName.toLowerCase(Locale.ROOT));
            if (material == null || material.path == null || material.path.isBlank()) {
                continue;
            }
            copyIndexedResourcePathToDeploy(new File("./deploy"), material.path);
            JSONObject resource = findIndexedResourceByName(
                    "materials",
                    material.name,
                    new File("./resources/material/materials.json"),
                    getProjectResourceIndexFile("material/materials-ext.json")
            );
            if (resource == null) {
                resource = new JSONObject();
                resource.put("name", material.name);
                resource.put("path", material.path);
                resource.put("transparent", material.transparent);
                resource.put("doubleSided", material.doubleSided);
            }
            upsertIndexedResource(targetArray, resource);
        }
    }

    private void copyUsedBuiltInMaterialTextures() {
        Map<String, String[]> textures = builtInMaterialTexturePaths();
        for (String materialName : builtInMaterialNamesUsed) {
            String[] paths = textures.get(materialName.toLowerCase(Locale.ROOT));
            if (paths == null) {
                continue;
            }
            for (String path : paths) {
                copyResourceFileToDeploy(new File("./deploy"), path);
            }
        }
    }

    private Map<String, String[]> builtInMaterialTexturePaths() {
        Map<String, String[]> textures = new LinkedHashMap<>();
        textures.put("pond", new String[]{"Textures/Terrain/Pond/Pond.jpg", "Textures/Terrain/Pond/Pond_normal.png"});
        textures.put("rock", new String[]{"Textures/Terrain/Rock/rock.png", "Textures/Terrain/Rock/rock_normal.png"});
        textures.put("rock2", new String[]{"Textures/Terrain/Rock/rock2.jpg", "Textures/Terrain/Rock/rock_normal.png"});
        textures.put("brickwall", new String[]{"Textures/Terrain/BrickWall/brickwall.jpg", "Textures/Terrain/BrickWall/brickwall_normal.jpg"});
        textures.put("dirt", new String[]{"Textures/Terrain/Splat/dirt.jpg", "Textures/Terrain/Splat/dirt_normal.png"});
        textures.put("grass", new String[]{"Textures/Terrain/Splat/grass.jpg", "Textures/Terrain/Splat/grass_normal.jpg"});
        textures.put("road", new String[]{"Textures/Terrain/Splat/road.jpg", "Textures/Terrain/Splat/road_normal.png"});
        textures.put("alpha", new String[]{"Textures/Terrain/Splat/alpha1.png", "Textures/Terrain/Splat/alphamap.png"});
        textures.put("alpha2", new String[]{"Textures/Terrain/Splat/alpha2.png", "Textures/Terrain/Splat/alphamap2.png"});
        return textures;
    }

    private void appendUsedVideoResources(File deployFolder, JSONArray targetArray) {
        appendUsedIndexedResources(deployFolder,
                "videos",
                targetArray,
                normalizeVideoResourceNames(),
                new File("./resources/videos/videos.json"),
                getProjectResourceIndexFile("videos/videos-ext.json"));
    }

    private Set<String> normalizeVideoResourceNames() {
        Set<String> names = new LinkedHashSet<>();
        for (String used : SceneMaxLanguageParser.videoUsed) {
            if (used == null || used.isBlank()) {
                continue;
            }
            String name = used.trim();
            if (name.toLowerCase(Locale.ROOT).startsWith("videos.")) {
                name = name.substring("videos.".length());
            }
            if (!name.isBlank()) {
                names.add(name);
            }
        }
        return names;
    }

    private void appendUsedIndexedResources(File deployFolder, String arrayKey, JSONArray targetArray, Set<String> usedNames, File... indexFiles) {
        if (usedNames == null || usedNames.isEmpty()) {
            return;
        }
        for (String usedName : usedNames) {
            JSONObject resource = findIndexedResourceByName(arrayKey, usedName, indexFiles);
            if (resource == null) {
                continue;
            }
            copyIndexedResourcePathToDeploy(deployFolder, resource.optString("path", ""));
            upsertIndexedResource(targetArray, resource);
        }
    }

    private JSONObject findIndexedResourceByName(String arrayKey, String name, File... indexFiles) {
        if (name == null || name.isBlank() || indexFiles == null) {
            return null;
        }
        String key = name.toLowerCase(Locale.ROOT);
        for (File indexFile : indexFiles) {
            if (indexFile == null || !indexFile.isFile()) {
                continue;
            }
            try {
                JSONObject root = new JSONObject(FileUtils.readFileToString(indexFile, StandardCharsets.UTF_8));
                JSONArray array = root.optJSONArray(arrayKey);
                if (array == null) {
                    continue;
                }
                for (int i = 0; i < array.length(); i++) {
                    JSONObject resource = array.optJSONObject(i);
                    if (resource == null) {
                        continue;
                    }
                    if (key.equals(resource.optString("name", "").toLowerCase(Locale.ROOT))) {
                        return new JSONObject(resource.toString());
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    private File getProjectResourceIndexFile(String relativePath) {
        File projectResources = getPackagedProjectResourcesFolder();
        return projectResources == null ? null : new File(projectResources, relativePath);
    }

    private void copyIndexedResourcePathToDeploy(File deployFolder, String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return;
        }

        File sourceFile = resolveResourceFile(resourcePath);
        if (sourceFile == null || !sourceFile.exists()) {
            return;
        }

        File sourceToCopy = sourceFile.isDirectory() ? sourceFile : sourceFile.getParentFile();
        String normalizedPath = resourcePath.replace("\\", "/");
        String targetRelativePath = normalizedPath;
        int slash = normalizedPath.lastIndexOf('/');
        if (slash > 0) {
            targetRelativePath = normalizedPath.substring(0, slash);
        }

        File target = new File(deployFolder, targetRelativePath);
        try {
            if (sourceToCopy != null && sourceToCopy.isDirectory()) {
                FileUtils.copyDirectory(sourceToCopy, target);
            } else if (sourceFile.isFile()) {
                FileUtils.copyFile(sourceFile, new File(deployFolder, normalizedPath));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private File resolveResourceFile(String resourcePath) {
        String normalizedPath = resourcePath.replace("\\", "/");
        File projectResources = getPackagedProjectResourcesFolder();
        if (projectResources != null) {
            File projectFile = new File(projectResources, normalizedPath);
            if (projectFile.exists()) {
                return projectFile;
            }
        }

        File defaultFile = new File(Util.getDefaultResourcesFolder(), normalizedPath);
        if (defaultFile.exists()) {
            return defaultFile;
        }

        File activeFile = new File(Util.getResourcePath(normalizedPath));
        return activeFile.exists() ? activeFile : null;
    }

    void copyModelResourceToDeploy(File deployFolder, ResourceSetup res) {
        if (deployFolder == null || res == null || res.path == null || res.path.isBlank()) {
            return;
        }

        String normalizedPath = res.path.replace("\\", "/");
        File sourceFile = resolveResourceFile(normalizedPath);
        if (sourceFile == null || !sourceFile.isFile()) {
            throw new RuntimeException("Missing model file for resource '" + res.name + "': " + normalizedPath);
        }

        File targetFile = new File(deployFolder, normalizedPath);
        copyFileIfNeeded(sourceFile, targetFile);

        String lowerName = sourceFile.getName().toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".gltf")) {
            copyGltfExternalDependencies(sourceFile, targetFile);
        } else if (lowerName.endsWith(".j3o")) {
            copyJ3oSidecars(sourceFile, targetFile);
        }
    }

    private void copyGltfExternalDependencies(File sourceGltf, File targetGltf) {
        try {
            JSONObject gltf = new JSONObject(FileUtils.readFileToString(sourceGltf, StandardCharsets.UTF_8));
            copyGltfUriArray(sourceGltf, targetGltf, gltf.optJSONArray("buffers"));
            copyGltfUriArray(sourceGltf, targetGltf, gltf.optJSONArray("images"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to copy GLTF dependencies for " + sourceGltf.getAbsolutePath(), e);
        }
    }

    private void copyGltfUriArray(File sourceGltf, File targetGltf, JSONArray array) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject entry = array.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            String uri = entry.optString("uri", "").trim();
            if (uri.isEmpty() || uri.startsWith("data:") || uri.contains("://")) {
                continue;
            }
            File source = new File(sourceGltf.getParentFile(), uri.replace('/', File.separatorChar));
            if (!source.isFile()) {
                continue;
            }
            File target = new File(targetGltf.getParentFile(), uri.replace('/', File.separatorChar));
            copyFileIfNeeded(source, target);
        }
    }

    private void copyJ3oSidecars(File sourceJ3o, File targetJ3o) {
        copySiblingModelSidecars(sourceJ3o, targetJ3o);

        File texturesRoot = new File(sourceJ3o.getParentFile(), "textures");
        if (!texturesRoot.isDirectory()) {
            return;
        }

        String baseName = stripExtension(sourceJ3o.getName());
        File matchingTextureDir = new File(texturesRoot, baseName);
        if (matchingTextureDir.isDirectory()) {
            copyDirectoryIfNeeded(matchingTextureDir, new File(new File(targetJ3o.getParentFile(), "textures"), baseName));
            return;
        }

        File[] rootFiles = texturesRoot.listFiles(File::isFile);
        if (rootFiles == null) {
            return;
        }
        File targetTexturesRoot = new File(targetJ3o.getParentFile(), "textures");
        for (File rootFile : rootFiles) {
            copyFileIfNeeded(rootFile, new File(targetTexturesRoot, rootFile.getName()));
        }
    }

    private void copyResourceFileToDeploy(File deployFolder, String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return;
        }
        File sourceFile = resolveResourceFile(resourcePath);
        if (sourceFile == null || !sourceFile.isFile()) {
            return;
        }
        copyFileIfNeeded(sourceFile, new File(deployFolder, resourcePath.replace("\\", "/")));
    }

    private void copySiblingModelSidecars(File sourceModel, File targetModel) {
        File sourceFolder = sourceModel.getParentFile();
        File targetFolder = targetModel.getParentFile();
        if (sourceFolder == null || targetFolder == null || !sourceFolder.isDirectory()) {
            return;
        }

        File[] sidecars = sourceFolder.listFiles(File::isFile);
        if (sidecars == null) {
            return;
        }

        String sourceName = sourceModel.getName();
        for (File sidecar : sidecars) {
            if (sourceName.equals(sidecar.getName()) || !isModelSidecarFile(sidecar)) {
                continue;
            }
            copyFileIfNeeded(sidecar, new File(targetFolder, sidecar.getName()));
        }
    }

    private boolean isModelSidecarFile(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".material")
                || name.endsWith(".j3m")
                || name.endsWith(".j3md")
                || name.endsWith(".j3odata")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".png")
                || name.endsWith(".dds")
                || name.endsWith(".tga")
                || name.endsWith(".bmp")
                || name.endsWith(".gif")
                || name.endsWith(".webp");
    }

    private void copyFileIfNeeded(File source, File target) {
        try {
            File parent = target.getParentFile();
            if (parent != null) {
                FileUtils.forceMkdir(parent);
            }
            if (!target.exists() || source.length() != target.length()) {
                Files.copy(source.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy " + source.getAbsolutePath() + " to " + target.getAbsolutePath(), e);
        }
    }

    private void copyDirectoryIfNeeded(File sourceDir, File targetDir) {
        try {
            FileUtils.copyDirectory(sourceDir, targetDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy " + sourceDir.getAbsolutePath() + " to " + targetDir.getAbsolutePath(), e);
        }
    }

    private Set<String> collectStandaloneAssetCandidateNames(String extension, String... folderNames) {
        Set<String> names = new LinkedHashSet<>();
        for (StandaloneAssetFile assetFile : collectStandaloneAssetFiles(extension, folderNames)) {
            names.addAll(assetFile.candidateNames);
        }
        return names;
    }

    private Set<String> collectIKStandaloneAssetCandidateNames() {
        Set<String> names = new LinkedHashSet<>();
        for (StandaloneAssetFile assetFile : collectIKStandaloneAssetFiles()) {
            names.addAll(assetFile.candidateNames);
        }
        return names;
    }

    private List<StandaloneAssetFile> collectIKStandaloneAssetFiles() {
        List<StandaloneAssetFile> files = new ArrayList<>();
        for (File root : collectIKStandaloneAssetRoots()) {
            Collection<File> found = FileUtils.listFiles(root, new String[]{"smik", "json"}, true);
            for (File file : found) {
                if (!isIKStandaloneAssetFile(file)) {
                    continue;
                }
                files.add(new StandaloneAssetFile(root, file, collectStandaloneAssetCandidateNames(file)));
            }
        }
        return files;
    }

    private List<StandaloneAssetFile> collectStandaloneAssetFiles(String extension, String... folderNames) {
        List<StandaloneAssetFile> files = new ArrayList<>();
        for (File root : collectStandaloneAssetRoots(folderNames)) {
            Collection<File> found = FileUtils.listFiles(root, new String[]{extension}, true);
            for (File file : found) {
                files.add(new StandaloneAssetFile(root, file, collectStandaloneAssetCandidateNames(file)));
            }
        }
        return files;
    }

    private List<File> collectStandaloneAssetRoots(String... folderNames) {
        LinkedHashSet<File> roots = new LinkedHashSet<>();
        File projectResources = getPackagedProjectResourcesFolder();
        File defaultResources = new File(Util.getDefaultResourcesFolder());
        for (String folderName : folderNames) {
            addStandaloneAssetRoot(roots, projectResources == null ? null : new File(projectResources, folderName));
            addStandaloneAssetRoot(roots, new File(defaultResources, folderName));
        }
        return new ArrayList<>(roots);
    }

    private List<File> collectIKStandaloneAssetRoots() {
        LinkedHashSet<File> roots = new LinkedHashSet<>();
        for (File root : collectStandaloneAssetRoots("ik", "IK")) {
            addStandaloneAssetRoot(roots, root);
        }
        addStandaloneAssetRoot(roots, scriptFolder);
        return new ArrayList<>(roots);
    }

    private void addStandaloneAssetRoot(Set<File> roots, File root) {
        if (root == null || !root.isDirectory()) {
            return;
        }
        try {
            roots.add(root.getCanonicalFile());
        } catch (IOException ignored) {
            roots.add(root.getAbsoluteFile());
        }
    }

    private Set<String> collectStandaloneAssetCandidateNames(File file) {
        Set<String> names = new LinkedHashSet<>();
        names.add(stripStandaloneAssetExtension(file.getName()));
        try {
            JSONObject root = new JSONObject(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
            addIfNotBlank(names, root.optString("id", ""));
            addIfNotBlank(names, root.optString("displayName", ""));
            addIfNotBlank(names, root.optString("name", ""));
            JSONObject metadata = root.optJSONObject("designerMetadata");
            if (metadata != null) {
                addIfNotBlank(names, metadata.optString("displayName", ""));
                addIfNotBlank(names, metadata.optString("name", ""));
            }
        } catch (Exception ignored) {
        }
        return names;
    }

    private void copyReferencedStandaloneAssetsToDeploy(List<StandaloneAssetFile> assetFiles, Set<String> usedNames,
                                                        File targetDir, String extension) {
        if (assetFiles == null || assetFiles.isEmpty() || usedNames == null || usedNames.isEmpty()) {
            return;
        }
        try {
            FileUtils.forceMkdir(targetDir);
            for (StandaloneAssetFile assetFile : assetFiles) {
                List<String> matchedNames = matchedStandaloneAssetNames(assetFile, usedNames);
                if (matchedNames.isEmpty()) {
                    continue;
                }

                File relativeTarget = new File(targetDir, assetFile.root.toURI().relativize(assetFile.file.toURI()).getPath());
                File relativeParent = relativeTarget.getParentFile();
                if (relativeParent != null) {
                    FileUtils.forceMkdir(relativeParent);
                }
                FileUtils.copyFile(assetFile.file, relativeTarget);
                FileUtils.copyFile(assetFile.file, new File(targetDir, assetFile.file.getName()));
                for (String matchedName : matchedNames) {
                    File aliasTarget = new File(targetDir, matchedName + "." + extension);
                    if (!aliasTarget.getCanonicalFile().equals(assetFile.file.getCanonicalFile())
                            && !aliasTarget.getCanonicalFile().equals(relativeTarget.getCanonicalFile())) {
                        FileUtils.copyFile(assetFile.file, aliasTarget);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> matchedStandaloneAssetNames(StandaloneAssetFile assetFile, Set<String> usedNames) {
        List<String> matches = new ArrayList<>();
        for (String usedName : usedNames) {
            for (String candidateName : assetFile.candidateNames) {
                if (usedName.equalsIgnoreCase(candidateName)) {
                    matches.add(usedName);
                    break;
                }
            }
        }
        return matches;
    }

    private String stripExtension(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    private String stripStandaloneAssetExtension(String name) {
        if (name == null) {
            return "";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".ik.json")) {
            return name.substring(0, name.length() - ".ik.json".length());
        }
        return stripExtension(name);
    }

    private boolean isIKStandaloneAssetFile(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        String lower = file.getName().toLowerCase(Locale.ROOT);
        return lower.endsWith(".smik") || lower.endsWith(".ik.json");
    }

    private static final class StandaloneAssetFile {
        private final File root;
        private final File file;
        private final Set<String> candidateNames;

        private StandaloneAssetFile(File root, File file, Set<String> candidateNames) {
            this.root = root;
            this.file = file;
            this.candidateNames = candidateNames;
        }
    }

    private static final class SizeReportRow {
        private final String label;
        private final String path;
        private final long bytes;
        private final int count;

        private SizeReportRow(String label, String path, long bytes, int count) {
            this.label = label == null ? "" : label;
            this.path = path == null ? "" : path;
            this.bytes = bytes;
            this.count = count;
        }

        private JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("label", label);
            json.put("path", path);
            json.put("bytes", bytes);
            json.put("miB", Math.round((bytes / 1024.0 / 1024.0) * 100.0) / 100.0);
            json.put("count", count);
            return json;
        }
    }

    private void copyResourceDirectoryToDeploy(String relativePath) {
        File defaultDir = new File("./resources", relativePath);
        File projectResources = getPackagedProjectResourcesFolder();
        File projectDir = projectResources == null ? null : new File(projectResources, relativePath);
        File deployDir = new File("./deploy", relativePath);

        copyDirectoryContents(defaultDir, deployDir);
        copyDirectoryContents(projectDir, deployDir);
    }

    private void appendCinematicResources(JSONArray targetArray) {
        File projectRoot = getPackagedProjectRoot();
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return;
        }

        for (String designerPath : scannedDesignerFiles) {
            if (designerPath == null || designerPath.isBlank()) {
                continue;
            }
            File designerFile = new File(designerPath);
            if (!designerFile.isFile()) {
                continue;
            }
            String raw;
            try {
                raw = FileUtils.readFileToString(designerFile, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            if (raw == null || raw.isBlank()) {
                continue;
            }

            try {
                JSONObject root = new JSONObject(raw);
                appendCinematicResourcesRecursive(projectRoot, designerFile, root.optJSONArray("entities"), raw, targetArray);
            } catch (Exception ignored) {
            }
        }
    }

    private JSONObject buildPackagingInventory(JSONObject packagedResources, AssetsMapping assetsMapping) {
        JSONObject inventory = new JSONObject();
        inventory.put("scriptRoot", scriptFolder == null ? JSONObject.NULL : scriptFolder.getAbsolutePath());
        File projectRoot = getPackagedProjectRoot();
        inventory.put("projectRoot", projectRoot == null ? JSONObject.NULL : projectRoot.getAbsolutePath());
        File projectResources = getPackagedProjectResourcesFolder();
        inventory.put("resourcesRoot", projectResources == null ? JSONObject.NULL : projectResources.getAbsolutePath());
        inventory.put("scannedScriptFiles", toRelativeJsonArray(scannedScriptFiles, scriptFolder));
        inventory.put("scannedDesignerFiles", toRelativeJsonArray(scannedDesignerFiles, projectRoot));
        inventory.put("scannedUiFiles", toRelativeJsonArray(reachableUiFiles, scriptFolder));
        inventory.put("uiImagePaths", toSortedJsonArray(uiReferencedImagePaths));
        List<String> packageTargets = new ArrayList<>();
        for (PackageTarget target : targets) {
            packageTargets.add(target.name());
        }
        inventory.put("packageTargets", toSortedJsonArray(packageTargets));

        JSONObject referenced = new JSONObject();
        referenced.put("models", toSortedJsonArray(SceneMaxLanguageParser.modelsUsed));
        referenced.put("sprites", toSortedJsonArray(SceneMaxLanguageParser.spriteSheetUsed));
        referenced.put("effects", toSortedJsonArray(SceneMaxLanguageParser.effekseerUsed));
        referenced.put("videos", toSortedJsonArray(SceneMaxLanguageParser.videoUsed));
        referenced.put("audio", toSortedJsonArray(SceneMaxLanguageParser.audioUsed));
        referenced.put("fonts", toSortedJsonArray(SceneMaxLanguageParser.fontsUsed));
        referenced.put("skyboxes", toSortedJsonArray(SceneMaxLanguageParser.skyboxUsed));
        referenced.put("terrains", toSortedJsonArray(SceneMaxLanguageParser.terrainsUsed));
        referenced.put("animations", toSortedJsonArray(animationNamesUsed));
        referenced.put("shaders", toSortedJsonArray(shaderNamesUsed));
        referenced.put("environmentShaders", toSortedJsonArray(environmentShaderNamesUsed));
        referenced.put("materials", toSortedJsonArray(materialNamesUsed));
        referenced.put("builtInMaterials", toSortedJsonArray(builtInMaterialNamesUsed));
        referenced.put("weapons", toSortedJsonArray(weaponAssetNamesUsed));
        referenced.put("throwMotions", toSortedJsonArray(throwMotionAssetNamesUsed));
        referenced.put("ik", toSortedJsonArray(ikAssetNamesUsed));
        inventory.put("referencedResources", referenced);

        JSONObject missing = new JSONObject();
        missing.put("models", findMissingIndexedResources(SceneMaxLanguageParser.modelsUsed, assetsMapping.get3DModelsIndex()));
        missing.put("sprites", findMissingIndexedResources(SceneMaxLanguageParser.spriteSheetUsed, assetsMapping.getSpriteSheetsIndex()));
        missing.put("audio", findMissingIndexedResources(SceneMaxLanguageParser.audioUsed, assetsMapping.getAudioIndex()));
        missing.put("fonts", findMissingIndexedResources(SceneMaxLanguageParser.fontsUsed, assetsMapping.getFontsIndex()));
        missing.put("skyboxes", findMissingIndexedResources(SceneMaxLanguageParser.skyboxUsed, assetsMapping.getSkyboxesIndex()));
        missing.put("terrains", findMissingIndexedResources(SceneMaxLanguageParser.terrainsUsed, assetsMapping.getTerrainsIndex()));
        missing.put("effects", findMissingEffects(SceneMaxLanguageParser.effekseerUsed));
        missing.put("videos", findMissingIndexedResources(normalizeVideoResourceNames(), assetsMapping.getVideosIndex()));
        missing.put("uiImages", findMissingUiImages(uiReferencedImagePaths));
        missing.put("animations", findMissingIndexedResources(animationNamesUsed, assetsMapping.getAnimationsIndex()));
        missing.put("shaders", findMissingIndexedResources(shaderNamesUsed, assetsMapping.getShadersIndex()));
        missing.put("environmentShaders", findMissingIndexedResources(environmentShaderNamesUsed, assetsMapping.getShadersIndex()));
        missing.put("materials", findMissingIndexedResources(materialNamesUsed, assetsMapping.getMaterialsIndex()));
        missing.put("weapons", findMissingStandaloneAssets(weaponAssetNamesUsed, "smweapon", "weapons", "Weapons"));
        missing.put("throwMotions", findMissingStandaloneAssets(throwMotionAssetNamesUsed, "smmotion", "throw_motions", "ThrowMotions"));
        missing.put("ik", findMissingIKStandaloneAssets(ikAssetNamesUsed));
        inventory.put("missingResources", missing);

        inventory.put("packagedResources", new JSONObject(packagedResources.toString()));
        return inventory;
    }

    private JSONArray toRelativeJsonArray(List<String> absolutePaths, File root) {
        List<String> values = new ArrayList<>();
        for (String absolutePath : absolutePaths) {
            values.add(toRelativePath(absolutePath, root));
        }
        Collections.sort(values);
        JSONArray array = new JSONArray();
        for (String value : values) {
            array.put(value);
        }
        return array;
    }

    private String toRelativePath(String absolutePath, File root) {
        if (absolutePath == null || absolutePath.isBlank()) {
            return "";
        }
        if (root == null) {
            return absolutePath;
        }

        try {
            String rootPath = root.getCanonicalFile().toURI().toString();
            String targetPath = new File(absolutePath).getCanonicalFile().toURI().toString();
            if (targetPath.startsWith(rootPath)) {
                return root.toURI().relativize(new File(absolutePath).toURI()).getPath();
            }
        } catch (IOException ignored) {
        }

        return absolutePath;
    }

    private JSONArray toSortedJsonArray(Collection<String> values) {
        List<String> sorted = new ArrayList<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!sorted.contains(trimmed)) {
                sorted.add(trimmed);
            }
        }
        Collections.sort(sorted);
        JSONArray array = new JSONArray();
        for (String value : sorted) {
            array.put(value);
        }
        return array;
    }

    private JSONArray findMissingIndexedResources(Collection<String> names, Map<String, ?> index) {
        List<String> missing = new ArrayList<>();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            if (index == null || !index.containsKey(name.toLowerCase(Locale.ROOT))) {
                if (!missing.contains(name)) {
                    missing.add(name);
                }
            }
        }
        Collections.sort(missing);
        JSONArray array = new JSONArray();
        for (String name : missing) {
            array.put(name);
        }
        return array;
    }

    private JSONArray findMissingEffects(Collection<String> effectNames) {
        List<String> missing = new ArrayList<>();
        for (String effectName : effectNames) {
            if (effectName == null || effectName.isBlank()) {
                continue;
            }
            String assetId = effectName;
            String prefix = "effects.effekseer.";
            if (assetId.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                assetId = assetId.substring(prefix.length());
            }
            File sourceDir = resolveEffekseerEffectSource(assetId);
            if (!sourceDir.isDirectory() && !missing.contains(effectName)) {
                missing.add(effectName);
            }
        }
        Collections.sort(missing);
        JSONArray array = new JSONArray();
        for (String name : missing) {
            array.put(name);
        }
        return array;
    }

    private JSONArray findMissingUiImages(Collection<String> imagePaths) {
        List<String> missing = new ArrayList<>();
        for (String imagePath : imagePaths) {
            File resolved = resolveUiImageFile(imagePath);
            if ((resolved == null || !resolved.isFile()) && !missing.contains(imagePath)) {
                missing.add(imagePath);
            }
        }
        Collections.sort(missing);
        JSONArray array = new JSONArray();
        for (String name : missing) {
            array.put(name);
        }
        return array;
    }

    private JSONArray findMissingStandaloneAssets(Collection<String> names, String extension, String... folderNames) {
        List<String> missing = new ArrayList<>();
        List<StandaloneAssetFile> assetFiles = collectStandaloneAssetFiles(extension, folderNames);
        appendMissingStandaloneAssets(names, assetFiles, missing);
        Collections.sort(missing);
        JSONArray array = new JSONArray();
        for (String name : missing) {
            array.put(name);
        }
        return array;
    }

    private JSONArray findMissingIKStandaloneAssets(Collection<String> names) {
        List<String> missing = new ArrayList<>();
        appendMissingStandaloneAssets(names, collectIKStandaloneAssetFiles(), missing);
        Collections.sort(missing);
        JSONArray array = new JSONArray();
        for (String name : missing) {
            array.put(name);
        }
        return array;
    }

    private void appendMissingStandaloneAssets(Collection<String> names, List<StandaloneAssetFile> assetFiles,
                                               List<String> missing) {
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            boolean found = false;
            for (StandaloneAssetFile assetFile : assetFiles) {
                for (String candidateName : assetFile.candidateNames) {
                    if (name.equalsIgnoreCase(candidateName)) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    break;
                }
            }
            if (!found && !missing.contains(name)) {
                missing.add(name);
            }
        }
    }

    private void appendCinematicResourcesRecursive(File projectRoot, File designerFile, JSONArray entities, String documentBuffer, JSONArray targetArray) {
        if (entities == null) {
            return;
        }

        for (int i = 0; i < entities.length(); i++) {
            JSONObject entity = entities.optJSONObject(i);
            if (entity == null) {
                continue;
            }

            if ("CINEMATIC_RIG".equals(entity.optString("type", ""))) {
                String runtimeId = entity.optString("cinematicRuntimeId", entity.optString("id", "")).toLowerCase(Locale.ROOT);
                if (!runtimeId.isBlank()) {
                    JSONObject resource = new JSONObject();
                    resource.put("name", runtimeId);
                    resource.put("runtimeId", runtimeId);
                    resource.put("sourcePath", projectRoot.toURI().relativize(designerFile.toURI()).getPath());
                    resource.put("jsonBuffer", new JSONObject(entity.toString()));
                    resource.put("documentBuffer", documentBuffer);
                    upsertIndexedResource(targetArray, resource);
                }
            }

            appendCinematicResourcesRecursive(projectRoot, designerFile, entity.optJSONArray("children"), documentBuffer, targetArray);
        }
    }

    private void copyDirectoryContents(File sourceDir, File targetDir) {
        if (sourceDir == null || !sourceDir.exists() || !sourceDir.isDirectory()) {
            return;
        }

        try {
            FileUtils.forceMkdir(targetDir);
            File[] children = sourceDir.listFiles();
            if (children == null) {
                return;
            }

            for (File child : children) {
                File targetChild = new File(targetDir, child.getName());
                if (child.isDirectory()) {
                    FileUtils.copyDirectory(child, targetChild);
                } else {
                    FileUtils.copyFile(child, targetChild);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void copyStandaloneAssetFilesToDeploy(File sourceRoot, File targetDir, String extension, boolean replaceExisting) {
        if (sourceRoot == null || !sourceRoot.exists() || !sourceRoot.isDirectory()) {
            return;
        }
        if (extension == null || extension.isBlank()) {
            return;
        }

        try {
            FileUtils.forceMkdir(targetDir);
            Collection<File> files = FileUtils.listFiles(sourceRoot, new String[]{extension}, true);
            for (File file : files) {
                File targetFile = new File(targetDir, file.getName());
                if (targetFile.exists() && !replaceExisting) {
                    continue;
                }
                FileUtils.copyFile(file, targetFile);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void mergeIndexedResources(File baseIndexFile, File projectIndexFile, String arrayKey, JSONArray targetArray) {
        mergeIndexedResourcesFromFile(baseIndexFile, arrayKey, targetArray);
        mergeIndexedResourcesFromFile(projectIndexFile, arrayKey, targetArray);
    }

    private void mergeIndexedResourcesFromFile(File indexFile, String arrayKey, JSONArray targetArray) {
        if (indexFile == null || !indexFile.exists()) {
            return;
        }

        try {
            String content = FileUtils.readFileToString(indexFile, StandardCharsets.UTF_8);
            if (content == null || content.trim().length() == 0) {
                return;
            }

            JSONObject root = new JSONObject(content);
            JSONArray sourceArray = root.optJSONArray(arrayKey);
            if (sourceArray == null) {
                return;
            }

            for (int i = 0; i < sourceArray.length(); i++) {
                JSONObject resource = sourceArray.optJSONObject(i);
                if (resource == null) {
                    continue;
                }
                upsertIndexedResource(targetArray, resource);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void upsertIndexedResource(JSONArray targetArray, JSONObject resource) {
        String name = resource.optString("name", "").toLowerCase();
        String path = resource.optString("path", "").toLowerCase();

        for (int i = 0; i < targetArray.length(); i++) {
            JSONObject existing = targetArray.optJSONObject(i);
            if (existing == null) {
                continue;
            }

            String existingName = existing.optString("name", "").toLowerCase();
            String existingPath = existing.optString("path", "").toLowerCase();
            if ((!name.isEmpty() && name.equals(existingName)) || (!path.isEmpty() && path.equals(existingPath))) {
                for (String key : resource.keySet()) {
                    existing.put(key, resource.get(key));
                }
                return;
            }
        }

        targetArray.put(new JSONObject(resource.toString()));
    }

    private void collectUiDocumentReferences(List<String> uiFiles) {
        if (uiFiles == null || uiFiles.isEmpty()) {
            return;
        }

        for (String path : uiFiles) {
            if (path == null || path.isBlank()) {
                continue;
            }
            File uiFile = new File(path);
            if (!uiFile.isFile()) {
                continue;
            }
            try {
                UIDocument document = UIDocument.load(uiFile);
                for (UILayerDef layer : document.getLayers()) {
                    for (UIWidgetDef widget : layer.getWidgets()) {
                        collectUiWidgetReferences(widget);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void collectUiWidgetReferences(UIWidgetDef widget) {
        if (widget == null) {
            return;
        }

        addIfNotBlank(uiReferencedFontNames, widget.getFontName());
        addIfNotBlank(uiReferencedSpriteNames, widget.getSpriteName());
        addIfNotBlank(uiReferencedImagePaths, widget.getImagePath());
        addIfNotBlank(uiReferencedImagePaths, widget.getBackgroundImage());

        for (UIWidgetDef child : widget.getChildren()) {
            collectUiWidgetReferences(child);
        }
    }

    private void addIfNotBlank(Set<String> target, String value) {
        if (value == null) {
            return;
        }

        String trimmed = value.trim();
        if (trimmed.length() == 0) {
            return;
        }

        target.add(trimmed);
    }

    private void copyUiImageResource(String imagePath) {
        File directFile = resolveUiImageFile(imagePath);
        if (directFile == null || !directFile.exists() || !directFile.isFile()) {
            return;
        }

        String normalized = imagePath.replace("\\", "/").trim();
        String deployRelativePath = inferDeployRelativePath(normalized, directFile);
        File targetFile = new File("./deploy", deployRelativePath);
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try {
            FileUtils.copyFile(directFile, targetFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private File resolveUiImageFile(String imagePath) {
        if (imagePath == null) {
            return null;
        }

        String normalized = imagePath.replace("\\", "/").trim();
        if (normalized.length() == 0) {
            return null;
        }

        File directFile = new File(normalized);
        if (!directFile.exists()) {
            directFile = new File(Util.getResourcePath(normalized));
        }

        return directFile;
    }

    private String inferDeployRelativePath(String originalPath, File resolvedFile) {
        String normalized = originalPath.replace("\\", "/");
        if (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("resources/")) {
            normalized = normalized.substring("resources/".length());
        }

        if (normalized.length() > 0 && !normalized.contains(":")) {
            return normalized;
        }

        String absolute = resolvedFile.getAbsolutePath().replace("\\", "/");
        Map<String, String> roots = new LinkedHashMap<>();
        File projectResources = getPackagedProjectResourcesFolder();
        if (projectResources != null) {
            roots.put(projectResources.getAbsolutePath().replace("\\", "/"), "");
        }
        roots.put(new File("./resources").getAbsolutePath().replace("\\", "/"), "");

        for (Map.Entry<String, String> entry : roots.entrySet()) {
            String root = entry.getKey();
            if (absolute.startsWith(root + "/")) {
                return absolute.substring(root.length() + 1);
            }
        }

        return resolvedFile.getName();
    }


    private void copyFileFromResourceToDeploy(String path) {

        File src = new File(path);
        String destPath = path.replaceFirst("(.+?)/resources/","./deploy/");
        File destFolder = new File(destPath).getParentFile();
        if(!destFolder.exists()) {
            destFolder.mkdirs();
        }

        File dest = new File(destPath);
        try {
            Files.copy(src.toPath(), dest.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private String getModelDestPath(File src) {

        String folder=src.getParentFile().getPath();
        int start = folder.indexOf("Models");
        folder="./deploy/"+folder.substring(start);

        return folder;
        //"./deploy/Models/"+src.getParentFile().getName()


    }

    private void copyFile(String source,String destFolder) {

        try {
            File src = new File(source);
            String dest = destFolder+"/"+source.replaceFirst("\\./resources/","");
            File targetFile = new File(dest);
            targetFile.getParentFile().mkdirs();
            if(!targetFile.exists()) {
                Files.copy(src.toPath(), targetFile.toPath());
            }
        }catch(Exception e) {
            e.printStackTrace();
        }

    }


    private JSONObject createFontResourceEntity(String name, String path) {
        JSONObject obj = new JSONObject();
        obj.put("name",name);
        obj.put("path",path);

        return obj;
    }

    private JSONObject createAudioResourceEntity(String name, String path) {
        JSONObject obj = new JSONObject();
        obj.put("name",name);
        obj.put("path",path);

        return obj;
    }

    private JSONObject createSpriteResourceEntity(String name, String fileName, String parentFolder, ResourceSetup2D res) {
        JSONObject obj = new JSONObject();
        obj.put("name",name);
        obj.put("path",parentFolder+"/"+fileName);
        obj.put("cols",res.cols);
        obj.put("rows",res.rows);

        return obj;
    }

    @Override
    public void done() {

        if(this.isCancelled()) {
            if (failureMessage == null || failureMessage.trim().length() == 0) {
                failureMessage = appendPackageLogPath("Packaging was canceled before it completed.");
            }
            logPackage("Packaging canceled.");
            this.canceled.run();
        } else {
            try {
                get();
                finish.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failureMessage = appendPackageLogPath("Packaging was interrupted.");
                logPackageException("Packaging was interrupted.", e);
                this.canceled.run();
            } catch (ExecutionException e) {
                e.printStackTrace();
                Throwable cause = e.getCause() == null ? e : e.getCause();
                String message = cause.getMessage();
                if (failureMessage == null || failureMessage.trim().length() == 0) {
                    failureMessage = appendPackageLogPath(message == null || message.trim().length() == 0 ? cause.toString() : message.trim());
                }
                logPackageException("Packaging failed.", cause);
                this.canceled.run();
            }
        }
    }

    public List<File> getProducedArtifacts() {
        return Collections.unmodifiableList(producedArtifacts);
    }

    public File getOutputFolder() {
        return outputFolder == null ? new File("build_games") : outputFolder;
    }

    public String getCompletionNotes() {
        return completionNotes.toString();
    }

    public String getStatusNote() {
        return statusNote;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public File getPackageLogFile() {
        return packageLogFile;
    }

    private String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String escapeHtml(String value) {
        return escapeXml(value).replace("\"", "&quot;");
    }

    private void copyPlatformIcon(File sourceIcon, File targetFolder, String targetBaseName) throws IOException {
        if (sourceIcon == null || !sourceIcon.exists() || !sourceIcon.isFile()) {
            return;
        }

        String name = sourceIcon.getName();
        int idx = name.lastIndexOf('.');
        String ext = idx >= 0 ? name.substring(idx) : "";
        File target = new File(targetFolder, targetBaseName + ext);
        FileUtils.copyFile(sourceIcon, target);
    }

    private File createPlatformZip(File platformFolder, String zipFileName) throws IOException {
        if (platformFolder == null || !platformFolder.exists() || !platformFolder.isDirectory()) {
            return null;
        }

        File zipFile = new File(platformFolder, zipFileName);
        if (zipFile.exists()) {
            zipFile.delete();
        }

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            File[] children = platformFolder.listFiles();
            if (children == null) {
                return zipFile;
            }
            for (File child : children) {
                if (child.equals(zipFile)) {
                    continue;
                }
                addToZip(child, child.getName(), zos);
            }
        }

        return zipFile;
    }

    private void deletePlatformArtifactsExceptZip(File platformFolder, File zipFile) throws IOException {
        if (platformFolder == null || !platformFolder.exists() || !platformFolder.isDirectory()) {
            return;
        }

        File[] children = platformFolder.listFiles();
        if (children == null) {
            return;
        }

        for (File child : children) {
            if (zipFile != null && child.equals(zipFile)) {
                continue;
            }
            if (child.isDirectory()) {
                FileUtils.deleteDirectory(child);
            } else {
                child.delete();
            }
        }
    }

    private void addToZip(File source, String entryName, ZipOutputStream zos) throws IOException {
        String normalized = entryName.replace("\\", "/");
        if (source.isDirectory()) {
            if (!normalized.endsWith("/")) {
                normalized += "/";
            }
            zos.putNextEntry(new ZipEntry(normalized));
            zos.closeEntry();
            File[] children = source.listFiles();
            if (children != null) {
                for (File child : children) {
                    addToZip(child, normalized + child.getName(), zos);
                }
            }
            return;
        }

        zos.putNextEntry(new ZipEntry(normalized));
        try (InputStream in = new FileInputStream(source)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                zos.write(buffer, 0, read);
            }
        }
        zos.closeEntry();
    }

    private void addClass(Class c, JarOutputStream jarOutputStream) throws IOException
    {
        String path = c.getName().replace('.', '/') + ".class";
        jarOutputStream.putNextEntry(new JarEntry(path));
        jarOutputStream.write(Util.toByteArray(c.getClassLoader().getResourceAsStream(path)));
        jarOutputStream.closeEntry();
    }

    private void addSingleFile(File source, JarOutputStream target) throws IOException {

        BufferedInputStream in = null;
        try
        {
            JarEntry entry = new JarEntry(source.getName());

            entry.setTime(source.lastModified());
            target.putNextEntry(entry);
            in = new BufferedInputStream(new FileInputStream(source));

            byte[] buffer = new byte[1024];
            while (true)
            {
                int count = in.read(buffer);
                if (count == -1)
                    break;
                target.write(buffer, 0, count);
            }
            target.closeEntry();

            advanceProgressUnit();

        } catch(Exception e) {
            e.printStackTrace();
        }
        finally
        {
            if (in != null) {
                in.close();
            }
        }


    }

    private void add(File source, JarOutputStream target) throws IOException
    {
        String path = source.getPath().replace("\\", "/");
        BufferedInputStream in = null;
        try
        {
            if (source.isDirectory())
            {
                String name = source.getPath().replace("\\", "/");
                if (!name.isEmpty())
                {
                    if(!name.startsWith("deploy")) { // deploy folder & sub folders should not be added
                        if (!name.endsWith("/"))
                            name += "/";
                        if (addedJarEntries.add(name)) {
                            JarEntry entry = new JarEntry(name);
                            entry.setTime(source.lastModified());
                            target.putNextEntry(entry);
                            target.closeEntry();
                        }
                    }
                }
                for (File nestedFile: source.listFiles())
                    add(nestedFile, target);
                return;
            }

            String entryName = path.replace("deploy/","");
            if (!addedJarEntries.add(entryName)) {
                return;
            }

            JarEntry entry = new JarEntry(entryName);
                    //.replace("running/",""));

            entry.setTime(source.lastModified());
            target.putNextEntry(entry);
            in = new BufferedInputStream(new FileInputStream(source));

            byte[] buffer = new byte[1024];
            while (true)
            {
                int count = in.read(buffer);
                if (count == -1)
                    break;
                target.write(buffer, 0, count);
            }
            target.closeEntry();

            advanceProgressUnit();

        } catch(Exception e) {
            e.printStackTrace();
        }
        finally
        {
            if (in != null)
                in.close();
        }
    }


    public static List<String> getSoundFiles(int soundType) {
        switch(soundType) {
            case 1:
                return getEngineFiles1();

            case 2:
                return getEngineFiles2();

            case 4:
                return getEngineFiles4();

            case 5:
                return getEngineFiles5();

        }

        return null;
    }

    private static List<String> getEngineFiles1() {
        List<String> retval = new ArrayList<>();
        retval.add("audio/engine-1d2.wav");
        retval.add("audio/engine-1.wav");
        retval.add("audio/engine-1x2.wav");
        retval.add("audio/engine-1x4.wav");

        return retval;
    }

    private static List<String> getEngineFiles2() {
        List<String> retval = new ArrayList<>();
        retval.add("audio/engine-2d2.wav");
        retval.add("audio/engine-2.wav");
        retval.add("audio/engine-2x2.wav");
        retval.add("audio/engine-2x4.wav");

        return retval;
    }

    private static List<String> getEngineFiles4() {
        List<String> retval = new ArrayList<>();
        retval.add("audio/engine-4d8.wav");
        retval.add("audio/engine-4d4.wav");
        retval.add("audio/engine-4d2.wav");
        retval.add("audio/engine-4.wav");
        retval.add("audio/engine-4x2.wav");


        return retval;
    }

    private static List<String> getEngineFiles5() {
        List<String> retval = new ArrayList<>();
        retval.add("audio/engine-5d4.wav");
        retval.add("audio/engine-5d2.wav");
        retval.add("audio/engine-5.wav");
        retval.add("audio/engine-5x2.wav");

        return retval;
    }

}
