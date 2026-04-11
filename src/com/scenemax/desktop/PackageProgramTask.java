package com.scenemax.desktop;

import com.scenemaxeng.common.ui.model.UIDocument;
import com.scenemaxeng.common.ui.model.UILayerDef;
import com.scenemaxeng.common.ui.model.UIWidgetDef;
import com.scenemaxeng.compiler.ApplyMacroResults;
import com.scenemaxeng.compiler.MacroFilter;
import com.scenemaxeng.compiler.ProgramDef;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.scenemaxeng.compiler.SceneMaxLanguageParser.macroFilter;

public class PackageProgramTask extends SwingWorker<Integer, String> {

    private static final float ESTIMATED_FILES_COUNT = 6610;// total files packed in an executable scene
    private static final String SCENE_JAR_NAME = "scenemax3d_scene.jar";
    private File scriptFolder=null;
    private String prg;
    private Runnable finish;
    private Runnable canceled;
    private int globalCounter=0;
    private final EnumSet<PackageTarget> targets;
    private final List<File> producedArtifacts = new ArrayList<>();
    private final Set<String> addedJarEntries = new LinkedHashSet<>();
    private final Set<String> uiReferencedSpriteNames = new LinkedHashSet<>();
    private final Set<String> uiReferencedFontNames = new LinkedHashSet<>();
    private final Set<String> uiReferencedImagePaths = new LinkedHashSet<>();
    private final PackageOptions options;
    private File outputFolder;
    private final StringBuilder completionNotes = new StringBuilder();
    private volatile String statusNote = "";

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

        public PackageOptions(File windowsIcon, File linuxIcon, File macIcon,
                              String webBaseUrl, String webVendor, String webHomepage,
                              String webRemoteFolder, boolean uploadWebStart, boolean signWebStart,
                              boolean generateSelfSignedCertificate, File keystoreFile,
                              String keystoreAlias, String keystorePassword, String keyPassword) {
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
        }
    }

    public PackageProgramTask(String scriptFilePath, String prg, List<PackageTarget> targets, PackageOptions options, Runnable finish, Runnable canceled) {
        this.prg=prg;
        this.finish=finish;
        this.canceled=canceled;
        this.options = options == null ? new PackageOptions(null, null, null, "", "", "", "", false, false, false, null, "", "", "") : options;
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


    @Override
    protected Integer doInBackground() throws Exception {

        SceneMaxLanguageParser.modelsUsed = new ArrayList<>();
        SceneMaxLanguageParser.effekseerUsed = new ArrayList<>();
        SceneMaxLanguageParser.spriteSheetUsed = new ArrayList<>();
        SceneMaxLanguageParser.audioUsed = new ArrayList<>();
        SceneMaxLanguageParser.fontsUsed = new ArrayList<>();
        addedJarEntries.clear();
        uiReferencedSpriteNames.clear();
        uiReferencedFontNames.clear();
        uiReferencedImagePaths.clear();
        SceneMaxLanguageParser.parseUsingResource = true; // look for manual resource declarations
        SceneMaxLanguageParser parser = new SceneMaxLanguageParser(null, this.scriptFolder.getAbsolutePath());
        MacroFilter macroFilter = new MacroFilter();
        macroFilter.loadMacroRulesFromMacroFolder(new File("macro"));
        parser.setMacroFilter(macroFilter);
        ProgramDef program = parser.parse(this.prg);
        AssetsMapping assetsMapping = new AssetsMapping(Util.getResourcesFolder());

        JSONObject resources = new JSONObject("{ skyboxes:[], terrains:[], sprites:[],models:[],sounds:[], fonts:[], shaders:[], environmentShaders:[], cinematics:[], animations:[] }");

        File deployFolder = new File("deploy");
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
                    File src = new File(Util.getResourcePath(res.path));
                    File srcDir = src.getParentFile();
                    File destDir = new File(getModelDestPath(src));
                    destDir.mkdirs();
                    try {
                        FileUtils.copyDirectory(srcDir, destDir);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    // for vehicles , we need to copy the wheel model as well
                    if (res.isVehicle) {

                        // add engine audio file
                        List<String> soundFiles = getSoundFiles(Integer.parseInt(res.engine.audio));
                        for (String f : soundFiles) {
                            src = new File(Util.getResourcePath(f));
                            File dest = new File("./deploy/" + f);
                            Files.copy(src.toPath(), dest.toPath());

                        }

                        // add horn audio file
                        src = new File(Util.getResourcePath(res.horn));
                        File dest = new File("./deploy/" + res.horn);
                        Files.copy(src.toPath(), dest.toPath());

                    }


                    JSONObject model = res.toJson();
                    models.put(model);
                }
            } else {
                // resource not exist - abort packaging
                this.cancel(true);
                return 0;
            }
        }

        copyEffekseerResourcesToDeploy(deployFolder);
        copyAnimationResourcesToDeploy(deployFolder, resources.getJSONArray("animations"));

        // copy all materials - in the future, check and copy just what is needed
        File materials = new File("./deploy/Materials");
        FileUtils.copyDirectory(new File(Util.getDefaultResourcesFolder() + "/Materials"), materials);

        // copy all textures - in the future, check skybox & terrain and copy just what is needed
        File textures = new File("./deploy/Textures");

        FileUtils.copyDirectory(new File(Util.getDefaultResourcesFolder() + "/Textures"), textures,
                new FileFilter() {
                    @Override
                    public boolean accept(File f) {
                        if (f.isFile()) {
                            return true;
                        }

                        boolean isFirstLevel = f.getParentFile().getName().equalsIgnoreCase("Textures");
                        if (isFirstLevel && !(f.getName().equalsIgnoreCase("Terrain") ||
                                f.getName().equalsIgnoreCase("Particles") ||
                                f.getName().equalsIgnoreCase("Vehicles"))) {
                            return false;
                        }

                        return true;
                    }
                });


        collectUiDocumentReferences(scriptFolder);

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

        copyResourceDirectoryToDeploy("fonts");
        mergeIndexedResources(
                new File("./resources/fonts/fonts.json"),
                new File(Util.getResourcesFolder(), "fonts/fonts-ext.json"),
                "fonts",
                resources.getJSONArray("fonts")
        );

        copyResourceDirectoryToDeploy("shaders");
        mergeIndexedResources(
                new File("./resources/shaders/shaders.json"),
                new File(Util.getResourcesFolder(), "shaders/shaders-ext.json"),
                "shaders",
                resources.getJSONArray("shaders")
        );

        copyResourceDirectoryToDeploy("environment_shaders");
        mergeIndexedResources(
                new File("./resources/environment_shaders/environment-shaders.json"),
                new File(Util.getResourcesFolder(), "environment_shaders/environment-shaders-ext.json"),
                "environmentShaders",
                resources.getJSONArray("environmentShaders")
        );

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


        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "com.scenemaxeng.projector.SceneMaxLauncher");//SceneMaxLauncher.class.getName()
        JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(SCENE_JAR_NAME), manifest);

        String projectorJarPath = Util.getWorkingDir() + "/out/artifacts/scenemax_win_projector.jar";
        File projectorFile = new File(projectorJarPath);
        JarUtils.addJar(jarOutputStream, "", projectorFile, new Runnable() {
            @Override
            public void run() {
                globalCounter += 1;
                Float progress = globalCounter / ESTIMATED_FILES_COUNT * 100;
                if (progress > 100) {
                    progress = 100f;
                }
                PackageProgramTask.this.setProgress(progress.intValue());
            }
        }, addedJarEntries);


        for (File nestedFile : new File("deploy").listFiles()) {
            add(nestedFile, jarOutputStream);
        }

        if (addedJarEntries.add("resources.json")) {
            jarOutputStream.putNextEntry(new JarEntry("resources.json"));
            jarOutputStream.write(resources.toString().getBytes());
            jarOutputStream.closeEntry();
        }

        File scriptFolderCopy = copyAndApplyMacro(scriptFolder);
        FileUtils.moveDirectory(scriptFolderCopy, new File(deployFolder, "running")); // rename
        scriptFolderCopy = new File(deployFolder, "running");
        add(scriptFolderCopy, jarOutputStream);
        // Close jar
        jarOutputStream.close();
        prepareTargetPackages();

        return globalCounter;
    }

    private File copyAndApplyMacro(File folder) {
        try {
            File deployFolder = new File("deploy");
            FileUtils.copyDirectoryToDirectory(folder, deployFolder);
            File createdFolder = new File(deployFolder, folder.getName());
            Iterator<File> files = FileUtils.iterateFiles(createdFolder,null, true);
            while (files.hasNext()) {
                File curr = files.next();
                String code = FileUtils.readFileToString(curr, StandardCharsets.UTF_8);
                ApplyMacroResults mr = macroFilter.apply(code);
                FileUtils.write(curr,mr.finalPrg,StandardCharsets.UTF_8);
            }

            return createdFolder;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void prepareTargetPackages() throws IOException {
        verifyEffekseerNativeResourcesForSelectedTargets();
        String gameName = getGameName();
        outputFolder = new File("build_games", gameName);
        if (outputFolder.exists()) {
            FileUtils.deleteDirectory(outputFolder);
        }
        FileUtils.forceMkdir(outputFolder);
        producedArtifacts.clear();

        if (targets.contains(PackageTarget.WINDOWS)) {
            File windowsExe = prepareWindowsExecutable(gameName);
            if (windowsExe != null && windowsExe.exists()) {
                producedArtifacts.add(windowsExe);
            }
        }

        if (targets.contains(PackageTarget.LINUX)) {
            File linuxFolder = prepareScriptPackage(gameName, "linux", gameName + ".sh", false);
            File linuxZip = createPlatformZip(linuxFolder, gameName + "_linux.zip");
            if (linuxZip != null && linuxZip.exists()) {
                producedArtifacts.add(linuxZip);
            }
            deletePlatformArtifactsExceptZip(linuxFolder, linuxZip);
        }

        if (targets.contains(PackageTarget.MAC_OSX)) {
            File macFolder = prepareScriptPackage(gameName, "macos", gameName + ".command", true);
            File macZip = createPlatformZip(macFolder, gameName + "_macos.zip");
            if (macZip != null && macZip.exists()) {
                producedArtifacts.add(macZip);
            }
            deletePlatformArtifactsExceptZip(macFolder, macZip);
        }

        if (targets.contains(PackageTarget.WEB_START)) {
            File webFolder = prepareWebStartPackage(gameName);
            if (webFolder != null && webFolder.exists()) {
                producedArtifacts.add(webFolder);
            }
        }
    }

    private File prepareWindowsExecutable(String gameName) throws IOException {
        File buildFolder = new File("build_games");
        if(!buildFolder.exists()) {
            buildFolder.mkdir();
        }

        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-XX:MaxDirectMemorySize=1024m");

        String jvmArch = AppDB.getInstance().getParam("projector_jvm_arch");
        if (jvmArch != null && (jvmArch.equals("64") || jvmArch.equals("32"))) {
            command.add("-d" + jvmArch);
        }

        command.add("-jar");

        String launcherName = "Launch4j\\launch4j.jar";
        command.add(launcherName);
        File launch4jConfig = createLaunch4jConfig(gameName);
        command.add(launch4jConfig.getAbsolutePath());


        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(command);
        File log = new File("package_log.txt");
        if (log.exists()) {
            log.delete();
        }
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(log));
        Process process = null;
        try {
            process = processBuilder.start();
            StreamGobbler sg = new StreamGobbler(process.getInputStream(), System.out::println);
            Executors.newSingleThreadExecutor().submit(sg);
            int exitCode = process.waitFor();
            System.out.printf("Program ended with exitCode %d", exitCode);
            if (exitCode != 0) {
                throw new IOException("Launch4j failed with exit code " + exitCode);
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            if (launch4jConfig.exists()) {
                launch4jConfig.delete();
            }
        }

        File windowsFolder = new File(outputFolder, "windows");
        if (!windowsFolder.exists()) {
            windowsFolder.mkdirs();
        }

        File exePath = new File(windowsFolder, gameName + ".exe");
        if (!exePath.exists()) {
            throw new RuntimeException("Windows executable was not created.");
        }

        copyPlatformIcon(options.windowsIcon, windowsFolder, "icon");
        return exePath;
    }

    private File prepareScriptPackage(String gameName, String platformFolderName, String launcherFileName, boolean macLauncher) throws IOException {
        File platformFolder = new File(outputFolder, platformFolderName);
        FileUtils.forceMkdir(platformFolder);

        File targetJar = new File(platformFolder, gameName + ".jar");
        FileUtils.copyFile(new File(SCENE_JAR_NAME), targetJar);

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
        sb.append("java -XX:MaxDirectMemorySize=1024m -jar \"").append(gameName).append(".jar\"").append(lineBreak);
        if (macLauncher) {
            sb.append("exit $?").append(lineBreak);
        }
        return sb.toString();
    }

    private String createLauncherReadme(String launcherFileName, boolean macLauncher) {
        StringBuilder sb = new StringBuilder();
        sb.append("SceneMax packaged game").append("\n\n");
        sb.append("Requirements: Java 11 or newer installed on the target machine.").append("\n\n");
        if (macLauncher) {
            sb.append("Run: ./").append(launcherFileName).append("\n");
            sb.append("If needed, make it executable first with: chmod +x ").append(launcherFileName).append("\n");
        } else {
            sb.append("Run: ./").append(launcherFileName).append("\n");
            sb.append("If needed, make it executable first with: chmod +x ").append(launcherFileName).append("\n");
        }
        return sb.toString();
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
        setProgress(0);
        try {
            Util.ftpUploadFiles(files, options.webRemoteFolder, new IMonitor() {
                @Override
                public void setNote(String note) {
                    updateStatus(note);
                }

                @Override
                public void setProgress(int progress) {
                    PackageProgramTask.this.setProgress(progress);
                }

                @Override
                public void onEnd() {
                    updateStatus("Upload completed.");
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
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (workingDir != null) {
            processBuilder.directory(workingDir);
        }
        processBuilder.redirectErrorStream(true);
        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException e) {
            throw new IOException("Failed to start " + toolName + ". Make sure it is available in PATH.", e);
        }

        String output;
        try (InputStream in = process.getInputStream()) {
            output = new String(Util.toByteArray(in), StandardCharsets.UTF_8);
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException(toolName + " failed with exit code " + exitCode + (output.trim().length() == 0 ? "" : ": " + output.trim()));
            }
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

    private void copyEffekseerResourcesToDeploy(File deployFolder) {
        File deployEffectsDir = new File(deployFolder, "resources/effects");
        File projectResources = getPackagedProjectResourcesFolder();
        if (projectResources != null) {
            copyDirectoryContents(new File(projectResources, "effects"), deployEffectsDir);
        }

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
                this.cancel(true);
                return;
            }

            try {
                FileUtils.copyDirectory(sourceDir, targetDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void copyAnimationResourcesToDeploy(File deployFolder, JSONArray targetArray) {
        File projectResources = getPackagedProjectResourcesFolder();
        if (projectResources == null) {
            return;
        }

        File projectAnimationsDir = new File(projectResources, "animations");
        File deployAnimationsDir = new File(deployFolder, "animations");
        if (projectAnimationsDir.isDirectory()) {
            try {
                FileUtils.copyDirectory(projectAnimationsDir, deployAnimationsDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        mergeIndexedResourcesFromFile(new File("./resources/animations/animations.json"), "animations", targetArray);
        mergeIndexedResourcesFromFile(new File(projectAnimationsDir, "animations-ext.json"), "animations", targetArray);
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

        Collection<File> designerFiles = FileUtils.listFiles(projectRoot, new String[]{"smdesign"}, true);
        for (File designerFile : designerFiles) {
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

    private void collectUiDocumentReferences(File folder) {
        if (folder == null || !folder.exists()) {
            return;
        }

        Iterator<File> files = FileUtils.iterateFiles(folder, new String[]{"smui"}, true);
        while (files.hasNext()) {
            File uiFile = files.next();
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
        String normalized = imagePath.replace("\\", "/").trim();
        if (normalized.length() == 0) {
            return;
        }

        File directFile = new File(normalized);
        if (!directFile.exists()) {
            directFile = new File(Util.getResourcePath(normalized));
        }

        if (!directFile.exists() || !directFile.isFile()) {
            return;
        }

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
            this.canceled.run();
        } else {
            try {
                get();
                finish.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                this.canceled.run();
            } catch (ExecutionException e) {
                e.printStackTrace();
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

    private File createLaunch4jConfig(String gameName) throws IOException {
        File windowsFolder = new File(outputFolder, "windows");
        if (!windowsFolder.exists()) {
            windowsFolder.mkdirs();
        }

        File configFile = File.createTempFile(gameName + "_launch4j_", ".xml");
        String iconPath = resolveWindowsIconPath();
        String xml =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<launch4jConfig>\n" +
                "  <dontWrapJar>false</dontWrapJar>\n" +
                "  <headerType>gui</headerType>\n" +
                "  <jar>" + escapeXml(new File(SCENE_JAR_NAME).getAbsolutePath()) + "</jar>\n" +
                "  <outfile>" + escapeXml(new File(windowsFolder, gameName + ".exe").getAbsolutePath()) + "</outfile>\n" +
                "  <errTitle></errTitle>\n" +
                "  <cmdLine></cmdLine>\n" +
                "  <chdir>.</chdir>\n" +
                "  <priority>normal</priority>\n" +
                "  <downloadUrl>https://scenemax3d.com/java-run-time-install/</downloadUrl>\n" +
                "  <supportUrl>https://www.scenemax3d.com</supportUrl>\n" +
                "  <stayAlive>false</stayAlive>\n" +
                "  <restartOnCrash>false</restartOnCrash>\n" +
                "  <manifest></manifest>\n" +
                "  <icon>" + escapeXml(iconPath) + "</icon>\n" +
                "  <jre>\n" +
                "    <path>%JAVA_HOME%;%PATH%</path>\n" +
                "    <requiresJdk>false</requiresJdk>\n" +
                "    <requires64Bit>true</requires64Bit>\n" +
                "    <minVersion>11.0.21</minVersion>\n" +
                "    <maxVersion></maxVersion>\n" +
                "    <opt>-XX:MaxDirectMemorySize=1024m</opt>\n" +
                "  </jre>\n" +
                "</launch4jConfig>\n";
        FileUtils.writeStringToFile(configFile, xml, StandardCharsets.UTF_8);
        return configFile;
    }

    private String resolveWindowsIconPath() {
        if (options.windowsIcon != null && options.windowsIcon.exists() && options.windowsIcon.isFile()) {
            String name = options.windowsIcon.getName().toLowerCase();
            if (name.endsWith(".ico")) {
                return options.windowsIcon.getAbsolutePath();
            }
        }
        if (options.windowsIcon != null && options.windowsIcon.exists()) {
            System.out.println("Windows packaging icon must be a .ico file. Falling back to default application icon.");
        }
        File defaultIco = new File("scenemax.ico");
        if (defaultIco.exists()) {
            return defaultIco.getAbsolutePath();
        }
        if (options.windowsIcon != null && options.windowsIcon.exists()) {
            return options.windowsIcon.getAbsolutePath();
        }
        return defaultIco.getAbsolutePath();
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

            globalCounter+=1;
            Float progress=globalCounter/ESTIMATED_FILES_COUNT*100;
            if(progress>100) {
                progress=100f;
            }
            this.setProgress(progress.intValue());

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

            globalCounter+=1;
            Float progress=globalCounter/ESTIMATED_FILES_COUNT*100;
            if(progress>100) {
                progress=100f;
            }
            this.setProgress(progress.intValue());

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
