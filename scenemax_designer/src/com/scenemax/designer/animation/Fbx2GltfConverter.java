package com.scenemax.designer.animation;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class Fbx2GltfConverter {

    private static final String BUNDLED_ZIP_RESOURCE = "fbx2gltf/FBX2glTF-windows-x86_64.zip";
    private static final String EXECUTABLE_NAME = "FBX2glTF-windows-x86_64.exe";
    private static File bundledExecutable;

    private Fbx2GltfConverter() {
    }

    static ConversionResult convertToGlb(File sourceFile) throws IOException {
        File executable = resolveExecutable();
        File outputRoot = Files.createTempDirectory("scenemax-fbx2gltf-output-").toFile();
        File outputBase = new File(outputRoot, stripExtension(sourceFile.getName()));
        File outputGlb = new File(outputRoot, outputBase.getName() + ".glb");

        List<String> command = new ArrayList<>();
        command.add(executable.getAbsolutePath());
        command.add("--binary");
        command.add("--skinning-weights");
        command.add("4");
        command.add("--anim-framerate");
        command.add("bake30");
        command.add("--input");
        command.add(sourceFile.getAbsolutePath());
        command.add("--output");
        command.add(outputBase.getAbsolutePath());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        File logFile = new File(outputRoot, "fbx2gltf.log");
        builder.redirectOutput(logFile);
        Process process = builder.start();
        String output;
        try {
            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            output = logFile.isFile() ? FileUtils.readFileToString(logFile, StandardCharsets.UTF_8) : "";
            if (!finished) {
                process.destroyForcibly();
                FileUtils.deleteQuietly(outputRoot);
                throw new IOException("FBX2glTF timed out while converting " + sourceFile.getName() + ".\n" + tail(output));
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                FileUtils.deleteQuietly(outputRoot);
                throw new IOException("FBX2glTF failed with exit code " + exitCode + ".\n" + tail(output));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            FileUtils.deleteQuietly(outputRoot);
            throw new IOException("FBX2glTF conversion was interrupted.", e);
        }

        if (!outputGlb.isFile()) {
            FileUtils.deleteQuietly(outputRoot);
            throw new IOException("FBX2glTF did not create a GLB file for " + sourceFile.getName() + ".\n" + tail(output));
        }
        return new ConversionResult(outputGlb, outputRoot);
    }

    private static File resolveExecutable() throws IOException {
        String configured = System.getProperty("scenemax.fbx2gltf.path");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("SCENEMAX_FBX2GLTF");
        }
        if (configured != null && !configured.isBlank()) {
            File executable = new File(configured);
            if (executable.isFile()) {
                return executable;
            }
            throw new IOException("Configured FBX2glTF executable was not found: " + executable.getAbsolutePath());
        }

        if (!isWindows()) {
            throw new IOException("The bundled FBX2glTF converter is currently packaged for Windows only. "
                    + "Set scenemax.fbx2gltf.path or SCENEMAX_FBX2GLTF to a platform-specific FBX2glTF executable.");
        }
        return extractBundledExecutable();
    }

    private static synchronized File extractBundledExecutable() throws IOException {
        if (bundledExecutable != null && bundledExecutable.isFile()) {
            return bundledExecutable;
        }

        InputStream zipStream = openBundledZipStream();
        if (zipStream == null) {
            throw new IOException("Bundled FBX2glTF converter resource is missing: " + BUNDLED_ZIP_RESOURCE);
        }

        File targetRoot = new File(System.getProperty("java.io.tmpdir"), "scenemax-fbx2gltf-0.13.1");
        File executable = new File(targetRoot, "FBX2glTF-windows-x86_64/" + EXECUTABLE_NAME);
        if (!executable.isFile()) {
            FileUtils.deleteQuietly(targetRoot);
            if (!targetRoot.mkdirs() && !targetRoot.isDirectory()) {
                throw new IOException("Failed to create FBX2glTF extraction folder: " + targetRoot.getAbsolutePath());
            }
            unzip(zipStream, targetRoot);
        } else {
            zipStream.close();
        }

        if (!executable.isFile()) {
            throw new IOException("Bundled FBX2glTF executable was not found after extraction: " + EXECUTABLE_NAME);
        }
        bundledExecutable = executable;
        return bundledExecutable;
    }

    private static InputStream openBundledZipStream() throws IOException {
        InputStream zipStream = Fbx2GltfConverter.class.getClassLoader().getResourceAsStream(BUNDLED_ZIP_RESOURCE);
        if (zipStream != null) {
            return zipStream;
        }

        File[] candidates = new File[]{
                new File("scenemax_designer/build/resources/main/" + BUNDLED_ZIP_RESOURCE),
                new File("scenemax_designer/assets/" + BUNDLED_ZIP_RESOURCE),
                new File("build/resources/main/" + BUNDLED_ZIP_RESOURCE),
                new File("assets/" + BUNDLED_ZIP_RESOURCE)
        };
        for (File candidate : candidates) {
            if (candidate.isFile()) {
                return Files.newInputStream(candidate.toPath());
            }
        }
        return null;
    }

    private static void unzip(InputStream zipStream, File targetRoot) throws IOException {
        Path rootPath = targetRoot.toPath().toAbsolutePath().normalize();
        try (ZipInputStream zip = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path target = rootPath.resolve(entry.getName()).normalize();
                if (!target.startsWith(rootPath)) {
                    throw new IOException("Blocked unsafe FBX2glTF zip entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String stripExtension(String fileName) {
        String name = fileName == null ? "model" : fileName;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String tail(String output) {
        if (output == null) {
            return "";
        }
        int maxLength = 6000;
        return output.length() <= maxLength ? output : output.substring(output.length() - maxLength);
    }

    static final class ConversionResult {
        private final File glbFile;
        private final File cleanupRoot;

        private ConversionResult(File glbFile, File cleanupRoot) {
            this.glbFile = glbFile;
            this.cleanupRoot = cleanupRoot;
        }

        File getGlbFile() {
            return glbFile;
        }

        void cleanup() {
            FileUtils.deleteQuietly(cleanupRoot);
        }
    }
}
