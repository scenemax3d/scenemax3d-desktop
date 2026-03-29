package com.scenemax.desktop;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Manages all Git operations for SceneMax projects by invoking the git CLI.
 */
public class GitManager {

    public static class GitResult {
        public final int exitCode;
        public final String output;
        public final String error;

        public GitResult(int exitCode, String output, String error) {
            this.exitCode = exitCode;
            this.output = output;
            this.error = error;
        }

        public boolean isSuccess() {
            return exitCode == 0;
        }

        public String getFullOutput() {
            if (error != null && !error.isEmpty()) {
                return output + "\n" + error;
            }
            return output;
        }
    }

    public static class GitFileStatus {
        public final String status;
        public final String filePath;

        public GitFileStatus(String status, String filePath) {
            this.status = status;
            this.filePath = filePath;
        }

        public String getStatusLabel() {
            switch (status) {
                case "M": return "Modified";
                case "A": return "Added";
                case "D": return "Deleted";
                case "R": return "Renamed";
                case "C": return "Copied";
                case "U": return "Unmerged";
                case "??": return "Untracked";
                case "MM": return "Modified (staged+unstaged)";
                case "AM": return "Added (modified)";
                default: return status;
            }
        }

        public boolean isStaged() {
            if (status.equals("??")) return false;
            if (status.length() >= 1) {
                char first = status.charAt(0);
                return first != ' ' && first != '?';
            }
            return false;
        }
    }

    public static class BranchInfo {
        public final String name;
        public final boolean isCurrent;
        public final boolean isRemote;
        public final String tracking;

        public BranchInfo(String name, boolean isCurrent, boolean isRemote, String tracking) {
            this.name = name;
            this.isCurrent = isCurrent;
            this.isRemote = isRemote;
            this.tracking = tracking;
        }
    }

    public static class LogEntry {
        public final String hash;
        public final String shortHash;
        public final String author;
        public final String date;
        public final String message;

        public LogEntry(String hash, String shortHash, String author, String date, String message) {
            this.hash = hash;
            this.shortHash = shortHash;
            this.author = author;
            this.date = date;
            this.message = message;
        }
    }

    // ─── Detection & Installation ───

    public static boolean isGitInstalled() {
        try {
            GitResult result = runGitCommand(null, "git", "--version");
            return result.isSuccess();
        } catch (Exception e) {
            return false;
        }
    }

    public static String getGitVersion() {
        GitResult result = runGitCommand(null, "git", "--version");
        return result.isSuccess() ? result.output.trim() : null;
    }

    public static String getGitInstallInstructions() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("windows")) {
            return "Git is not installed on your system.\n\n" +
                    "To install Git on Windows:\n\n" +
                    "1. Download Git from: https://git-scm.com/download/win\n" +
                    "2. Run the installer and follow the setup wizard\n" +
                    "3. Use the default settings (recommended)\n" +
                    "4. Restart SceneMax after installation\n\n" +
                    "Alternatively, install via winget:\n" +
                    "   winget install --id Git.Git -e --source winget\n\n" +
                    "Or via Chocolatey:\n" +
                    "   choco install git";
        } else if (os.contains("mac")) {
            return "Git is not installed on your system.\n\n" +
                    "To install Git on macOS:\n\n" +
                    "1. Open Terminal and run: xcode-select --install\n" +
                    "   (This installs Git as part of Xcode Command Line Tools)\n\n" +
                    "Or install via Homebrew:\n" +
                    "   brew install git";
        } else {
            return "Git is not installed on your system.\n\n" +
                    "To install Git on Linux:\n\n" +
                    "Ubuntu/Debian:  sudo apt install git\n" +
                    "Fedora:         sudo dnf install git\n" +
                    "Arch:           sudo pacman -S git";
        }
    }

    // ─── Configuration ───

    public static String getGlobalConfig(String key) {
        GitResult result = runGitCommand(null, "git", "config", "--global", key);
        return result.isSuccess() ? result.output.trim() : "";
    }

    public static boolean setGlobalConfig(String key, String value) {
        GitResult result = runGitCommand(null, "git", "config", "--global", key, value);
        return result.isSuccess();
    }

    public static String getUserName() {
        return getGlobalConfig("user.name");
    }

    public static String getUserEmail() {
        return getGlobalConfig("user.email");
    }

    public static String getDefaultBranch() {
        String val = getGlobalConfig("init.defaultBranch");
        return val.isEmpty() ? "main" : val;
    }

    public static boolean setUserName(String name) {
        return setGlobalConfig("user.name", name);
    }

    public static boolean setUserEmail(String email) {
        return setGlobalConfig("user.email", email);
    }

    public static boolean setDefaultBranch(String branch) {
        return setGlobalConfig("init.defaultBranch", branch);
    }

    // ─── Repository Status ───

    public static boolean isGitRepository(String projectPath) {
        GitResult result = runGitCommand(projectPath, "git", "rev-parse", "--is-inside-work-tree");
        return result.isSuccess() && result.output.trim().equals("true");
    }

    public static GitResult initRepository(String projectPath) {
        return runGitCommand(projectPath, "git", "init");
    }

    public static String getCurrentBranch(String projectPath) {
        GitResult result = runGitCommand(projectPath, "git", "rev-parse", "--abbrev-ref", "HEAD");
        return result.isSuccess() ? result.output.trim() : "";
    }

    public static List<GitFileStatus> getStatus(String projectPath) {
        List<GitFileStatus> files = new ArrayList<>();
        GitResult result = runGitCommand(projectPath, "git", "status", "--porcelain");
        if (!result.isSuccess()) return files;

        String[] lines = result.output.split("\n");
        for (String line : lines) {
            if (line.length() < 4) continue;
            String status = line.substring(0, 2).trim();
            String path = line.substring(3).trim();
            // Handle quoted paths
            if (path.startsWith("\"") && path.endsWith("\"")) {
                path = path.substring(1, path.length() - 1);
            }
            files.add(new GitFileStatus(status, path));
        }
        return files;
    }

    public static boolean hasUncommittedChanges(String projectPath) {
        GitResult result = runGitCommand(projectPath, "git", "status", "--porcelain");
        return result.isSuccess() && !result.output.trim().isEmpty();
    }

    public static String getRemoteUrl(String projectPath) {
        GitResult result = runGitCommand(projectPath, "git", "remote", "get-url", "origin");
        return result.isSuccess() ? result.output.trim() : "";
    }

    public static boolean hasRemote(String projectPath) {
        GitResult result = runGitCommand(projectPath, "git", "remote");
        return result.isSuccess() && !result.output.trim().isEmpty();
    }

    // ─── Staging ───

    public static GitResult addFile(String projectPath, String filePath) {
        return runGitCommand(projectPath, "git", "add", filePath);
    }

    public static GitResult addAll(String projectPath) {
        return runGitCommand(projectPath, "git", "add", "-A");
    }

    public static GitResult unstageFile(String projectPath, String filePath) {
        return runGitCommand(projectPath, "git", "reset", "HEAD", filePath);
    }

    public static GitResult unstageAll(String projectPath) {
        return runGitCommand(projectPath, "git", "reset", "HEAD");
    }

    // ─── Committing ───

    public static GitResult commit(String projectPath, String message) {
        return runGitCommand(projectPath, "git", "commit", "-m", message);
    }

    // ─── Branching ───

    public static List<BranchInfo> getBranches(String projectPath, boolean includeRemote) {
        List<BranchInfo> branches = new ArrayList<>();

        // Local branches
        GitResult result = runGitCommand(projectPath, "git", "branch", "-v");
        if (result.isSuccess()) {
            for (String line : result.output.split("\n")) {
                if (line.trim().isEmpty()) continue;
                boolean current = line.startsWith("*");
                String name = line.substring(2).trim().split("\\s+")[0];
                String tracking = "";
                if (line.contains("[")) {
                    int start = line.indexOf('[');
                    int end = line.indexOf(']');
                    if (end > start) {
                        tracking = line.substring(start + 1, end);
                    }
                }
                branches.add(new BranchInfo(name, current, false, tracking));
            }
        }

        // Remote branches
        if (includeRemote) {
            result = runGitCommand(projectPath, "git", "branch", "-r");
            if (result.isSuccess()) {
                for (String line : result.output.split("\n")) {
                    String name = line.trim();
                    if (name.isEmpty() || name.contains("->")) continue;
                    branches.add(new BranchInfo(name, false, true, ""));
                }
            }
        }

        return branches;
    }

    public static GitResult createBranch(String projectPath, String branchName) {
        return runGitCommand(projectPath, "git", "branch", branchName);
    }

    public static GitResult checkoutBranch(String projectPath, String branchName) {
        return runGitCommand(projectPath, "git", "checkout", branchName);
    }

    public static GitResult createAndCheckoutBranch(String projectPath, String branchName) {
        return runGitCommand(projectPath, "git", "checkout", "-b", branchName);
    }

    public static GitResult deleteBranch(String projectPath, String branchName) {
        return runGitCommand(projectPath, "git", "branch", "-d", branchName);
    }

    public static GitResult forceDeleteBranch(String projectPath, String branchName) {
        return runGitCommand(projectPath, "git", "branch", "-D", branchName);
    }

    public static GitResult mergeBranch(String projectPath, String branchName) {
        return runGitCommand(projectPath, "git", "merge", branchName);
    }

    public static GitResult renameBranch(String projectPath, String oldName, String newName) {
        return runGitCommand(projectPath, "git", "branch", "-m", oldName, newName);
    }

    // ─── Remote Operations ───

    public static GitResult addRemote(String projectPath, String name, String url) {
        return runGitCommand(projectPath, "git", "remote", "add", name, url);
    }

    public static GitResult removeRemote(String projectPath, String name) {
        return runGitCommand(projectPath, "git", "remote", "remove", name);
    }

    public static GitResult setRemoteUrl(String projectPath, String name, String url) {
        return runGitCommand(projectPath, "git", "remote", "set-url", name, url);
    }

    public static List<String> getRemotes(String projectPath) {
        List<String> remotes = new ArrayList<>();
        GitResult result = runGitCommand(projectPath, "git", "remote", "-v");
        if (result.isSuccess()) {
            for (String line : result.output.split("\n")) {
                if (line.trim().isEmpty()) continue;
                String name = line.split("\\s+")[0];
                if (!remotes.contains(name)) {
                    remotes.add(name);
                }
            }
        }
        return remotes;
    }

    public static GitResult fetch(String projectPath) {
        return runGitCommand(projectPath, "git", "fetch", "--all");
    }

    public static GitResult pull(String projectPath) {
        return runGitCommand(projectPath, "git", "pull");
    }

    public static GitResult pullRebase(String projectPath) {
        return runGitCommand(projectPath, "git", "pull", "--rebase");
    }

    public static GitResult push(String projectPath) {
        return runGitCommand(projectPath, "git", "push");
    }

    public static GitResult pushSetUpstream(String projectPath, String remote, String branch) {
        return runGitCommand(projectPath, "git", "push", "-u", remote, branch);
    }

    public static GitResult cloneRepository(String url, String targetPath) {
        return runGitCommand(null, "git", "clone", url, targetPath);
    }

    // ─── Log ───

    public static List<LogEntry> getLog(String projectPath, int maxCount) {
        List<LogEntry> entries = new ArrayList<>();
        // Use a delimiter that won't appear in normal commit data
        String sep = "<<SEP>>";
        String format = "%H" + sep + "%h" + sep + "%an" + sep + "%ad" + sep + "%s";
        GitResult result = runGitCommand(projectPath, "git", "log",
                "--pretty=format:" + format, "--date=short",
                "-n", String.valueOf(maxCount));

        if (!result.isSuccess()) return entries;

        for (String line : result.output.split("\n")) {
            if (line.trim().isEmpty()) continue;
            String[] parts = line.split(sep, 5);
            if (parts.length == 5) {
                entries.add(new LogEntry(parts[0], parts[1], parts[2], parts[3], parts[4]));
            }
        }
        return entries;
    }

    // ─── Diff ───

    public static String getDiff(String projectPath) {
        GitResult result = runGitCommand(projectPath, "git", "diff");
        return result.isSuccess() ? result.output : "";
    }

    public static String getDiffStaged(String projectPath) {
        GitResult result = runGitCommand(projectPath, "git", "diff", "--staged");
        return result.isSuccess() ? result.output : "";
    }

    public static String getDiffFile(String projectPath, String filePath) {
        GitResult result = runGitCommand(projectPath, "git", "diff", filePath);
        return result.isSuccess() ? result.output : "";
    }

    // ─── Stash ───

    public static GitResult stash(String projectPath) {
        return runGitCommand(projectPath, "git", "stash");
    }

    public static GitResult stashPop(String projectPath) {
        return runGitCommand(projectPath, "git", "stash", "pop");
    }

    public static GitResult stashList(String projectPath) {
        return runGitCommand(projectPath, "git", "stash", "list");
    }

    // ─── Tags ───

    public static GitResult createTag(String projectPath, String tagName, String message) {
        if (message != null && !message.isEmpty()) {
            return runGitCommand(projectPath, "git", "tag", "-a", tagName, "-m", message);
        }
        return runGitCommand(projectPath, "git", "tag", tagName);
    }

    public static GitResult pushTags(String projectPath) {
        return runGitCommand(projectPath, "git", "push", "--tags");
    }

    // ─── Discard Changes ───

    public static GitResult discardFileChanges(String projectPath, String filePath) {
        return runGitCommand(projectPath, "git", "checkout", "--", filePath);
    }

    // ─── .gitignore helper ───

    public static boolean hasGitignore(String projectPath) {
        return new File(projectPath, ".gitignore").exists();
    }

    public static void createDefaultGitignore(String projectPath) throws IOException {
        File gitignore = new File(projectPath, ".gitignore");
        StringBuilder sb = new StringBuilder();
        sb.append("# SceneMax3D project ignores\n");
        sb.append("*.class\n");
        sb.append("*.jar\n");
        sb.append("*.log\n");
        sb.append(".idea/\n");
        sb.append("*.iml\n");
        sb.append("build/\n");
        sb.append("out/\n");
        sb.append(".gradle/\n");
        sb.append("data/\n");
        sb.append("config.properties\n");
        sb.append("*.bak\n");
        sb.append("*.tmp\n");
        sb.append("Thumbs.db\n");
        sb.append(".DS_Store\n");

        try (FileWriter writer = new FileWriter(gitignore)) {
            writer.write(sb.toString());
        }
    }

    // ─── Process execution ───

    public static GitResult runGitCommand(String workingDir, String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            if (workingDir != null) {
                pb.directory(new File(workingDir));
            }
            pb.redirectErrorStream(false);

            // Ensure git uses English output
            pb.environment().put("LC_ALL", "C");
            pb.environment().put("GIT_TERMINAL_PROMPT", "0");

            Process process = pb.start();

            String output = readStream(process.getInputStream());
            String error = readStream(process.getErrorStream());

            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new GitResult(-1, "", "Command timed out after 60 seconds");
            }

            return new GitResult(process.exitValue(), output, error);

        } catch (Exception e) {
            return new GitResult(-1, "", e.getMessage());
        }
    }

    /**
     * Runs a git command with real-time output streaming to a callback.
     */
    public static GitResult runGitCommandWithProgress(String workingDir, ProgressCallback callback, String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            if (workingDir != null) {
                pb.directory(new File(workingDir));
            }
            pb.redirectErrorStream(true);
            pb.environment().put("LC_ALL", "C");
            pb.environment().put("GIT_TERMINAL_PROMPT", "0");

            Process process = pb.start();

            StringBuilder fullOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    fullOutput.append(line).append("\n");
                    if (callback != null) {
                        callback.onProgress(line);
                    }
                }
            }

            boolean finished = process.waitFor(300, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new GitResult(-1, fullOutput.toString(), "Command timed out");
            }

            return new GitResult(process.exitValue(), fullOutput.toString(), "");

        } catch (Exception e) {
            return new GitResult(-1, "", e.getMessage());
        }
    }

    private static String readStream(InputStream stream) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(line);
            }
        }
        return sb.toString();
    }

    public interface ProgressCallback {
        void onProgress(String line);
    }
}
