package com.scenemax.desktop;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

final class JavaExtensionBuildTool {
    static final String MARKER_FILE_NAME = ".scenemax-java-extension";
    static final String EXTENSIONS_FOLDER_NAME = "java-extensions";
    static final String EXTENSIONS_INDEX_NAME = "extensions.json";
    static final String COMPILE_LOG_NAME = "java-extension-compile.log";

    private JavaExtensionBuildTool() {
    }

    static boolean isValidJavaClassName(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        String name = value.trim();
        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    static String createInitialJavaSource(String className) {
        return "import com.scenemaxeng.projector.SceneMaxApp;\n"
                + "import com.scenemaxeng.projector.SceneMaxBaseAppState;\n"
                + "\n"
                + "public class " + className + " extends SceneMaxBaseAppState {\n"
                + "    @Override\n"
                + "    protected void onSceneMaxInitialize(SceneMaxApp app) {\n"
                + "        // Access SceneMax runtime services here, for example getEntitySpatial(\"player\") or app.getRootNode().\n"
                + "    }\n"
                + "\n"
                + "    @Override\n"
                + "    public void update(float tpf) {\n"
                + "        // Called every frame while this extension is attached and enabled.\n"
                + "    }\n"
                + "\n"
                + "    @Override\n"
                + "    protected void onEnable() {\n"
                + "    }\n"
                + "\n"
                + "    @Override\n"
                + "    protected void onDisable() {\n"
                + "    }\n"
                + "}\n";
    }

    static BuildResult buildExtensions(File scriptRoot, File outputRoot, Consumer<String> logger) throws IOException {
        BuildResult result = new BuildResult();
        if (scriptRoot == null || !scriptRoot.isDirectory()) {
            writeIndex(outputRoot, result.extensions);
            return result;
        }

        List<File> extensionFolders = findExtensionFolders(scriptRoot);
        if (extensionFolders.isEmpty()) {
            if (outputRoot.exists()) {
                FileUtils.deleteDirectory(outputRoot);
            }
            return result;
        }

        if (outputRoot.exists()) {
            FileUtils.deleteDirectory(outputRoot);
        }
        FileUtils.forceMkdir(outputRoot);

        for (File extensionFolder : extensionFolders) {
            ExtensionBuild extension = buildExtension(scriptRoot, extensionFolder, outputRoot, logger);
            result.extensions.add(extension);
        }

        File scratchFolder = new File(outputRoot, "build");
        if (scratchFolder.exists()) {
            FileUtils.deleteDirectory(scratchFolder);
        }
        writeIndex(outputRoot, result.extensions);
        return result;
    }

    private static ExtensionBuild buildExtension(File scriptRoot, File extensionFolder, File outputRoot,
                                                Consumer<String> logger) throws IOException {
        String extensionName = extensionFolder.getName();
        File primarySource = new File(extensionFolder, extensionName + ".java");
        if (!primarySource.isFile()) {
            throw new IOException("Java extension '" + extensionName + "' is missing " + primarySource.getName());
        }

        List<File> sources = listJavaSources(extensionFolder);
        if (sources.isEmpty()) {
            throw new IOException("Java extension '" + extensionName + "' does not contain Java source files.");
        }

        File buildRoot = new File(outputRoot, "build/" + extensionName);
        File classesDir = new File(buildRoot, "classes");
        FileUtils.forceMkdir(classesDir);

        compileSources(sources, classesDir, logger);

        File jarFile = new File(outputRoot, extensionName + ".jar");
        createJar(classesDir, jarFile);
        FileUtils.deleteDirectory(buildRoot);

        ExtensionBuild extension = new ExtensionBuild();
        extension.name = extensionName;
        extension.className = extensionName;
        extension.sourceFolder = relativePath(extensionFolder, scriptRoot);
        extension.jarFile = jarFile;
        extension.jarRelativePath = jarFile.getName();
        if (logger != null) {
            logger.accept("Built Java extension " + extensionName + " -> " + jarFile.getAbsolutePath());
        }
        return extension;
    }

    private static void compileSources(List<File> sources, File classesDir, Consumer<String> logger) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IOException("No Java compiler is available. Run SceneMax with a JDK to build Java extensions.");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(classesDir));
            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(sources);
            String classpath = buildCompileClasspath();
            List<String> options = List.of(
                    "-classpath", classpath,
                    "-encoding", "UTF-8",
                    "-source", "11",
                    "-target", "11"
            );

            Boolean ok = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits).call();
            if (!Boolean.TRUE.equals(ok)) {
                throw new IOException(formatCompileLog(sources, diagnostics, classpath));
            }
        }
    }

    private static String formatCompileLog(List<File> sources,
                                           DiagnosticCollector<JavaFileObject> diagnostics,
                                           String classpath) {
        String lineBreak = System.lineSeparator();
        StringBuilder msg = new StringBuilder();
        msg.append("SceneMax Java Extension Compilation Failed").append(lineBreak);
        msg.append("Generated: ").append(java.time.LocalDateTime.now()).append(lineBreak).append(lineBreak);

        msg.append("Sources").append(lineBreak);
        for (File source : sources) {
            msg.append("  ").append(source.getAbsolutePath()).append(lineBreak);
        }

        msg.append(lineBreak).append("Diagnostics").append(lineBreak);
        if (diagnostics.getDiagnostics().isEmpty()) {
            msg.append("  The Java compiler did not return detailed diagnostics.").append(lineBreak);
        }
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            File sourceFile = diagnostic.getSource() == null ? null : new File(diagnostic.getSource().toUri());
            msg.append(lineBreak)
                    .append(diagnostic.getKind())
                    .append(" in ")
                    .append(sourceFile == null ? "(unknown source)" : sourceFile.getAbsolutePath())
                    .append(":")
                    .append(diagnostic.getLineNumber())
                    .append(":")
                    .append(diagnostic.getColumnNumber())
                    .append(lineBreak)
                    .append("  ")
                    .append(diagnostic.getMessage(null))
                    .append(lineBreak);
            appendSourceLine(msg, sourceFile, diagnostic.getLineNumber(), diagnostic.getColumnNumber());
        }

        msg.append(lineBreak).append("Compiler Classpath").append(lineBreak);
        String[] classpathEntries = classpath.split(java.util.regex.Pattern.quote(File.pathSeparator));
        for (String entry : classpathEntries) {
            if (!entry.isBlank()) {
                msg.append("  ").append(entry).append(lineBreak);
            }
        }
        return msg.toString();
    }

    private static void appendSourceLine(StringBuilder msg, File sourceFile, long lineNumber, long columnNumber) {
        if (sourceFile == null || lineNumber <= 0 || !sourceFile.isFile()) {
            return;
        }
        try {
            List<String> lines = FileUtils.readLines(sourceFile, StandardCharsets.UTF_8);
            int index = (int) lineNumber - 1;
            if (index < 0 || index >= lines.size()) {
                return;
            }
            String sourceLine = lines.get(index);
            msg.append("  ").append(sourceLine).append(System.lineSeparator());
            if (columnNumber > 0) {
                msg.append("  ");
                for (int i = 1; i < columnNumber; i++) {
                    msg.append(i - 1 < sourceLine.length() && sourceLine.charAt(i - 1) == '\t' ? '\t' : ' ');
                }
                msg.append("^").append(System.lineSeparator());
            }
        } catch (IOException ignored) {
        }
    }

    private static String buildCompileClasspath() {
        Set<String> entries = new LinkedHashSet<>();
        String current = System.getProperty("java.class.path", "");
        if (!current.isBlank()) {
            String[] parts = current.split(java.util.regex.Pattern.quote(File.pathSeparator));
            for (String part : parts) {
                if (!part.isBlank()) {
                    entries.add(part);
                }
            }
        }

        addIfExists(entries, new File("out/artifacts/scenemax_projector-windows.jar"));
        addIfExists(entries, new File("out/artifacts/scenemax_win_projector.jar"));
        addIfExists(entries, new File("scenemax_win_projector/build/classes/java/main"));
        addIfExists(entries, new File("scenemax3d_common_types/build/classes/java/main"));
        addIfExists(entries, new File("scenemax3d_compiler/build/classes/java/main"));
        addIfExists(entries, new File("scenemax_effekseer_runtime/build/classes/java/main"));
        return String.join(File.pathSeparator, entries);
    }

    private static void addIfExists(Set<String> entries, File file) {
        if (file.exists()) {
            entries.add(file.getAbsolutePath());
        }
    }

    private static List<File> findExtensionFolders(File root) {
        List<File> folders = new ArrayList<>();
        collectExtensionFolders(root, folders);
        folders.sort(Comparator.comparing(File::getAbsolutePath));
        return folders;
    }

    private static void collectExtensionFolders(File folder, List<File> folders) {
        File[] children = folder.listFiles();
        if (children == null) {
            return;
        }
        if (new File(folder, MARKER_FILE_NAME).isFile()) {
            folders.add(folder);
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectExtensionFolders(child, folders);
            }
        }
    }

    private static List<File> listJavaSources(File extensionFolder) {
        List<File> sources = new ArrayList<>();
        Iterator<File> files = FileUtils.iterateFiles(extensionFolder, new String[]{"java"}, true);
        while (files.hasNext()) {
            sources.add(files.next());
        }
        sources.sort(Comparator.comparing(File::getAbsolutePath));
        return sources;
    }

    private static void createJar(File classesDir, File jarFile) throws IOException {
        File parent = jarFile.getParentFile();
        if (parent != null) {
            FileUtils.forceMkdir(parent);
        }
        try (JarOutputStream out = new JarOutputStream(new java.io.FileOutputStream(jarFile))) {
            addClassesToJar(classesDir, classesDir, out);
        }
    }

    private static void addClassesToJar(File root, File current, JarOutputStream out) throws IOException {
        File[] children = current.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                addClassesToJar(root, child, out);
                continue;
            }
            String entryName = relativePath(child, root).replace("\\", "/");
            JarEntry entry = new JarEntry(entryName);
            entry.setTime(child.lastModified());
            out.putNextEntry(entry);
            try (FileInputStream in = new FileInputStream(child)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, read);
                }
            }
            out.closeEntry();
        }
    }

    private static void writeIndex(File outputRoot, List<ExtensionBuild> extensions) throws IOException {
        FileUtils.forceMkdir(outputRoot);
        JSONArray arr = new JSONArray();
        for (ExtensionBuild extension : extensions) {
            arr.put(extension.toJson());
        }
        JSONObject root = new JSONObject();
        root.put("version", 1);
        root.put("extensions", arr);
        FileUtils.writeStringToFile(new File(outputRoot, EXTENSIONS_INDEX_NAME), root.toString(2), StandardCharsets.UTF_8);
    }

    private static String relativePath(File file, File root) {
        Path rootPath = root.getAbsoluteFile().toPath().normalize();
        Path filePath = file.getAbsoluteFile().toPath().normalize();
        return rootPath.relativize(filePath).toString().replace("\\", "/");
    }

    static final class BuildResult {
        final List<ExtensionBuild> extensions = new ArrayList<>();

        boolean hasExtensions() {
            return !extensions.isEmpty();
        }
    }

    static final class ExtensionBuild {
        String name;
        String className;
        String sourceFolder;
        File jarFile;
        String jarRelativePath;

        JSONObject toJson() {
            return new JSONObject()
                    .put("name", name)
                    .put("className", className)
                    .put("sourceFolder", sourceFolder)
                    .put("jar", jarRelativePath);
        }
    }
}
