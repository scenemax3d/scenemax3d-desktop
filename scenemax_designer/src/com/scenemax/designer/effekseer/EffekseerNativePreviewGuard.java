package com.scenemax.designer.effekseer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;

final class EffekseerNativePreviewGuard {

    private static final String PROPERTY_NATIVE_PREVIEW = "scenemax.effekseer.nativePreview";
    private static final long RECENT_CRASH_WINDOW_MS = 30L * 24L * 60L * 60L * 1000L;
    private static volatile CrashAssessment cachedAssessment;

    private EffekseerNativePreviewGuard() {
    }

    static boolean isNativePreviewAllowed() {
        String mode = nativePreviewMode();
        if (isForcedDisabled(mode)) {
            return false;
        }
        return true;
    }

    static String getAvailabilityMessage() {
        String mode = nativePreviewMode();
        if (isForcedEnabled(mode)) {
            return "native preview forced on by -" + PROPERTY_NATIVE_PREVIEW + "=" + mode;
        }
        if (isForcedDisabled(mode)) {
            return "native preview disabled by -" + PROPERTY_NATIVE_PREVIEW + "=" + mode;
        }

        CrashAssessment assessment = findRecentNativePreviewCrash();
        if (assessment.detected) {
            return "recent native OpenGL crash report detected: "
                    + assessment.file.getName()
                    + ". Native preview remains enabled; start with -"
                    + PROPERTY_NATIVE_PREVIEW
                    + "=false to disable it.";
        }
        return "native preview guard found no recent matching crash reports";
    }

    static String getModeDescription() {
        return PROPERTY_NATIVE_PREVIEW + "=" + nativePreviewMode()
                + "; " + getAvailabilityMessage();
    }

    private static String nativePreviewMode() {
        String configured = System.getProperty(PROPERTY_NATIVE_PREVIEW, "auto");
        return configured == null ? "auto" : configured.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isForcedEnabled(String mode) {
        return "true".equals(mode) || "on".equals(mode) || "enabled".equals(mode) || "force".equals(mode);
    }

    private static boolean isForcedDisabled(String mode) {
        return "false".equals(mode) || "off".equals(mode) || "disabled".equals(mode);
    }

    private static CrashAssessment findRecentNativePreviewCrash() {
        CrashAssessment assessment = cachedAssessment;
        if (assessment != null) {
            return assessment;
        }
        assessment = scanRecentCrashReports();
        cachedAssessment = assessment;
        return assessment;
    }

    private static CrashAssessment scanRecentCrashReports() {
        File workingDir = new File(System.getProperty("user.dir", "."));
        File[] reports = workingDir.listFiles((dir, name) ->
                name != null && name.startsWith("hs_err_pid") && name.endsWith(".log"));
        if (reports == null || reports.length == 0) {
            return CrashAssessment.none();
        }

        long cutoff = System.currentTimeMillis() - RECENT_CRASH_WINDOW_MS;
        CrashAssessment newestMatch = CrashAssessment.none();
        for (File report : reports) {
            if (report == null || !report.isFile() || report.lastModified() < cutoff) {
                continue;
            }
            if (looksLikeNativePreviewCrash(report)
                    && (newestMatch.file == null || report.lastModified() > newestMatch.file.lastModified())) {
                newestMatch = CrashAssessment.detected(report);
            }
        }
        return newestMatch;
    }

    private static boolean looksLikeNativePreviewCrash(File report) {
        boolean accessViolation = false;
        boolean jmeThread = false;
        boolean nvidiaOpenGl = false;
        boolean effekseerBridge = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(report))) {
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null && lineCount++ < 1800) {
                String normalized = line.toLowerCase(Locale.ROOT);
                accessViolation |= normalized.contains("exception_access_violation");
                jmeThread |= normalized.contains("jme3 main");
                nvidiaOpenGl |= normalized.contains("nvoglv64.dll");
                effekseerBridge |= normalized.contains("scenemax_effekseer_jni");
                if (accessViolation && jmeThread && nvidiaOpenGl && effekseerBridge) {
                    return true;
                }
            }
        } catch (IOException ignored) {
            return false;
        }
        return false;
    }

    private static final class CrashAssessment {
        private final boolean detected;
        private final File file;

        private CrashAssessment(boolean detected, File file) {
            this.detected = detected;
            this.file = file;
        }

        private static CrashAssessment none() {
            return new CrashAssessment(false, null);
        }

        private static CrashAssessment detected(File file) {
            return new CrashAssessment(true, file);
        }
    }
}
