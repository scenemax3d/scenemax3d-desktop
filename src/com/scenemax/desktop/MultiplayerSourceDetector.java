package com.scenemax.desktop;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

final class MultiplayerSourceDetector {
    private static final Pattern MULTIPLAYER_ENTITY_PATTERN =
            Pattern.compile("\\bmultiplayer\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NETWORK_RUNTIME_PATTERN =
            Pattern.compile("\\bnetwork\\s*\\.\\s*(join\\s+session|ready\\b|state\\b|send\\b|on\\b)",
                    Pattern.CASE_INSENSITIVE);

    private MultiplayerSourceDetector() {
    }

    static boolean usesMultiplayer(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return MULTIPLAYER_ENTITY_PATTERN.matcher(code).find()
                || NETWORK_RUNTIME_PATTERN.matcher(code).find();
    }

    static boolean usesMultiplayerInReachableScripts(File scriptRoot, String entryCode) {
        if (usesMultiplayer(entryCode)) {
            return true;
        }
        if (scriptRoot == null || !scriptRoot.exists()) {
            return false;
        }

        ScriptTreeResourceCollector.CollectionResult reachableSources =
                ScriptTreeResourceCollector.collectReachableResources(scriptRoot, null);
        return usesMultiplayerInFiles(reachableSources.scriptFiles)
                || usesMultiplayerInFiles(reachableSources.designerFiles);
    }

    private static boolean usesMultiplayerInFiles(List<String> paths) {
        for (String path : paths) {
            if (path == null || path.isBlank()) {
                continue;
            }
            try {
                if (usesMultiplayer(FileUtils.readFileToString(new File(path), StandardCharsets.UTF_8))) {
                    return true;
                }
            } catch (IOException ignored) {
            }
        }
        return false;
    }
}
