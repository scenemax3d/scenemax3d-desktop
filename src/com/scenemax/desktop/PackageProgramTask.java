package com.scenemax.desktop;

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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static com.scenemaxeng.compiler.SceneMaxLanguageParser.macroFilter;

public class PackageProgramTask extends SwingWorker<Integer, String> {

    private static final float ESTIMATED_FILES_COUNT = 6610;// total files packed in an executable scene
    private File scriptFolder=null;
    private String prg;
    private Runnable finish;
    private Runnable canceled;
    private int globalCounter=0;

    public PackageProgramTask(String scriptFilePath, String prg, Runnable finish, Runnable canceled) {
        this.prg=prg;
        this.finish=finish;
        this.canceled=canceled;

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
        SceneMaxLanguageParser.spriteSheetUsed = new ArrayList<>();
        SceneMaxLanguageParser.audioUsed = new ArrayList<>();
        SceneMaxLanguageParser.fontsUsed = new ArrayList<>();
        SceneMaxLanguageParser.parseUsingResource = true; // look for manual resource declarations
        SceneMaxLanguageParser parser = new SceneMaxLanguageParser(null, this.scriptFolder.getAbsolutePath());
        MacroFilter macroFilter = new MacroFilter();
        macroFilter.loadMacroRulesFromMacroFolder(new File("macro"));
        parser.setMacroFilter(macroFilter);
        ProgramDef program = parser.parse(this.prg);
        AssetsMapping assetsMapping = new AssetsMapping(Util.getResourcesFolder());

        JSONObject resources = new JSONObject("{ skyboxes:[], terrains:[], sprites:[],models:[],sounds:[], fonts:[] }");

        File deployFolder = new File("deploy");
        FileUtils.deleteDirectory(deployFolder);

        File texDir = new File(deployFolder, "Textures");
        FileUtils.forceMkdir(texDir);

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
        JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream("scenemax3d_scene.jar"), manifest);

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
        });


        for (File nestedFile : new File("deploy").listFiles()) {
            add(nestedFile, jarOutputStream);
        }

        jarOutputStream.putNextEntry(new JarEntry("resources.json"));
        jarOutputStream.write(resources.toString().getBytes());
        jarOutputStream.closeEntry();

        File scriptFolderCopy = copyAndApplyMacro(scriptFolder);
        FileUtils.moveDirectory(scriptFolderCopy, new File(deployFolder, "running")); // rename
        scriptFolderCopy = new File(deployFolder, "running");
        add(scriptFolderCopy, jarOutputStream);
        // Close jar
        jarOutputStream.close();
        //prepareExe();

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

    private void prepareExe() {
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
        command.add(".\\Launch4j\\scenemax3d_launch4j_project.xml");


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

        } catch (Exception e) {
            e.printStackTrace();
        }
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
            prepareExe();
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            finish.run();
        }
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
                        JarEntry entry = new JarEntry(name);
                        entry.setTime(source.lastModified());
                        target.putNextEntry(entry);
                        target.closeEntry();
                    }
                }
                for (File nestedFile: source.listFiles())
                    add(nestedFile, target);
                return;
            }

            JarEntry entry = new JarEntry(path.replace("deploy/",""));
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
