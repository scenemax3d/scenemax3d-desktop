package com.scenemaxeng.projector;

import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.scene.Geometry;
import com.jme3.scene.SceneGraphVisitor;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.plugins.AWTLoader;
import com.scenemaxeng.common.types.ResourceVideo;
import com.scenemaxeng.compiler.ProgramDef;
import com.scenemaxeng.compiler.VideoPlayCommand;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

public class VideoPlayController extends SceneMaxBaseController {

    private final AWTLoader awtLoader = new AWTLoader();
    private VideoPlaybackDecoder decoder;
    private Texture2D videoTexture;
    private boolean initialized;
    private boolean errorReported;

    public VideoPlayController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, VideoPlayCommand cmd) {
        super(app, prg, scope, cmd);
    }

    @Override
    public boolean run(float tpf) {
        if (forceStop) {
            return true;
        }
        if (!initialized) {
            initialized = true;
            if (!startPlayback()) {
                return true;
            }
        }

        BufferedImage frame = decoder.pollFrame();
        if (frame != null) {
            videoTexture.setImage(awtLoader.load(frame, true));
        }

        String error = decoder.getErrorMessage();
        if (error != null && !errorReported) {
            errorReported = true;
            app.handleRuntimeError("Video playback failed: " + error);
            return true;
        }

        return decoder.isFinished();
    }

    @Override
    public void dispose() {
        if (decoder != null) {
            decoder.close();
            decoder = null;
        }
    }

    private boolean startPlayback() {
        VideoPlayCommand play = (VideoPlayCommand) cmd;
        ResourceVideo video = resolveVideoResource(play);
        if (video == null) {
            app.handleRuntimeError("Video resource '" + play.targetVar + "' was not found");
            return false;
        }

        File videoFile = app.resolveRuntimeResourceFile(video.path);
        if (videoFile == null || !videoFile.isFile()) {
            app.handleRuntimeError("Video file was not found: " + video.path);
            return false;
        }

        RunTimeVarDef target = app.findVarRuntime(prg, scope, play.targetObjectVar);
        if (target == null) {
            app.handleRuntimeError("Video target object '" + play.targetObjectVar + "' was not found");
            return false;
        }

        Spatial targetSpatial = app.getEntitySpatial(target.varName, target.varDef.varType);
        if (targetSpatial == null) {
            app.handleRuntimeError("Video target object '" + play.targetObjectVar + "' has no renderable spatial");
            return false;
        }

        videoTexture = new Texture2D();
        videoTexture.setMinFilter(Texture.MinFilter.BilinearNoMipMaps);
        videoTexture.setMagFilter(Texture.MagFilter.Bilinear);
        videoTexture.setWrap(Texture.WrapMode.EdgeClamp);
        videoTexture.setImage(awtLoader.load(createPlaceholderFrame(), true));

        Material material = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        material.setTexture("ColorMap", videoTexture);
        material.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        applyMaterial(targetSpatial, material);

        double startSeconds = parseTimestamp(play.startTimestamp, 0d);
        double endSeconds = parseTimestamp(play.endTimestamp, -1d);
        decoder = new VideoPlaybackDecoder(videoFile, startSeconds, endSeconds, play.reverse, play.loop);
        decoder.start();
        return true;
    }

    private ResourceVideo resolveVideoResource(VideoPlayCommand play) {
        if (play == null || play.varDef == null || play.varDef.resName == null || app.getAssetsMapping() == null) {
            return null;
        }
        String assetId = play.varDef.resName;
        if (assetId.toLowerCase(Locale.ROOT).startsWith("videos.")) {
            assetId = assetId.substring("videos.".length());
        }
        return app.getAssetsMapping().getVideosIndex().get(assetId.toLowerCase(Locale.ROOT));
    }

    private void applyMaterial(Spatial spatial, Material material) {
        spatial.depthFirstTraversal(new SceneGraphVisitor() {
            @Override
            public void visit(Spatial visited) {
                if (visited instanceof Geometry) {
                    ((Geometry) visited).setMaterial(material);
                }
            }
        });
    }

    private double parseTimestamp(String timestamp, double defaultValue) {
        if (timestamp == null || timestamp.trim().isEmpty()) {
            return defaultValue;
        }
        String[] parts = timestamp.trim().split(":");
        try {
            double seconds = 0d;
            for (String part : parts) {
                seconds = seconds * 60d + Double.parseDouble(part);
            }
            return Math.max(0d, seconds);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private BufferedImage createPlaceholderFrame() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR);
        image.setRGB(0, 0, 0xff000000);
        return image;
    }
}
