package com.scenemax.designer.animation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BevyRetargetDesignerLauncher {

    public static Process open(File resourcesFolder, String animationName, String modelName) throws IOException {
        if (resourcesFolder == null || resourcesFolder.getParentFile() == null) {
            throw new IOException("Project resources folder is not available.");
        }
        if (animationName == null || animationName.trim().isEmpty()) {
            throw new IOException("Animation name is required.");
        }

        File projectRoot = resourcesFolder.getParentFile().getCanonicalFile();
        CommandTarget target = commandTarget();
        List<String> command = new ArrayList<>(target.command);
        command.add("retarget-designer");
        command.add("--project-root");
        command.add(projectRoot.getAbsolutePath());
        command.add("--animation");
        command.add(animationName.trim());
        if (modelName != null && !modelName.trim().isEmpty()) {
            command.add("--model");
            command.add(modelName.trim());
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        if (target.workingDirectory != null) {
            builder.directory(target.workingDirectory);
        }
        builder.redirectErrorStream(true);
        return builder.start();
    }

    private static CommandTarget commandTarget() {
        File current = new File(System.getProperty("user.dir", ".")).getAbsoluteFile();
        File repoRoot = findRepoRoot(current);
        File nextgenRoot = new File(repoRoot, "scenemax_projector_nextgen");
        File exe = new File(nextgenRoot, "target/debug/scenemax_projector_nextgen.exe");
        List<String> command = new ArrayList<>();
        if (exe.isFile() && !isStaleDevExecutable(exe, nextgenRoot)) {
            command.add(exe.getAbsolutePath());
            return new CommandTarget(command, nextgenRoot);
        }
        command.add("cargo");
        command.add("run");
        command.add("-p");
        command.add("scenemax_projector_nextgen");
        command.add("--");
        return new CommandTarget(command, nextgenRoot);
    }

    private static boolean isStaleDevExecutable(File exe, File nextgenRoot) {
        long exeModified = exe.lastModified();
        return newerThan(exeModified, new File(nextgenRoot, "crates/projector/src/main.rs"))
                || newerThan(exeModified, new File(nextgenRoot, "crates/runtime/src/lib.rs"))
                || newerThan(exeModified, new File(nextgenRoot, "crates/runtime/src/animation.rs"))
                || newerThan(exeModified, new File(nextgenRoot, "crates/runtime/src/retarget_designer.rs"));
    }

    private static boolean newerThan(long timestamp, File file) {
        return file.isFile() && file.lastModified() > timestamp;
    }

    private static File findRepoRoot(File start) {
        File current = start;
        while (current != null) {
            if (new File(current, "scenemax_projector_nextgen/Cargo.toml").isFile()) {
                return current;
            }
            current = current.getParentFile();
        }
        return start;
    }

    private static class CommandTarget {
        private final List<String> command;
        private final File workingDirectory;

        private CommandTarget(List<String> command, File workingDirectory) {
            this.command = command;
            this.workingDirectory = workingDirectory;
        }
    }
}
