package com.scenemax.desktop;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ProjectMenuAction extends AbstractAction {
    private final MainApp app;
    private final SceneMaxProject project;

    public ProjectMenuAction(MainApp app, SceneMaxProject p) {
        super(p.name);
        this.app=app;
        this.project=p;
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        SceneMaxProject currentProject = Util.getActiveProject();
        if (currentProject != null && currentProject.name.equals(this.project.name)) {
            return;
        }

        EditorTabPanel tabPanel = this.app.getEditorTabPanel();

        // Save current project's open tabs
        if (currentProject != null && tabPanel != null) {
            List<String> openPaths = tabPanel.getOpenFilePaths();
            String activeFilePath = tabPanel.getActiveFilePath();

            JSONArray pathsArray = new JSONArray(openPaths);
            AppDB.getInstance().setParam("open_tabs~" + currentProject.name, pathsArray.toString());
            AppDB.getInstance().setParam("active_tab~" + currentProject.name,
                    activeFilePath != null ? activeFilePath : "");

            // Close all tabs
            tabPanel.closeAllTabs();
        }

        // Switch project
        Util.switchProject(this.project.name);
        this.app.refreshScriptsFolder();
        this.app.refreshAssetsMenu();
        this.app.refreshAppTitle();

        // Restore tabs for the new project
        if (tabPanel != null) {
            String savedTabs = AppDB.getInstance().getParam("open_tabs~" + this.project.name);
            String savedActiveTab = AppDB.getInstance().getParam("active_tab~" + this.project.name);

            if (savedTabs != null && !savedTabs.isEmpty()) {
                JSONArray pathsArray = new JSONArray(savedTabs);
                for (int i = 0; i < pathsArray.length(); i++) {
                    String filePath = pathsArray.getString(i);
                    File f = new File(filePath);
                    if (f.isFile()) {
                        try {
                            String content = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
                            tabPanel.openFile(f.getAbsolutePath(), content);
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                }

                // Re-activate the previously active tab
                if (savedActiveTab != null && !savedActiveTab.isEmpty()) {
                    File activeFile = new File(savedActiveTab);
                    if (tabPanel.isFileOpen(activeFile.getAbsolutePath())) {
                        try {
                            String content = FileUtils.readFileToString(activeFile, StandardCharsets.UTF_8);
                            tabPanel.openFile(activeFile.getAbsolutePath(), content);
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            }
        }

    }
}
