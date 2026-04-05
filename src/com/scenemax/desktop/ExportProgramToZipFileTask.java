package com.scenemax.desktop;

import com.scenemaxeng.compiler.MacroFilter;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.SceneMaxLanguageParser;
import com.scenemaxeng.common.types.*;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public class ExportProgramToZipFileTask extends SwingWorker<Integer, String> {

    public static final int TARGET_DEVICE_PC = 10;
    public static final int TARGET_DEVICE_ANDROID = 20;
    public static final int TARGET_DEVICE_NATIVE_ANDROID = 30;

    public static final int EXPORT_TYPE_RESOURCES_ONLY = 100;
    public static final int EXPORT_TYPE_CODE_ONLY = 200;
    public static final int EXPORT_TYPE_FULL = 300;

    private static final float ESTIMATED_FILES_COUNT = 6610;// total files packed in an executable scene
    private final int exportType;
    private final boolean includeDefaultResources;
    private boolean exportCodeOnly=false;
    private String targetFileName;
    private File scriptFolder=null;
    private File mainScriptFile=null;
    private String prg;
    private Runnable finish;
    private int globalCounter=0;
    private String targetFolderName;
    private String targetPath;
    private Integer resourcesHash;
    private int targetDevice;

    public ExportProgramToZipFileTask(String scriptFilePath, String prg, String targetPath,
                                      String targetFileName, int exportType,
                                      Integer resourcesHash,
                                      boolean includeDefaultResources,
                                      int targetDevice,
                                      Runnable finish) {

        this.prg=prg;
        this.finish=finish;
        this.targetPath=targetPath;
        this.targetFileName = targetFileName;
        this.exportType = exportType;
        this.exportCodeOnly = exportType==EXPORT_TYPE_CODE_ONLY;
        this.resourcesHash = resourcesHash;
        this.includeDefaultResources = includeDefaultResources;
        this.targetDevice = targetDevice;

        if(scriptFilePath!=null) {

            mainScriptFile = new File(scriptFilePath);
            if(mainScriptFile.isFile()) {
                this.scriptFolder =mainScriptFile.getParentFile();
            } else {
                this.scriptFolder = mainScriptFile;
            }

        }

    }


    @Override
    protected Integer doInBackground() throws Exception {

        SceneMaxLanguageParser.modelsUsed=new ArrayList<>();
        SceneMaxLanguageParser.effekseerUsed=new ArrayList<>();
        SceneMaxLanguageParser.spriteSheetUsed=new ArrayList<>();
        SceneMaxLanguageParser.audioUsed=new ArrayList<>();
        SceneMaxLanguageParser.fontsUsed=new ArrayList<>();
        SceneMaxLanguageParser.parseUsingResource=true; // look for manual resource declarations

        MacroFilter macroFilter = new MacroFilter();
        macroFilter.loadMacroRulesFromMacroFolder(new File("macro"));
        SceneMaxLanguageParser.setMacroFilter(macroFilter);

        ProgramDef program = new SceneMaxLanguageParser(null,scriptFolder.getAbsolutePath()).parse(this.prg);
        AssetsMapping assetsMapping = new AssetsMapping(Util.getResourcesFolder());

        JSONObject resources = new JSONObject("{ skyboxes:[], terrains:[], sprites:[],models:[],sounds:[], fonts:[] }");
        JSONObject config = new JSONObject("{ }");
        config.put("targetFolder",scriptFolder.getName());
        config.put("scriptFile",mainScriptFile.getName());
        config.put("resOnly",this.exportType==EXPORT_TYPE_RESOURCES_ONLY);
        config.put("codeOnly",exportCodeOnly);


        targetFolderName="export_res";

        // prepare program folder for zipping
        File targetFolder = new File(targetFolderName);
        if(!targetFolder.exists()) {
            targetFolder.mkdir();
        } else {
            FileUtils.deleteDirectory(targetFolder);
            targetFolder.mkdir();
        }

        try {

            for (File f : targetFolder.listFiles()) {
                if (f.isFile()) {
                    f.delete();
                } else {
                    FileUtils.deleteDirectory(f);
                }
            }

        } catch (Exception e) {

        }

        try {

            File spritesDir = new File(targetFolderName+"/sprites");
            if(!spritesDir.exists()) {
                spritesDir.mkdir();
            }

            File texDir = new File(targetFolderName+"/Textures");
            if(!texDir.exists()) {
                texDir.mkdir();
            }

            File modelsDir = new File(targetFolderName+"/Models");
            if(!modelsDir.exists()) {
                modelsDir.mkdir();
            }

            File audioDir = new File(targetFolderName+"/audio");
            if(!audioDir.exists()) {
                audioDir.mkdir();
            }

            File fontsDir = new File(targetFolderName+"/fonts");
            if(!fontsDir.exists()) {
                fontsDir.mkdir();
            }

            File skyboxesDir = new File(targetFolderName+"/skyboxes");
            if(!skyboxesDir.exists()) {
                skyboxesDir.mkdir();
            }

            File probesDir = new File(targetFolderName+"/probes");
            if(!probesDir.exists()) {
                probesDir.mkdir();
            }

            File effectDir = new File(targetFolderName+"/resources/effects");
            if(!effectDir.exists()) {
                effectDir.mkdirs();
            }


        }catch(Exception ex) {

        }

        new File(targetFolderName+"/resources.json").delete();


        File targetMacroDir = new File(targetFolderName+"/macro");
        FileUtils.deleteDirectory(targetMacroDir);
        targetMacroDir.mkdir();

        File macroFolder = new File("macro");
        for(File f:macroFolder.listFiles()) {
            String name=f.getName();
            if(SceneMaxLanguageParser.macroFilesUsed.contains(name)) {
                FileUtils.copyFileToDirectory(f,targetMacroDir);
            }
        }

        if(!exportCodeOnly && targetDevice==TARGET_DEVICE_NATIVE_ANDROID) {
            File probes = new File(Util.getDefaultResourcesFolder(),"probes");
            File dest = new File("./"+targetFolderName);
            FileUtils.copyDirectoryToDirectory(probes, dest);
        }

        if(!exportCodeOnly) {
            JSONArray models = resources.getJSONArray("models");
            for (String modelName : SceneMaxLanguageParser.modelsUsed) {
                ResourceSetup res = assetsMapping.get3DModelsIndex().get(modelName.toLowerCase());
                if (res != null) {

                    if (res.path.startsWith("Models/")) {
                        String resPath = Util.getResourcePath(res.path);

                        if(includeDefaultResources || !resPath.startsWith("./resources/")) {

                            File src = new File(resPath);
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
                                List<String> soundFiles = PackageProgramTask.getSoundFiles(Integer.parseInt(res.engine.audio));
                                for (String f : soundFiles) {
                                    src = new File(Util.getResourcePath(f));
                                    File dest = new File("./" + targetFolderName + "/" + f);
                                    Files.copy(src.toPath(), dest.toPath());

                                }

                                // add horn audio file
                                src = new File(Util.getResourcePath(res.horn));
                                File dest = new File("./" + targetFolderName + "/" + res.horn);
                                Files.copy(src.toPath(), dest.toPath());

                            }

                            JSONObject model = res.toJson();
                            models.put(model);

                        }

                    }
                }
            }
        }

        if(!exportCodeOnly) {
            for (String effectName : SceneMaxLanguageParser.effekseerUsed) {
                String assetId = effectName;
                String prefix = "effects.effekseer.";
                if (assetId.toLowerCase().startsWith(prefix)) {
                    assetId = assetId.substring(prefix.length());
                }
                File src = new File(Util.getResourcesFolder(), "effects/" + assetId);
                File dest = new File("./" + targetFolderName + "/resources/effects/" + assetId);
                if (src.isDirectory()) {
                    FileUtils.copyDirectory(src, dest);
                }
            }
        }


        if(!exportCodeOnly) {
            JSONArray sprites = resources.getJSONArray("sprites");
            for (String spriteSheet : SceneMaxLanguageParser.spriteSheetUsed) {
                ResourceSetup2D res = assetsMapping.getSpriteSheetsIndex().get(spriteSheet.toLowerCase());
                if (res != null) {
                    if (res.path.startsWith("sprites/")) {
                        File src = new File(Util.getResourcePath(res.path));
                        File dest = new File("./" + targetFolderName + "/sprites/" + src.getName());
                        Files.copy(src.toPath(), dest.toPath());

                        JSONObject sprite = createSpriteResourceEntity(spriteSheet.toLowerCase(), src.getName(), "sprites", res);
                        sprites.put(sprite);
                    }
                }
            }
        }

        if(!exportCodeOnly) {
            JSONArray terrains = resources.getJSONArray("terrains");
            for (String terrain : SceneMaxLanguageParser.terrainsUsed) {
                TerrainResource res = assetsMapping.getTerrainsIndex().get(terrain.toLowerCase());
                if (res != null) {

                    copyFile(Util.getResourcePath(res.alphaMap), "./" + targetFolderName);
                    copyFile(Util.getResourcePath(res.redTex), "./" + targetFolderName);
                    copyFile(Util.getResourcePath(res.greenTex), "./" + targetFolderName);
                    copyFile(Util.getResourcePath(res.blueTex), "./" + targetFolderName);
                    copyFile(Util.getResourcePath(res.heightMap), "./" + targetFolderName);

                    JSONObject tr = new JSONObject(res.buff);
                    terrains.put(tr);

                }
            }
        }


        if(!exportCodeOnly) {
            JSONArray audioFiles = resources.getJSONArray("sounds");
            for (String audio : SceneMaxLanguageParser.audioUsed) {
                ResourceAudio res = assetsMapping.getAudioIndex().get(audio.toLowerCase());

                if (res != null) {

                    if((this.targetDevice==TARGET_DEVICE_ANDROID || this.targetDevice==TARGET_DEVICE_NATIVE_ANDROID)
                            && res.path.endsWith(".ogg")) {
                        System.out.println("Android: skipping "+res.path);
                        continue; // Android doesn't support OGG files loaded from storage
                    }

                    String resPath = Util.getResourcePath(res.path);
                    if(includeDefaultResources || !resPath.startsWith("./resources/")) {
                        File src = new File(resPath);
                        File dest = new File("./" + targetFolderName + "/" + res.path);
                        Files.copy(src.toPath(), dest.toPath());

                        JSONObject audioObj = createAudioResourceEntity(res.name, res.path);
                        audioFiles.put(audioObj);

                    }

                }
            }
        }

        if(!exportCodeOnly) {
            JSONArray fontsFiles = resources.getJSONArray("fonts");
            for (String font : SceneMaxLanguageParser.fontsUsed) {
                ResourceFont res = assetsMapping.getFontsIndex().get(font.toLowerCase());
                if (res != null) {

                    File src = new File(Util.getResourcePath(res.path));
                    File dest = new File("./" + targetFolderName + "/" + res.path);
                    Files.copy(src.toPath(), dest.toPath());

                    src = new File(Util.getResourcePath(res.path).replace(".fnt", ".png"));
                    dest = new File("./" + targetFolderName + "/" + res.path.replace(".fnt", ".png"));
                    Files.copy(src.toPath(), dest.toPath());

                    JSONObject fontObj = createFontResourceEntity(res.name, res.path);
                    fontsFiles.put(fontObj);

                }
            }
        }


        if(!exportCodeOnly) {
            //////// SKYBOX ///////////
            JSONArray skyboxesFiles = resources.getJSONArray("skyboxes");
            for (String skybox : SceneMaxLanguageParser.skyboxUsed) {
                SkyBoxResource res = assetsMapping.getSkyboxesIndex().get(skybox.toLowerCase());
                if (res != null) {

                    File skyFolder = new File(Util.getResourcePath(res.up)).getParentFile();
                    FileUtils.copyDirectory(skyFolder, new File(targetFolderName + "/skyboxes/" + skyFolder.getName()));
                    skyboxesFiles.put(new JSONObject(res.buff));

                }
            }
        }

        if(targetFileName==null) {
            targetFileName = scriptFolder.getName();
        }
        JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(targetPath+"/"+targetFileName+".zip"));

        for (File nestedFile : new File(targetFolderName).listFiles()) {
            add(nestedFile, jarOutputStream);
        }

        if(this.resourcesHash==null) {
            resourcesHash=resources.toString().hashCode();
        }

        config.put("resourcesHash", resourcesHash);

        jarOutputStream.putNextEntry(new JarEntry("resources.json"));
        jarOutputStream.write(resources.toString().getBytes());
        jarOutputStream.closeEntry();

        jarOutputStream.putNextEntry(new JarEntry("extract_config.json"));
        jarOutputStream.write(config.toString().getBytes());
        jarOutputStream.closeEntry();

        boolean exportResOnly=exportType==EXPORT_TYPE_RESOURCES_ONLY;

        if(!exportResOnly) {
            File runningFolder = new File("export_src");
            if(runningFolder.exists()) {
                FileUtils.deleteDirectory(runningFolder);
            }
            FileUtils.forceMkdir(runningFolder);
            FileUtils.copyDirectoryToDirectory(scriptFolder, runningFolder);
            for (File nestedFile : new File(runningFolder, scriptFolder.getName()).listFiles()) {
                add(nestedFile, jarOutputStream);
            }
            FileUtils.deleteDirectory(runningFolder);
        }

        // Close jar
        jarOutputStream.close();

        return this.resourcesHash;
    }

    private void copyFileFromResourceToDeploy(String path) {

        File src = new File(path);
        String destPath = path.replaceFirst("(.+?)/resources/","/"+ targetFolderName +"/");
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
        folder="./"+targetFolderName+"/"+folder.substring(start);

        return folder;

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
        finish.run();
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
                String name = path;
                if (!name.isEmpty())
                {
                    if (!name.endsWith("/"))
                        name += "/";
                    JarEntry entry = new JarEntry(name);
                    entry.setTime(source.lastModified());
                    target.putNextEntry(entry);
                    target.closeEntry();
                }
                for (File nestedFile: source.listFiles())
                    add(nestedFile, target);
                return;
            }

            JarEntry entry = new JarEntry(path);

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


}
