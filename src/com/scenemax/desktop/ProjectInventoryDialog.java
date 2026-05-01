package com.scenemax.desktop;

import com.scenemax.designer.inventory.InventoryModelPreview;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ProjectInventoryDialog extends JDialog {

    private final ProjectInventoryPanel inventoryPanel;

    public ProjectInventoryDialog(Frame owner) {
        super(owner, "Project Inventory", false);
        inventoryPanel = new ProjectInventoryPanel();
        setContentPane(inventoryPanel);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1020, 680));
        setSize(1240, 760);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                inventoryPanel.dispose();
            }
        });
    }

    public void refreshInventory() {
        inventoryPanel.refreshInventory();
    }
}

class ProjectInventoryPanel extends JPanel {

    private static final String CATEGORY_ALL = "All";
    private static final String CATEGORY_MODELS = "Models";
    private static final String CATEGORY_AUDIO = "Audio";
    private static final String CATEGORY_SPRITES = "Sprites";
    private static final String CATEGORY_FONTS = "Fonts";
    private static final String CATEGORY_ANIMATIONS = "Animations";
    private static final String CATEGORY_TEXTURES = "Textures";
    private static final String CATEGORY_SHADERS = "Shaders";
    private static final String CATEGORY_MATERIALS = "Materials";
    private static final String CATEGORY_EFFECTS = "Effekseer";
    private static final String CATEGORY_SCENES = "Scenes";
    private static final String CATEGORY_UI = "UI";
    private static final String CATEGORY_SKYBOXES = "Skyboxes";
    private static final String CATEGORY_TERRAIN = "Terrain";

    private static final String[] CATEGORY_ORDER = {
            CATEGORY_ALL, CATEGORY_MODELS, CATEGORY_AUDIO, CATEGORY_SPRITES, CATEGORY_FONTS,
            CATEGORY_ANIMATIONS, CATEGORY_TEXTURES, CATEGORY_SHADERS, CATEGORY_MATERIALS,
            CATEGORY_EFFECTS, CATEGORY_SCENES, CATEGORY_UI, CATEGORY_SKYBOXES, CATEGORY_TERRAIN
    };

    private final List<InventoryAsset> allAssets = new ArrayList<>();
    private final DefaultListModel<String> categoryModel = new DefaultListModel<>();
    private final JList<String> categoryList = new JList<>(categoryModel);
    private final DefaultListModel<InventoryAsset> assetModel = new DefaultListModel<>();
    private final JList<InventoryAsset> assetList = new JList<>(assetModel);
    private final JTextField searchField = new JTextField();
    private final JLabel statusLabel = new JLabel("Scanning assets...");
    private final PropertiesTableModel propertiesTableModel = new PropertiesTableModel();
    private final JTable propertiesTable = new JTable(propertiesTableModel);

    private final CardLayout previewCards = new CardLayout();
    private final JPanel previewPanel = new JPanel(previewCards);
    private final JPanel canvasCard = new JPanel(new BorderLayout());
    private final JLabel imagePreviewLabel = new JLabel("", SwingConstants.CENTER);
    private final JTextArea textPreview = new JTextArea();
    private final JLabel audioLabel = new JLabel("", SwingConstants.CENTER);
    private final JButton playAudioButton = new JButton("Play");
    private final JButton stopAudioButton = new JButton("Stop");

    private InventoryModelPreview previewApp;
    private Canvas previewCanvas;
    private Clip currentClip;
    private InventoryAsset selectedAsset;
    private String selectedCategory = CATEGORY_ALL;

    ProjectInventoryPanel() {
        super(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        buildUi();
        refreshInventory();
    }

    void refreshInventory() {
        allAssets.clear();
        allAssets.addAll(new InventoryScanner().scan());
        allAssets.sort(Comparator
                .comparing((InventoryAsset a) -> categoryRank(a.category))
                .thenComparing(a -> a.name.toLowerCase(Locale.ROOT))
                .thenComparing(a -> a.source.toLowerCase(Locale.ROOT)));
        rebuildCategories();
        applyFilter();
    }

    void dispose() {
        stopAudio();
        if (previewApp != null) {
            previewApp.stop();
            previewApp = null;
        }
    }

    private void buildUi() {
        JLabel title = new JLabel("Project Inventory");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));

        JPanel top = new JPanel(new BorderLayout(10, 0));
        top.add(title, BorderLayout.WEST);
        searchField.putClientProperty("JTextField.placeholderText", "Search assets");
        top.add(searchField, BorderLayout.CENTER);
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refreshInventory());
        top.add(refreshButton, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        categoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        categoryList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && categoryList.getSelectedValue() != null) {
                selectedCategory = stripCount(categoryList.getSelectedValue());
                applyFilter();
            }
        });
        JScrollPane categoryScroll = new JScrollPane(categoryList);
        categoryScroll.setPreferredSize(new Dimension(170, 100));

        assetList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        assetList.setVisibleRowCount(-1);
        assetList.setFixedCellWidth(154);
        assetList.setFixedCellHeight(142);
        assetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        assetList.setCellRenderer(new AssetCellRenderer());
        assetList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showAsset(assetList.getSelectedValue());
            }
        });
        JScrollPane galleryScroll = new JScrollPane(assetList);

        JSplitPane leftSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, categoryScroll, galleryScroll);
        leftSplit.setResizeWeight(0);
        leftSplit.setDividerLocation(180);

        JPanel details = buildDetailsPanel();
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplit, details);
        mainSplit.setResizeWeight(1);
        mainSplit.setDividerLocation(820);
        add(mainSplit, BorderLayout.CENTER);

        statusLabel.setBorder(new EmptyBorder(4, 2, 0, 2));
        add(statusLabel, BorderLayout.SOUTH);

        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                applyFilter();
            }
        });
    }

    private JPanel buildDetailsPanel() {
        JPanel details = new JPanel(new BorderLayout(8, 8));
        details.setBorder(new EmptyBorder(0, 8, 0, 0));
        details.setPreferredSize(new Dimension(360, 100));

        JLabel previewTitle = new JLabel("Preview");
        previewTitle.setFont(previewTitle.getFont().deriveFont(Font.BOLD, 14f));
        details.add(previewTitle, BorderLayout.NORTH);

        previewPanel.setPreferredSize(new Dimension(320, 240));
        previewPanel.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground")));

        canvasCard.add(new JLabel("Select a 3D model to preview it.", SwingConstants.CENTER), BorderLayout.CENTER);
        previewPanel.add(canvasCard, "canvas");

        imagePreviewLabel.setOpaque(true);
        imagePreviewLabel.setBackground(new Color(24, 27, 32));
        previewPanel.add(new JScrollPane(imagePreviewLabel), "image");

        textPreview.setEditable(false);
        textPreview.setLineWrap(true);
        textPreview.setWrapStyleWord(true);
        textPreview.setMargin(new Insets(8, 8, 8, 8));
        previewPanel.add(new JScrollPane(textPreview), "text");

        JPanel audioPanel = new JPanel(new BorderLayout(8, 8));
        audioPanel.setBorder(new EmptyBorder(16, 16, 16, 16));
        audioPanel.add(audioLabel, BorderLayout.CENTER);
        JPanel audioButtons = new JPanel(new FlowLayout(FlowLayout.CENTER));
        playAudioButton.addActionListener(e -> playSelectedAudio());
        stopAudioButton.addActionListener(e -> stopAudio());
        audioButtons.add(playAudioButton);
        audioButtons.add(stopAudioButton);
        audioPanel.add(audioButtons, BorderLayout.SOUTH);
        previewPanel.add(audioPanel, "audio");

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.add(previewPanel, BorderLayout.NORTH);
        propertiesTable.setFillsViewportHeight(true);
        propertiesTable.getColumnModel().getColumn(0).setPreferredWidth(92);
        center.add(new JScrollPane(propertiesTable), BorderLayout.CENTER);
        details.add(center, BorderLayout.CENTER);
        return details;
    }

    private void rebuildCategories() {
        String previous = selectedCategory;
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String category : CATEGORY_ORDER) {
            counts.put(category, 0);
        }
        for (InventoryAsset asset : allAssets) {
            counts.put(asset.category, counts.getOrDefault(asset.category, 0) + 1);
            counts.put(CATEGORY_ALL, counts.getOrDefault(CATEGORY_ALL, 0) + 1);
        }

        categoryModel.clear();
        for (String category : CATEGORY_ORDER) {
            int count = counts.getOrDefault(category, 0);
            if (CATEGORY_ALL.equals(category) || count > 0) {
                categoryModel.addElement(category + "  (" + count + ")");
            }
        }

        int selectIndex = 0;
        for (int i = 0; i < categoryModel.size(); i++) {
            if (stripCount(categoryModel.get(i)).equals(previous)) {
                selectIndex = i;
                break;
            }
        }
        categoryList.setSelectedIndex(Math.max(0, selectIndex));
    }

    private void applyFilter() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        assetModel.clear();
        for (InventoryAsset asset : allAssets) {
            if (!CATEGORY_ALL.equals(selectedCategory) && !asset.category.equals(selectedCategory)) {
                continue;
            }
            if (!query.isEmpty() && !asset.matches(query)) {
                continue;
            }
            assetModel.addElement(asset);
        }
        statusLabel.setText(assetModel.size() + " shown / " + allAssets.size() + " total assets");
        if (assetModel.size() > 0) {
            assetList.setSelectedIndex(0);
        } else {
            showAsset(null);
        }
    }

    private void showAsset(InventoryAsset asset) {
        selectedAsset = asset;
        stopAudio();
        propertiesTableModel.setAsset(asset);
        if (asset == null) {
            textPreview.setText("No asset selected.");
            previewCards.show(previewPanel, "text");
            return;
        }

        if (CATEGORY_MODELS.equals(asset.category)) {
            showModelPreview(asset);
        } else if (CATEGORY_AUDIO.equals(asset.category)) {
            audioLabel.setText("<html><center>" + escapeHtml(asset.name) + "<br>" + escapeHtml(asset.path) + "</center></html>");
            playAudioButton.setEnabled(asset.file != null && asset.file.isFile());
            previewCards.show(previewPanel, "audio");
        } else if (CATEGORY_SPRITES.equals(asset.category) || CATEGORY_TEXTURES.equals(asset.category)
                || CATEGORY_FONTS.equals(asset.category) || CATEGORY_SKYBOXES.equals(asset.category)
                || (asset.file != null && asset.file.isFile() && isImageFile(asset.file))) {
            showImagePreview(asset);
        } else {
            showTextPreview(asset);
        }
    }

    private void showModelPreview(InventoryAsset asset) {
        ensurePreviewApp();
        previewCards.show(previewPanel, "canvas");
        if (previewApp != null) {
            previewApp.previewModel(asset.path, asset.properties);
            scheduleModelThumbnail(asset);
        }
    }

    private void showImagePreview(InventoryAsset asset) {
        File imageFile = resolvePreviewImage(asset);
        if (imageFile == null || !imageFile.isFile()) {
            showTextPreview(asset);
            return;
        }
        try {
            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) {
                showTextPreview(asset);
                return;
            }
            imagePreviewLabel.setText("");
            imagePreviewLabel.setIcon(new ImageIcon(scaleToFit(image, 320, 230)));
            previewCards.show(previewPanel, "image");
        } catch (IOException ex) {
            showTextPreview(asset);
        }
    }

    private void showTextPreview(InventoryAsset asset) {
        StringBuilder sb = new StringBuilder();
        sb.append(asset.name).append("\n\n");
        sb.append(asset.category).append(" | ").append(asset.source).append("\n");
        if (asset.path != null && !asset.path.isBlank()) {
            sb.append(asset.path).append("\n");
        }
        if (asset.file != null && asset.file.isFile() && asset.file.length() < 128 * 1024) {
            try {
                sb.append("\n").append(new String(Files.readAllBytes(asset.file.toPath())));
            } catch (Exception ignored) {
            }
        }
        textPreview.setText(sb.toString());
        textPreview.setCaretPosition(0);
        previewCards.show(previewPanel, "text");
    }

    private void ensurePreviewApp() {
        if (previewApp != null) {
            return;
        }
        SceneMaxProject project = Util.getActiveProject();
        String projectPath = project != null ? project.path : null;
        String resourcesPath = project != null ? project.getResourcesPath() : null;
        previewApp = new InventoryModelPreview(projectPath, resourcesPath);
        previewCanvas = previewApp.getCanvas();
        if (project != null) {
            previewCanvas.setName(project.name + " model preview");
        }

        canvasCard.removeAll();
        canvasCard.add(previewCanvas, BorderLayout.CENTER);
        previewApp.start();
        canvasCard.revalidate();
        canvasCard.repaint();
    }

    private void scheduleModelThumbnail(InventoryAsset asset) {
        File cached = getCachedThumbnailFile(asset);
        if (cached.exists() || previewCanvas == null) {
            assetList.repaint();
            return;
        }
        javax.swing.Timer timer = new javax.swing.Timer(1200, e -> captureModelThumbnail(asset));
        timer.setRepeats(false);
        timer.start();
    }

    private void captureModelThumbnail(InventoryAsset asset) {
        if (previewCanvas == null || selectedAsset != asset || previewCanvas.getWidth() < 16 || previewCanvas.getHeight() < 16) {
            return;
        }
        BufferedImage snapshot = new BufferedImage(previewCanvas.getWidth(), previewCanvas.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = snapshot.createGraphics();
        previewCanvas.paint(g);
        g.dispose();

        SwingWorker<BufferedImage, Void> worker = new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() throws Exception {
                int crop = Math.min(snapshot.getWidth(), snapshot.getHeight());
                int x = (snapshot.getWidth() - crop) / 2;
                int y = (snapshot.getHeight() - crop) / 2;
                BufferedImage square = snapshot.getSubimage(x, y, crop, crop);
                BufferedImage scaled = new BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB);
                Graphics2D gg = scaled.createGraphics();
                gg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                gg.drawImage(square, 0, 0, 96, 96, null);
                gg.dispose();

                File target = getCachedThumbnailFile(asset);
                File parent = target.getParentFile();
                if (!parent.exists()) {
                    parent.mkdirs();
                }
                ImageIO.write(scaled, "png", target);
                return scaled;
            }

            @Override
            protected void done() {
                assetList.repaint();
            }
        };
        worker.execute();
    }

    private void playSelectedAudio() {
        if (selectedAsset == null || selectedAsset.file == null || !selectedAsset.file.isFile()) {
            return;
        }
        stopAudio();
        try {
            AudioInputStream stream = AudioSystem.getAudioInputStream(selectedAsset.file);
            currentClip = AudioSystem.getClip();
            currentClip.open(stream);
            currentClip.start();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Audio preview is unavailable for this file type or codec.\n" + ex.getMessage(),
                    "Audio Preview", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void stopAudio() {
        if (currentClip != null) {
            currentClip.stop();
            currentClip.close();
            currentClip = null;
        }
    }

    private File resolvePreviewImage(InventoryAsset asset) {
        if (asset == null) {
            return null;
        }
        if (CATEGORY_FONTS.equals(asset.category)) {
            File fontFile = asset.file;
            if (fontFile != null) {
                String name = fontFile.getName();
                int dot = name.lastIndexOf('.');
                if (dot > 0) {
                    File png = new File(fontFile.getParentFile(), name.substring(0, dot) + ".png");
                    if (png.isFile()) {
                        return png;
                    }
                }
            }
        }
        if (asset.file != null && asset.file.isFile() && isImageFile(asset.file)) {
            return asset.file;
        }
        if (asset.file != null && asset.file.isDirectory()) {
            File image = findFirstImage(asset.file);
            if (image != null) {
                return image;
            }
        }
        return null;
    }

    private File findFirstImage(File folder) {
        File[] files = folder.listFiles();
        if (files == null) {
            return null;
        }
        for (File file : files) {
            if (file.isFile() && isImageFile(file)) {
                return file;
            }
        }
        for (File file : files) {
            if (file.isDirectory()) {
                File nested = findFirstImage(file);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private BufferedImage scaleToFit(BufferedImage image, int maxW, int maxH) {
        double scale = Math.min(maxW / (double) image.getWidth(), maxH / (double) image.getHeight());
        scale = Math.min(1.0, Math.max(0.05, scale));
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, width, height, null);
        g.dispose();
        return scaled;
    }

    private Icon iconForAsset(InventoryAsset asset, int size) {
        if (asset == null) {
            return null;
        }
        File thumb = CATEGORY_MODELS.equals(asset.category) ? getCachedThumbnailFile(asset) : null;
        if (thumb != null && thumb.isFile()) {
            try {
                BufferedImage image = ImageIO.read(thumb);
                if (image != null) {
                    return new ImageIcon(scaleToFit(image, size, size));
                }
            } catch (IOException ignored) {
            }
        }

        File imageFile = resolvePreviewImage(asset);
        if (imageFile != null && imageFile.isFile()) {
            try {
                BufferedImage image = ImageIO.read(imageFile);
                if (image != null) {
                    return new ImageIcon(scaleToFit(image, size, size));
                }
            } catch (IOException ignored) {
            }
        }
        return new CategoryIcon(asset.category, size, size);
    }

    private File getCachedThumbnailFile(InventoryAsset asset) {
        SceneMaxProject project = Util.getActiveProject();
        String projectName = project != null ? project.name : "no_project";
        String key = sanitize(asset.source + "_" + asset.category + "_" + asset.name + "_" + asset.path);
        return new File(new File("tmp/project_inventory_thumbs", sanitize(projectName)), key + ".png");
    }

    private static String sanitize(String value) {
        return value == null ? "asset" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String stripCount(String value) {
        int index = value == null ? -1 : value.indexOf("  (");
        return index >= 0 ? value.substring(0, index) : value;
    }

    private static int categoryRank(String category) {
        for (int i = 0; i < CATEGORY_ORDER.length; i++) {
            if (CATEGORY_ORDER[i].equals(category)) {
                return i;
            }
        }
        return CATEGORY_ORDER.length;
    }

    private static boolean isImageFile(File file) {
        String lower = file.getName().toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".bmp") || lower.endsWith(".gif");
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private class AssetCellRenderer extends JPanel implements ListCellRenderer<InventoryAsset> {
        private final JLabel icon = new JLabel("", SwingConstants.CENTER);
        private final JLabel name = new JLabel("", SwingConstants.CENTER);
        private final JLabel meta = new JLabel("", SwingConstants.CENTER);

        AssetCellRenderer() {
            super(new BorderLayout(4, 4));
            setBorder(new EmptyBorder(7, 7, 7, 7));
            icon.setPreferredSize(new Dimension(96, 82));
            name.setFont(name.getFont().deriveFont(Font.BOLD, 12f));
            meta.setFont(meta.getFont().deriveFont(Font.PLAIN, 10f));
            JPanel text = new JPanel(new GridLayout(2, 1, 0, 0));
            text.setOpaque(false);
            text.add(name);
            text.add(meta);
            add(icon, BorderLayout.CENTER);
            add(text, BorderLayout.SOUTH);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends InventoryAsset> list, InventoryAsset value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            icon.setIcon(iconForAsset(value, 82));
            name.setText(value == null ? "" : value.name);
            meta.setText(value == null ? "" : value.source + " / " + value.category);
            Color bg = isSelected ? list.getSelectionBackground() : UIManager.getColor("Panel.background");
            Color fg = isSelected ? list.getSelectionForeground() : UIManager.getColor("Label.foreground");
            setBackground(bg);
            setOpaque(true);
            name.setForeground(fg);
            meta.setForeground(isSelected ? fg : UIManager.getColor("Label.disabledForeground"));
            return this;
        }
    }

    private static class PropertiesTableModel extends AbstractTableModel {
        private final List<Map.Entry<String, String>> rows = new ArrayList<>();

        void setAsset(InventoryAsset asset) {
            rows.clear();
            if (asset != null) {
                rows.addAll(asset.properties.entrySet());
            }
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return column == 0 ? "Property" : "Value";
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Map.Entry<String, String> entry = rows.get(rowIndex);
            return columnIndex == 0 ? entry.getKey() : entry.getValue();
        }
    }

    static class InventoryAsset {
        final String category;
        final String name;
        final String source;
        final String path;
        final File file;
        final File resourceRoot;
        final Map<String, String> properties = new LinkedHashMap<>();

        InventoryAsset(String category, String name, String source, String path, File file, File resourceRoot) {
            this.category = category;
            this.name = name == null || name.isBlank() ? "(unnamed)" : name;
            this.source = source;
            this.path = path == null ? "" : path.replace('\\', '/');
            this.file = file;
            this.resourceRoot = resourceRoot;
            put("Name", this.name);
            put("Category", this.category);
            put("Source", this.source);
            put("Runtime path", this.path);
            if (file != null) {
                put("File", file.getAbsolutePath());
                if (file.isFile()) {
                    put("Size", formatSize(file.length()));
                }
            }
        }

        boolean matches(String query) {
            return name.toLowerCase(Locale.ROOT).contains(query)
                    || category.toLowerCase(Locale.ROOT).contains(query)
                    || source.toLowerCase(Locale.ROOT).contains(query)
                    || path.toLowerCase(Locale.ROOT).contains(query);
        }

        void put(String key, Object value) {
            if (value != null) {
                properties.put(key, String.valueOf(value));
            }
        }

        private static String formatSize(long bytes) {
            if (bytes < 1024) {
                return bytes + " B";
            }
            if (bytes < 1024 * 1024) {
                return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
            }
            return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static class InventoryScanner {
        private final List<InventoryAsset> assets = new ArrayList<>();
        private final Set<String> seen = new LinkedHashSet<>();

        List<InventoryAsset> scan() {
            File defaultResources = new File(Util.getDefaultResourcesFolder());
            scanResourceRoot(defaultResources, "Default");

            SceneMaxProject project = Util.getActiveProject();
            if (project != null) {
                File projectResources = new File(project.getResourcesPath());
                scanResourceRoot(projectResources, "Project");
                scanDesignerDocuments(new File(project.path));
            }
            return assets;
        }

        private void scanResourceRoot(File root, String source) {
            if (root == null || !root.isDirectory()) {
                return;
            }
            addFromIndex(root, source, "Models/models.json", "models", CATEGORY_MODELS);
            addFromIndex(root, source, "Models/models-ext.json", "models", CATEGORY_MODELS);
            addFromIndex(root, source, "sprites/sprites.json", "sprites", CATEGORY_SPRITES);
            addFromIndex(root, source, "sprites/sprites-ext.json", "sprites", CATEGORY_SPRITES);
            addFromIndex(root, source, "audio/audio.json", "sounds", CATEGORY_AUDIO);
            addFromIndex(root, source, "audio/audio-ext.json", "sounds", CATEGORY_AUDIO);
            addFromIndex(root, source, "fonts/fonts.json", "fonts", CATEGORY_FONTS);
            addFromIndex(root, source, "fonts/fonts-ext.json", "fonts", CATEGORY_FONTS);
            addFromIndex(root, source, "animations/animations.json", "animations", CATEGORY_ANIMATIONS);
            addFromIndex(root, source, "animations/animations-ext.json", "animations", CATEGORY_ANIMATIONS);
            addFromIndex(root, source, "shaders/shaders.json", "shaders", CATEGORY_SHADERS);
            addFromIndex(root, source, "shaders/shaders-ext.json", "shaders", CATEGORY_SHADERS);
            addFromIndex(root, source, "environment_shaders/environment-shaders.json", "environmentShaders", CATEGORY_SHADERS);
            addFromIndex(root, source, "environment_shaders/environment-shaders-ext.json", "environmentShaders", CATEGORY_SHADERS);
            addFromIndex(root, source, "material/materials.json", "materials", CATEGORY_MATERIALS);
            addFromIndex(root, source, "material/materials-ext.json", "materials", CATEGORY_MATERIALS);
            addFromIndex(root, source, "skyboxes/skyboxes.json", "skyboxes", CATEGORY_SKYBOXES);
            addFromIndex(root, source, "skyboxes/skyboxes-ext.json", "skyboxes", CATEGORY_SKYBOXES);
            addFromIndex(root, source, "terrain/terrains.json", "terrains", CATEGORY_TERRAIN);
            addFromIndex(root, source, "terrain/terrains-ext.json", "terrains", CATEGORY_TERRAIN);

            scanEffects(root, source);
            scanTextureFiles(root, source);
            scanStandaloneFiles(root, source);
        }

        private void addFromIndex(File root, String source, String relativeIndex, String arrayKey, String category) {
            File index = findCaseInsensitive(root, relativeIndex);
            if (index == null || !index.isFile()) {
                return;
            }
            try {
                JSONObject json = new JSONObject(Util.readFile(index));
                JSONArray array = json.optJSONArray(arrayKey);
                if (array == null) {
                    return;
                }
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.optJSONObject(i);
                    if (obj == null) {
                        continue;
                    }
                    String name = obj.optString("name", deriveName(obj));
                    String path = primaryPath(obj, category);
                    File file = path == null || path.isBlank() ? null : new File(root, path.replace('/', File.separatorChar));
                    InventoryAsset asset = new InventoryAsset(category, name, source, path, file, root);
                    copyJsonProperties(asset, obj);
                    add(asset);
                }
            } catch (Exception ex) {
                System.err.println("Could not read asset index " + index + ": " + ex.getMessage());
            }
        }

        private String primaryPath(JSONObject obj, String category) {
            if (obj.has("path")) {
                return obj.optString("path", "");
            }
            if (CATEGORY_SKYBOXES.equals(category)) {
                return obj.optString("back", obj.optString("front", ""));
            }
            if (CATEGORY_TERRAIN.equals(category)) {
                return obj.optString("HeightMap", obj.optString("Alpha", ""));
            }
            return "";
        }

        private String deriveName(JSONObject obj) {
            String path = obj.optString("path", "");
            if (path.isBlank()) {
                path = obj.optString("back", obj.optString("HeightMap", ""));
            }
            if (path.isBlank()) {
                return "asset";
            }
            String fileName = new File(path).getName();
            int dot = fileName.lastIndexOf('.');
            return dot > 0 ? fileName.substring(0, dot) : fileName;
        }

        private void copyJsonProperties(InventoryAsset asset, JSONObject obj) {
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = obj.opt(key);
                if (value != null) {
                    asset.put(key, value);
                }
            }
        }

        private void scanEffects(File root, String source) {
            File effects = new File(root, "effects");
            File[] folders = effects.listFiles(File::isDirectory);
            if (folders == null) {
                return;
            }
            for (File folder : folders) {
                File[] files = folder.listFiles((dir, name) -> {
                    String lower = name.toLowerCase(Locale.ROOT);
                    return lower.endsWith(".efkproj") || lower.endsWith(".efkefc");
                });
                if (files != null && files.length > 0) {
                    String relative = relativize(root, folder);
                    InventoryAsset asset = new InventoryAsset(CATEGORY_EFFECTS, folder.getName(), source, relative, folder, root);
                    asset.put("Effect files", files.length);
                    add(asset);
                }
            }
        }

        private void scanTextureFiles(File root, String source) {
            Collection<File> files = listFiles(root, file -> {
                if (!isImageFile(file)) {
                    return false;
                }
                String rel = relativize(root, file).toLowerCase(Locale.ROOT);
                return rel.startsWith("textures/") || rel.startsWith("models/") || rel.startsWith("materials/")
                        || rel.startsWith("material/") || rel.startsWith("effects/");
            });
            for (File file : files) {
                InventoryAsset asset = new InventoryAsset(CATEGORY_TEXTURES, stripExtension(file.getName()), source,
                        relativize(root, file), file, root);
                add(asset);
            }
        }

        private void scanStandaloneFiles(File root, String source) {
            addStandaloneFiles(root, source, "animations", CATEGORY_ANIMATIONS, ".j3o", ".gltf", ".glb", ".anim");
            addStandaloneFiles(root, source, "shaders", CATEGORY_SHADERS, ".j3md", ".j3m");
            addStandaloneFiles(root, source, "environment_shaders", CATEGORY_SHADERS, ".j3md", ".j3m");
            addStandaloneFiles(root, source, "material", CATEGORY_MATERIALS, ".mat", ".j3m");
            addStandaloneFiles(root, source, "Materials", CATEGORY_MATERIALS, ".mat", ".j3m");
            addStandaloneFiles(root, source, "scenes", CATEGORY_SCENES, ".smdesign", ".code", ".png");
        }

        private void addStandaloneFiles(File root, String source, String folderName, String category, String... extensions) {
            File folder = new File(root, folderName);
            if (!folder.isDirectory()) {
                return;
            }
            Collection<File> files = listFiles(folder, file -> hasExtension(file, extensions));
            for (File file : files) {
                InventoryAsset asset = new InventoryAsset(category, stripExtension(file.getName()), source,
                        relativize(root, file), file, root);
                add(asset);
            }
        }

        private void scanDesignerDocuments(File projectRoot) {
            if (projectRoot == null || !projectRoot.isDirectory()) {
                return;
            }
            Collection<File> files = listFiles(projectRoot, file -> {
                String lower = file.getName().toLowerCase(Locale.ROOT);
                return lower.endsWith(".smdesign") || lower.endsWith(".smui")
                        || lower.endsWith(".smeffectdesign") || lower.endsWith(".smshader")
                        || lower.endsWith(".smenvshader") || lower.endsWith(".mat");
            });
            for (File file : files) {
                String lower = file.getName().toLowerCase(Locale.ROOT);
                String category;
                if (lower.endsWith(".smdesign")) {
                    category = CATEGORY_SCENES;
                } else if (lower.endsWith(".smui")) {
                    category = CATEGORY_UI;
                } else if (lower.endsWith(".smeffectdesign")) {
                    category = CATEGORY_EFFECTS;
                } else if (lower.endsWith(".smshader") || lower.endsWith(".smenvshader")) {
                    category = CATEGORY_SHADERS;
                } else {
                    category = CATEGORY_MATERIALS;
                }
                InventoryAsset asset = new InventoryAsset(category, stripExtension(file.getName()), "Designer",
                        relativize(projectRoot, file), file, null);
                add(asset);
            }
        }

        private void add(InventoryAsset asset) {
            String key = asset.category + "|" + asset.source + "|" + asset.path + "|" + asset.name;
            if (seen.add(key.toLowerCase(Locale.ROOT))) {
                assets.add(asset);
            }
        }

        private File findCaseInsensitive(File root, String relativePath) {
            File direct = new File(root, relativePath.replace('/', File.separatorChar));
            if (direct.exists()) {
                return direct;
            }
            String[] parts = relativePath.split("/");
            File current = root;
            for (String part : parts) {
                File[] matches = current.listFiles((dir, name) -> name.equalsIgnoreCase(part));
                if (matches == null || matches.length == 0) {
                    return null;
                }
                current = matches[0];
            }
            return current;
        }

        private Collection<File> listFiles(File root, java.util.function.Predicate<File> predicate) {
            List<File> results = new ArrayList<>();
            collectFiles(root, predicate, results);
            return results;
        }

        private void collectFiles(File root, java.util.function.Predicate<File> predicate, List<File> results) {
            File[] files = root == null ? null : root.listFiles();
            if (files == null) {
                return;
            }
            for (File file : files) {
                if (file.isDirectory()) {
                    collectFiles(file, predicate, results);
                } else if (predicate.test(file)) {
                    results.add(file);
                }
            }
        }

        private boolean hasExtension(File file, String... extensions) {
            String lower = file.getName().toLowerCase(Locale.ROOT);
            for (String ext : extensions) {
                if (lower.endsWith(ext)) {
                    return true;
                }
            }
            return false;
        }

        private String relativize(File root, File file) {
            try {
                return root.getCanonicalFile().toURI().relativize(file.getCanonicalFile().toURI()).getPath();
            } catch (IOException ex) {
                return file.getName();
            }
        }

        private String stripExtension(String name) {
            int dot = name.lastIndexOf('.');
            return dot > 0 ? name.substring(0, dot) : name;
        }
    }

    private static class CategoryIcon implements Icon {
        private final String category;
        private final int width;
        private final int height;

        CategoryIcon(String category, int width, int height) {
            this.category = category;
            this.width = width;
            this.height = height;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color color = colorForCategory(category);
            g2.setColor(new Color(27, 31, 38));
            g2.fillRoundRect(x, y, width, height, 10, 10);
            g2.setColor(color);
            g2.fillRoundRect(x + 12, y + 12, width - 24, height - 24, 8, 8);
            g2.setColor(Color.WHITE);
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, Math.max(14f, width / 4f)));
            String text = shortLabel(category);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, x + (width - fm.stringWidth(text)) / 2, y + (height + fm.getAscent()) / 2 - 4);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return width;
        }

        @Override
        public int getIconHeight() {
            return height;
        }

        private static Color colorForCategory(String category) {
            if (CATEGORY_MODELS.equals(category)) return new Color(86, 151, 255);
            if (CATEGORY_AUDIO.equals(category)) return new Color(44, 180, 124);
            if (CATEGORY_SPRITES.equals(category)) return new Color(238, 163, 65);
            if (CATEGORY_FONTS.equals(category)) return new Color(181, 120, 242);
            if (CATEGORY_ANIMATIONS.equals(category)) return new Color(236, 89, 112);
            if (CATEGORY_TEXTURES.equals(category)) return new Color(62, 190, 205);
            if (CATEGORY_EFFECTS.equals(category)) return new Color(245, 96, 77);
            return new Color(120, 138, 160);
        }

        private static String shortLabel(String category) {
            if (category == null || category.length() < 2) {
                return "?";
            }
            if (CATEGORY_UI.equals(category)) {
                return "UI";
            }
            return category.substring(0, Math.min(2, category.length())).toUpperCase(Locale.ROOT);
        }
    }

}
