package com.scenemax.desktop;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class ImportProgramZipFileTask extends SwingWorker<Integer, String> {

    private final Callback finish;
    private String filePath;
    public String error;
    private String targetScriptPath;
    private String resourcesHash="";
    private boolean importResourcesOnly = false;

    public ImportProgramZipFileTask(String filePath, Callback finish) {

        this.finish = finish;
        this.filePath = filePath;
        this.error = "";

    }

    public void setImportResourcesOnly(boolean importResourcesOnly) {
        this.importResourcesOnly=importResourcesOnly;
    }

    private File extractProgramZip(String zipFile) {

        File f = new File(zipFile);

        File folder = new File(f.getParentFile().getAbsolutePath()+"/"+f.getName().toLowerCase().replace(".zip",""));
        if(folder.exists()) {
            try {
                FileUtils.deleteDirectory(folder);
            } catch (IOException e) {
                e.printStackTrace();
                this.error = e.getMessage();
            }
        }

        this.error+=Util.unzip(zipFile,folder.getAbsolutePath());

        return folder;
    }

    private JSONObject getResourcesFolderIndex(String path) {
        File f = new File(path);
        if(!f.exists()) return null;

        String s = Util.readFile(f);
        if(s==null || s.length()==0) return null;
        return new JSONObject(s);
    }


    private boolean skyboxNameExists(JSONArray skyboxes, String name) {

        for(int i=0;i<skyboxes.length();++i) {
            JSONObject m = skyboxes.getJSONObject(i);
            if(m.getString("name").equalsIgnoreCase(name)) {
                return true;
            }
        }

        return false;
    }

    private boolean spriteNameExists(JSONArray sprites, String name) {

        for(int i=0;i<sprites.length();++i) {
            JSONObject m = sprites.getJSONObject(i);
            if(m.getString("name").equalsIgnoreCase(name)) {
                return true;
            }
        }

        return false;
    }

    private boolean modelNameExists(JSONArray models, String name) {


        for(int i=0;i<models.length();++i) {
            JSONObject m = models.getJSONObject(i);
            if(m.getString("name").equalsIgnoreCase(name)) {
                return true;
            }
        }

        return false;
    }

    private boolean audioNameExists(JSONArray sounds, String name) {

        for(int i=0;i<sounds.length();++i) {
            JSONObject m = sounds.getJSONObject(i);
            if(m.getString("name").equalsIgnoreCase(name)) {
                return true;
            }
        }

        return false;
    }

    private void copyResourcesIndex(File srcFolder, File srcResFile) {

        try {
            String text = new String(Files.readAllBytes(srcResFile.toPath()));
            JSONObject json = new JSONObject(text);

            // Update models index
            JSONObject builtInRes = getResourcesFolderIndex((Util.getDefaultResourcesFolder()+"/Models/models.json"));
            JSONArray currBuiltInModels = builtInRes.getJSONArray("models");
            JSONObject res = getResourcesFolderIndex((Util.getResourcesFolder()+"/Models/models-ext.json"));
            if(res==null) {
                res=new JSONObject("{\"models\":[]}");
            }
            JSONArray currModels = res.getJSONArray("models");

            boolean changed = false;

            if(json.has("models")) {
                JSONArray models = json.getJSONArray("models");

                for (int i = 0; i < models.length(); ++i) {
                    JSONObject model = models.getJSONObject(i);
                    String name = model.getString("name");
                    if (!modelNameExists(currModels, name) && !modelNameExists(currBuiltInModels, name)) {
                        String modelPath = model.getString("path");
                        File srcDir = new File(srcFolder.getAbsolutePath()+"/export_res/"+modelPath).getParentFile();
                        File destDir = new File(Util.getResourcesFolder()+"/Models/"+srcDir.getName());
                        destDir.mkdirs();
                        FileUtils.copyDirectory(srcDir,destDir);
                        currModels.put(model);
                        changed = true;
                    }

                }

                if (changed) {
                    Util.writeFile(Util.getResourcesFolder()+"/Models/models-ext.json", res.toString(2));
                }
            }

            // Update sprites index
            if(json.has("sprites")) {
                builtInRes = getResourcesFolderIndex((Util.getDefaultResourcesFolder()+"/sprites/sprites.json"));
                JSONArray currBuiltInSprites = builtInRes.getJSONArray("sprites");
                res = getResourcesFolderIndex((Util.getResourcesFolder()+"/sprites/sprites-ext.json"));
                if(res==null) {
                    res=new JSONObject("{\"sprites\":[]}");
                }

                JSONArray currSprites = res.getJSONArray("sprites");
                JSONArray sprites = json.getJSONArray("sprites");
                changed = false;
                for (int i = 0; i < sprites.length(); ++i) {
                    JSONObject sprite = sprites.getJSONObject(i);
                    String name = sprite.getString("name");
                    if (!spriteNameExists(currSprites, name) && !spriteNameExists(currBuiltInSprites,name)) {

                        String path = sprite.getString("path");
                        File src = new File(srcFolder.getAbsolutePath()+"/export_res/"+path);
                        File dest = new File(Util.getResourcesFolder()+"/"+path);
                        FileUtils.copyFile(src,dest);
                        currSprites.put(sprite);
                        changed = true;
                    }

                }

                if (changed) {
                    Util.writeFile(Util.getResourcesFolder()+"/sprites/sprites-ext.json", res.toString(2));
                }
            }


            // Update audio index
            if(json.has("sounds")) {
                builtInRes = getResourcesFolderIndex((Util.getDefaultResourcesFolder()+"/audio/audio.json"));
                JSONArray currBuiltInAudio = builtInRes.getJSONArray("sounds");
                res = getResourcesFolderIndex((Util.getResourcesFolder()+"/audio/audio-ext.json"));
                if(res==null) {
                    res=new JSONObject("{\"sounds\":[]}");
                }

                JSONArray currAudio = res.getJSONArray("sounds");

                JSONArray sounds = json.getJSONArray("sounds");
                changed = false;
                for (int i = 0; i < sounds.length(); ++i) {
                    JSONObject sound = sounds.getJSONObject(i);
                    String name = sound.getString("name");
                    if (!audioNameExists(currAudio, name) && !audioNameExists(currBuiltInAudio, name)) {

                        String path = sound.getString("path");
                        File src = new File(srcFolder.getAbsolutePath()+"/export_res/"+path);
                        File dest = new File(Util.getResourcesFolder()+"/"+path);
                        FileUtils.copyFile(src,dest);
                        currAudio.put(sound);
                        changed = true;
                    }

                }

                if (changed) {
                    Util.writeFile(Util.getResourcesFolder()+"/audio/audio-ext.json", res.toString(2));
                }

            }



            // Update skyboxes index
            if(json.has("skyboxes")) {
                builtInRes = getResourcesFolderIndex((Util.getDefaultResourcesFolder()+"/skyboxes/skyboxes.json"));
                JSONArray currBuiltInSkyBoxes = builtInRes.getJSONArray("skyboxes");
                res = getResourcesFolderIndex((Util.getResourcesFolder()+"/skyboxes/skyboxes-ext.json"));
                if(res==null) {
                    res=new JSONObject("{\"skyboxes\":[]}");
                }
                JSONArray currSkyboxes = res.getJSONArray("skyboxes");

                JSONArray skyboxes = json.getJSONArray("skyboxes");
                changed = false;
                for (int i = 0; i < skyboxes.length(); ++i) {
                    JSONObject skybox = skyboxes.getJSONObject(i);
                    String name = skybox.getString("name");
                    if (!skyboxNameExists(currSkyboxes, name) && !skyboxNameExists(currBuiltInSkyBoxes, name)) {

                        String path = skybox.getString("back");
                        File srcDir = new File(srcFolder.getAbsolutePath()+"/export_res/"+path).getParentFile();
                        File destDir = new File(Util.getResourcesFolder()+"/skyboxes/"+srcDir.getName());
                        destDir.mkdirs();
                        FileUtils.copyDirectory(srcDir,destDir);

                        currSkyboxes.put(skybox);
                        changed = true;
                    }

                }

                if (changed) {
                    Util.writeFile(Util.getResourcesFolder()+"/skyboxes/skyboxes-ext.json", res.toString(2));
                }

            }


            mergeResourceIndex(json, "fonts", "fonts/fonts-ext.json");
            mergeResourceIndex(json, "animations", "animations/animations-ext.json");
            mergeResourceIndex(json, "videos", "videos/videos-ext.json");
            mergeResourceIndex(json, "shaders", "shaders/shaders-ext.json");
            mergeResourceIndex(json, "environmentShaders", "environment_shaders/environment-shaders-ext.json");
            mergeResourceIndex(json, "materials", "material/materials-ext.json");
            mergeResourceIndex(json, "terrains", "terrain/terrains-ext.json");


         } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void mergeResourceIndex(JSONObject importedResources, String arrayKey, String targetIndexRelativePath) {
        if (importedResources == null || !importedResources.has(arrayKey)) {
            return;
        }

        try {
            JSONArray importedArray = importedResources.optJSONArray(arrayKey);
            if (importedArray == null || importedArray.length() == 0) {
                return;
            }

            File targetIndex = new File(Util.getResourcesFolder(), targetIndexRelativePath);
            JSONObject targetRoot = getResourcesFolderIndex(targetIndex.getAbsolutePath());
            if (targetRoot == null) {
                targetRoot = new JSONObject();
                targetRoot.put(arrayKey, new JSONArray());
            }

            JSONArray targetArray = targetRoot.optJSONArray(arrayKey);
            if (targetArray == null) {
                targetArray = new JSONArray();
                targetRoot.put(arrayKey, targetArray);
            }

            boolean changed = false;
            for (int i = 0; i < importedArray.length(); i++) {
                JSONObject imported = importedArray.optJSONObject(i);
                if (imported == null || resourceExists(targetArray, imported)) {
                    continue;
                }
                targetArray.put(new JSONObject(imported.toString()));
                changed = true;
            }

            if (changed) {
                File parent = targetIndex.getParentFile();
                if (parent != null) {
                    FileUtils.forceMkdir(parent);
                }
                Util.writeFile(targetIndex.getAbsolutePath(), targetRoot.toString(2));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean resourceExists(JSONArray currentResources, JSONObject imported) {
        String importedName = imported.optString("name", "");
        String importedPath = imported.optString("path", "");
        for (int i = 0; i < currentResources.length(); i++) {
            JSONObject current = currentResources.optJSONObject(i);
            if (current == null) {
                continue;
            }
            if (!importedName.isBlank() && importedName.equalsIgnoreCase(current.optString("name", ""))) {
                return true;
            }
            if (!importedPath.isBlank() && importedPath.equalsIgnoreCase(current.optString("path", ""))) {
                return true;
            }
        }
        return false;
    }


    @Override
    protected void done() {

        JSONObject res = new JSONObject();
        try {
            res.put("targetScriptPath",targetScriptPath);
            res.put("resourcesHash",resourcesHash);

        } catch (JSONException e) {
            e.printStackTrace();
        }

        this.finish.run(res);
    }

    @Override
    protected Integer doInBackground() throws Exception {

        // first copy to local temp folder
        File src = new File(filePath);
        File copyLocal = new File("tmp/"+src.getName());
        if(!copyLocal.getAbsolutePath().equals(src.getAbsolutePath())) {
            FileUtils.copyFile(src, copyLocal);
        }
        filePath=copyLocal.getAbsolutePath();

        // set src to the new local copy file
        src = extractProgramZip(filePath);

        // ****
        String targetFolderName = src.getName();
        String targetScriptFile = "";

        for (File f:src.listFiles()) {
            if(f.isFile()) {
                if (f.getName().equals("extract_config.json")) {
                    String json = Util.readFile(f);
                    JSONObject config = new JSONObject(json);
                    if(config.has("targetFolder")) {
                        targetFolderName=config.getString("targetFolder");
                    }

                    if (config.has("scriptFile")) {
                        targetScriptFile=config.getString("scriptFile");
                    }

                    if (!importResourcesOnly && config.has("resOnly")) {
                        importResourcesOnly=config.getBoolean("resOnly");
                    }

                    if (config.has("resourcesHash")) {
                        resourcesHash=String.valueOf(config.getInt("resourcesHash"));
                    }

                }

            }
        }

        File scriptFolder = null;
        // create parent folder if needed
        if(!importResourcesOnly) {
            scriptFolder = new File(Util.getScriptsFolder()+"/" + targetFolderName);
            if (!scriptFolder.exists()) {
                scriptFolder.mkdir();
            }
        }


        // copy script files
        for (File f:src.listFiles()) {
            if(f.isFile()) {
                if(f.getName().equals("extract_config.json")) {
                    // not copying this file
                } else if(f.getName().equals("resources.json")) {
                    copyResourcesIndex(src,f);
                } else {

                    if(!importResourcesOnly) {
                        File destFile = new File(scriptFolder.getAbsolutePath() + "/" + f.getName());
                        // delete existing file if exists
                        if (destFile.exists()) {
                            // backup existing
                            File backupFile = new File(scriptFolder.getAbsolutePath() + "/" + f.getName() + ".bkup");
                            FileUtils.copyFile(destFile, backupFile);
                            destFile.delete();
                        }

                        FileUtils.copyFileToDirectory(f, scriptFolder);

                        // allow main script file to be auto selected
                        if (f.getName().equals(targetScriptFile)) {
                            // set focus on this file when the system opens
                            AppDB.getInstance().setParam("selected_tree_node_parent", targetFolderName);
                            AppDB.getInstance().setParam("selected_tree_node", targetScriptFile);

                            targetScriptPath = destFile.getAbsolutePath();
                        }
                    }

                }
            } else {
                if(f.getName().equals("export_res")) {
                    importResFolder(f);
                } else if(f.getName().equals("export_src")) {
                    importSrcFolder(f, targetFolderName, targetScriptFile);
                }
            }
        }

        return 0;
    }

    private void importSrcFolder(File folder, String targetFolderName, String targetScriptFile) {
        for(File f: folder.listFiles()) {
            if(f.isDirectory()) {
                try {
                    FileUtils.copyDirectoryToDirectory(f,new File(Util.getScriptsFolder()));
                    if (f.getName().equals(targetFolderName) && targetScriptFile != null && targetScriptFile.length() > 0) {
                        File targetFile = new File(new File(Util.getScriptsFolder(), targetFolderName), targetScriptFile);
                        if (targetFile.isFile()) {
                            AppDB.getInstance().setParam("selected_tree_node_parent", targetFolderName);
                            AppDB.getInstance().setParam("selected_tree_node", targetScriptFile);
                            targetScriptPath = targetFile.getAbsolutePath();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void importResFolder(File f) throws IOException {
        File resourcesRoot = new File(Util.getResourcesFolder());
        FileUtils.forceMkdir(resourcesRoot);
        for(File resFile: f.listFiles()) {
            if(resFile.isDirectory()) {
                if(resFile.getName().equals("macro")) {
                    File macroFolder = new File("macro");
                    for(File macroFile:resFile.listFiles()) {
                        if(macroFile.isFile()) {
                            FileUtils.copyFileToDirectory(macroFile,macroFolder);
                        }
                    }
                } else if(resFile.getName().equals("resources")) {
                    File[] nestedResources = resFile.listFiles();
                    if (nestedResources != null) {
                        for (File nestedResource : nestedResources) {
                            copyResourcePayloadEntry(nestedResource, resourcesRoot);
                        }
                    }
                } else {
                    copyResourcePayloadEntry(resFile, resourcesRoot);
                }

            } else if(resFile.isFile()) {
                FileUtils.copyFileToDirectory(resFile, resourcesRoot);
            }
        }
    }

    private static void copyResourcePayloadEntry(File source, File resourcesRoot) throws IOException {
        if (source == null || resourcesRoot == null) {
            return;
        }
        if (source.isDirectory()) {
            copyResourcePayloadDirectory(source, new File(resourcesRoot, source.getName()));
        } else if (source.isFile()) {
            copyResourcePayloadFile(source, new File(resourcesRoot, source.getName()));
        }
    }

    private static void copyResourcePayloadDirectory(File sourceDir, File targetDir) throws IOException {
        File[] children = sourceDir.listFiles();
        if (children == null) {
            return;
        }
        FileUtils.forceMkdir(targetDir);
        for (File child : children) {
            File target = new File(targetDir, child.getName());
            if (child.isDirectory()) {
                copyResourcePayloadDirectory(child, target);
            } else if (child.isFile()) {
                copyResourcePayloadFile(child, target);
            }
        }
    }

    private static void copyResourcePayloadFile(File source, File target) throws IOException {
        if (isMergeManagedResourceIndex(source)) {
            return;
        }
        File parent = target.getParentFile();
        if (parent != null) {
            FileUtils.forceMkdir(parent);
        }
        FileUtils.copyFile(source, target);
    }

    private static boolean isMergeManagedResourceIndex(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        String name = file.getName().toLowerCase();
        return name.equals("models-ext.json")
                || name.equals("sprites-ext.json")
                || name.equals("audio-ext.json")
                || name.equals("fonts-ext.json")
                || name.equals("skyboxes-ext.json")
                || name.equals("animations-ext.json")
                || name.equals("videos-ext.json")
                || name.equals("shaders-ext.json")
                || name.equals("environment-shaders-ext.json")
                || name.equals("materials-ext.json")
                || name.equals("terrains-ext.json")
                || name.equals("terrains.json");
    }


}
