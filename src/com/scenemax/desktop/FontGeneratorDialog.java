package com.scenemax.desktop;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class FontGeneratorDialog extends JDialog {

    private final JTextField txtAssetName = new JTextField("headline_font");
    private final JComboBox<String> cmbFontFamily = new JComboBox<>();
    private final JComboBox<String> cmbStyle = new JComboBox<>(new String[]{"Plain", "Bold", "Italic", "Bold Italic"});
    private final JSpinner spFontSize = new JSpinner(new SpinnerNumberModel(64, 12, 256, 1));
    private final JSpinner spOutline = new JSpinner(new SpinnerNumberModel(3, 0, 24, 1));
    private final JSpinner spShadowX = new JSpinner(new SpinnerNumberModel(3, -32, 32, 1));
    private final JSpinner spShadowY = new JSpinner(new SpinnerNumberModel(3, -32, 32, 1));
    private final JSpinner spPadding = new JSpinner(new SpinnerNumberModel(6, 0, 32, 1));
    private final JCheckBox chkAntiAlias = new JCheckBox("Smooth edges", true);
    private final JCheckBox chkGradient = new JCheckBox("Gradient fill", true);
    private final JComboBox<String> cmbCharset = new JComboBox<>(new String[]{"Latin + Hebrew", "Basic Latin", "Custom"});
    private final JTextArea txtCharacters = new JTextArea(BitmapFontGenerator.LATIN_AND_HEBREW_CHARSET, 4, 20);
    private final JTextField txtPreview = new JTextField("SceneMax Font 123");
    private final JLabel previewLabel = new JLabel();
    private final JButton btnFillColor = createColorButton(new Color(255, 242, 163));
    private final JButton btnGradientColor = createColorButton(new Color(255, 127, 39));
    private final JButton btnOutlineColor = createColorButton(new Color(51, 18, 77));
    private final JButton btnShadowColor = createColorButton(new Color(0, 0, 0));

    public FontGeneratorDialog(Frame owner) {
        super(owner, "Font Generator", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(buildContent());
        setMinimumSize(new Dimension(1060, 760));
        populateFonts();
        bindEvents();
        pack();
        refreshPreview();
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.weightx = 1.0;

        int row = 0;
        addRow(form, gbc, row++, "Asset Name", txtAssetName);
        addRow(form, gbc, row++, "System Font", cmbFontFamily);
        addRow(form, gbc, row++, "Style", cmbStyle);
        addRow(form, gbc, row++, "Font Size", spFontSize);
        addRow(form, gbc, row++, "Outline Width", spOutline);
        addRow(form, gbc, row++, "Shadow X", spShadowX);
        addRow(form, gbc, row++, "Shadow Y", spShadowY);
        addRow(form, gbc, row++, "Padding", spPadding);
        addRow(form, gbc, row++, "Fill Color", btnFillColor);
        addRow(form, gbc, row++, "Gradient Color", btnGradientColor);
        addRow(form, gbc, row++, "Outline Color", btnOutlineColor);
        addRow(form, gbc, row++, "Shadow Color", btnShadowColor);
        addRow(form, gbc, row++, "Preview Text", txtPreview);
        addRow(form, gbc, row++, "Character Set", cmbCharset);

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        form.add(new JLabel("Characters"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        txtCharacters.setLineWrap(true);
        txtCharacters.setWrapStyleWord(true);
        JScrollPane charsScroll = new JScrollPane(txtCharacters);
        charsScroll.setPreferredSize(new Dimension(220, 96));
        form.add(charsScroll, gbc);

        row++;
        gbc.gridx = 1;
        gbc.gridy = row;
        JPanel toggles = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        toggles.add(chkAntiAlias);
        toggles.add(chkGradient);
        form.add(toggles, gbc);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(form, BorderLayout.NORTH);

        JPanel previewPanel = new JPanel(new BorderLayout(8, 8));
        previewPanel.setBorder(BorderFactory.createTitledBorder("Preview"));
        previewLabel.setHorizontalAlignment(SwingConstants.CENTER);
        previewLabel.setVerticalAlignment(SwingConstants.CENTER);
        previewPanel.add(new JScrollPane(previewLabel), BorderLayout.CENTER);

        JTextArea help = new JTextArea(
                "Exports an AngelCode bitmap font pair (.fnt + .png) into the active project's resources/fonts folder.\n" +
                "The exported font is also registered in fonts-ext.json so SceneMax can load it at runtime."
        );
        help.setEditable(false);
        help.setOpaque(false);
        help.setWrapStyleWord(true);
        help.setLineWrap(true);
        help.setBorder(new EmptyBorder(4, 4, 4, 4));
        previewPanel.add(help, BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, previewPanel);
        splitPane.setResizeWeight(0.35);
        root.add(splitPane, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnPreview = new JButton("Refresh Preview");
        btnPreview.addActionListener(e -> refreshPreview());
        JButton btnExport = new JButton("Export To Project");
        btnExport.addActionListener(e -> exportFont());
        JButton btnCancel = new JButton("Close");
        btnCancel.addActionListener(e -> dispose());
        buttons.add(btnPreview);
        buttons.add(btnExport);
        buttons.add(btnCancel);
        root.add(buttons, BorderLayout.SOUTH);

        return root;
    }

    private void addRow(JPanel panel, GridBagConstraints gbc, int row, String label, Component component) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(component, gbc);
    }

    private void populateFonts() {
        String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        Arrays.sort(families, String.CASE_INSENSITIVE_ORDER);
        for (String family : families) {
            cmbFontFamily.addItem(family);
        }
        cmbFontFamily.setSelectedItem("Arial");
        if (cmbFontFamily.getSelectedItem() == null && cmbFontFamily.getItemCount() > 0) {
            cmbFontFamily.setSelectedIndex(0);
        }
    }

    private void bindEvents() {
        bindPreviewRefresh(txtAssetName);
        bindPreviewRefresh(txtPreview);
        bindPreviewRefresh(txtCharacters);
        cmbFontFamily.addActionListener(e -> refreshPreview());
        cmbStyle.addActionListener(e -> refreshPreview());
        cmbCharset.addActionListener(e -> {
            if ("Latin + Hebrew".equals(cmbCharset.getSelectedItem())) {
                txtCharacters.setText(BitmapFontGenerator.LATIN_AND_HEBREW_CHARSET);
            } else if ("Basic Latin".equals(cmbCharset.getSelectedItem())) {
                txtCharacters.setText(BitmapFontGenerator.BASIC_LATIN_CHARSET);
            }
            refreshPreview();
        });
        chkAntiAlias.addActionListener(e -> refreshPreview());
        chkGradient.addActionListener(e -> refreshPreview());
        bindPreviewRefresh(spFontSize, spOutline, spShadowX, spShadowY, spPadding);
        bindColorChooser(btnFillColor, "Fill Color");
        bindColorChooser(btnGradientColor, "Gradient Color");
        bindColorChooser(btnOutlineColor, "Outline Color");
        bindColorChooser(btnShadowColor, "Shadow Color");
    }

    private void bindPreviewRefresh(JTextComponent component) {
        component.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshPreview();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshPreview();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshPreview();
            }
        });
    }

    private void bindPreviewRefresh(JSpinner... spinners) {
        for (JSpinner spinner : spinners) {
            spinner.addChangeListener(e -> refreshPreview());
        }
    }

    private void bindColorChooser(JButton button, String title) {
        button.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(this, title, button.getBackground());
            if (chosen != null) {
                button.setBackground(chosen);
                refreshPreview();
            }
        });
    }

    private JButton createColorButton(Color color) {
        JButton button = new JButton(" ");
        button.setBackground(color);
        button.setOpaque(true);
        button.setPreferredSize(new Dimension(48, 26));
        return button;
    }

    private void refreshPreview() {
        BitmapFontGenerator.FontGeneratorOptions options = collectOptions();
        BufferedImage preview = BitmapFontGenerator.createPreview(options, txtPreview.getText().trim(), 640, 320);
        previewLabel.setIcon(new ImageIcon(preview));
    }

    private BitmapFontGenerator.FontGeneratorOptions collectOptions() {
        BitmapFontGenerator.FontGeneratorOptions options = new BitmapFontGenerator.FontGeneratorOptions();
        Object family = cmbFontFamily.getSelectedItem();
        if (family != null) {
            options.setFontFamily(family.toString());
        }
        options.setFontStyle(styleFromSelection());
        options.setFontSize((Integer) spFontSize.getValue());
        options.setOutlineWidth((Integer) spOutline.getValue());
        options.setShadowOffsetX((Integer) spShadowX.getValue());
        options.setShadowOffsetY((Integer) spShadowY.getValue());
        options.setPadding((Integer) spPadding.getValue());
        options.setAntiAlias(chkAntiAlias.isSelected());
        options.setGradientEnabled(chkGradient.isSelected());
        options.setFillColor(btnFillColor.getBackground());
        options.setGradientColor(btnGradientColor.getBackground());
        options.setOutlineColor(btnOutlineColor.getBackground());
        options.setShadowColor(btnShadowColor.getBackground());
        options.setCharacters(txtCharacters.getText());
        return options;
    }

    private int styleFromSelection() {
        switch (cmbStyle.getSelectedIndex()) {
            case 1:
                return Font.BOLD;
            case 2:
                return Font.ITALIC;
            case 3:
                return Font.BOLD | Font.ITALIC;
            default:
                return Font.PLAIN;
        }
    }

    private void exportFont() {
        SceneMaxProject project = Util.getActiveProject();
        if (project == null) {
            JOptionPane.showMessageDialog(this, "Open a project first.", "Font Generator", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String assetName = txtAssetName.getText().trim();
        if (!assetName.matches("[A-Za-z0-9_-]+")) {
            JOptionPane.showMessageDialog(this, "Use only letters, digits, '_' or '-' in the asset name.", "Font Generator", JOptionPane.WARNING_MESSAGE);
            return;
        }

        File fontsFolder = new File(project.getResourcesPath() + "/fonts");
        File extIndexFile = new File(fontsFolder, "fonts-ext.json");

        if (fontExists(assetName, new File("./resources/fonts/fonts.json")) || fontExists(assetName, extIndexFile)) {
            JOptionPane.showMessageDialog(this, "A font named '" + assetName + "' already exists.", "Font Generator", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            BitmapFontGenerator.GeneratedFont generated = BitmapFontGenerator.generate(collectOptions(), fontsFolder, assetName);
            JSONObject extIndex = readFontsIndex(extIndexFile);
            registerFont(extIndex, assetName, "fonts/" + generated.getFntFile().getName());
            Util.writeFile(extIndexFile.getAbsolutePath(), extIndex.toString(2));
            JOptionPane.showMessageDialog(
                    this,
                    "Font exported to:\n" + generated.getFntFile().getAbsolutePath(),
                    "Font Generator",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Font Generator", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JSONObject readFontsIndex(File file) {
        if (!file.exists()) {
            return new JSONObject().put("fonts", new JSONArray());
        }
        String content = Util.readFile(file);
        if (content == null || content.isBlank()) {
            return new JSONObject().put("fonts", new JSONArray());
        }
        return new JSONObject(content);
    }

    private boolean fontExists(String assetName, File jsonFile) {
        JSONObject json = readFontsIndex(jsonFile);
        JSONArray fonts = json.optJSONArray("fonts");
        if (fonts == null) {
            return false;
        }
        for (int i = 0; i < fonts.length(); i++) {
            JSONObject font = fonts.getJSONObject(i);
            if (assetName.equalsIgnoreCase(font.optString("name"))) {
                return true;
            }
        }
        return false;
    }

    private void registerFont(JSONObject extIndex, String assetName, String path) {
        JSONArray fonts = extIndex.optJSONArray("fonts");
        if (fonts == null) {
            fonts = new JSONArray();
            extIndex.put("fonts", fonts);
        }
        JSONObject font = new JSONObject();
        font.put("name", assetName);
        font.put("path", path);
        fonts.put(font);
    }
}
