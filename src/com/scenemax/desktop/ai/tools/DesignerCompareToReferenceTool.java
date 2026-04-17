package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import com.scenemax.desktop.ai.ToolPaths;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class DesignerCompareToReferenceTool extends AbstractSceneMaxTool {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Override
    public String getName() {
        return "designer.compare_to_reference";
    }

    @Override
    public String getDescription() {
        return "Compares a reference image to a designer snapshot and returns structural similarity signals and improvement hints. Accepts a workspace image path or a directly attached image file object.";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject fileInputSchema = new JSONObject()
                .put("description", "Either a workspace image path string or an attached-file object. Attached files can provide local_path/path/url/download_url and will be staged into workspace/tmp/mcp-inputs when needed.")
                .put("oneOf", new JSONArray()
                        .put(new JSONObject()
                                .put("type", "string")
                                .put("description", "Path to an image file, resolved relative to the selected base when not absolute."))
                        .put(new JSONObject()
                                .put("type", "object")
                                .put("description", "Attached-file object passed through the tool layer.")
                                .put("properties", new JSONObject()
                                        .put("path", new JSONObject().put("type", "string").put("description", "Workspace or absolute image path."))
                                        .put("local_path", new JSONObject().put("type", "string").put("description", "Local filesystem path supplied by the caller."))
                                        .put("download_url", new JSONObject().put("type", "string").put("description", "Download URL for staged external files."))
                                        .put("url", new JSONObject().put("type", "string").put("description", "Generic file URL for staged external files."))
                                        .put("name", new JSONObject().put("type", "string").put("description", "Display name used when staging the file."))
                                        .put("filename", new JSONObject().put("type", "string").put("description", "Alternate filename field used by some clients."))
                                        .put("content_type", new JSONObject().put("type", "string").put("description", "Optional MIME type hint."))
                                        .put("mime_type", new JSONObject().put("type", "string").put("description", "Optional MIME type hint.")))));
        return new JSONObject()
                .put("type", "object")
                .put("description", "Compare a reference image against an existing designer snapshot, or auto-capture a new reliable snapshot first.")
                .put("properties", new JSONObject()
                        .put("reference_image_path", new JSONObject(fileInputSchema.toString()).put("description", "Reference image to compare against. Alias of reference_image."))
                        .put("reference_image", new JSONObject(fileInputSchema.toString()).put("description", "Reference image to compare against. Alias of reference_image_path."))
                        .put("snapshot_path", new JSONObject()
                                .put("type", "string")
                                .put("description", "Optional existing designer snapshot PNG to compare. If omitted, the tool auto-captures one when capture_if_missing is true."))
                        .put("output_snapshot_path", new JSONObject()
                                .put("type", "string")
                                .put("description", "Optional output path for an auto-captured snapshot. Ignored when snapshot_path is provided."))
                        .put("base", new JSONObject()
                                .put("type", "string")
                                .put("description", "Path base used to resolve relative reference_image_path, reference_image.path, snapshot_path, and output_snapshot_path values.")
                                .put("enum", new JSONArray().put("workspace").put("project").put("scripts").put("resources"))
                                .put("default", "workspace"))
                        .put("capture_if_missing", new JSONObject()
                                .put("type", "boolean")
                                .put("description", "When true and snapshot_path is omitted, auto-captures a reliable designer snapshot before comparing.")
                                .put("default", true))
                        .put("capture_clean", new JSONObject()
                                .put("type", "boolean")
                                .put("description", "Whether the auto-captured snapshot should hide editor-only aids in scene designers.")
                                .put("default", true))
                        .put("capture_width", new JSONObject()
                                .put("type", "integer")
                                .put("description", "Requested width in pixels for an auto-captured snapshot.")
                                .put("default", 1280))
                        .put("capture_height", new JSONObject()
                                .put("type", "integer")
                                .put("description", "Requested height in pixels for an auto-captured snapshot.")
                                .put("default", 720)))
                .put("required", new JSONArray());
    }

    @Override
    public JSONObject getOutputSchema() {
        JSONObject boxSchema = new JSONObject()
                .put("type", "object")
                .put("description", "Normalized silhouette bounds extracted from the image.")
                .put("properties", new JSONObject()
                        .put("minX", new JSONObject().put("type", "integer"))
                        .put("minY", new JSONObject().put("type", "integer"))
                        .put("maxX", new JSONObject().put("type", "integer"))
                        .put("maxY", new JSONObject().put("type", "integer"))
                        .put("widthNorm", new JSONObject().put("type", "number"))
                        .put("heightNorm", new JSONObject().put("type", "number"))
                        .put("centerXNorm", new JSONObject().put("type", "number"))
                        .put("centerYNorm", new JSONObject().put("type", "number")))
                .put("required", new JSONArray()
                        .put("minX")
                        .put("minY")
                        .put("maxX")
                        .put("maxY")
                        .put("widthNorm")
                        .put("heightNorm")
                        .put("centerXNorm")
                        .put("centerYNorm"));

        return new JSONObject()
                .put("type", "object")
                .put("description", "Comparison results between the reference image and the designer snapshot.")
                .put("properties", new JSONObject()
                        .put("referenceImagePath", new JSONObject().put("type", "string").put("description", "Resolved local path of the reference image used for comparison."))
                        .put("snapshotPath", new JSONObject().put("type", "string").put("description", "Resolved local path of the designer snapshot used for comparison."))
                        .put("metrics", new JSONObject()
                                .put("type", "object")
                                .put("description", "Aggregate comparison metrics. Lower MSE values and a higher overallScore are better.")
                                .put("properties", new JSONObject()
                                        .put("grayscaleMse", new JSONObject().put("type", "number"))
                                        .put("edgeMse", new JSONObject().put("type", "number"))
                                        .put("widthRatio", new JSONObject().put("type", "number"))
                                        .put("heightRatio", new JSONObject().put("type", "number"))
                                        .put("centerDeltaX", new JSONObject().put("type", "number"))
                                        .put("centerDeltaY", new JSONObject().put("type", "number"))
                                        .put("referenceSymmetry", new JSONObject().put("type", "number"))
                                        .put("snapshotSymmetry", new JSONObject().put("type", "number"))
                                        .put("overallScore", new JSONObject().put("type", "number").put("minimum", 0).put("maximum", 1)))
                                .put("required", new JSONArray()
                                        .put("grayscaleMse")
                                        .put("edgeMse")
                                        .put("widthRatio")
                                        .put("heightRatio")
                                        .put("centerDeltaX")
                                        .put("centerDeltaY")
                                        .put("referenceSymmetry")
                                        .put("snapshotSymmetry")
                                        .put("overallScore")))
                        .put("referenceBox", boxSchema)
                        .put("snapshotBox", boxSchema)
                        .put("suggestions", new JSONObject()
                                .put("type", "array")
                                .put("description", "Human-readable hints for the next scene-improvement pass.")
                                .put("items", new JSONObject().put("type", "string"))))
                .put("required", new JSONArray()
                        .put("referenceImagePath")
                        .put("snapshotPath")
                        .put("metrics")
                        .put("referenceBox")
                        .put("snapshotBox")
                        .put("suggestions"));
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        Object referenceInput = arguments.has("reference_image")
                ? arguments.opt("reference_image")
                : arguments.opt("reference_image_path");
        Path referencePath = ToolPaths.resolveFlexibleFileInput(
                context,
                referenceInput,
                optionalString(arguments, "base", "workspace"),
                "reference_image");

        Path snapshotPath;
        String rawSnapshot = arguments.optString("snapshot_path", "").trim();
        if (!rawSnapshot.isEmpty()) {
            snapshotPath = ToolPaths.resolvePath(context, rawSnapshot, optionalString(arguments, "base", "workspace"));
        } else if (optionalBoolean(arguments, "capture_if_missing", true)) {
            MainApp host = context.getHost();
            if (host == null) {
                throw new IllegalStateException("A running IDE host is required to capture a snapshot automatically.");
            }
            String rawOutputSnapshot = arguments.optString("output_snapshot_path", "").trim();
            if (!rawOutputSnapshot.isEmpty()) {
                snapshotPath = ToolPaths.resolvePath(context, rawOutputSnapshot, optionalString(arguments, "base", "workspace"));
            } else {
                snapshotPath = context.getWorkspaceRoot()
                        .resolve("tmp")
                        .resolve("mcp-captures")
                        .resolve("designer_compare_snapshot_" + FORMATTER.format(LocalDateTime.now()) + ".png")
                        .normalize()
                        .toAbsolutePath();
                ToolPaths.ensureAllowed(context, snapshotPath);
            }
            if (snapshotPath.getParent() != null) {
                Files.createDirectories(snapshotPath.getParent());
            }
            host.captureActiveDesignerCanvasForAutomationReliable(
                    snapshotPath.toFile(),
                    optionalInt(arguments, "capture_width", 1280),
                    optionalInt(arguments, "capture_height", 720),
                    optionalBoolean(arguments, "capture_clean", true));
        } else {
            throw new IllegalArgumentException("Provide snapshot_path or enable capture_if_missing.");
        }

        BufferedImage reference = ImageIO.read(referencePath.toFile());
        BufferedImage snapshot = ImageIO.read(snapshotPath.toFile());
        if (reference == null) {
            throw new IllegalArgumentException("Unable to read reference image: " + referencePath);
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("Unable to read snapshot image: " + snapshotPath);
        }

        BufferedImage scaledReference = scale(reference, 256, 256);
        BufferedImage scaledSnapshot = scale(snapshot, 256, 256);
        double[][] refGray = grayscale(scaledReference);
        double[][] snapGray = grayscale(scaledSnapshot);
        double[][] refEdge = edgeMap(refGray);
        double[][] snapEdge = edgeMap(snapGray);

        double grayscaleMse = mse(refGray, snapGray);
        double edgeMse = mse(refEdge, snapEdge);
        BBox refBox = silhouetteBox(refGray, refEdge);
        BBox snapBox = silhouetteBox(snapGray, snapEdge);
        double symmetryRef = symmetry(refEdge);
        double symmetrySnap = symmetry(snapEdge);

        JSONArray suggestions = new JSONArray();
        double widthRatio = ratio(snapBox.widthNorm(), refBox.widthNorm());
        double heightRatio = ratio(snapBox.heightNorm(), refBox.heightNorm());
        double centerDeltaX = snapBox.centerXNorm() - refBox.centerXNorm();
        double centerDeltaY = snapBox.centerYNorm() - refBox.centerYNorm();

        if (widthRatio < 0.92) {
            suggestions.put("The main silhouette is narrower than the reference. Widen the roof span or side wings.");
        } else if (widthRatio > 1.08) {
            suggestions.put("The main silhouette is wider than the reference. Tighten the roof width or side structures.");
        }
        if (heightRatio < 0.92) {
            suggestions.put("The structure reads shorter than the reference. Raise the roof or facade height.");
        } else if (heightRatio > 1.08) {
            suggestions.put("The structure reads taller than the reference. Lower the roof or compress the facade.");
        }
        if (centerDeltaX < -0.03) {
            suggestions.put("The scene mass sits too far left compared to the reference. Shift the facade slightly right.");
        } else if (centerDeltaX > 0.03) {
            suggestions.put("The scene mass sits too far right compared to the reference. Shift the facade slightly left.");
        }
        if (centerDeltaY < -0.03) {
            suggestions.put("The structure sits too high in frame. Lower the building or adjust the camera down.");
        } else if (centerDeltaY > 0.03) {
            suggestions.put("The structure sits too low in frame. Raise the building or adjust the camera up.");
        }
        if (symmetrySnap + 0.08 < symmetryRef) {
            suggestions.put("The reference reads more symmetrical. Improve left/right matching on roof, posts, and side bays.");
        }
        if (edgeMse > 0.035) {
            suggestions.put("The edge structure differs noticeably. Add or refine roof layers, beams, and doorway framing.");
        }
        if (grayscaleMse > 0.05) {
            suggestions.put("The overall light/dark composition is still off. Rebalance plaster, wood, and roof materials.");
        }

        JSONObject metrics = new JSONObject();
        metrics.put("grayscaleMse", grayscaleMse);
        metrics.put("edgeMse", edgeMse);
        metrics.put("widthRatio", widthRatio);
        metrics.put("heightRatio", heightRatio);
        metrics.put("centerDeltaX", centerDeltaX);
        metrics.put("centerDeltaY", centerDeltaY);
        metrics.put("referenceSymmetry", symmetryRef);
        metrics.put("snapshotSymmetry", symmetrySnap);
        metrics.put("overallScore", overallScore(grayscaleMse, edgeMse, widthRatio, heightRatio, centerDeltaX, centerDeltaY));

        JSONObject data = new JSONObject();
        data.put("referenceImagePath", referencePath.toString());
        data.put("snapshotPath", snapshotPath.toString());
        data.put("metrics", metrics);
        data.put("referenceBox", refBox.toJson());
        data.put("snapshotBox", snapBox.toJson());
        data.put("suggestions", suggestions);
        return SceneMaxToolResult.success("Compared the designer snapshot to the reference image.", data);
    }

    private BufferedImage scale(BufferedImage source, int width, int height) {
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = scaled.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(source, 0, 0, width, height, null);
        g2.dispose();
        return scaled;
    }

    private double[][] grayscale(BufferedImage image) {
        double[][] gray = new double[image.getHeight()][image.getWidth()];
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                gray[y][x] = ((0.2126 * r) + (0.7152 * g) + (0.0722 * b)) / 255.0;
            }
        }
        return gray;
    }

    private double[][] edgeMap(double[][] gray) {
        int height = gray.length;
        int width = gray[0].length;
        double[][] edges = new double[height][width];
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                double gx = -gray[y - 1][x - 1] + gray[y - 1][x + 1]
                        - 2 * gray[y][x - 1] + 2 * gray[y][x + 1]
                        - gray[y + 1][x - 1] + gray[y + 1][x + 1];
                double gy = gray[y - 1][x - 1] + 2 * gray[y - 1][x] + gray[y - 1][x + 1]
                        - gray[y + 1][x - 1] - 2 * gray[y + 1][x] - gray[y + 1][x + 1];
                edges[y][x] = Math.min(1.0, Math.sqrt((gx * gx) + (gy * gy)));
            }
        }
        return edges;
    }

    private double mse(double[][] a, double[][] b) {
        double total = 0d;
        int count = 0;
        for (int y = 0; y < a.length; y++) {
            for (int x = 0; x < a[y].length; x++) {
                double delta = a[y][x] - b[y][x];
                total += delta * delta;
                count++;
            }
        }
        return count == 0 ? 0d : total / count;
    }

    private BBox silhouetteBox(double[][] gray, double[][] edges) {
        int minX = gray[0].length;
        int minY = gray.length;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < gray.length; y++) {
            for (int x = 0; x < gray[y].length; x++) {
                boolean occupied = gray[y][x] < 0.92 || edges[y][x] > 0.15;
                if (!occupied) {
                    continue;
                }
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        if (maxX < minX || maxY < minY) {
            return new BBox(0, 0, gray[0].length - 1, gray.length - 1, gray[0].length, gray.length);
        }
        return new BBox(minX, minY, maxX, maxY, gray[0].length, gray.length);
    }

    private double symmetry(double[][] edgeMap) {
        int height = edgeMap.length;
        int width = edgeMap[0].length;
        double totalDiff = 0d;
        int count = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width / 2; x++) {
                double left = edgeMap[y][x];
                double right = edgeMap[y][width - 1 - x];
                totalDiff += Math.abs(left - right);
                count++;
            }
        }
        return count == 0 ? 0d : Math.max(0d, 1d - (totalDiff / count));
    }

    private double ratio(double a, double b) {
        if (b == 0d) {
            return 1d;
        }
        return a / b;
    }

    private double overallScore(double grayscaleMse, double edgeMse, double widthRatio,
                                double heightRatio, double centerDeltaX, double centerDeltaY) {
        double compositionPenalty = Math.abs(1d - widthRatio) + Math.abs(1d - heightRatio)
                + Math.abs(centerDeltaX) + Math.abs(centerDeltaY);
        double raw = 1d - (grayscaleMse * 3.0) - (edgeMse * 4.0) - (compositionPenalty * 0.8);
        return Math.max(0d, Math.min(1d, raw));
    }

    private static final class BBox {
        final int minX;
        final int minY;
        final int maxX;
        final int maxY;
        final int imageWidth;
        final int imageHeight;

        BBox(int minX, int minY, int maxX, int maxY, int imageWidth, int imageHeight) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.imageWidth = imageWidth;
            this.imageHeight = imageHeight;
        }

        double widthNorm() {
            return (maxX - minX + 1d) / imageWidth;
        }

        double heightNorm() {
            return (maxY - minY + 1d) / imageHeight;
        }

        double centerXNorm() {
            return ((minX + maxX) / 2d) / imageWidth;
        }

        double centerYNorm() {
            return ((minY + maxY) / 2d) / imageHeight;
        }

        JSONObject toJson() {
            return new JSONObject()
                    .put("minX", minX)
                    .put("minY", minY)
                    .put("maxX", maxX)
                    .put("maxY", maxY)
                    .put("widthNorm", widthNorm())
                    .put("heightNorm", heightNorm())
                    .put("centerXNorm", centerXNorm())
                    .put("centerYNorm", centerYNorm());
        }
    }
}
