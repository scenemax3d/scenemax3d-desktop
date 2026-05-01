package com.scenemaxeng.plugins.ide.meshy;

import com.scenemaxeng.common.types.SceneMaxPluginContext;
import com.scenemaxeng.common.types.SceneMaxPluginImportResult;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

final class MeshyViewPanel extends JPanel {
    private static final String API_KEY_SETTING = "meshy_api_key";
    private static final int COMMUNITY_PAGE_SIZE = 24;
    private static final ImageIcon THUMBNAIL_PLACEHOLDER = createPlaceholderIcon();

    private final SceneMaxPluginContext context;
    private final JPasswordField apiKeyField = new JPasswordField();
    private final JTextArea promptArea = new JTextArea(4, 36);
    private final JTextField filterField = new JTextField(18);
    private final JComboBox<String> modelTypeCombo = new JComboBox<>(new String[]{"standard", "lowpoly"});
    private final JComboBox<String> aiModelCombo = new JComboBox<>(new String[]{"latest", "meshy-6", "meshy-5"});
    private final JSpinner targetPolycountSpinner = new JSpinner(new SpinnerNumberModel(30000, 100, 300000, 1000));
    private final JCheckBox remeshCheck = new JCheckBox("Remesh", true);
    private final JCheckBox staticModelCheck = new JCheckBox("Static environment model", false);
    private final JButton createPreviewButton = new JButton("Generate Model");
    private final JButton refineButton = new JButton("Refine Selected");
    private final JButton refreshButton = new JButton("Search Tasks");
    private final JButton communitySearchButton = new JButton("Search Community");
    private final JButton previewImportButton = new JButton("Preview And Import");
    private final JButton downloadButton = new JButton("Import Directly");
    private final JButton docsButton = new JButton("Docs");
    private final JTextField communitySearchField = new JTextField(22);
    private final JCheckBox communityRiggedCheck = new JCheckBox("Rig or animation", false);
    private final JCheckBox communityAnimatedCheck = new JCheckBox("Animation only", false);
    private final JComboBox<SortOption> communitySortCombo = new JComboBox<>(new SortOption[]{
            new SortOption("Popular", "-public_popularity"),
            new SortOption("Most Downloaded", "-downloads"),
            new SortOption("Most Reacted", "-total_emoji_count"),
            new SortOption("Newest", "-created_at"),
            new SortOption("Recently Updated", "-updated_at")
    });
    private final JLabel statusLabel = new JLabel("Enter a Meshy API key, create a preview, or refresh existing tasks.");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JTabbedPane resultsTabs = new JTabbedPane();
    private final DefaultListModel<MeshyTaskItem> taskListModel = new DefaultListModel<>();
    private final JList<MeshyTaskItem> taskList = new JList<>(taskListModel);
    private final DefaultListModel<MeshyCommunityModelItem> communityListModel = new DefaultListModel<>();
    private final JList<MeshyCommunityModelItem> communityList = new JList<>(communityListModel);
    private final List<MeshyTaskItem> allTasks = new ArrayList<>();
    private final Set<String> pollingTaskIds = new HashSet<>();
    private final Map<String, ImageIcon> thumbnailCache = new ConcurrentHashMap<>();
    private final Set<String> loadingThumbnailUrls = ConcurrentHashMap.newKeySet();
    private int communityPage = 1;
    private boolean busy;

    MeshyViewPanel(SceneMaxPluginContext context) {
        super(new BorderLayout(8, 8));
        this.context = context;
        buildUi();
        loadApiKey();
    }

    private void buildUi() {
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel topActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        topActions.add(createPreviewButton);
        topActions.add(refreshButton);
        topActions.add(communitySearchButton);
        topActions.add(refineButton);
        topActions.add(previewImportButton);
        topActions.add(downloadButton);
        add(topActions, BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        JScrollPane formScroll = new JScrollPane(form);
        formScroll.setPreferredSize(new Dimension(430, 0));
        formScroll.setBorder(BorderFactory.createTitledBorder("Meshy AI"));
        formScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(formScroll, BorderLayout.WEST);

        form.add(row("API Key:", apiKeyField, docsButton));
        docsButton.addActionListener(e -> openDocs());

        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        JScrollPane promptScroll = new JScrollPane(promptArea);
        promptScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        promptScroll.setBorder(BorderFactory.createTitledBorder("Prompt"));
        form.add(promptScroll);
        form.add(Box.createVerticalStrut(6));

        form.add(row("Model:", modelTypeCombo, new JLabel("AI:"), aiModelCombo));
        form.add(row("Polycount:", targetPolycountSpinner, remeshCheck));
        staticModelCheck.setToolTipText("Use for buildings, walls, ground and other non-moving environment models.");
        form.add(row("Usage:", staticModelCheck));

        form.add(row("Task Filter:", filterField));
        form.add(row("Community:", communitySearchField));
        communityRiggedCheck.setToolTipText("Show community models that Meshy marks as animated, rigged, humanoid, or character-like.");
        communityAnimatedCheck.setToolTipText("Only show community models with Meshy animation metadata.");
        communityAnimatedCheck.addActionListener(e -> {
            if (communityAnimatedCheck.isSelected()) {
                communityRiggedCheck.setSelected(true);
            }
        });
        form.add(row("Motion:", communityRiggedCheck, communityAnimatedCheck));
        form.add(row("Sort:", communitySortCombo));

        form.add(Box.createVerticalStrut(8));

        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        form.add(progressBar);

        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(statusLabel);
        form.add(Box.createVerticalGlue());

        taskList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        taskList.setCellRenderer(new TaskRenderer());
        taskList.addListSelectionListener(e -> updateSelectionState());
        JScrollPane resultsScroll = new JScrollPane(taskList);
        resultsScroll.setBorder(BorderFactory.createEmptyBorder());

        communityList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        communityList.setCellRenderer(new CommunityRenderer());
        communityList.setFixedCellHeight(92);
        communityList.addListSelectionListener(e -> updateSelectionState());
        JScrollPane communityScroll = new JScrollPane(communityList);
        communityScroll.setBorder(BorderFactory.createEmptyBorder());

        resultsTabs.addTab("My Tasks", resultsScroll);
        resultsTabs.addTab("Community", communityScroll);
        resultsTabs.addChangeListener(e -> updateSelectionState());
        resultsTabs.setBorder(BorderFactory.createTitledBorder("Meshy Models"));
        add(resultsTabs, BorderLayout.CENTER);

        createPreviewButton.addActionListener(e -> createPreview());
        refineButton.addActionListener(e -> refineSelected());
        refreshButton.addActionListener(e -> refreshTasks());
        communitySearchButton.addActionListener(e -> searchCommunityModels());
        previewImportButton.addActionListener(e -> previewSelected());
        downloadButton.addActionListener(e -> downloadSelected());
        filterField.addActionListener(e -> applyFilter());
        filterField.getDocument().addDocumentListener(new SimpleDocumentListener(this::applyFilter));
        communitySearchField.addActionListener(e -> searchCommunityModels());

        updateSelectionState();
    }

    private JPanel row(Object... components) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        for (Object component : components) {
            if (component instanceof String) {
                row.add(new JLabel((String) component));
            } else {
                row.add((Component) component);
            }
        }
        return row;
    }

    private void loadApiKey() {
        apiKeyField.setText(context.getSetting(API_KEY_SETTING, ""));
    }

    private void saveApiKey() {
        context.setSetting(API_KEY_SETTING, apiKey());
    }

    private String apiKey() {
        return new String(apiKeyField.getPassword()).trim();
    }

    private void createPreview() {
        String key = requireApiKey();
        if (key == null) {
            return;
        }
        String prompt = promptArea.getText().trim();
        if (prompt.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a prompt first.", "Meshy AI", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (prompt.length() > 600) {
            JOptionPane.showMessageDialog(this, "Meshy text-to-3D prompts are limited to 600 characters.", "Meshy AI", JOptionPane.WARNING_MESSAGE);
            return;
        }

        setBusy(true, "Creating Meshy preview task...");
        new SwingWorker<JSONObject, Void>() {
            @Override
            protected JSONObject doInBackground() throws Exception {
                JSONObject response = MeshyService.createPreview(
                        key,
                        prompt,
                        (String) modelTypeCombo.getSelectedItem(),
                        (String) aiModelCombo.getSelectedItem(),
                        remeshCheck.isSelected(),
                        ((Number) targetPolycountSpinner.getValue()).intValue());
                return MeshyService.getTask(key, response.optString("result"));
            }

            @Override
            protected void done() {
                try {
                    MeshyTaskItem item = new MeshyTaskItem(get());
                    upsertTask(item.task);
                    selectTaskById(item.id());
                    statusLabel.setText("Preview task created: " + item.id() + ". Meshy will texture it after preview succeeds.");
                    if (!isTerminal(item.status())) {
                        startPollingTask(item.id(), key, true);
                    } else if ("SUCCEEDED".equals(item.status()) && item.isPreview()) {
                        createRefineForPreview(item, key);
                    }
                } catch (Exception e) {
                    showError("Preview creation failed", e);
                } finally {
                    setBusy(false, "");
                }
            }
        }.execute();
    }

    private void refineSelected() {
        MeshyTaskItem selected = taskList.getSelectedValue();
        if (selected == null || !selected.isPreview()) {
            JOptionPane.showMessageDialog(this, "Select a completed preview task first.", "Meshy AI", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!"SUCCEEDED".equals(selected.status())) {
            JOptionPane.showMessageDialog(this, "The preview task must finish before it can be refined.", "Meshy AI", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String key = requireApiKey();
        if (key == null) {
            return;
        }

        createRefineForPreview(selected, key);
    }

    private void createRefineForPreview(MeshyTaskItem previewTask, String key) {
        setBusy(true, "Creating Meshy refine task...");
        new SwingWorker<JSONObject, Void>() {
            @Override
            protected JSONObject doInBackground() throws Exception {
                JSONObject response = MeshyService.createRefine(key, previewTask.id());
                return MeshyService.getTask(key, response.optString("result"));
            }

            @Override
            protected void done() {
                try {
                    MeshyTaskItem item = new MeshyTaskItem(get());
                    upsertTask(item.task);
                    selectTaskById(item.id());
                    statusLabel.setText("Textured refine task created: " + item.id() + ". Polling Meshy...");
                    if (!isTerminal(item.status())) {
                        startPollingTask(item.id(), key, false);
                    }
                } catch (Exception e) {
                    showError("Refine creation failed", e);
                } finally {
                    setBusy(false, "");
                }
            }
        }.execute();
    }

    private void refreshTasks() {
        String key = requireApiKey();
        if (key == null) {
            return;
        }
        setBusy(true, "Loading Meshy tasks...");
        new SwingWorker<JSONArray, Void>() {
            @Override
            protected JSONArray doInBackground() throws Exception {
                return MeshyService.listTasks(key, 1, 50);
            }

            @Override
            protected void done() {
                try {
                    JSONArray tasks = get();
                    allTasks.clear();
                    for (int i = 0; i < tasks.length(); i++) {
                        MeshyTaskItem item = new MeshyTaskItem(tasks.getJSONObject(i));
                        allTasks.add(item);
                        if (!isTerminal(item.status())) {
                            startPollingTask(item.id(), key, false);
                        }
                    }
                    applyFilter();
                    statusLabel.setText("Loaded " + taskListModel.size() + " Meshy task(s).");
                } catch (Exception e) {
                    showError("Task refresh failed", e);
                } finally {
                    setBusy(false, "");
                }
            }
        }.execute();
    }

    private void searchCommunityModels() {
        communityPage = 1;
        setBusy(true, "Searching Meshy community models...");
        resultsTabs.setSelectedIndex(1);
        SortOption sort = (SortOption) communitySortCombo.getSelectedItem();
        String query = communitySearchField.getText().trim();
        String sortValue = sort == null ? "-public_popularity" : sort.value;
        boolean rigOrAnimationOnly = communityRiggedCheck.isSelected();
        boolean animationOnly = communityAnimatedCheck.isSelected();
        int pageSize = (rigOrAnimationOnly || animationOnly) ? 96 : COMMUNITY_PAGE_SIZE;
        new SwingWorker<JSONArray, Void>() {
            @Override
            protected JSONArray doInBackground() throws Exception {
                return MeshyService.searchCommunityModels(query, sortValue, communityPage, pageSize);
            }

            @Override
            protected void done() {
                try {
                    JSONArray models = get();
                    communityListModel.clear();
                    for (int i = 0; i < models.length(); i++) {
                        MeshyCommunityModelItem item = new MeshyCommunityModelItem(models.getJSONObject(i));
                        if (matchesCommunityMotionFilter(item, rigOrAnimationOnly, animationOnly)) {
                            communityListModel.addElement(item);
                        }
                    }
                    if (rigOrAnimationOnly || animationOnly) {
                        statusLabel.setText("Loaded " + communityListModel.size()
                                + " Meshy community model(s) with "
                                + (animationOnly ? "animation metadata." : "rig/animation hints."));
                        if (communityListModel.isEmpty()) {
                            JOptionPane.showMessageDialog(MeshyViewPanel.this,
                                    "Meshy's public community search does not expose a strict rigged-only filter. "
                                            + "Try searches like humanoid, character, rigged, animated, walk, or fight.",
                                    "Meshy AI", JOptionPane.INFORMATION_MESSAGE);
                        }
                    } else {
                        statusLabel.setText("Loaded " + communityListModel.size() + " Meshy community model(s).");
                    }
                } catch (Exception e) {
                    showError("Community search failed", e);
                } finally {
                    setBusy(false, "");
                }
            }
        }.execute();
    }

    private void downloadSelected() {
        if (isCommunityTabSelected()) {
            downloadSelectedCommunity(false);
        } else {
            downloadSelectedTask(false);
        }
    }

    private void previewSelected() {
        if (isCommunityTabSelected()) {
            downloadSelectedCommunity(true);
        } else {
            downloadSelectedTask(true);
        }
    }

    private void downloadSelectedTask(boolean previewBeforeImport) {
        MeshyTaskItem selected = taskList.getSelectedValue();
        if (selected == null) {
            return;
        }
        if (!"SUCCEEDED".equals(selected.status()) || selected.glbUrl().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select a succeeded task with a GLB output.", "Meshy AI", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!selected.isTextured()) {
            JOptionPane.showMessageDialog(this,
                    "This Meshy task is a geometry-only preview. Press Refine Selected first, then use Preview And Import on the finished textured refine task.",
                    "Meshy AI", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String requestedName = suggestedAssetName(selected);
        final boolean staticModel = staticModelCheck.isSelected();
        setBusy(true, previewBeforeImport ? "Downloading Meshy model for preview..." : "Downloading Meshy model...");
        progressBar.setVisible(true);
        progressBar.setValue(0);
        progressBar.setString("Downloading...");

        new SwingWorker<Object, Integer>() {
            @Override
            protected Object doInBackground() throws Exception {
                File tempRoot = Files.createTempDirectory("scenemax-meshy-").toFile();
                File glbFile = new File(tempRoot, requestedName + ".glb");
                MeshyService.downloadFile(selected.glbUrl(), glbFile, pct -> publish(pct));
                JSONObject metadata = new JSONObject();
                metadata.put("provider", "meshy.ai");
                metadata.put("taskId", selected.id());
                metadata.put("prompt", selected.prompt());
                metadata.put("taskType", selected.task.optString("type", ""));
                metadata.put("isStatic", staticModel);
                if (previewBeforeImport) {
                    context.previewModelAsset(glbFile, requestedName, metadata);
                    return null;
                }
                return context.importModelAsset(glbFile, requestedName, metadata);
            }

            @Override
            protected void process(List<Integer> chunks) {
                if (chunks.isEmpty()) {
                    return;
                }
                int value = chunks.get(chunks.size() - 1);
                progressBar.setValue(value);
                progressBar.setString("Downloading... " + value + "%");
            }

            @Override
            protected void done() {
                try {
                    Object result = get();
                    if (previewBeforeImport) {
                        statusLabel.setText("Opened Meshy model in the 3D import preview.");
                    } else {
                        SceneMaxPluginImportResult importResult = (SceneMaxPluginImportResult) result;
                        statusLabel.setText("Imported Meshy model as " + importResult.getAssetName() + ".");
                        JOptionPane.showMessageDialog(MeshyViewPanel.this,
                                "Imported Meshy model as " + importResult.getAssetName() + ".",
                                "Meshy AI", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception e) {
                    showError(previewBeforeImport ? "Download/preview failed" : "Download/import failed", e);
                } finally {
                    setBusy(false, "");
                    progressBar.setVisible(false);
                    progressBar.setValue(0);
                }
            }
        }.execute();
    }

    private void downloadSelectedCommunity(boolean previewBeforeImport) {
        MeshyCommunityModelItem selected = communityList.getSelectedValue();
        if (selected == null) {
            return;
        }
        String key = requireApiKey();
        if (key == null) {
            return;
        }

        String requestedName = selected.suggestedAssetName();
        final boolean staticModel = staticModelCheck.isSelected();
        setBusy(true, previewBeforeImport ? "Downloading Meshy community model for preview..." : "Downloading Meshy community model...");
        progressBar.setVisible(true);
        progressBar.setValue(0);
        progressBar.setString("Preparing download...");

        new SwingWorker<Object, Integer>() {
            @Override
            protected Object doInBackground() throws Exception {
                MeshyService.CommunityDownloadAsset downloadAsset = MeshyService.getCommunityDownloadAsset(key, selected, "glb");
                File tempRoot = Files.createTempDirectory("scenemax-meshy-community-").toFile();
                File glbFile = new File(tempRoot, requestedName + downloadAsset.extension);
                MeshyService.downloadFile(downloadAsset.url, glbFile, pct -> publish(pct));
                JSONObject metadata = new JSONObject();
                metadata.put("provider", "meshy.ai");
                metadata.put("source", "community");
                metadata.put("showcaseId", selected.id());
                metadata.put("resultId", selected.resultId());
                metadata.put("animationId", selected.animationId());
                metadata.put("downloadTaskId", selected.downloadTaskId());
                metadata.put("downloadExtension", downloadAsset.extension);
                metadata.put("hasAnimation", selected.hasAnimation());
                metadata.put("hasRigHint", selected.hasRigHint());
                metadata.put("title", selected.title());
                metadata.put("author", selected.author());
                metadata.put("license", selected.license());
                metadata.put("pageUrl", selected.pageUrl());
                metadata.put("isStatic", staticModel);
                if (previewBeforeImport) {
                    context.previewModelAsset(glbFile, requestedName, metadata);
                    return null;
                }
                return context.importModelAsset(glbFile, requestedName, metadata);
            }

            @Override
            protected void process(List<Integer> chunks) {
                if (chunks.isEmpty()) {
                    return;
                }
                int value = chunks.get(chunks.size() - 1);
                progressBar.setValue(value);
                progressBar.setString("Downloading... " + value + "%");
            }

            @Override
            protected void done() {
                try {
                    Object result = get();
                    if (previewBeforeImport) {
                        statusLabel.setText("Opened Meshy community model in the 3D import preview.");
                    } else {
                        SceneMaxPluginImportResult importResult = (SceneMaxPluginImportResult) result;
                        statusLabel.setText("Imported Meshy community model as " + importResult.getAssetName() + ".");
                        JOptionPane.showMessageDialog(MeshyViewPanel.this,
                                "Imported Meshy community model as " + importResult.getAssetName() + ".",
                                "Meshy AI", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception e) {
                    showError(previewBeforeImport ? "Community download/preview failed" : "Community download/import failed", e);
                } finally {
                    setBusy(false, "");
                    progressBar.setVisible(false);
                    progressBar.setValue(0);
                }
            }
        }.execute();
    }

    private String suggestedAssetName(MeshyTaskItem item) {
        String prompt = item.prompt();
        if (prompt == null || prompt.trim().isEmpty()) {
            return "meshy_" + shortId(item.id());
        }
        String sanitized = prompt.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
        sanitized = sanitized.replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        if (sanitized.isEmpty()) {
            return "meshy_" + shortId(item.id());
        }
        if (sanitized.length() > 42) {
            sanitized = sanitized.substring(0, 42).replaceAll("_+$", "");
        }
        return "meshy_" + sanitized;
    }

    private String shortId(String id) {
        return id == null ? "model" : id.substring(0, Math.min(8, id.length()));
    }

    private boolean matchesCommunityMotionFilter(MeshyCommunityModelItem item, boolean rigOrAnimationOnly, boolean animationOnly) {
        if (animationOnly) {
            return item.hasAnimation();
        }
        if (rigOrAnimationOnly) {
            return item.hasRigHint();
        }
        return true;
    }

    private String requireApiKey() {
        String key = apiKey();
        if (key.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Enter a Meshy API key first. You can also use Meshy's documented test key while developing.",
                    "Meshy AI", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        saveApiKey();
        return key;
    }

    private void applyFilter() {
        String needle = filterField.getText().trim().toLowerCase(Locale.ROOT);
        MeshyTaskItem selected = taskList.getSelectedValue();
        taskListModel.clear();
        for (MeshyTaskItem item : allTasks) {
            if (needle.isEmpty()
                    || item.prompt().toLowerCase(Locale.ROOT).contains(needle)
                    || item.id().toLowerCase(Locale.ROOT).contains(needle)
                    || item.status().toLowerCase(Locale.ROOT).contains(needle)) {
                taskListModel.addElement(item);
            }
        }
        if (selected != null) {
            taskList.setSelectedValue(selected, true);
        }
        updateSelectionState();
    }

    private void upsertTask(JSONObject task) {
        String id = task.optString("id", "");
        if (id.isEmpty()) {
            return;
        }
        String selectedId = taskList.getSelectedValue() == null ? "" : taskList.getSelectedValue().id();
        MeshyTaskItem replacement = new MeshyTaskItem(task);
        for (int i = 0; i < allTasks.size(); i++) {
            if (id.equals(allTasks.get(i).id())) {
                allTasks.set(i, replacement);
                applyFilter();
                selectTaskById(selectedId.isEmpty() ? id : selectedId);
                return;
            }
        }
        allTasks.add(0, replacement);
        applyFilter();
        selectTaskById(selectedId.isEmpty() ? id : selectedId);
    }

    private void selectTaskById(String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        for (int i = 0; i < taskListModel.size(); i++) {
            MeshyTaskItem item = taskListModel.get(i);
            if (id.equals(item.id())) {
                taskList.setSelectedIndex(i);
                taskList.ensureIndexIsVisible(i);
                return;
            }
        }
    }

    private void startPollingTask(String taskId, String key, boolean autoRefinePreview) {
        if (taskId == null || taskId.isEmpty()) {
            return;
        }
        synchronized (pollingTaskIds) {
            if (!pollingTaskIds.add(taskId)) {
                return;
            }
        }
        new SwingWorker<JSONObject, JSONObject>() {
            @Override
            protected JSONObject doInBackground() throws Exception {
                JSONObject latest = null;
                for (int attempt = 0; attempt < 180 && !isCancelled(); attempt++) {
                    latest = MeshyService.getTask(key, taskId);
                    publish(latest);
                    if (isTerminal(latest.optString("status", ""))) {
                        return latest;
                    }
                    Thread.sleep(5000);
                }
                return latest;
            }

            @Override
            protected void process(List<JSONObject> chunks) {
                if (chunks.isEmpty()) {
                    return;
                }
                JSONObject latest = chunks.get(chunks.size() - 1);
                upsertTask(latest);
                MeshyTaskItem item = new MeshyTaskItem(latest);
                statusLabel.setText("Meshy task " + shortId(item.id()) + ": " + item.status() + " " + item.progress() + "%");
            }

            @Override
            protected void done() {
                synchronized (pollingTaskIds) {
                    pollingTaskIds.remove(taskId);
                }
                try {
                    JSONObject latest = get();
                    if (latest == null) {
                        statusLabel.setText("Meshy polling ended without a task update.");
                        return;
                    }
                    upsertTask(latest);
                    MeshyTaskItem item = new MeshyTaskItem(latest);
                    if (isTerminal(item.status())) {
                        if (autoRefinePreview && "SUCCEEDED".equals(item.status()) && item.isPreview()) {
                            statusLabel.setText("Preview " + shortId(item.id()) + " succeeded. Creating textured refine task...");
                            createRefineForPreview(item, key);
                        } else {
                            statusLabel.setText("Meshy task " + shortId(item.id()) + " finished: " + item.status() + ".");
                        }
                    } else {
                        statusLabel.setText("Meshy task " + shortId(item.id()) + " is still " + item.status()
                                + " " + item.progress() + "%. Use Search Tasks to refresh later.");
                    }
                } catch (Exception e) {
                    Throwable root = e;
                    while (root.getCause() != null) {
                        root = root.getCause();
                    }
                    statusLabel.setText("Meshy polling stopped: " + root.getMessage());
                }
            }
        }.execute();
    }

    private boolean isTerminal(String status) {
        String normalized = status == null ? "" : status.toUpperCase(Locale.ROOT);
        return "SUCCEEDED".equals(normalized)
                || "FAILED".equals(normalized)
                || "CANCELED".equals(normalized)
                || "CANCELLED".equals(normalized)
                || "EXPIRED".equals(normalized);
    }

    private void updateSelectionState() {
        if (isCommunityTabSelected()) {
            MeshyCommunityModelItem selected = communityList.getSelectedValue();
            boolean canDownloadCommunity = selected != null && !selected.downloadTaskId().isEmpty();
            refineButton.setEnabled(false);
            previewImportButton.setEnabled(!busy && canDownloadCommunity);
            downloadButton.setEnabled(!busy && canDownloadCommunity);
            return;
        }

        MeshyTaskItem selected = taskList.getSelectedValue();
        boolean hasSelection = selected != null;
        refineButton.setEnabled(!busy && hasSelection && selected.isPreview() && "SUCCEEDED".equals(selected.status()));
        boolean hasFinishedModel = hasSelection && "SUCCEEDED".equals(selected.status()) && !selected.glbUrl().isEmpty();
        boolean canImport = hasFinishedModel && selected.isTextured();
        previewImportButton.setEnabled(!busy && canImport);
        downloadButton.setEnabled(!busy && canImport);
        if (!busy && hasSelection && selected.isPreview() && "SUCCEEDED".equals(selected.status())) {
            statusLabel.setText("Selected task is Meshy's geometry preview. Press Refine Selected to create the textured model.");
        }
    }

    private boolean isCommunityTabSelected() {
        return resultsTabs.getSelectedIndex() == 1;
    }

    private void setBusy(boolean busy, String message) {
        this.busy = busy;
        createPreviewButton.setEnabled(!busy);
        refreshButton.setEnabled(!busy);
        communitySearchButton.setEnabled(!busy);
        taskList.setEnabled(!busy);
        communityList.setEnabled(!busy);
        if (busy && message != null && !message.isEmpty()) {
            statusLabel.setText(message);
        }
        updateSelectionState();
    }

    private void showError(String title, Exception e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        statusLabel.setText(title + ".");
        JOptionPane.showMessageDialog(this, title + ":\n" + root.getMessage(), "Meshy AI", JOptionPane.ERROR_MESSAGE);
    }

    private void openDocs() {
        try {
            Desktop.getDesktop().browse(new URI("https://docs.meshy.ai/en/api/quick-start"));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Unable to open Meshy docs. Visit https://docs.meshy.ai/en/api/quick-start",
                    "Meshy AI", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private static final class TaskRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (component instanceof JLabel && value instanceof MeshyTaskItem) {
                MeshyTaskItem item = (MeshyTaskItem) value;
                ((JLabel) component).setText("<html><b>" + escape(item.status()) + " " + item.progress()
                        + "%</b> &nbsp; " + escape(item.task.optString("type", "text-to-3d"))
                        + "<br>" + escape(item.prompt())
                        + "<br><span style='font-size:9px'>" + escape(item.id()) + "</span></html>");
            }
            return component;
        }

        private String escape(String value) {
            return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    private ImageIcon getThumbnailIcon(MeshyCommunityModelItem item) {
        String thumbnailUrl = item.thumbnailUrl();
        if (thumbnailUrl.isEmpty()) {
            return THUMBNAIL_PLACEHOLDER;
        }

        ImageIcon cached = thumbnailCache.get(thumbnailUrl);
        if (cached != null) {
            return cached;
        }

        if (loadingThumbnailUrls.add(thumbnailUrl)) {
            new SwingWorker<ImageIcon, Void>() {
                @Override
                protected ImageIcon doInBackground() {
                    try (BufferedInputStream in = new BufferedInputStream(new URL(thumbnailUrl).openStream())) {
                        BufferedImage image = ImageIO.read(in);
                        if (image == null) {
                            return THUMBNAIL_PLACEHOLDER;
                        }
                        Image scaled = image.getScaledInstance(96, 72, Image.SCALE_SMOOTH);
                        BufferedImage buffered = new BufferedImage(96, 72, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g = buffered.createGraphics();
                        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        g.drawImage(scaled, 0, 0, null);
                        g.dispose();
                        return new ImageIcon(buffered);
                    } catch (Exception e) {
                        return THUMBNAIL_PLACEHOLDER;
                    }
                }

                @Override
                protected void done() {
                    try {
                        thumbnailCache.put(thumbnailUrl, get());
                    } catch (Exception e) {
                        thumbnailCache.put(thumbnailUrl, THUMBNAIL_PLACEHOLDER);
                    } finally {
                        loadingThumbnailUrls.remove(thumbnailUrl);
                        communityList.repaint();
                    }
                }
            }.execute();
        }
        return THUMBNAIL_PLACEHOLDER;
    }

    private static ImageIcon createPlaceholderIcon() {
        BufferedImage image = new BufferedImage(96, 72, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(38, 38, 38));
        g.fillRoundRect(0, 0, 95, 71, 8, 8);
        g.setColor(new Color(86, 86, 86));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(1, 1, 93, 69, 8, 8);
        g.drawLine(26, 48, 43, 31);
        g.drawLine(43, 31, 59, 47);
        g.drawLine(50, 40, 59, 31);
        g.drawLine(59, 31, 73, 48);
        g.fillOval(23, 18, 10, 10);
        g.dispose();
        return new ImageIcon(image);
    }

    private final class CommunityRenderer extends JPanel implements javax.swing.ListCellRenderer<MeshyCommunityModelItem> {
        private final JLabel thumb = new JLabel(THUMBNAIL_PLACEHOLDER);
        private final JLabel text = new JLabel();

        CommunityRenderer() {
            super(new BorderLayout(10, 0));
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            thumb.setPreferredSize(new Dimension(96, 72));
            add(thumb, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends MeshyCommunityModelItem> list,
                                                      MeshyCommunityModelItem value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            text.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            thumb.setIcon(getThumbnailIcon(value));
            String motionLabel = value.motionLabel();
            String badge = motionLabel.isEmpty() ? "" : " &nbsp; " + escape(motionLabel);
            text.setText("<html><b>" + escape(value.title()) + "</b>"
                    + "<br>" + escape(value.author().isEmpty() ? "Meshy community" : value.author())
                    + " &nbsp; " + value.views() + " views"
                    + " &nbsp; " + value.downloads() + " downloads"
                    + "<br><span style='font-size:9px'>" + escape(value.license())
                    + badge
                    + " &nbsp; " + escape(value.downloadTaskId()) + "</span></html>");
            return this;
        }

        private String escape(String value) {
            return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    private static final class SortOption {
        private final String label;
        private final String value;

        private SortOption(String label, String value) {
            this.label = label;
            this.value = value;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class SimpleDocumentListener implements javax.swing.event.DocumentListener {
        private final Runnable callback;

        SimpleDocumentListener(Runnable callback) {
            this.callback = callback;
        }

        @Override
        public void insertUpdate(javax.swing.event.DocumentEvent e) {
            SwingUtilities.invokeLater(callback);
        }

        @Override
        public void removeUpdate(javax.swing.event.DocumentEvent e) {
            SwingUtilities.invokeLater(callback);
        }

        @Override
        public void changedUpdate(javax.swing.event.DocumentEvent e) {
            SwingUtilities.invokeLater(callback);
        }
    }
}
