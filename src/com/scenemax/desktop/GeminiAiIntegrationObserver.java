package com.scenemax.desktop;

import com.scenemaxeng.common.types.PluginBase;
import org.apache.commons.io.FileUtils;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class GeminiAiIntegrationObserver extends PluginBase implements ActionListener {
    private MainApp host;

    public GeminiAiIntegrationObserver(MainApp host) {
        this.host = host;
    }

    @Override
    public int run(Object... args) {
        String command = (String)args[0];
        if (command.equals("install_assets")) {
            File assetsZipFile = new File((String)args[1]);
            if(assetsZipFile.exists()) {
                ImportProgramZipFileTask importTask = new ImportProgramZipFileTask(assetsZipFile.getAbsolutePath(), new Callback() {
                    @Override
                    public void run(Object res) {

                    }
                });
                importTask.setImportResourcesOnly(true);
                importTask.run();
            }
        } else if (command.equals("add_toolbar_button")) {
            String img = (String)args[1];
            String actionCommand = (String)args[2]; // show_gemini_agent
            String tooltipText = (String)args[3];
            this.host.addToolbarButton(img, actionCommand, tooltipText, this);
        } else if(command.equals("gemini_update_running_main_file")) {
            File mainFile = new File("running/main");
            String mainCode = (String)args[1];
            try {
                FileUtils.writeStringToFile(mainFile, mainCode, StandardCharsets.UTF_8);
            } catch (IOException e) {
                System.out.println("failed to write running/main file. error = "+e.getMessage());
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } else if(command.equals("gemini_create_scene_folder")) {
            File folder = this.host.createNewScriptsFolder("GeminiAI");
            if (folder!=null) {
                File mainFile = new File(folder, "main");
                if (mainFile.exists()) {
                    try {
                        String mainCode = (String)args[1];
                        FileUtils.writeStringToFile(mainFile, mainCode, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                this.host.saveSelectedTreeNodePosition(folder.getAbsolutePath(), "main");
                this.host.openLastTreeNode();
            }
        } else if(command.equals("gemini_play_scene")) {
            this.host.prepareAndRunLauncher();
        } else if(command.equals("export_to_android")) {
            File geminiAiFolder = new File(Util.getScriptsFolder(), "GeminiAI");
            try {
                copyDirectoryContents(new File("running"), geminiAiFolder);
                this.host.saveSelectedTreeNodePosition(geminiAiFolder.getAbsolutePath(), "main");
                this.host.openLastTreeNode();
                String path = this.host.exportNativeAndroidApp(geminiAiFolder.getAbsolutePath());
                this.openAndroidProject(path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }
        return 0;
    }

    public void copyDirectoryContents(File sourceDir, File destDir) throws IOException {
        if (!sourceDir.isDirectory()) {
            throw new IllegalArgumentException("Source is not a directory");
        }

        File[] files = sourceDir.listFiles();
        if (files == null) {
            throw new IOException("Failed to list contents of " + sourceDir);
        }

        for (File file : files) {
            if (file.isDirectory()) {
                FileUtils.copyDirectory(file, new File(destDir, file.getName()));
            } else {
                FileUtils.copyFileToDirectory(file, destDir);
            }
        }
    }

    private void openAndroidProject(String path) {

        if(path == null) {
            System.out.println("export to Android failed. no project path created.");
            return;
        }

        String androidStudioPath = "C:\\Program Files\\Android\\Android Studio\\bin\\studio64.exe";
        if (!new File(androidStudioPath).exists()) {
            return;
        }

        if(!new File(path).exists()) {
            return;
        }

        // Command to open Android Studio with the project
        String command = androidStudioPath + " " + path;

        // Execute the command
        try {
            Runtime.getRuntime().exec(command);
            System.out.println("Android Studio is opening the project...");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String cmd = e.getActionCommand();
        if (cmd.equals("show_gemini_dialog")) {
            this.observer.run("show_gemini_dialog"); // delegate command to the GeminiIntegration plugin
        }
    }
}
