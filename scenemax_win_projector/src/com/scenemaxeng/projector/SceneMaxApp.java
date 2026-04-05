package com.scenemaxeng.projector;


import com.jayfella.jme.vehicle.skid.SkidMarksState;
import com.scenemax.effekseer.runtime.EffekseerNativeBridge;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.scenemaxeng.common.types.*;
import com.scenemaxeng.compiler.*;
import com.abware.scenemaxlang.parser.SceneMaxParser;
import com.epaga.particles.Emitter;
import com.epaga.particles.ParticleHelper;
import com.epaga.particles.emittershapes.EmitterCone;
import com.epaga.particles.emittershapes.EmitterLine;
import com.epaga.particles.emittershapes.EmitterMesh;
import com.epaga.particles.emittershapes.EmitterSphere;
import com.epaga.particles.influencers.*;
import com.epaga.particles.valuetypes.*;
import com.jayfella.jme.vehicle.*;
import com.jayfella.jme.vehicle.part.Wheel;
import com.jme3.anim.AnimComposer;
import com.jme3.anim.Joint;
import com.jme3.anim.SkinningControl;
import com.jme3.animation.SkeletonControl;
import com.jme3.app.Application;
import com.jme3.app.state.AppState;
import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.audio.AudioNode;

import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.animation.*;
import com.jme3.bullet.collision.shapes.*;
import com.jme3.bullet.control.CharacterControl;
import com.jme3.bullet.control.GhostControl;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.control.VehicleControl;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh;
import com.jme3.effect.shapes.EmitterSphereShape;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.light.DirectionalLight;
import com.jme3.light.LightProbe;
import com.jme3.material.Material;
import com.jme3.material.MatParamTexture;
import com.jme3.material.MaterialDef;
import com.jme3.material.MatParam;
import com.jme3.material.RenderState;
import com.jme3.shader.VarType;
import com.jme3.math.*;
import com.jme3.niftygui.NiftyJmeDisplay;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.DepthOfFieldFilter;
import com.jme3.post.filters.FXAAFilter;
import com.jme3.post.ssao.SSAOFilter;
import com.jme3.profile.AppProfiler;
import com.jme3.renderer.Camera;
import com.jme3.renderer.Caps;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.*;
import com.jme3.scene.control.BillboardControl;
import com.jme3.scene.control.CameraControl;
import com.jme3.scene.control.Control;
import com.jme3.scene.debug.Arrow;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;
import com.jme3.shadow.DirectionalLightShadowFilter;
import com.jme3.system.JmeContext;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.ui.Picture;
import com.jme3.util.SkyFactory;
import com.jme3.util.TangentBinormalGenerator;
import com.jme3.water.WaterFilter;
import com.scenemaxeng.projector.outliner.OutlineFilter;
import com.scenemaxeng.projector.outliner.SelectObjectOutliner;
import com.simsilica.lemur.*;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.component.TbtQuadBackgroundComponent;
import com.simsilica.lemur.style.Attributes;
import com.simsilica.lemur.style.Styles;
import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.screen.Screen;
import jme3utilities.sky.SkyControl;
import jme3utilities.sky.StarsOption;
import org.apache.commons.io.FileUtils;
import org.lwjgl.opengl.Display;


import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;


public class SceneMaxApp extends com.jme3.app.SimpleApplication implements IUiProxy, IApplicationChannel {

    private static final float HP_TO_WATT = 746;
    public ISceneMaxPlugin pluginsCommunicationChannel;
    private Logger debugLogger=null;

    public static final int EVENT_DESTROY = 1000;

    public static int HOST_APP_DEFAULT=0;
    public static int HOST_APP_WINDOWS = 100;
    public static int HOST_APP_WINDOWS_ALLOW_CODE_CHANGE_BUTTON = 110;

    CanvasRect appRect;
    private BulletAppState bulletAppState;

    private HashMap<String,CollisionHandler> collisionHandlers = new HashMap<>();
    private HashMap<String,UserInputDoBlockController> inputHandlers = new HashMap<>();
    private HashMap<String,UserInputDoBlockController> releaseInputHandlers = new HashMap<>();

    private HashMap<String,Integer> keyMapping = new HashMap<>();
    //private HashMap<String, java.lang.Object> csharpRegisters = new HashMap<>();

    private static HashMap<String, GroupInst> groups=new HashMap<>();
    public static HashMap<String,String> geoName2ModelName = new HashMap<>();
    private static HashMap<String,EntityInstBase> geoName2EntityInst = new HashMap<>();
    private static HashMap<String,Node> spheres = new HashMap<>();
    private static HashMap<String,Node> boxes = new HashMap<>();
    private static HashMap<String,Node> cylinders = new HashMap<>();
    private static HashMap<String,Node> hollowCylinders = new HashMap<>();
    private static HashMap<String,Node> quads = new HashMap<>();
    private static HashMap<String, EffekseerInst> effekseerEffects = new HashMap<>();
    //private static HashMap<String,SkyBoxMaterial> skyboxMaterials = new HashMap<>();
    private static HashMap<String,ResourceMaterial> materials = new HashMap<>();
    private static final Map<Geometry, Material> originalMaterials = new WeakHashMap<>();
    private static final Map<Geometry, RenderQueue.Bucket> originalMaterialBuckets = new WeakHashMap<>();
    private static final Set<Material> runtimeShaderMaterials = ConcurrentHashMap.newKeySet();
    private static HashMap<String, AppModel> models = new HashMap<String, AppModel>();

    private static HashMap<String, List<java.lang.Object>> collisionControlsCache=new HashMap<>();
    private static HashMap<String, SpriteEmitter> sprites = new HashMap<String, SpriteEmitter>();
    protected static AssetsMapping assetsMapping = null;
    private static HashMap<String, AudioNode> _audioNodes = new HashMap<>();
    private static HashMap<String, BitmapText> _printChannels = new HashMap<>();
    private static HashMap<String, PictureExt> _drawChannels = new HashMap<>();

    private IAppObserver _appObserver = null;
    private UITerrainHandler terrainHandler = null;

    // Reusable scratch objects to avoid per-frame allocations
    private final float[] _tmpAngles = new float[3];
    private final CollisionResults _tmpCollisionResults = new CollisionResults();

    private ArrayList<SceneMaxBaseController> _controllers = new ArrayList<>();
    private SceneMaxScope mainScope = null;
    private String runTimeError=null;
    private boolean hasRunTimeError=false;
    private int hostAppType = 0;
    private SceneMaxChaseCamera chaseCam;
    public CameraNode attachCameraNode;
    public boolean scenePaused;
    private WaterFilter water;
      private String workingFolder;
    private String currentLevel = "";
    private String entryScriptFileName;
    private ProgramDef prg;
    private float runtimeShaderElapsedTime = 0f;
    private SceneMaxBaseController lastWaitController;
    private SkyControl skyControl;
    private Spatial skybox = null;
    private Picture environmentShaderOverlay;
    private CollisionListener collisionListener;
    private SelectObjectOutliner outliner;
    private MiniMapState miniMapState;

    private int entityInstCounter=0;
    private HashMap<String, EntityInstBase> modelInstances = new HashMap<>();
    private HashMap<String, EntityInstBase> spriteInstances = new HashMap<>();
    private HashMap<String, EntityInstBase> sphereInstances = new HashMap<>();
    private HashMap<String, EntityInstBase> boxInstances = new HashMap<>();
    private HashMap<String, EntityInstBase> cylinderInstances = new HashMap<>();
    private HashMap<String, EntityInstBase> hollowCylinderInstances = new HashMap<>();
    private HashMap<String, EntityInstBase> quadInstances = new HashMap<>();
    private HashMap<String, EntityInstBase> effekseerInstances = new HashMap<>();
    private String projectName = null;
    private DungeonCameraAppState dungeonCameraState = null;
    private FollowCameraAppState followCameraState = null;
    private ExecutorService executorService = null;
    private RunTimeVarDef cameraRuntimeVarDef = null;
    private int eventHandlersCount;
    private String switchStateCode = null;
    private Logger logger;
    private EffekseerRenderProcessor effekseerRenderProcessor;

    // UI system manager for .smui documents
    private com.scenemaxeng.common.ui.widget.UIManager uiManager;
    private float effekseerFrameTpf;

    public void clearThreads() {
        if (executorService!=null) {
            executorService.shutdown();
        }
    }

    public void initThreads() {
        executorService = Executors.newFixedThreadPool(2);
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public SceneMaxApp(int hostAppType) {
        this();
        this.hostAppType=hostAppType;
        this.pluginsCommunicationChannel = new PluginsCommunicationChannel(this);
    }

    public SceneMaxApp() {
        this.logger = Logger.getLogger(SceneMaxApp.class.getName());
        //debugLogger = ProjectorLogger.run();
        ActionLogicalExpressionVm.setApp(this);
        this.showSettings=false;
        initKeyMapping();
        VariableDef camVarDef = new VariableDef();
        camVarDef.varName = "camera";
        camVarDef.varType = VariableDef.VAR_TYPE_CAMERA;
        cameraRuntimeVarDef = new RunTimeVarDef(camVarDef);
    }

    @Override
    public void handleError(String errMsg, Throwable t) {
        Collection<Caps> caps = renderer != null ? renderer.getCaps() : Collections.emptyList();

        Logger logger = Logger.getLogger(SceneMaxApp.class.getName());
        logger.log(Level.SEVERE, errMsg, t);
        if (this.context != null && this.context.getType() != JmeContext.Type.Headless) {
            if (t != null) {

                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                t.printStackTrace(pw);
                String sStackTrace = sw.toString(); // stack trace as a string

                errMsg = errMsg + "\n" + t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : "");
                errMsg+="\n Stack Trace:\n"+sStackTrace;
            }

            errMsg+="\n\nCapabilities:\n"+caps.toString();
            try {
                long currentTimeMillis = System.currentTimeMillis();
                Date currentDate = new Date(currentTimeMillis);
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
                String formattedDateTime = dateFormat.format(currentDate);
                FileUtils.writeStringToFile(new File("exception_"+formattedDateTime),errMsg,StandardCharsets.UTF_8);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to write exception report", e);
            }
        }

        this.clearThreads();
        try {
            this.stop();
        } catch (Exception stopEx) {
            logger.log(Level.SEVERE, "Failed to stop application after error", stopEx);
        }
    }

    private void initKeyMapping() {

        keyMapping.put("a",KeyInput.KEY_A);
        keyMapping.put("b",KeyInput.KEY_B);
        keyMapping.put("c",KeyInput.KEY_C);
        keyMapping.put("d",KeyInput.KEY_D);
        keyMapping.put("e",KeyInput.KEY_E);
        keyMapping.put("f",KeyInput.KEY_F);
        keyMapping.put("g",KeyInput.KEY_G);
        keyMapping.put("h",KeyInput.KEY_H);
        keyMapping.put("i",KeyInput.KEY_I);
        keyMapping.put("j",KeyInput.KEY_J);
        keyMapping.put("k",KeyInput.KEY_K);
        keyMapping.put("l",KeyInput.KEY_L);
        keyMapping.put("m",KeyInput.KEY_M);
        keyMapping.put("n",KeyInput.KEY_N);
        keyMapping.put("o",KeyInput.KEY_O);
        keyMapping.put("p",KeyInput.KEY_P);
        keyMapping.put("q",KeyInput.KEY_Q);
        keyMapping.put("r",KeyInput.KEY_R);
        keyMapping.put("s",KeyInput.KEY_S);
        keyMapping.put("t",KeyInput.KEY_T);
        keyMapping.put("u",KeyInput.KEY_U);
        keyMapping.put("v",KeyInput.KEY_V);
        keyMapping.put("w",KeyInput.KEY_W);
        keyMapping.put("x",KeyInput.KEY_X);
        keyMapping.put("y",KeyInput.KEY_Y);
        keyMapping.put("z",KeyInput.KEY_Z);
        keyMapping.put("up",KeyInput.KEY_UP);
        keyMapping.put("down",KeyInput.KEY_DOWN);
        keyMapping.put("left",KeyInput.KEY_LEFT);
        keyMapping.put("right",KeyInput.KEY_RIGHT);
        keyMapping.put("space",KeyInput.KEY_SPACE);

        keyMapping.put("0",KeyInput.KEY_0);
        keyMapping.put("1",KeyInput.KEY_1);
        keyMapping.put("2",KeyInput.KEY_2);
        keyMapping.put("3",KeyInput.KEY_3);
        keyMapping.put("4",KeyInput.KEY_4);
        keyMapping.put("5",KeyInput.KEY_5);
        keyMapping.put("6",KeyInput.KEY_6);
        keyMapping.put("7",KeyInput.KEY_7);
        keyMapping.put("8",KeyInput.KEY_8);
        keyMapping.put("9",KeyInput.KEY_9);

        keyMapping.put("del",KeyInput.KEY_DELETE);

        keyMapping.put("toggle_console",KeyInput.KEY_F12);
        keyMapping.put("toggle_add_resource",KeyInput.KEY_F10);

    }

    public void start() {
        super.start();
    }

    public void stopScript() {
        clearScene();
        editScript();
        if (_appObserver != null) _appObserver.onEndCode(null);
    }

    public SceneMaxApp(IAppObserver observer) {
        _appObserver=observer;
    }

    public void setObserver(IAppObserver observer) {
        _appObserver=observer;

    }

    /**
     * Initializes the SceneMax runtime for designer mode (without needing an IAppObserver or run()).
     * This sets up mainScope, ProgramDef, and threads so runPartialCode() can be used.
     */
    protected void initDesignerRuntime(String designerProjectPath) {
        this.workingFolder = designerProjectPath != null ? designerProjectPath : "";
        this.currentLevel = "";
        initThreads();
        mainScope = new SceneMaxScope();
        mainScope.mainController.adhereToPauseStatus = false;
        prg = new ProgramDef();

        // Add an implicit wait command to keep the main controller alive
        // (mirrors run() lines 822-825). Without this, the controller finishes
        // immediately and gets removed from _controllers in simpleUpdate().
        WaitStatementCommand finalCommand = new WaitStatementCommand();
        finalCommand.explicitWaitTime = 1800.0f;
        this.lastWaitController = runAction(prg, finalCommand, mainScope);

        // Register the main controller so simpleUpdate() processes it
        // (mirrors run() line 828). This is essential for runPartialCode()
        // commands to execute and create entity nodes in the scene graph.
        registerController(mainScope.mainController);
    }

    protected SceneMaxScope getMainScope() {
        return mainScope;
    }

    @Override
    public void simpleInitApp() {

        assetsMapping = null;

        try {

            File f = new File("./resources");
            if(f.exists()) {
                assetManager.registerLocator(f.getCanonicalPath(), FileLocator.class);
            }

            if(this.projectName!=null) {
                this.logger.log(Level.INFO, "SimpleInitApp projectName = "+this.projectName);
                String projFolder = "./projects/"+this.projectName;
                assetsMapping = new AssetsMapping(projFolder+"/resources");
                assetManager.registerLocator(new File(projFolder+"/resources").getCanonicalPath(), FileLocator.class);
            } else if(workingFolder!=null) {
                this.logger.log(Level.INFO, "SimpleInitApp workingFolder = "+this.workingFolder);
                File wf = new File (workingFolder);
                String projFolder = wf.getParentFile().getParentFile().getAbsolutePath();
                File f2 = new File(projFolder+"/resources");
                if(f2.exists()) {
                    assetsMapping = new AssetsMapping(projFolder + "/resources");
                    assetManager.registerLocator(f2.getCanonicalPath(), FileLocator.class);
                }
            }

            if(assetsMapping==null) {
                assetsMapping = new AssetsMapping();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        loadMaterialsMap();
        //loadSkyBoxMaterials();

        if(_appObserver!=null) {
            _appObserver.init();
        }

        // bullet init
        initBulletAppState();

//////////////////////////////////////////////////////

        this.setDisplayFps(false);
        this.setDisplayStatView(false);

        if (this.flyCam != null) {
            this.flyCam.setEnabled(false); // eliminates the mouse binding to the camera
        }

        // You must add a light to make the model visible
        addLighting();
        ensureEffekseerRenderProcessor();


        terrainHandler = new UITerrainHandler();
        terrainHandler.assetManager=this.assetManager;
        terrainHandler.rootNode = this.rootNode;
        terrainHandler.camera = this.getCamera();

        // Initialize UI system manager
        uiManager = new com.scenemaxeng.common.ui.widget.UIManager(this, guiNode, rootNode, assetsMapping);

        if (inputManager != null) {
            GuiGlobals.initialize(this);
            loadGlassStyle(this);
            addConsoleShowHideListener();
        }

        stateManager.attach(
            new ChunkManager()
        );

    }

    public AssetsMapping getAssetsMapping() {
        return SceneMaxApp.assetsMapping;
    }

    private void initBulletAppState() {
        if(bulletAppState==null) {
            bulletAppState = new BulletAppState();
        }

        stateManager.attach(bulletAppState);
        collisionListener = new CollisionListener(this);
        bulletAppState.getPhysicsSpace().addCollisionListener(collisionListener);
    }

    private final ActionListener addResourceShowHideListener = new ActionListener() {
        @Override
        public void onAction(String name, boolean keyPressed, float tpf) {
            if(keyPressed) {
                toggleAddResource();
            }
        }
    };

    private final ActionListener consoleShowHideListener = new ActionListener() {
        @Override
        public void onAction(String name, boolean keyPressed, float tpf) {

            if(keyPressed) {
                toggleConsole();
            }

        }
    };

    private void addConsoleShowHideListener() {

        inputManager.addMapping("toggle_console", new KeyTrigger(keyMapping.get("toggle_console")));
        inputManager.addListener(consoleShowHideListener, "toggle_console");

        inputManager.addMapping("toggle_add_resource", new KeyTrigger(keyMapping.get("toggle_add_resource")));
        inputManager.addListener(addResourceShowHideListener, "toggle_add_resource");

    }

    private void toggleAddResource() {

        SceneEditorScreen activeScreen = this.stateManager.getState(SceneEditorScreen.class);
        if(activeScreen!=null) {
            activeScreen.exit();
            this.stateManager.detach(activeScreen);
        } else {

            closeConsoleWindow();
            setChaseCameraOff();
            setAttachCameraOff();

            assetManager.registerLocator("resources\\fonts", FileLocator.class);
            NiftyJmeDisplay niftyDisplay = NiftyJmeDisplay.newNiftyJmeDisplay(
                    assetManager, inputManager, audioRenderer, guiViewPort);

            Nifty nifty = niftyDisplay.getNifty();

            try {
                URL menuURL = SceneMaxApp.class.getResource("/Interface/add_resource_window.xml");
                InputStream consoleWindow = menuURL.openStream();
                nifty.fromXml("start", consoleWindow, "start");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            guiViewPort.addProcessor(niftyDisplay);
            Screen sc = nifty.getScreen("start");
            SceneEditorScreen state = (SceneEditorScreen)sc.getScreenController();
            this.stateManager.attach(state);
        }

    }

    private void closeConsoleWindow() {
        SceneMaxScreen activeScreen = this.stateManager.getState(SceneMaxScreen.class);
        if(activeScreen!=null) {
            activeScreen.exit();
            this.stateManager.detach(activeScreen);
        }
    }

    private void toggleConsole() {

        SceneMaxScreen activeScreen = this.stateManager.getState(SceneMaxScreen.class);
        if(activeScreen!=null) {
            activeScreen.exit();
            this.stateManager.detach(activeScreen);
        } else {
            closeEntitiesEditorWindow();
            assetManager.registerLocator("resources\\fonts", FileLocator.class);
            NiftyJmeDisplay niftyDisplay = NiftyJmeDisplay.newNiftyJmeDisplay(
                    assetManager, inputManager, audioRenderer, guiViewPort);

            Nifty nifty = niftyDisplay.getNifty();

            try {
                URL menuURL = SceneMaxApp.class.getResource("/Interface/console_window.xml");
                InputStream consoleWindow = menuURL.openStream();
                nifty.fromXml("start", consoleWindow, "start");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            guiViewPort.addProcessor(niftyDisplay);
            Screen sc = nifty.getScreen("start");
            this.stateManager.attach((AppState) sc.getScreenController());
        }

    }

    private void closeEntitiesEditorWindow() {
        SceneEditorScreen activeScreen = this.stateManager.getState(SceneEditorScreen.class);
        if(activeScreen!=null) {
            activeScreen.exit();
            this.stateManager.detach(activeScreen);
        }
    }

    private void addLighting() {

        DirectionalLight sun = new DirectionalLight(
                new Vector3f(-0.1f, -.7f, -1f).normalizeLocal(),//new Vector3f(1, -.45f, 0.5f).normalizeLocal(),
                ColorRGBA.White.clone()
        );
        rootNode.addLight(sun);

        FilterPostProcessor fpp = new FilterPostProcessor(assetManager);

        DirectionalLightShadowFilter shadowFilter = new DirectionalLightShadowFilter(assetManager, 4096, 4);
        shadowFilter.setLight(sun);
        shadowFilter.setShadowIntensity(0.3f);
        shadowFilter.setShadowZExtend(256);
        shadowFilter.setShadowZFadeLength(128);
        // shadowFilter.setEdgeFilteringMode(EdgeFilteringMode.PCFPOISSON);
        fpp.addFilter(shadowFilter);

        SSAOFilter ssaoFilter = new SSAOFilter();
        fpp.addFilter(ssaoFilter);

        viewPort.addProcessor(fpp);

//        AmbientLight al = new AmbientLight();
//        al.setColor(ColorRGBA.White.mult(0.8f));
//        al.setEnabled(true);
//        rootNode.addLight(al);



    }

    public CanvasRect getCanvasRect() {

        return appRect;

    }

    private void loadMaterialsMap() {
        materials.put("pond", new ResourceMaterial("Textures/Terrain/Pond/Pond.jpg","Textures/Terrain/Pond/Pond_normal.png"));
        materials.put("rock", new ResourceMaterial("Textures/Terrain/Rock/rock.png","Textures/Terrain/Rock/rock_normal.png"));
        materials.put("rock2", new ResourceMaterial("Textures/Terrain/Rock/rock2.jpg","Textures/Terrain/Rock/rock_normal.png"));
        materials.put("brickwall", new ResourceMaterial("Textures/Terrain/BrickWall/brickwall.jpg","Textures/Terrain/BrickWall/brickwall_normal.jpg"));

        materials.put("dirt", new ResourceMaterial("Textures/Terrain/Splat/dirt.jpg","Textures/Terrain/Splat/dirt_normal.png"));
        materials.put("grass", new ResourceMaterial("Textures/Terrain/Splat/grass.jpg","Textures/Terrain/Splat/grass_normal.jpg"));
        materials.put("road", new ResourceMaterial("Textures/Terrain/Splat/road.jpg","Textures/Terrain/Splat/road_normal.png"));

        materials.put("alpha", new ResourceMaterial("Textures/Terrain/Splat/alpha1.png","Textures/Terrain/Splat/alphamap.png"));
        materials.put("alpha2", new ResourceMaterial("Textures/Terrain/Splat/alpha2.png","Textures/Terrain/Splat/alphamap2.png"));
    }


    public static void loadGlassStyle(Application app) {

        Styles styles = GuiGlobals.getInstance().getStyles();
        Attributes attrs;

        TbtQuadBackgroundComponent gradient = TbtQuadBackgroundComponent.create(
                app.getAssetManager().loadTexture("/com/simsilica/lemur/icons/bordered-gradient.png"),
                1f, 1, 1, 126, 126,
                1f, false);

        TbtQuadBackgroundComponent bevel = TbtQuadBackgroundComponent.create(
                app.getAssetManager().loadTexture("/com/simsilica/lemur/icons/bevel-quad.png"),
                1f, 1, 1, 126, 126,
                1f, false);

        TbtQuadBackgroundComponent border = TbtQuadBackgroundComponent.create(
                app.getAssetManager().loadTexture("/com/simsilica/lemur/icons/border.png"),
                1f, 1, 1, 126, 126,
                1f, false);

        TbtQuadBackgroundComponent border2 = TbtQuadBackgroundComponent.create(
                app.getAssetManager().loadTexture("/com/simsilica/lemur/icons/border.png"),
                1f, 1, 1, 126, 126,
                1f, false);

        QuadBackgroundComponent doubleGradient = new QuadBackgroundComponent(new ColorRGBA(.5f, .75f, .85f, .5f));
        doubleGradient.setTexture(app.getAssetManager().loadTexture("/com/simsilica/lemur/icons/double-gradient-128.png"));

        attrs = styles.getSelector("glass");
        attrs.set("fontSize", 24);

        attrs = styles.getSelector("label", "glass");
        attrs.set("insets", new Insets3f(2, 2, 0, 2));
        attrs.set("color", new ColorRGBA(0.5f, 0.75f, 0.75f, 0.85f));

        attrs = styles.getSelector("container", "glass");
        TbtQuadBackgroundComponent gradient_clone_container = gradient.clone();
        gradient_clone_container.setColor(new ColorRGBA(.25f, .5f, .5f, .5f));
        attrs.set("background", gradient_clone_container);

        Command<Button> pressedCommand = new Command<Button>() {
            @Override
            public void execute(Button source) {
                if(source.isPressed()) {
                    source.move(1, -1, 0);
                } else {
                    source.move(-1, 1, 0);
                }
            }
        };

        Map<Button.ButtonAction, List<Command<? super Button>>> stdButtonCommands = new HashMap<>();
        List<Command<? super Button>> listButtonCommands = new ArrayList<>();
        listButtonCommands.add(pressedCommand);
        stdButtonCommands.put(Button.ButtonAction.Down, listButtonCommands);
        stdButtonCommands.put(Button.ButtonAction.Up, listButtonCommands);

//		selector( "title", "glass" ) {
//		    color = color(0.8, 0.9, 1, 0.85f)
//		    highlightColor = color(1, 0.8, 1, 0.85f)
//		    shadowColor = color(0, 0, 0, 0.75f)
//		    shadowOffset = new com.jme3.math.Vector3f(2, -2, -1);
//		    background = new QuadBackgroundComponent( color(0.5, 0.75, 0.85, 0.5) );
//		    background.texture = texture( name:"/com/simsilica/lemur/icons/double-gradient-128.png",
//		                                  generateMips:false )
//		    insets = new Insets3f( 2, 2, 2, 2 );
//
//		    buttonCommands = stdButtonCommands;
//		}

        attrs = styles.getSelector("title", "glass");

        attrs = styles.getSelector("button", "glass");
        TbtQuadBackgroundComponent gradient_clone_button = gradient.clone();
        gradient_clone_button.setColor(new ColorRGBA(0, .75f, .75f, .5f));
        attrs.set("background", gradient_clone_button);
        attrs.set("color", new ColorRGBA(.8f, .9f, 1f, .85f));
        attrs.set("insets", new Insets3f(2, 2, 2, 2));
        attrs.set("buttonCommands", stdButtonCommands);

        styles.setDefaultStyle("glass");
    }

    protected void editScript() {
        if (_appObserver != null) _appObserver.showScriptEditor();
    }


    public void clearScene() {
        this.clearThreads();
        groups.clear();
        _controllers.clear();
        collisionHandlers.clear();
        inputHandlers.clear();
        releaseInputHandlers.clear();
        sprites.clear();

        stopTrackedAudioNodes();
        _printChannels.clear();
        _drawChannels.clear();

        for (Object key : models.keySet().toArray()) {
            AppModel am = models.get(key);
            if (!am.entityInst.varDef.isShared) {
                this.killModel((String) key);
            }
        }

        for (Object key : boxes.keySet().toArray()) {
            if (!geoName2EntityInst.get(key).varDef.isShared) {
                this.killBox((String) key);
            }
        }

        for (Object key : spheres.keySet().toArray()) {
            if (!geoName2EntityInst.get(key).varDef.isShared) {
                this.killSphere((String) key);
            }
        }

        for (Object key : effekseerEffects.keySet().toArray()) {
            EffekseerInst inst = effekseerEffects.get(key);
            if (inst != null && !inst.varDef.isShared) {
                this.killEffekseerEffect((String) key);
            }
        }

        //collisionControlsCache.clear();
        //geoName2ModelName.clear();
        //geoName2EntityInst.clear();

        //rootNode.detachAllChildren();
        rootNode.removeControl(SkinningControl.class);
        this.skyControl = null;

        // Unload all UI systems before clearing guiNode
        unloadAllUI();

        guiNode.detachAllChildren();
        environmentShaderOverlay = null;
        runtimeShaderMaterials.clear();

        if (cam != null) {
            this.cam.setLocation(new Vector3f(0, 0, 10));
            Quaternion q = new Quaternion();
            q.fromAngles(0, FastMath.PI, 0);
            this.cam.setRotation(q);
        }

        entityInstCounter=0;
        //modelInstances = new HashMap<>();
        spriteInstances = new HashMap<>();
        sphereInstances = new HashMap<>();
        boxInstances = new HashMap<>();
        effekseerInstances = new HashMap<>();
        //this.mainScope.clearVars();
    }

    /**
     * Like {@link #clearScene()} but also kills shared/static entities.
     * Used by the designer when switching between documents where ALL
     * entities must be removed regardless of their shared flag.
     */
    public void clearSceneAll() {
        this.clearThreads();
        groups.clear();
        _controllers.clear();
        collisionHandlers.clear();
        inputHandlers.clear();
        releaseInputHandlers.clear();
        sprites.clear();

        stopTrackedAudioNodes();
        _printChannels.clear();
        _drawChannels.clear();

        // Kill ALL models, boxes, spheres — including shared/static ones
        for (Object key : models.keySet().toArray()) {
            this.killModel((String) key);
        }

        for (Object key : boxes.keySet().toArray()) {
            this.killBox((String) key);
        }

        for (Object key : spheres.keySet().toArray()) {
            this.killSphere((String) key);
        }

        for (Object key : effekseerEffects.keySet().toArray()) {
            this.killEffekseerEffect((String) key);
        }

        rootNode.removeControl(SkinningControl.class);
        this.skyControl = null;

        // Unload all UI systems before clearing guiNode
        unloadAllUI();

        guiNode.detachAllChildren();
        environmentShaderOverlay = null;
        runtimeShaderMaterials.clear();

        if (cam != null) {
            this.cam.setLocation(new Vector3f(0, 0, 10));
            Quaternion q = new Quaternion();
            q.fromAngles(0, FastMath.PI, 0);
            this.cam.setRotation(q);
        }

        entityInstCounter=0;
        spriteInstances = new HashMap<>();
        sphereInstances = new HashMap<>();
        boxInstances = new HashMap<>();
        effekseerInstances = new HashMap<>();
    }

    private void clearNode(Node n) {

        for (Spatial child : n.getChildren()) {
            RigidBodyControl ctl = child.getControl(RigidBodyControl.class);
            bulletAppState.getPhysicsSpace().remove(ctl);
            child.removeFromParent();
        }

    }

    public void run(String code) {

        this.initThreads();
        mainScope = mainScope == null ? new SceneMaxScope() : mainScope;
        mainScope.mainController.adhereToPauseStatus=false; // main scope never pauses

        hasRunTimeError=false;
        appRect = new CanvasRect();
        appRect.x = Display.getX();
        appRect.y = Display.getY();
        appRect.width = Display.getWidth();
        appRect.height = Display.getHeight();

        _appObserver.onStartCode();

        // parse & compile the source code
        SceneMaxLanguageParser.parseUsingResource=false; // do not look for manual resource declarations
        SceneMaxLanguageParser parser = new SceneMaxLanguageParser(null,
                this.workingFolder + this.currentLevel);
        if (this.prg != null) {
            parser.setCurrentProgramState(this.prg);
        }
        final ProgramDef prg = parser.parse(code);
        if(prg==null){
            onEndCode();
            return;
        } else if(prg.syntaxErrors.size()>0) {
            onEndCode(prg.syntaxErrors);
            showFloatingMessage(prg.syntaxErrors,"Close Application",0);
            return;
        }

        this.prg = prg;

        // load resources if needed
        for(StatementDef st:prg.requireResourceActions){
            loadResource(st);
        }

        for(FunctionBlockDef f: prg.functions.values()) {
            f.doBlock.creatorScope = mainScope;
        }

        // run actions
        for(StatementDef action:prg.actions){
            runAction(prg,(ActionStatementBase)action,mainScope);
        }

        // implicitly add wait 30 minutes in the end of every program
        WaitStatementCommand finalCommand = new WaitStatementCommand();
        finalCommand.explicitWaitTime=1800.0f;
        this.lastWaitController = runAction(prg,finalCommand,mainScope);

        // run the main scope controller
        registerController(mainScope.mainController);

        if(mainScope.mainController.size()==0){
            //onEndCode();
        }

    }

    public void runPartialCode(String code, SceneMaxScope scope, boolean closeOnError) {

        // parse & compile the source code
        final ProgramDef prg = new SceneMaxLanguageParser(this.prg).parse(code);
        if(prg==null){
            onEndCode();
            return;
        } else if(prg.syntaxErrors.size()>0) {
            if(closeOnError) {
                onEndCode(prg.syntaxErrors);
                showFloatingMessage(prg.syntaxErrors, "Close Application", 0);
            } else {
                showFloatingMessage(prg.syntaxErrors, "OK", 10);
            }
            return;
        }

        if(scope==null) {
            scope=mainScope;
            this.prg.vars.addAll(prg.vars);
            this.prg.vars_index.putAll(prg.vars_index);
        }

        boolean isMainScope = mainScope==scope;
        if(isMainScope && this.lastWaitController!=null) {
            scope.mainController.remove(this.lastWaitController);
        }

        int cnt = scope.mainController.size();

        // load resources if needed
        for(StatementDef st:prg.requireResourceActions){
            loadResource(st);
        }

        for(FunctionBlockDef f: prg.functions.values()) {
            f.doBlock.creatorScope = scope;
        }

        // run actions
        for(StatementDef action:prg.actions){
            runAction(prg,(ActionStatementBase)action,scope);
        }

        scope.mainController.init(cnt==0?0:cnt-1);

        if(isMainScope && this.lastWaitController!=null) {
            scope.mainController.add(this.lastWaitController);
        }

    }

    private void showFloatingMessage(String msg) {
        showFloatingMessage(msg,"OK",0);
    }

    private void showFloatingMessage(String msg,String actionButtonText, final int onClickAction) {
        final Container myWindow = new Container();
        guiNode.attachChild(myWindow);
        myWindow.setLocalTranslation(50, 550, 0);

        Label l = new Label(msg);
        l.setFontSize(18);
        l.setPreferredSize(new Vector3f(700, 300, 0));
        myWindow.addChild(l);
        Button clickMe = myWindow.addChild(new Button(actionButtonText));
        clickMe.addClickCommands(new Command<Button>() {
            @Override
            public void execute(Button source) {
                SceneMaxApp.this.enqueue(new Runnable() {
                    @Override
                    public void run() {

                        if (onClickAction == 0) {
                            SceneMaxApp.this.clearScene();
                            SceneMaxApp.this.stop();
                        } else if (onClickAction == 10) {
                            guiNode.detachChild(myWindow);
                        }

                    }
                });

            }
        });

    }

    private String prepareFloatingMessage(List<String>rows) {

        String msg = "";
        if(rows!=null && rows.size()>0) {
            for (String s : rows) {

                String[] words = s.split(" ");
                for (int i = 0; i < words.length; i++) {
                    String w = words[i];
                    msg += w + " ";
                    if (i > 0 && i % 10 == 0) {
                        msg += "\r\n";
                    }
                }
                msg += "\r\n";
            }
        }

        return msg;
    }

    private void showFloatingMessage(List<String>rows, String actionButtonText, final int onClickAction) {

        String msg = prepareFloatingMessage(rows);
        showFloatingMessage(msg,actionButtonText,onClickAction);
    }

    public void loadResource(StatementDef st) {

        if(st instanceof PlayStopSoundCommand){
            loadAudioResource((PlayStopSoundCommand)st);
        }

    }

    private void loadUIResource(com.scenemaxeng.compiler.UILoadCommand cmd) {
        try {
            if (cmd == null) {
                handleRuntimeError("UI.load failed: command was null");
                return;
            }

            if (uiManager == null) {
                logger.log(Level.WARNING, "UI.load '{0}' skipped because UI manager is not initialized", cmd.uiName);
                handleRuntimeError("UI.load failed: UI manager is not initialized");
                return;
            }

            if (cmd.filePath == null || cmd.filePath.isEmpty()) {
                logger.log(Level.WARNING, "UI.load '{0}' failed because no .smui path was resolved", cmd.uiName);
                handleRuntimeError("UI.load failed: no .smui file path was resolved for '" + cmd.uiName + "'");
                return;
            }

            java.io.File uiFile = new java.io.File(cmd.filePath);
            logger.log(Level.INFO, "UI.load '{0}' resolved to {1}", new Object[]{cmd.uiName, uiFile.getAbsolutePath()});

            if (!uiFile.exists()) {
                if (!loadPackagedUiDocument(cmd.uiName)) {
                    logger.log(Level.WARNING, "UI.load '{0}' could not find file at {1}", new Object[]{cmd.uiName, uiFile.getAbsolutePath()});
                    handleRuntimeError("UI.load failed: UI file not found at '" + uiFile.getAbsolutePath() + "'");
                    return;
                }
            } else {
                uiManager.load(uiFile);
            }
            logger.log(Level.INFO, "UI.load '{0}' loaded successfully", cmd.uiName);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "UI.load resource failed for '" + (cmd != null ? cmd.uiName : "<null>") + "'", e);
            handleRuntimeError("UI.load resource failed: " + e.getMessage());
        }
    }

    private void loadAudioResource(PlayStopSoundCommand st) {

        //_audioNodesRes
        AudioNode n = _audioNodes.get(st.sound);
        if(n==null) {
            ResourceAudio ra = assetsMapping.getAudioIndex().get(st.sound);
            if(ra==null) {
                // error no such audio resource
                return;
            }

            AudioNode an = new AudioNode(assetManager, ra.path, ra.dataType);
            an.setPositional(false);
            an.setLooping(false);

            float volume = 100;
            if(st.volumeExpr!=null) {
                volume = ((Double)new ActionLogicalExpressionVm(st.volumeExpr,mainScope).evaluate()).floatValue();
                if(volume>100) {
                    volume=100;
                }
            }
            an.setVolume(volume/100f);

            rootNode.attachChild(an);
            _audioNodes.put(st.sound,an);
        }

    }

    public SceneMaxBaseController runAction(ProgramDef prg,ActionStatementBase action, SceneMaxScope scope) {

        if(action instanceof ActionCommandRotate) {
            SceneMaxParser.Logical_expressionContext loopExpr = ((ActionCommandRotate) action).loopExpr;
            for(ActionStatementBase rotateAction: action.statements) {
                ActionCommandRotate cmd = (ActionCommandRotate) rotateAction;
                cmd.loopExpr = loopExpr;
                RotateController rc = new RotateController(this, prg, scope, cmd);

                rc.axis = cmd.axis;
                rc.numSign = cmd.numSign;
                rc.num = cmd.num;
                rc.numExpr = new ActionLogicalExpressionVm(cmd.numExpr,scope );
                rc.speedExpr = new ActionLogicalExpressionVm(cmd.speedExp,scope);
                rc.async= loopExpr!=null || action.isAsync;
                scope.add(rc);
            }

        } else if(action instanceof ActionCommandMove) {
            for(ActionStatementBase moveAction: action.statements) {
                ActionCommandMove cmd = (ActionCommandMove) moveAction;
                MoveController mc = new MoveController(this, prg, scope, cmd);

                mc.targetVarDef = cmd.varDef;
                mc.axis = cmd.axis;
                mc.numSign = cmd.numSign;
                mc.num = cmd.num;
                mc.numExpr = new ActionLogicalExpressionVm(cmd.numExpr,scope);
                mc.speedExpr = new ActionLogicalExpressionVm(cmd.speedExpr,scope);
                mc.async=action.isAsync;
                scope.add(mc);
            }

        } else if(action instanceof ActionCommandAnimate) {
            ActionCommandAnimate cmdAnim = (ActionCommandAnimate)action;

            AnimateCompositeController animateController = new AnimateCompositeController(cmdAnim, scope);
            for(ActionStatementBase animAction: action.statements) {
                ActionCommandAnimate cmd = (ActionCommandAnimate) animAction;
                ModelAnimateController anim = new ModelAnimateController(this, prg, cmd,scope);
                animateController.add(anim);
            }

            animateController.async=action.isAsync || cmdAnim.loop;

            scope.add(animateController);

        } else if(action instanceof ActionCommandPlay) {
            ActionCommandPlay cmd = (ActionCommandPlay) action;
            SpritePlayFramesController c = new SpritePlayFramesController(this,scope,cmd);
            c.async=action.isAsync || (cmd.durationStrategy==2 && cmd.loopTimes.equals("-1"));
            scope.add(c);
        } else if(action instanceof EffekseerPlayCommand) {
            EffekseerPlayController c = new EffekseerPlayController(this, prg, scope, (EffekseerPlayCommand) action);
            c.async = action.isAsync;
            scope.add(c);

        } else if(action instanceof DoBlockCommand) {
            DoBlockCommand cmd = (DoBlockCommand)action;
            DoBlockController c = new DoBlockController(this,scope, cmd);
            c.goExpr=cmd.goExpr;
            c.app=this;
            c.async=action.isAsync;
            scope.add(c);
        } else if(action instanceof FunctionInvocationCommand) {
            FunctionInvocationCommand fic = (FunctionInvocationCommand)action;
            SceneMaxBaseController ctl = runFunctionInvocationCommand(prg, scope, fic);
            scope.add(ctl);
        } else if(action instanceof IfStatementCommand) {
            IfStatementCommand ifCmd = (IfStatementCommand)action;
            IfStatmentController ctl = new IfStatmentController(this, scope,ifCmd);
            scope.add(ctl);

        } else if(action instanceof VariableAssignmentCommand) {
            VariableAssignmentCommand cmd= (VariableAssignmentCommand)action;
            VariableAssignmentController ctl = new VariableAssignmentController(this,scope,prg,cmd);
            scope.add(ctl);

        } else if(action instanceof PrintStatementCommand) {
            PrintStatementContoller ctl = new PrintStatementContoller(this, prg, (PrintStatementCommand)action,scope);
            scope.add(ctl);
        } else if(action instanceof WaitStatementCommand) {
            WaitStatementController ctl = new WaitStatementController(this,prg,(WaitStatementCommand)action,scope);
            scope.add(ctl);
            return ctl;
        } else if(action instanceof ActionCommandShowHide) {
            ActionCommandShowHide cmd = (ActionCommandShowHide)action;
            ShowHideController ctl = new ShowHideController(this,prg,cmd,scope);
            scope.add(ctl);
        } else if(action instanceof CreateSpriteCommand) {
            CreateSpriteController c = new CreateSpriteController(this,prg,(CreateSpriteCommand)action,scope);
            scope.add(c);
        } else if(action instanceof GraphicEntityCreationCommand) {
            InstantiateGraphicEntityController c = new InstantiateGraphicEntityController(this,prg,(GraphicEntityCreationCommand)action,scope);
            scope.add(c);
        } else if(action instanceof SkyBoxCommand) {
            SkyBoxActionController c = new SkyBoxActionController(this,prg,(SkyBoxCommand)action,scope);
            scope.add(c);
        } else if (action instanceof CollisionStatementCommand) {
            CollisionController ctl = new CollisionController(this, prg,scope,(CollisionStatementCommand)action);
            scope.add(ctl);
        } else if(action instanceof ActionCommandStop) {
            StopModelController ctl = new StopModelController(this,prg,(ActionCommandStop)action,scope);
            scope.add(ctl);
        } else if(action instanceof StopBlockCommand) {
            StopBlockController ctl = new StopBlockController(this,prg,(StopBlockCommand)action,scope);
            scope.add(ctl);
        } else if(action instanceof InputStatementCommand) {
            InputController ctl = new InputController(this, prg,scope,(InputStatementCommand)action);
            scope.add(ctl);
        } else if (action instanceof ActionCommandPos) {
            EntityPosController ctl = new EntityPosController(this, prg,scope,(ActionCommandPos)action);
            scope.add(ctl);
        } else if (action instanceof PlayStopSoundCommand) {
            PlaySoundController ctl = new PlaySoundController(this, prg,scope,(PlayStopSoundCommand)action);
            scope.add(ctl);
        } else if(action instanceof WaterShowCommand) {
            ShowWaterController ctl = new ShowWaterController(this, prg,scope,(WaterShowCommand)action);
            scope.add(ctl);
        } else if(action instanceof ParticleSystemCommand) {
            ParticleSystemController ctl = new ParticleSystemController(this, prg,scope,(ParticleSystemCommand)action);
            ctl.async = true;
            scope.add(ctl);
        } else if(action instanceof TerrainCommand) {
            TerrainController ctl = new TerrainController(this,prg,scope,(TerrainCommand)action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if(action instanceof ChaseCameraCommand){
            ChaseCameraController ctl = new ChaseCameraController(this,prg,scope,(ChaseCameraCommand)action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if(action instanceof CheckIsStaticCommand) {
            CheckIsStaticController ctl = new CheckIsStaticController(this,prg,scope,(CheckIsStaticCommand)action);
            ctl.async = true;
            scope.add(ctl);
        } else if(action instanceof SwitchModeCommand) {
            SwitchModeController ctl = new SwitchModeController(this,prg,scope,(SwitchModeCommand)action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if(action instanceof DirectionalMoveCommand) {
            DirectionalMoveController ctl = new DirectionalMoveController(this,prg,scope,(DirectionalMoveCommand)action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if (action instanceof ChangeMassCommand) {
            ChangeMassController ctl = new ChangeMassController(this,prg,scope,(ChangeMassCommand)action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if(action instanceof ChangeDebugMode){
            ChangeDebugModeController ctl = new ChangeDebugModeController(this,prg,scope,(ChangeDebugMode)action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if (action instanceof SetUserDataCommand) {
            SetUserDataController ctl = new SetUserDataController(this,prg,scope,(SetUserDataCommand)action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if (action instanceof AddEntityToGroupCommand) {
            AddEntityToGroupController ctl = new AddEntityToGroupController(this,prg,scope,(AddEntityToGroupCommand)action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if (action instanceof RayCheckCommand) {
            RayCheckCommandController ctl = new RayCheckCommandController(this,prg,scope,(RayCheckCommand)action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if(action instanceof MoveToCommand) {
            MoveToController ctl = new MoveToController(this,prg,scope,(MoveToCommand)action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if(action instanceof RotateResetCommand) {
            ResetRotateController ctl = new ResetRotateController(this,prg,scope,(RotateResetCommand)action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if(action instanceof FpsCameraCommand) {
            FpsCameraController ctl = new FpsCameraController(this,prg,scope,(FpsCameraCommand)action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if (action instanceof SceneActionCommand) {
            SceneActionController ctl = new SceneActionController(this,prg,scope,(SceneActionCommand)action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if (action instanceof EnvironmentShaderCommand) {
            EnvironmentShaderController ctl = new EnvironmentShaderController(this,prg,scope,(EnvironmentShaderCommand) action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if (action instanceof AttachToCommand) {

            AttachToController ctl = new AttachToController(this,prg,scope,(AttachToCommand)action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if(action instanceof CarResetCommand) {
            CarResetController ctl = new CarResetController(this,prg,scope,(CarResetCommand)action);
            scope.add(ctl);
        }  else if (action instanceof  AccelerateCommand) {
            CarAccelerateController ctl = new CarAccelerateController(this,prg,scope,(AccelerateCommand)action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if(action instanceof CarSteerCommand) {
            CarSteerController ctl = new CarSteerController(this,prg,scope,(CarSteerCommand)action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if(action instanceof CarBrakeCommand) {
            CarBrakeController ctl = new CarBrakeController(this,prg,scope,(CarBrakeCommand)action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if(action instanceof CarTurboCommand) {
            CarTurboController ctl = new CarTurboController(this,prg,scope,(CarTurboCommand)action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if(action instanceof VehicleSetupCommand) {
            CarSetupController ctl = new CarSetupController(this,prg,scope,(VehicleSetupCommand)action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if(action instanceof ActionScaleCommand) {
            ChangeScaleController ctl = new ChangeScaleController(this,prg,scope,(ActionScaleCommand)action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if(action instanceof CharacterJumpCommand) {
            CharacterJumpController ctl = new CharacterJumpController(this,prg,scope,(CharacterJumpCommand)action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if (action instanceof LighsActionCommand) {
            LightsActionController ctl = new LightsActionController(this,prg,scope,(LighsActionCommand)action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if (action instanceof LookAtCommand) {
            LookAtController ctl = new LookAtController(this,prg,scope,(LookAtCommand)action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if (action instanceof ClearModeCommand) {
            ClearModeController ctl = new ClearModeController(this,prg,scope,(ClearModeCommand)action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if (action instanceof DettachFromParentCommand) {
            DetachFromParentController ctl = new DetachFromParentController(this,prg,scope,(DettachFromParentCommand)action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if (action instanceof ChannelDrawCommand) {
            UIAttachController ctl = new UIAttachController(this,prg,scope,(ChannelDrawCommand)action);
            ctl.async = action.isAsync;
            scope.add(ctl);

        } else if (action instanceof MiniMapCommand) {
            MiniMapController ctl = new MiniMapController(this,prg,scope,(MiniMapCommand)action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if(action instanceof SetMaterialCommand) {
            SetMaterialController ctl = new SetMaterialController(this,prg,scope,(SetMaterialCommand)action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if(action instanceof SetShaderCommand) {
            SetShaderController ctl = new SetShaderController(this,prg,scope,(SetShaderCommand)action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if (action instanceof ForEachCommand) {
            ForEachCommandController ctl = new ForEachCommandController(this,prg,scope,(ForEachCommand)action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if (action instanceof ForCommand) {
            ForController ctl = new ForController(this,prg,scope,(ForCommand)action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if (action instanceof KillEntityCommand) {
            KillEntityController ctl = new KillEntityController (this,prg,scope,(KillEntityCommand)action);
            ctl.async = action.isAsync;
            scope.add(ctl);
        } else if(action instanceof ChangeVelocityCommand) {
            ChangeVelocityController ctl = new ChangeVelocityController(this,prg,scope,(ChangeVelocityCommand)action);
            ctl.async=action.isAsync;
            scope.add(ctl);
        } else if(action instanceof AnimateOptionsCommand) {
            AnimateOptionsController ctl = new AnimateOptionsController(this,prg,scope,(AnimateOptionsCommand)action);
            ctl.async=true;
            scope.add(ctl);
        } else if(action instanceof ActionCommandRotateTo) {
            RotateToController rc = new RotateToController(this, prg, scope, (ActionCommandRotateTo)action);
            rc.async=action.isAsync;
            scope.add(rc);
        } else if(action instanceof ActionCommandRecord) {
            ActionCommandRecord cmd = (ActionCommandRecord)action;
            if(cmd.recordType==ActionCommandRecord.RECORD_TYPE_TRANSITIONS) {
                RecordTransitionsController ctl = new RecordTransitionsController(this,prg,scope,cmd);
                ctl.async=true;
                scope.add(ctl);
            } else if(cmd.recordType==ActionCommandRecord.RECORD_TYPE_STOP) {
                StopRecordController ctl = new StopRecordController(this,prg,scope,cmd);
                scope.add(ctl);
            } else if(cmd.recordType==ActionCommandRecord.RECORD_TYPE_SAVE) {
                SaveRecordingController ctl = new SaveRecordingController(this,prg,scope,cmd);
                scope.add(ctl);
            }
        } else if(action instanceof WaitForCommand) {
            WaitForController ctl = new WaitForController(this,prg,scope,(WaitForCommand)action);
            scope.add(ctl);
        } else if(action instanceof ReplayCommand) {
            ReplayController ctl = new ReplayController(this,prg,scope,(ReplayCommand)action);
            ctl.async=true;
            scope.add(ctl);
        } else if (action instanceof HttpCommand) {
            HttpController ctl = new HttpController(this,prg,scope,(HttpCommand)action);
            ctl.async=true;
            scope.add(ctl);
        } else if(action instanceof CharacterIgnoreCommand) {
            CharacterIgnoreController ctl = new CharacterIgnoreController(this,prg,scope,(CharacterIgnoreCommand)action);
            scope.add(ctl);
        } else if(action instanceof WhenStateCommand) {
            WhenStateEventController ctl = new WhenStateEventController(this,prg,scope,(WhenStateCommand)action);
            ctl.async = true;
            scope.add(ctl);
        } else if (action instanceof SwitchStateCommand) {
            SwitchStateController ctl = new SwitchStateController(this,prg,scope,(SwitchStateCommand)action);
            ctl.async = true;
            scope.add(ctl);
        } else if (action instanceof ArrayCommand) {
            ArrayCommandController ctl = new ArrayCommandController(this,prg,scope,(ArrayCommand)action);
            scope.add(ctl);
        } else if (action instanceof PluginActionCommand) {
            PluginActionController ctl = new PluginActionController(this, prg, scope, (PluginActionCommand) action);
            scope.add(ctl);
        } else if (action instanceof com.scenemaxeng.compiler.UILoadCommand) {
            UILoadController ctl = new UILoadController(this, prg, scope, (com.scenemaxeng.compiler.UILoadCommand) action);
            scope.add(ctl);
        } else if (action instanceof com.scenemaxeng.compiler.UIShowHideCommand) {
            UIShowHideController ctl = new UIShowHideController(this, prg, scope, (com.scenemaxeng.compiler.UIShowHideCommand) action);
            scope.add(ctl);
        } else if (action instanceof com.scenemaxeng.compiler.UISetPropertyCommand) {
            UISetPropertyController ctl = new UISetPropertyController(this, prg, scope, (com.scenemaxeng.compiler.UISetPropertyCommand) action);
            scope.add(ctl);
        }

        return null;

    }


    public SceneMaxBaseController runFunctionInvocationCommand(ProgramDef prg, SceneMaxScope scope, FunctionInvocationCommand fic) {
        FunctionBlockDef fDef = prg.getFunc(fic.funcName);
        if(fDef!=null) {

            DoBlockCommand cmd = fDef.doBlock;
            DoBlockController c = new DoBlockController(this,scope, cmd);
            c.app = this;
            c.goExpr = fDef.goExpr;
            c.async = fic.isAsync || cmd.isAsync;
            if(fic.intervalExpr!=null) {
                c.intervalExpr = new ActionLogicalExpressionVm(fic.intervalExpr, scope);
                c.async=true;
            }

            if(fic.funcParam!=null) {
                c.setFunctionScopeParam(fDef.doBlock.inParams, (EntityInstBase)fic.funcParam);
            } else {
                c.setFunctionScopeParams(fDef.doBlock.inParams, fic.params);
            }

            return c;

        } else {
            // report error - function not defined
        }

        return null;
    }


    public int getEntityScopeId(SceneMaxScope scope, String targetVar, int varType) {
        return getEntityScopeId(scope, targetVar);
    }

    public int getEntityScopeId(SceneMaxScope scope, String targetVar) {
        EntityInstBase eb = scope.getEntityInst(targetVar);
        if (eb != null) {
            return eb.scope.scopeId;
        }
        return 0; // should throw error - instance not found
    }

    public ModelInst instantiate3DModelAsync(ProgramDef prg, VariableDef var, SceneMaxScope scope) {

        String fromRes = "";
        ModelDef md = null;

        if (var.resName != null) {
            md = prg.getModel(var.resName);
            fromRes = var.resName;
        } else {
            fromRes = new ActionLogicalExpressionVm(var.resNameExpr, scope).evaluate().toString();

            md = new ModelDef();
            md.name = fromRes;
            md.isVehicle = var.isVehicle;
            prg.models.put(md.name, md);
        }

        if (md != null) {
            ModelInst inst = new ModelInst(md, var, scope);
            String key = var.varName + "_" + ++entityInstCounter;
            inst.entityKey = key;
            modelInstances.put(key, inst);

            if (var.entityPos != null) {
                inst.entityForPos = findVarRuntime(prg, scope, var.entityPos.entityName);
            }

            if (var.entityRot != null) {
                inst.entityForRot = findVarRuntime(prg, scope, var.entityRot);
            }

            scope.entities.put(var.varName, inst);
            //loadModel(var.varName, fromRes, inst);
            return inst;
        }

        return null;
    }


    /**
     * Preloads a 3D model asset on a background thread so it is cached
     * by the AssetManager.  Only performs disk I/O — no physics or
     * scene-graph work.  Safe to call from any thread.
     */
    public void preloadModelAsset(String resourcePath) {
        ResourceSetup resource = assetsMapping.get3DModelsIndex().get(resourcePath.toLowerCase());
        if (resource != null && !resource.isVehicle) {
            try {
                assetManager.loadModel(resource.path);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void instantiateVariable(ProgramDef prg, VariableDef var, SceneMaxScope scope) {

        if(var.varType==VariableDef.VAR_TYPE_3D) {
            String fromRes = "";
            ModelDef md = null;

            if (var.resName != null) {
                md = prg.getModel(var.resName);
                fromRes = var.resName;
            } else {
                fromRes = new ActionLogicalExpressionVm(var.resNameExpr, scope).evaluate().toString();
                md = new ModelDef();
                md.name = fromRes;
                md.isVehicle = var.isVehicle;
                prg.models.put(md.name, md);
            }

            if (md != null) {
                ModelInst inst = new ModelInst(md, var, scope);
                String key = var.varName + "_" + ++entityInstCounter;
                inst.entityKey = key;
                modelInstances.put(key, inst);

                if (var.entityPos != null) {
                    inst.entityForPos = findVarRuntime(prg, scope, var.entityPos.entityName);
                }

                if (var.entityRot != null) {
                    inst.entityForRot = findVarRuntime(prg, scope, var.entityRot);
                }

                scope.entities.put(var.varName, inst);
                loadModel(var.varName, fromRes, inst);
                return;
            }

        }

        if (var.varType == VariableDef.VAR_TYPE_EFFEKSEER) {
            EffekseerInst inst = createEffekseerInst(prg, var, scope);
            if (inst != null) {
                scope.entities.put(var.varName, inst);
                loadEffekseerEffect(inst);
            }
            return;
        }

        if(var.resName.equals("sphere")) {
            SphereInst inst = new SphereInst((SphereVariableDef)var,scope);
            String key = var.varName+"_"+ ++entityInstCounter;
            inst.entityKey=key;
            sphereInstances.put(key, inst);
            if(var.entityPos!=null) {
                inst.entityForPos = findVarRuntime(prg,scope,var.entityPos.entityName);
            }

            if(var.entityRot!=null) {
                inst.entityForRot = findVarRuntime(prg,scope,var.entityRot);
            }

            scope.entities.put(var.varName,inst);
            loadSphere(inst);
            return;
        }

        if(var.resName.equals("box")) {
            BoxInst inst = new BoxInst((BoxVariableDef)var,scope);
            String key = var.varName+"_"+ ++entityInstCounter;
            inst.entityKey=key;
            boxInstances.put(key, inst);
            if(var.entityPos!=null) {
                inst.entityForPos = findVarRuntime(prg,scope,var.entityPos.entityName);
            }

            if(var.entityRot!=null) {
                inst.entityForRot = findVarRuntime(prg,scope,var.entityRot);
            }

            scope.entities.put(var.varName,inst);
            loadBox(inst);
            return;
        }

        if(var.resName.equals("cylinder")) {
            CylinderInst inst = new CylinderInst((CylinderVariableDef)var,scope);
            String key = var.varName+"_"+ ++entityInstCounter;
            inst.entityKey=key;
            cylinderInstances.put(key, inst);
            if(var.entityPos!=null) {
                inst.entityForPos = findVarRuntime(prg,scope,var.entityPos.entityName);
            }

            if(var.entityRot!=null) {
                inst.entityForRot = findVarRuntime(prg,scope,var.entityRot);
            }

            scope.entities.put(var.varName,inst);
            loadCylinder(inst);
            return;
        }

        if(var.resName.equals("hollowcylinder")) {
            HollowCylinderInst inst = new HollowCylinderInst((HollowCylinderVariableDef)var,scope);
            String key = var.varName+"_"+ ++entityInstCounter;
            inst.entityKey=key;
            hollowCylinderInstances.put(key, inst);
            if(var.entityPos!=null) {
                inst.entityForPos = findVarRuntime(prg,scope,var.entityPos.entityName);
            }

            if(var.entityRot!=null) {
                inst.entityForRot = findVarRuntime(prg,scope,var.entityRot);
            }

            scope.entities.put(var.varName,inst);
            loadHollowCylinder(inst);
            return;
        }

        if(var.resName.equals("quad")) {
            QuadInst inst = new QuadInst((QuadVariableDef)var,scope);
            String key = var.varName+"_"+ ++entityInstCounter;
            inst.entityKey=key;
            quadInstances.put(key, inst);
            if(var.entityPos!=null) {
                inst.entityForPos = findVarRuntime(prg,scope,var.entityPos.entityName);
            }

            if(var.entityRot!=null) {
                inst.entityForRot = findVarRuntime(prg,scope,var.entityRot);
            }

            scope.entities.put(var.varName,inst);
            loadQuad(inst);
        }

    }

    private void loadBox(BoxInst inst) {

        RigidBodyControl modelCtl = null;
        GhostControl ghost = null;

        float x=1.0f,y=1.0f,z=1.0f;
        BoxVariableDef varDef = (BoxVariableDef)inst.varDef;
        if(varDef.sizeX!=null) {
            x = ((Double)new ActionLogicalExpressionVm(varDef.sizeX,inst.scope).evaluate()).floatValue();
            y = ((Double)new ActionLogicalExpressionVm(varDef.sizeY,inst.scope).evaluate()).floatValue();
            z = ((Double)new ActionLogicalExpressionVm(varDef.sizeZ,inst.scope).evaluate()).floatValue();
        }

        String boxName = inst.varDef.varName+"@"+inst.scope.scopeId;
        Node boxNode = new Node(boxName);
        boxNode.setUserData("key",boxName);


        /////////// SET POSITION & ROTATION //////////

        if(inst.rxExpr!=null) {
            float rotateX = Float.parseFloat(inst.rxExpr.evaluate().toString());
            float rotateY = Float.parseFloat(inst.ryExpr.evaluate().toString());
            float rotateZ = Float.parseFloat(inst.rzExpr.evaluate().toString());
            boxNode.rotate(rotateX* FastMath.DEG_TO_RAD,rotateY* FastMath.DEG_TO_RAD,rotateZ* FastMath.DEG_TO_RAD);

        } else if(inst.entityForRot!=null) {
            Spatial sp = getEntitySpatial(inst.entityForRot.varName,inst.entityForRot.varDef.varType);
            boxNode.setLocalRotation(sp.getLocalRotation());
        }

        if(inst.xExpr!=null) {
            float localTranslationX = Float.parseFloat(inst.xExpr.evaluate().toString());
            float localTranslationY = Float.parseFloat(inst.yExpr.evaluate().toString());
            float localTranslationZ = Float.parseFloat(inst.zExpr.evaluate().toString());
            boxNode.setLocalTranslation(localTranslationX, localTranslationY, localTranslationZ);
        } else if(inst.entityForPos!=null) {

            if(inst.varDef.entityPos.entityJointName==null) {
                Spatial sp = getEntitySpatial(inst.entityForPos.varName,inst.entityForPos.varDef.varType);
                boxNode.setLocalTranslation(sp.getLocalTranslation());
            } else {
                AppModel am2 = models.get(inst.entityForPos.varName);
                if(am2!=null) {
                    Node n = am2.getJointAttachementNode(inst.varDef.entityPos.entityJointName);

                    if(n!=null) {
                        Joint j = (Joint) n.getUserData("AttachedBone");
                        Vector3f pos = j.getModelTransform().clone().combineWithParent(am2.getSkinningControl().getSpatial().getWorldTransform()).getTranslation();
                        boxNode.setLocalTranslation(pos);
                    }
                }
            }


        }



        if(varDef.isCollider) {
            ghost = new GhostControl(
                    new BoxCollisionShape(new Vector3f(x,y,z)));
            boxNode.addControl(ghost);
        } else {
            Box bm = new Box(x, y, z);
            final Geometry boxGeo = new Geometry(boxName, bm);

            boxNode.attachChild(boxGeo);
            TangentBinormalGenerator.generate(bm);           // for lighting effect

            if (inst.varDef.shadowMode == 3) {
                boxGeo.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
            } else if (inst.varDef.shadowMode == 2) {
                boxGeo.setShadowMode(RenderQueue.ShadowMode.Receive);
            } else if (inst.varDef.shadowMode == 1) {
                boxGeo.setShadowMode(RenderQueue.ShadowMode.Cast);
            }

            if (varDef.materialExpr != null) {
                String materialName = new ActionLogicalExpressionVm(varDef.materialExpr, inst.scope).evaluate().toString();
                if (!setGeometryMaterial(boxGeo, materialName)) {
                    handleRuntimeError("Cannot find material resource named: '" + materialName + "'");
                    return;
                }

            } else {
                Material mat1 = new Material(assetManager,
                        "Common/MatDefs/Misc/Unshaded.j3md");
                mat1.setColor("Color", ColorRGBA.Green);
                boxGeo.setMaterial(mat1);
            }


            /////////////// RIGID BODY //////////

            float mass = 1;
            boolean isPhysical = inst.massExpr!=null || inst.varDef.isStatic;
            if(isPhysical) {
                if(inst.varDef.isStatic) {
                    mass=0;
                } else {
                    mass = Float.parseFloat(inst.massExpr.evaluate().toString());
                }

            }

            CollisionShape modelShape;
            if(inst.varDef.isStatic) {
                modelShape = new MeshCollisionShape(boxGeo.getMesh());
            } else {
                modelShape = CollisionShapeFactory.createBoxShape(boxGeo);
            }

            modelCtl = new RigidBodyControl(modelShape,mass);

            if(!inst.varDef.isStatic) {
                modelCtl.setKinematic(!isPhysical);//
            }

            boxNode.addControl(modelCtl);

        }

        boxes.put(boxName,boxNode);
        geoName2ModelName.put(boxName,boxName);
        geoName2EntityInst.put(boxName,inst);

        List<java.lang.Object> ctls = new ArrayList<>();
        if(modelCtl!=null) {
            bulletAppState.getPhysicsSpace().add(modelCtl);
            ctls.add(modelCtl);
        }

        if(ghost!=null) {
            bulletAppState.getPhysicsSpace().add(ghost);
            ctls.add(ghost);
        }

        collisionControlsCache.put(boxName,ctls);

        if(inst.varDef.visible) {
            rootNode.attachChild(boxNode);
        }

    }

    private void loadSphere(SphereInst inst) {

        RigidBodyControl modelCtl = null;
        GhostControl ghost = null;

        SphereVariableDef varDef = (SphereVariableDef)inst.varDef;
        float radius = inst.radiusExpr==null?1f:((Double)inst.radiusExpr.evaluate()).floatValue();
        String sphereName = inst.varDef.varName+"@"+inst.scope.scopeId;
        Node sphereNode = new Node(sphereName);
        sphereNode.setUserData("key",sphereName);

        ////////// SET POSITION & ROTATION ///////

        if(inst.rxExpr!=null) {
            float rotateX = Float.parseFloat(inst.rxExpr.evaluate().toString());
            float rotateY = Float.parseFloat(inst.ryExpr.evaluate().toString());
            float rotateZ = Float.parseFloat(inst.rzExpr.evaluate().toString());
            sphereNode.rotate(rotateX* FastMath.DEG_TO_RAD,rotateY* FastMath.DEG_TO_RAD,rotateZ* FastMath.DEG_TO_RAD);

        } else if(inst.entityForRot!=null) {
            Spatial sp = getEntitySpatial(inst.entityForRot.varName,inst.entityForRot.varDef.varType);
            sphereNode.setLocalRotation(sp.getLocalRotation());

        }

        if(inst.xExpr!=null) {
            float localTranslationX = Float.parseFloat(inst.xExpr.evaluate().toString());
            float localTranslationY = Float.parseFloat(inst.yExpr.evaluate().toString());
            float localTranslationZ = Float.parseFloat(inst.zExpr.evaluate().toString());
            sphereNode.setLocalTranslation(localTranslationX, localTranslationY, localTranslationZ);
        } else if(inst.entityForPos!=null) {
            if(inst.varDef.entityPos.entityJointName==null) {
                Spatial sp = getEntitySpatial(inst.entityForPos.varName,inst.entityForPos.varDef.varType);
                sphereNode.setLocalTranslation(sp.getLocalTranslation());
            } else {
                AppModel am2 = models.get(inst.entityForPos.varName);
                if(am2!=null) {
                    Node n = am2.getJointAttachementNode(inst.varDef.entityPos.entityJointName);

                    if(n!=null) {
                        Joint j = (Joint) n.getUserData("AttachedBone");
                        Vector3f pos = j.getModelTransform().clone().combineWithParent(am2.getSkinningControl().getSpatial().getWorldTransform()).getTranslation();
                        sphereNode.setLocalTranslation(pos);
                    }
                }
            }


        }


        if(varDef.isCollider) {
            ghost = new GhostControl(
                    new SphereCollisionShape(radius));
            sphereNode.addControl(ghost);
        } else {

            Sphere sphereMesh = new Sphere(32, 32, radius);
            final Geometry sphereGeo = new Geometry(sphereName, sphereMesh);
            sphereNode.attachChild(sphereGeo);

            sphereMesh.setTextureMode(Sphere.TextureMode.Projected); // better quality on spheres
            TangentBinormalGenerator.generate(sphereMesh);           // for lighting effect

            if (inst.varDef.shadowMode == 3) {
                sphereGeo.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
            } else if (inst.varDef.shadowMode == 2) {
                sphereGeo.setShadowMode(RenderQueue.ShadowMode.Receive);
            } else if (inst.varDef.shadowMode == 1) {
                sphereGeo.setShadowMode(RenderQueue.ShadowMode.Cast);
            }

            if (inst.materialExpr != null) {
                String materialName = inst.materialExpr.evaluate().toString();
                if (!setGeometryMaterial(sphereGeo, materialName)) {
                    handleRuntimeError("Cannot find material resource named: '" + materialName + "'");
                    return;
                }

            } else {
                Material mat1 = new Material(assetManager,
                        "Common/MatDefs/Misc/Unshaded.j3md");
                mat1.setColor("Color", ColorRGBA.Blue);
                sphereGeo.setMaterial(mat1);
            }


            ///////////// ADD RIGID BODY CONTROL /////////

            float mass = 1;
            boolean isPhysical = inst.massExpr!=null || inst.varDef.isStatic;
            if(isPhysical) {
                if(inst.varDef.isStatic) {
                    mass=0;
                } else {
                    mass = Float.parseFloat(inst.massExpr.evaluate().toString());
                }

            }

            CollisionShape modelShape;
            if(inst.varDef.isStatic) {
                modelShape = new MeshCollisionShape(sphereGeo.getMesh());
            } else {
                modelShape = CollisionShapeFactory.createDynamicMeshShape(sphereGeo);
            }

            modelCtl = new RigidBodyControl(modelShape,mass);

            if(!inst.varDef.isStatic) {
                modelCtl.setKinematic(!isPhysical);//
            }

            sphereNode.addControl(modelCtl);


        }

        spheres.put(sphereName,sphereNode);
        geoName2ModelName.put(sphereName,sphereName);
        geoName2EntityInst.put(sphereName,inst);

        List<java.lang.Object> ctls = new ArrayList<>();
        if(modelCtl!=null) {
            bulletAppState.getPhysicsSpace().add(modelCtl);
            ctls.add(modelCtl);
        }

        if(ghost!=null) {
            bulletAppState.getPhysicsSpace().add(ghost);
            ctls.add(ghost);
        }

        collisionControlsCache.put(sphereName,ctls);

        if(inst.varDef.visible) {
            rootNode.attachChild(sphereNode);
        }

 }

    private void loadCylinder(CylinderInst inst) {

        RigidBodyControl modelCtl = null;
        GhostControl ghost = null;

        CylinderVariableDef varDef = (CylinderVariableDef)inst.varDef;
        float radiusTop = inst.radiusTopExpr==null?1f:((Double)inst.radiusTopExpr.evaluate()).floatValue();
        float radiusBottom = inst.radiusBottomExpr==null?1f:((Double)inst.radiusBottomExpr.evaluate()).floatValue();
        float height = inst.heightExpr==null?2f:((Double)inst.heightExpr.evaluate()).floatValue();

        // Guard against zero/negative dimensions which crash jME3's Cylinder constructor
        if (radiusTop <= 0) radiusTop = 1f;
        if (radiusBottom <= 0) radiusBottom = 1f;
        if (height <= 0) height = 2f;

        String cylinderName = inst.varDef.varName+"@"+inst.scope.scopeId;
        Node cylinderNode = new Node(cylinderName);
        cylinderNode.setUserData("key",cylinderName);

        ////////// SET POSITION & ROTATION ///////

        if(inst.rxExpr!=null) {
            float rotateX = Float.parseFloat(inst.rxExpr.evaluate().toString());
            float rotateY = Float.parseFloat(inst.ryExpr.evaluate().toString());
            float rotateZ = Float.parseFloat(inst.rzExpr.evaluate().toString());
            cylinderNode.rotate(rotateX* FastMath.DEG_TO_RAD,rotateY* FastMath.DEG_TO_RAD,rotateZ* FastMath.DEG_TO_RAD);

        } else if(inst.entityForRot!=null) {
            Spatial sp = getEntitySpatial(inst.entityForRot.varName,inst.entityForRot.varDef.varType);
            cylinderNode.setLocalRotation(sp.getLocalRotation());
        }

        if(inst.xExpr!=null) {
            float localTranslationX = Float.parseFloat(inst.xExpr.evaluate().toString());
            float localTranslationY = Float.parseFloat(inst.yExpr.evaluate().toString());
            float localTranslationZ = Float.parseFloat(inst.zExpr.evaluate().toString());
            cylinderNode.setLocalTranslation(localTranslationX, localTranslationY, localTranslationZ);
        } else if(inst.entityForPos!=null) {
            if(inst.varDef.entityPos.entityJointName==null) {
                Spatial sp = getEntitySpatial(inst.entityForPos.varName,inst.entityForPos.varDef.varType);
                cylinderNode.setLocalTranslation(sp.getLocalTranslation());
            } else {
                AppModel am2 = models.get(inst.entityForPos.varName);
                if(am2!=null) {
                    Node n = am2.getJointAttachementNode(inst.varDef.entityPos.entityJointName);
                    if(n!=null) {
                        Joint j = (Joint) n.getUserData("AttachedBone");
                        Vector3f pos = j.getModelTransform().clone().combineWithParent(am2.getSkinningControl().getSpatial().getWorldTransform()).getTranslation();
                        cylinderNode.setLocalTranslation(pos);
                    }
                }
            }
        }

        if(varDef.isCollider) {
            ghost = new GhostControl(
                    new CylinderCollisionShape(new Vector3f(Math.max(radiusTop,radiusBottom), height/2, Math.max(radiusTop,radiusBottom)), 1));
            cylinderNode.addControl(ghost);
        } else {
            com.jme3.scene.shape.Cylinder cylinderMesh = new com.jme3.scene.shape.Cylinder(16, 32, radiusTop, radiusBottom, height, true, false);
            final Geometry cylinderGeo = new Geometry(cylinderName, cylinderMesh);
            cylinderNode.attachChild(cylinderGeo);

            TangentBinormalGenerator.generate(cylinderMesh);

            if (inst.varDef.shadowMode == 3) {
                cylinderGeo.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
            } else if (inst.varDef.shadowMode == 2) {
                cylinderGeo.setShadowMode(RenderQueue.ShadowMode.Receive);
            } else if (inst.varDef.shadowMode == 1) {
                cylinderGeo.setShadowMode(RenderQueue.ShadowMode.Cast);
            }

            if (inst.materialExpr != null) {
                String materialName = inst.materialExpr.evaluate().toString();
                if (!setGeometryMaterial(cylinderGeo, materialName)) {
                    handleRuntimeError("Cannot find material resource named: '" + materialName + "'");
                    return;
                }
            } else {
                Material mat1 = new Material(assetManager,
                        "Common/MatDefs/Misc/Unshaded.j3md");
                mat1.setColor("Color", ColorRGBA.Yellow);
                cylinderGeo.setMaterial(mat1);
            }

            ///////////// ADD RIGID BODY CONTROL /////////

            float mass = 1;
            boolean isPhysical = inst.massExpr!=null || inst.varDef.isStatic;
            if(isPhysical) {
                if(inst.varDef.isStatic) {
                    mass=0;
                } else {
                    mass = Float.parseFloat(inst.massExpr.evaluate().toString());
                }
            }

            CollisionShape modelShape;
            if(inst.varDef.isStatic) {
                modelShape = new MeshCollisionShape(cylinderGeo.getMesh());
            } else {
                modelShape = CollisionShapeFactory.createDynamicMeshShape(cylinderGeo);
            }

            modelCtl = new RigidBodyControl(modelShape,mass);

            if(!inst.varDef.isStatic) {
                modelCtl.setKinematic(!isPhysical);
            }

            cylinderNode.addControl(modelCtl);
        }

        cylinders.put(cylinderName,cylinderNode);
        geoName2ModelName.put(cylinderName,cylinderName);
        geoName2EntityInst.put(cylinderName,inst);

        List<java.lang.Object> ctls = new ArrayList<>();
        if(modelCtl!=null) {
            bulletAppState.getPhysicsSpace().add(modelCtl);
            ctls.add(modelCtl);
        }

        if(ghost!=null) {
            bulletAppState.getPhysicsSpace().add(ghost);
            ctls.add(ghost);
        }

        collisionControlsCache.put(cylinderName,ctls);

        if(inst.varDef.visible) {
            rootNode.attachChild(cylinderNode);
        }
    }

    private void loadHollowCylinder(HollowCylinderInst inst) {

        RigidBodyControl modelCtl = null;
        GhostControl ghost = null;

        HollowCylinderVariableDef varDef = (HollowCylinderVariableDef)inst.varDef;
        float radiusTop = inst.radiusTopExpr==null?1f:((Double)inst.radiusTopExpr.evaluate()).floatValue();
        float radiusBottom = inst.radiusBottomExpr==null?1f:((Double)inst.radiusBottomExpr.evaluate()).floatValue();
        float innerRadiusTop = inst.innerRadiusTopExpr==null?0.5f:((Double)inst.innerRadiusTopExpr.evaluate()).floatValue();
        float innerRadiusBottom = inst.innerRadiusBottomExpr==null?0.5f:((Double)inst.innerRadiusBottomExpr.evaluate()).floatValue();
        float height = inst.heightExpr==null?2f:((Double)inst.heightExpr.evaluate()).floatValue();

        // Guard against zero/negative dimensions
        if (radiusTop <= 0) radiusTop = 1f;
        if (radiusBottom <= 0) radiusBottom = 1f;
        if (innerRadiusTop <= 0) innerRadiusTop = 0.5f;
        if (innerRadiusBottom <= 0) innerRadiusBottom = 0.5f;
        if (height <= 0) height = 2f;

        String hcName = inst.varDef.varName+"@"+inst.scope.scopeId;
        Node hcNode = new Node(hcName);
        hcNode.setUserData("key",hcName);

        ////////// SET POSITION & ROTATION ///////

        if(inst.rxExpr!=null) {
            float rotateX = Float.parseFloat(inst.rxExpr.evaluate().toString());
            float rotateY = Float.parseFloat(inst.ryExpr.evaluate().toString());
            float rotateZ = Float.parseFloat(inst.rzExpr.evaluate().toString());
            hcNode.rotate(rotateX* FastMath.DEG_TO_RAD,rotateY* FastMath.DEG_TO_RAD,rotateZ* FastMath.DEG_TO_RAD);

        } else if(inst.entityForRot!=null) {
            Spatial sp = getEntitySpatial(inst.entityForRot.varName,inst.entityForRot.varDef.varType);
            hcNode.setLocalRotation(sp.getLocalRotation());
        }

        if(inst.xExpr!=null) {
            float localTranslationX = Float.parseFloat(inst.xExpr.evaluate().toString());
            float localTranslationY = Float.parseFloat(inst.yExpr.evaluate().toString());
            float localTranslationZ = Float.parseFloat(inst.zExpr.evaluate().toString());
            hcNode.setLocalTranslation(localTranslationX, localTranslationY, localTranslationZ);
        } else if(inst.entityForPos!=null) {
            if(inst.varDef.entityPos.entityJointName==null) {
                Spatial sp = getEntitySpatial(inst.entityForPos.varName,inst.entityForPos.varDef.varType);
                hcNode.setLocalTranslation(sp.getLocalTranslation());
            } else {
                AppModel am2 = models.get(inst.entityForPos.varName);
                if(am2!=null) {
                    Node n = am2.getJointAttachementNode(inst.varDef.entityPos.entityJointName);
                    if(n!=null) {
                        Joint j = (Joint) n.getUserData("AttachedBone");
                        Vector3f pos = j.getModelTransform().clone().combineWithParent(am2.getSkinningControl().getSpatial().getWorldTransform()).getTranslation();
                        hcNode.setLocalTranslation(pos);
                    }
                }
            }
        }

        if(varDef.isCollider) {
            ghost = new GhostControl(
                    new CylinderCollisionShape(new Vector3f(Math.max(radiusTop,radiusBottom), height/2, Math.max(radiusTop,radiusBottom)), 1));
            hcNode.addControl(ghost);
        } else {
            HollowCylinderMesh hcMesh = new HollowCylinderMesh(radiusTop, radiusBottom, innerRadiusTop, innerRadiusBottom, height);
            final Geometry hcGeo = new Geometry(hcName, hcMesh);
            hcNode.attachChild(hcGeo);

            TangentBinormalGenerator.generate(hcMesh);

            if (inst.varDef.shadowMode == 3) {
                hcGeo.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
            } else if (inst.varDef.shadowMode == 2) {
                hcGeo.setShadowMode(RenderQueue.ShadowMode.Receive);
            } else if (inst.varDef.shadowMode == 1) {
                hcGeo.setShadowMode(RenderQueue.ShadowMode.Cast);
            }

            if (inst.materialExpr != null) {
                String materialName = inst.materialExpr.evaluate().toString();
                if (!setGeometryMaterial(hcGeo, materialName)) {
                    handleRuntimeError("Cannot find material resource named: '" + materialName + "'");
                    return;
                }
            } else {
                Material mat1 = new Material(assetManager,
                        "Common/MatDefs/Misc/Unshaded.j3md");
                mat1.setColor("Color", ColorRGBA.Cyan);
                hcGeo.setMaterial(mat1);
            }

            boolean isPhysical = inst.massExpr!=null;
            float mass = 0;
            if(isPhysical) {
                if(inst.varDef.isStatic) {
                    mass=0;
                } else {
                    mass = Float.parseFloat(inst.massExpr.evaluate().toString());
                }
            }

            CollisionShape modelShape;
            if(inst.varDef.isStatic) {
                modelShape = new MeshCollisionShape(hcGeo.getMesh());
            } else {
                modelShape = CollisionShapeFactory.createDynamicMeshShape(hcGeo);
            }

            modelCtl = new RigidBodyControl(modelShape,mass);

            if(!inst.varDef.isStatic) {
                modelCtl.setKinematic(!isPhysical);
            }

            hcNode.addControl(modelCtl);
        }

        hollowCylinders.put(hcName,hcNode);
        geoName2ModelName.put(hcName,hcName);
        geoName2EntityInst.put(hcName,inst);

        List<java.lang.Object> ctls = new ArrayList<>();
        if(modelCtl!=null) {
            bulletAppState.getPhysicsSpace().add(modelCtl);
            ctls.add(modelCtl);
        }

        if(ghost!=null) {
            bulletAppState.getPhysicsSpace().add(ghost);
            ctls.add(ghost);
        }

        collisionControlsCache.put(hcName,ctls);

        if(inst.varDef.visible) {
            rootNode.attachChild(hcNode);
        }
    }

    private void loadQuad(QuadInst inst) {

        RigidBodyControl modelCtl = null;

        QuadVariableDef varDef = (QuadVariableDef)inst.varDef;
        float width = inst.widthExpr==null?1f:((Double)inst.widthExpr.evaluate()).floatValue();
        float height = inst.heightExpr==null?1f:((Double)inst.heightExpr.evaluate()).floatValue();

        String quadName = inst.varDef.varName+"@"+inst.scope.scopeId;
        Node quadNode = new Node(quadName);
        quadNode.setUserData("key",quadName);

        ////////// SET POSITION & ROTATION ///////

        if(inst.rxExpr!=null) {
            float rotateX = Float.parseFloat(inst.rxExpr.evaluate().toString());
            float rotateY = Float.parseFloat(inst.ryExpr.evaluate().toString());
            float rotateZ = Float.parseFloat(inst.rzExpr.evaluate().toString());
            quadNode.rotate(rotateX* FastMath.DEG_TO_RAD,rotateY* FastMath.DEG_TO_RAD,rotateZ* FastMath.DEG_TO_RAD);

        } else if(inst.entityForRot!=null) {
            Spatial sp = getEntitySpatial(inst.entityForRot.varName,inst.entityForRot.varDef.varType);
            quadNode.setLocalRotation(sp.getLocalRotation());
        }

        if(inst.xExpr!=null) {
            float localTranslationX = Float.parseFloat(inst.xExpr.evaluate().toString());
            float localTranslationY = Float.parseFloat(inst.yExpr.evaluate().toString());
            float localTranslationZ = Float.parseFloat(inst.zExpr.evaluate().toString());
            quadNode.setLocalTranslation(localTranslationX, localTranslationY, localTranslationZ);
        } else if(inst.entityForPos!=null) {
            if(inst.varDef.entityPos.entityJointName==null) {
                Spatial sp = getEntitySpatial(inst.entityForPos.varName,inst.entityForPos.varDef.varType);
                quadNode.setLocalTranslation(sp.getLocalTranslation());
            } else {
                AppModel am2 = models.get(inst.entityForPos.varName);
                if(am2!=null) {
                    Node n = am2.getJointAttachementNode(inst.varDef.entityPos.entityJointName);
                    if(n!=null) {
                        Joint j = (Joint) n.getUserData("AttachedBone");
                        Vector3f pos = j.getModelTransform().clone().combineWithParent(am2.getSkinningControl().getSpatial().getWorldTransform()).getTranslation();
                        quadNode.setLocalTranslation(pos);
                    }
                }
            }
        }

        com.jme3.scene.shape.Quad quadMesh = new com.jme3.scene.shape.Quad(width, height);
        final Geometry quadGeo = new Geometry(quadName, quadMesh);
        quadNode.attachChild(quadGeo);

        TangentBinormalGenerator.generate(quadMesh);

        if (inst.varDef.shadowMode == 3) {
            quadGeo.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        } else if (inst.varDef.shadowMode == 2) {
            quadGeo.setShadowMode(RenderQueue.ShadowMode.Receive);
        } else if (inst.varDef.shadowMode == 1) {
            quadGeo.setShadowMode(RenderQueue.ShadowMode.Cast);
        }

        if (inst.materialExpr != null) {
            String materialName = inst.materialExpr.evaluate().toString();
            if (!setGeometryMaterial(quadGeo, materialName)) {
                handleRuntimeError("Cannot find material resource named: '" + materialName + "'");
                return;
            }
        } else {
            Material mat1 = new Material(assetManager,
                    "Common/MatDefs/Misc/Unshaded.j3md");
            mat1.setColor("Color", ColorRGBA.Magenta);
            quadGeo.setMaterial(mat1);
        }

        ///////////// ADD RIGID BODY CONTROL /////////

        float mass = 1;
        boolean isPhysical = inst.massExpr!=null || inst.varDef.isStatic;
        if(isPhysical) {
            if(inst.varDef.isStatic) {
                mass=0;
            } else {
                mass = Float.parseFloat(inst.massExpr.evaluate().toString());
            }
        }

        CollisionShape modelShape;
        if(inst.varDef.isStatic) {
            modelShape = new MeshCollisionShape(quadGeo.getMesh());
        } else {
            modelShape = CollisionShapeFactory.createDynamicMeshShape(quadGeo);
        }

        modelCtl = new RigidBodyControl(modelShape,mass);

        if(!inst.varDef.isStatic) {
            modelCtl.setKinematic(!isPhysical);
        }

        quadNode.addControl(modelCtl);

        quads.put(quadName,quadNode);
        geoName2ModelName.put(quadName,quadName);
        geoName2EntityInst.put(quadName,inst);

        List<java.lang.Object> ctls = new ArrayList<>();
        if(modelCtl!=null) {
            bulletAppState.getPhysicsSpace().add(modelCtl);
            ctls.add(modelCtl);
        }

        collisionControlsCache.put(quadName,ctls);

        if(inst.varDef.visible) {
            rootNode.attachChild(quadNode);
        }
    }

    private boolean setGeometryMaterial(Geometry geo, String materialName) {

        materialName=materialName.toLowerCase();
        ResourceSetup2D resource = null;
        ResourceMaterial m = materials.get(materialName.toLowerCase());
        if(m==null) {
            resource = assetsMapping.getSpriteSheetsIndex().get(materialName);

            if(resource==null) {
                return false;
            }
            //return false;
        }

        Material geoMat = new Material(assetManager,
                "Common/MatDefs/Light/Lighting.j3md");

        if(m!=null) {
            geoMat.setTexture("DiffuseMap",
                    assetManager.loadTexture(m.diffuseMap));//

            if (m.normalMap != null) {
                geoMat.setTexture("NormalMap",
                        assetManager.loadTexture(m.normalMap));//
            }

        } else {
            geoMat.setTexture("DiffuseMap",
                    assetManager.loadTexture(resource.path));//
        }

        geoMat.setBoolean("UseMaterialColors",true);
        geoMat.setColor("Diffuse",ColorRGBA.White);
        geoMat.setColor("Specular",ColorRGBA.White);
        geoMat.setColor("Ambient",ColorRGBA.White);
        geoMat.setFloat("Shininess", 64f);  // [0,128]
        geo.setMaterial(geoMat);

        return true;

    }

    private boolean setSpatialShader(Spatial spatial, String shaderName, boolean uiMode) {
        if (spatial == null) {
            return false;
        }

        if (shaderName == null || shaderName.trim().isEmpty()) {
            restoreOriginalMaterials(spatial);
            return true;
        }

        if (assetsMapping == null) {
            return false;
        }

        ResourceShader shader = assetsMapping.getShadersIndex().get(shaderName.toLowerCase(Locale.ROOT));
        if (shader == null) {
            return false;
        }

        final boolean[] applied = {false};
        forEachGeometry(spatial, geometry -> {
            rememberOriginalMaterial(geometry);
            Material sourceMaterial = originalMaterials.get(geometry);
            if (sourceMaterial == null) {
                sourceMaterial = geometry.getMaterial();
            }
            Material shaderMaterial = buildShaderMaterial(shader, sourceMaterial, uiMode);
            if (shaderMaterial != null) {
                geometry.setMaterial(shaderMaterial);
                geometry.setQueueBucket(uiMode ? RenderQueue.Bucket.Gui : RenderQueue.Bucket.Transparent);
                applied[0] = true;
            }
        });
        return applied[0];
    }

    private Material buildShaderMaterial(ResourceShader shader, Material sourceMaterial, boolean uiMode) {
        Material shaderTemplate;
        try {
            shaderTemplate = assetManager.loadMaterial(shader.path.replace(".j3md", ".j3m"));
        } catch (Exception ex) {
            try {
                shaderTemplate = new Material(assetManager, shader.path);
            } catch (Exception innerEx) {
                return null;
            }
        }

        Material shaderMaterial = new Material(assetManager, shader.path);
        copyAllParams(shaderTemplate, shaderMaterial);
        applyShaderMaterialDefaultsFromAsset(shader, shaderMaterial);
        boolean useOriginalTexture = getBooleanParam(shaderMaterial, "UseOriginalTexture", true);

        if (sourceMaterial != null) {
            if (useOriginalTexture) {
                copyTextureParamIfPresent(sourceMaterial, shaderMaterial, "ColorMap", "ColorMap");
                copyTextureParamIfPresent(sourceMaterial, shaderMaterial, "DiffuseMap", "ColorMap");
                copyTextureParamIfPresent(sourceMaterial, shaderMaterial, "Texture", "ColorMap");
                copyFirstTextureParamIfPresent(sourceMaterial, shaderMaterial, "ColorMap");
            }
            copyParamIfPresent(sourceMaterial, shaderMaterial, "VertexColor", "VertexColor");
        }

        ensureDefaultColorParam(shaderMaterial, "MainColor", ColorRGBA.White);

        if (uiMode) {
            shaderMaterial.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            shaderMaterial.getAdditionalRenderState().setDepthWrite(false);
            shaderMaterial.getAdditionalRenderState().setDepthTest(false);
        }

        if (shaderMaterial.getMaterialDef() != null && shaderMaterial.getMaterialDef().getMaterialParam("Time") != null) {
            runtimeShaderMaterials.add(shaderMaterial);
            shaderMaterial.setFloat("Time", runtimeShaderElapsedTime);
        }

        return shaderMaterial;
    }

    private void applyShaderMaterialDefaultsFromAsset(ResourceShader shader, Material shaderMaterial) {
        if (shader == null || shaderMaterial == null) {
            return;
        }

        String j3mPath = shader.path.replace(".j3md", ".j3m");
        try {
            AssetInfo assetInfo = assetManager.locateAsset(new AssetKey(j3mPath));
            if (assetInfo == null) {
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(assetInfo.openStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    applyShaderMaterialDefaultLine(shaderMaterial, line);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private boolean getBooleanParam(Material material, String paramName, boolean defaultValue) {
        if (material == null || paramName == null || paramName.isEmpty()) {
            return defaultValue;
        }

        MatParam param = material.getParam(paramName);
        if (param != null && param.getValue() instanceof Boolean) {
            return (Boolean) param.getValue();
        }

        MaterialDef def = material.getMaterialDef();
        if (def != null && def.getMaterialParam(paramName) != null) {
            Object defaultVal = def.getMaterialParam(paramName).getValue();
            if (defaultVal instanceof Boolean) {
                return (Boolean) defaultVal;
            }
        }

        return defaultValue;
    }

    private void applyShaderMaterialDefaultLine(Material material, String line) {
        if (material == null || line == null) {
            return;
        }

        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("Material ") || trimmed.equals("{") || trimmed.equals("}")) {
            return;
        }

        int sep = trimmed.indexOf(':');
        if (sep <= 0) {
            return;
        }

        String paramName = trimmed.substring(0, sep).trim();
        String rawValue = trimmed.substring(sep + 1).trim();
        if (paramName.isEmpty() || rawValue.isEmpty()) {
            return;
        }

        MaterialDef def = material.getMaterialDef();
        if (def == null || def.getMaterialParam(paramName) == null) {
            return;
        }

        VarType varType = def.getMaterialParam(paramName).getVarType();
        try {
            if (varType == VarType.Float) {
                material.setFloat(paramName, Float.parseFloat(rawValue));
            } else if (varType == VarType.Boolean) {
                material.setBoolean(paramName, Boolean.parseBoolean(rawValue));
            } else if (varType == VarType.Vector4 || varType == VarType.Vector4Array) {
                String[] parts = rawValue.split("\\s+");
                if (parts.length >= 4) {
                    material.setColor(paramName, new ColorRGBA(
                            Float.parseFloat(parts[0]),
                            Float.parseFloat(parts[1]),
                            Float.parseFloat(parts[2]),
                            Float.parseFloat(parts[3])
                    ));
                }
            } else if (varType == VarType.Texture2D && !rawValue.isEmpty()) {
                material.setTexture(paramName, assetManager.loadTexture(rawValue));
            }
        } catch (Exception ignored) {
        }
    }

    private void copyAllParams(Material sourceMaterial, Material targetMaterial) {
        if (sourceMaterial == null || targetMaterial == null) {
            return;
        }

        for (MatParam param : sourceMaterial.getParams()) {
            if (param == null || param.getValue() == null) {
                continue;
            }

            try {
                VarType varType = param.getVarType();
                if (varType != null) {
                    targetMaterial.setParam(param.getName(), varType, param.getValue());
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void copyTextureParamIfPresent(Material sourceMaterial, Material targetMaterial, String fromName, String toName) {
        MaterialDef targetDef = targetMaterial.getMaterialDef();
        if (targetDef == null || targetDef.getMaterialParam(toName) == null) {
            return;
        }

        MatParam param = sourceMaterial.getParam(fromName);
        if (param instanceof MatParamTexture) {
            targetMaterial.setTexture(toName, ((MatParamTexture) param).getTextureValue());
        }
    }

    private void copyFirstTextureParamIfPresent(Material sourceMaterial, Material targetMaterial, String toName) {
        MaterialDef targetDef = targetMaterial.getMaterialDef();
        if (targetDef == null || targetDef.getMaterialParam(toName) == null || targetMaterial.getParam(toName) != null) {
            return;
        }

        for (MatParam param : sourceMaterial.getParams()) {
            if (param instanceof MatParamTexture) {
                targetMaterial.setTexture(toName, ((MatParamTexture) param).getTextureValue());
                return;
            }
        }
    }

    private void copyParamIfPresent(Material sourceMaterial, Material targetMaterial, String fromName, String toName) {
        MaterialDef targetDef = targetMaterial.getMaterialDef();
        if (targetDef == null || targetDef.getMaterialParam(toName) == null) {
            return;
        }

        MatParam sourceParam = sourceMaterial.getParam(fromName);
        if (sourceParam == null || sourceParam.getValue() == null || sourceParam.getVarType() == null) {
            return;
        }

        try {
            targetMaterial.setParam(toName, sourceParam.getVarType(), sourceParam.getValue());
        } catch (Exception ignored) {
        }
    }

    private void ensureDefaultColorParam(Material material, String paramName, ColorRGBA defaultColor) {
        if (material == null || defaultColor == null) {
            return;
        }

        MaterialDef targetDef = material.getMaterialDef();
        if (targetDef == null || targetDef.getMaterialParam(paramName) == null) {
            return;
        }

        if (material.getParam(paramName) == null) {
            material.setColor(paramName, defaultColor.clone());
        }
    }

    private void rememberOriginalMaterial(Geometry geometry) {
        if (geometry == null || originalMaterials.containsKey(geometry) || geometry.getMaterial() == null) {
            return;
        }

        originalMaterials.put(geometry, geometry.getMaterial().clone());
        originalMaterialBuckets.put(geometry, geometry.getQueueBucket());
    }

    private void restoreOriginalMaterials(Spatial spatial) {
        forEachGeometry(spatial, geometry -> {
            Material original = originalMaterials.get(geometry);
            if (original != null) {
                geometry.setMaterial(original.clone());
            }
            RenderQueue.Bucket originalBucket = originalMaterialBuckets.get(geometry);
            if (originalBucket != null) {
                geometry.setQueueBucket(originalBucket);
            }
        });
    }

    private void forEachGeometry(Spatial spatial, java.util.function.Consumer<Geometry> visitor) {
        if (spatial == null || visitor == null) {
            return;
        }

        if (spatial instanceof Geometry) {
            visitor.accept((Geometry) spatial);
            return;
        }

        if (spatial instanceof Node) {
            for (Spatial child : ((Node) spatial).getChildren()) {
                forEachGeometry(child, visitor);
            }
        }
    }

    public Spatial loadModelSpatial(final String name, String resourcePath, final ModelInst modelInst) {
        final ResourceSetup resource = assetsMapping.get3DModelsIndex().get(resourcePath.toLowerCase());
        if (resource == null) {
            this.handleRuntimeError("Error: line " + modelInst.varDef.varLineNum + ", resource '" + resourcePath + "' doesn't exist");
            return null;
        }

        if (modelInst.varDef.isVehicle) {
            SceneMax3DGenericVehicle v = loadVehicleModel(name, modelInst, resource);
            v.getNode().setShadowMode(RenderQueue.ShadowMode.Cast);
            return null;
        }

        Spatial mm = null;
        try {
            mm = assetManager.loadModel(resource.path);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (mm == null) {
            return null;
        }

        if (modelInst.varDef.shadowMode == 3) {
            mm.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        } else if (modelInst.varDef.shadowMode == 2) {
            mm.setShadowMode(RenderQueue.ShadowMode.Receive);
        } else if (modelInst.varDef.shadowMode == 1) {
            mm.setShadowMode(RenderQueue.ShadowMode.Cast);
        }

        final Node parentNode = new Node();
        parentNode.attachChild(mm); // add it to the wrapper

        /////////////////////////////////////////////////

        SkeletonControl skeletonCtrl = mm.getControl(SkeletonControl.class);
        if (skeletonCtrl != null && hostAppType == 0) {
            skeletonCtrl.setHardwareSkinningPreferred(true);// for Android 7 bug
        }

        final String modelName = name + "@" + modelInst.scope.scopeId;
        AppModel am = new AppModel(parentNode);
        am.entityInst = modelInst;
        am.resource = resource;
        models.put(modelName, am);
        parentNode.setName(modelName);

        final Vector3f scale;
        if (modelInst.scaleExpr != null) {
            float sc = Float.parseFloat(modelInst.scaleExpr.evaluate().toString());
            parentNode.scale(sc, sc, sc);
            scale = new Vector3f(sc, sc, sc);

        } else {
            parentNode.scale(resource.scaleX, resource.scaleY, resource.scaleZ);
            scale = new Vector3f(resource.scaleX, resource.scaleY, resource.scaleZ);
        }

        if (modelInst.varDef.useVerbalTurn) {

            float rotateX = 0;
            float rotateY = 0;
            float rotateZ = 0;

            if (modelInst.rxExpr != null) {
                rotateX = Float.parseFloat(modelInst.rxExpr.evaluate().toString()) * modelInst.varDef.rotDir;
            } else if (modelInst.ryExpr != null) {
                rotateY = Float.parseFloat(modelInst.ryExpr.evaluate().toString()) * modelInst.varDef.rotDir;
            } else if (modelInst.rzExpr != null) {
                rotateZ = Float.parseFloat(modelInst.rzExpr.evaluate().toString()) * modelInst.varDef.rotDir;
            }


            if (!modelInst.varDef.isVehicle) {
                parentNode.rotate(rotateX * FastMath.DEG_TO_RAD, rotateY * FastMath.DEG_TO_RAD, rotateZ * FastMath.DEG_TO_RAD);
            }


        } else if (modelInst.rxExpr != null) {

            float rotateX = Float.parseFloat(modelInst.rxExpr.evaluate().toString());
            float rotateY = Float.parseFloat(modelInst.ryExpr.evaluate().toString());
            float rotateZ = Float.parseFloat(modelInst.rzExpr.evaluate().toString());

            if (!modelInst.varDef.isVehicle) {
                parentNode.rotate(rotateX * FastMath.DEG_TO_RAD, rotateY * FastMath.DEG_TO_RAD, rotateZ * FastMath.DEG_TO_RAD);
            } else {
                // vehicle model will be rotated later
                //entityLocalRotation = new Quaternion().fromAngles(rotateX * FastMath.DEG_TO_RAD, rotateY * FastMath.DEG_TO_RAD, rotateZ * FastMath.DEG_TO_RAD);
            }

        } else if (modelInst.entityForRot != null) {
            Spatial sp = getEntitySpatial(modelInst.entityForRot.varName, modelInst.entityForRot.varDef.varType);
            // non vehicle models can be rotated immediatley
            if (!modelInst.varDef.isVehicle) {
                parentNode.setLocalRotation(sp.getLocalRotation());
            } else {
                // vehicle model will be rotated later
                //entityLocalRotation = sp.getLocalRotation();
            }

        } else if (resource.rotateY != 0f && !modelInst.varDef.isVehicle) {
            Quaternion roll = new Quaternion();
            roll.fromAngleAxis(FastMath.PI * resource.rotateY / 180, new Vector3f(0, 1, 0));
            parentNode.setLocalRotation(roll);
        }


        if (modelInst.xExpr != null) {
            float localTranslationX = Float.parseFloat(modelInst.xExpr.evaluate().toString());
            float localTranslationY = Float.parseFloat(modelInst.yExpr.evaluate().toString());
            float localTranslationZ = Float.parseFloat(modelInst.zExpr.evaluate().toString());
            parentNode.setLocalTranslation(localTranslationX, localTranslationY, localTranslationZ);
        } else if (modelInst.entityForPos != null) {

            if (modelInst.varDef.entityPos.entityJointName == null) {
                Spatial sp = getEntitySpatial(modelInst.entityForPos.varName, modelInst.entityForPos.varDef.varType);
                parentNode.setLocalTranslation(sp.getLocalTranslation());
            } else {
                AppModel am2 = models.get(modelInst.entityForPos.varName);
                if (am2 != null) {
                    Node n = am2.getJointAttachementNode(modelInst.varDef.entityPos.entityJointName);

                    if (n != null) {
                        Joint j = (Joint) n.getUserData("AttachedBone");
                        Vector3f pos = j.getModelTransform().clone().combineWithParent(am2.getSkinningControl().getSpatial().getWorldTransform()).getTranslation();
                        parentNode.setLocalTranslation(pos);
                    }
                }
            }

        } else {
            parentNode.setLocalTranslation(resource.localTranslationX, resource.localTranslationY, resource.localTranslationZ);
        }

        final List<java.lang.Object> ctls = new ArrayList<>();
        collisionControlsCache.put(modelName, ctls);

        SceneGraphVisitor visitor = new SceneGraphVisitor() {

            @Override
            public void visit(Spatial spatial) {
                if (spatial instanceof Geometry) {

                    ///// This visitor handles only static models ////////

                    Geometry geometry = (Geometry) spatial;
                    MeshCollisionShape shape = new MeshCollisionShape(geometry.getMesh());
                    shape.setScale(scale);
                    RigidBodyControl collisionControl = new RigidBodyControl(shape, 0f);
                    geometry.addControl(collisionControl);
                    String geoKey = modelName + "~" + geometry.getName();
                    geometry.setUserData("key", geoKey);
                    geoName2ModelName.put(geoKey, modelName);
                    geoName2EntityInst.put(geoKey, modelInst);
                    bulletAppState.getPhysicsSpace().add(collisionControl);
                    ctls.add(collisionControl);// cache control

                }
            }
        };

        SceneGraphVisitor visitorSetName = new SceneGraphVisitor() {

            @Override
            public void visit(Spatial spatial) {
                if (spatial instanceof Geometry) {
                    Geometry geometry = (Geometry) spatial;
                    String geoKey = modelName + "~" + geometry.getName();
                    geometry.setName(geoKey);
                    geoName2ModelName.put(geoKey, modelName);
                    geoName2EntityInst.put(geoKey, modelInst);

                }
            }
        };

        float mass = 1;
        boolean isPhysical = modelInst.massExpr != null || modelInst.varDef.isStatic;
        if (isPhysical) {
            if (modelInst.varDef.isStatic) {
                mass = 0;
            } else {
                mass = Float.parseFloat(modelInst.massExpr.evaluate().toString());
            }

        }


        ////////////////////  RIGID BODY CONTROL ////////////////////
        CollisionShape modelShape = null;

        if (modelInst.varDef.isStatic) {
            am.isStatic = true;
            parentNode.breadthFirstTraversal(visitor);
        } else {
            parentNode.breadthFirstTraversal(visitorSetName); // we need to give all sub geometries a name & map them
            // - we will use that for ray casting & other utilities
            am.skinningControlNode = findSkinningControlNode(mm);
            if (am.skinningControlNode == null) {
                am.skinningControlNode = mm;
            }

            if (modelInst.varDef.isDynamic) {
                DynamicAnimControl modelCtl = new DynamicAnimControl();
                LinkConfig defaultConfig = new LinkConfig();
                RangeOfMotion defaultRom = new RangeOfMotion(1f);

                if (modelInst.varDef.joints != null) {

                    for (String s : modelInst.varDef.joints) {
                        if (s.length() > 2) {
                            s = s.substring(1, s.length() - 1);
                            modelCtl.link(s.trim(), defaultConfig, defaultRom); // right shoulder
                        }

                    }
                }

                try {
                    am.skinningControlNode.addControl(modelCtl);
                    modelCtl.setPhysicsSpace(bulletAppState.getPhysicsSpace());
                    ctls.add(modelCtl);// cache control

                } catch (IllegalArgumentException e) {
                    showFloatingMessage("Problem adding Dynamic Animation Control To Model: " + name + "\r\n" + e.getMessage());
                    return null;
                }

            } else {

                if (modelInst.varDef.collisionShape != VariableDef.COLLISION_SHAPE_DEFAULT) {
                    if (modelInst.varDef.collisionShape == VariableDef.COLLISION_SHAPE_BOX) {
                        //modelShape = new BoxCollisionShape(((BoundingBox)parentNode.getWorldBound()).getExtent(new Vector3f()));
                        modelShape = CollisionShapeFactory.createMergedBoxShape(parentNode);
                    } else if (modelInst.varDef.collisionShape == VariableDef.COLLISION_SHAPE_BOXES) {
                        modelShape = CollisionShapeFactory.createBoxShape(parentNode);
                    }
                } else {
                    try {
                        modelShape = CollisionShapeFactory.createDynamicMeshShape(parentNode);
                    } catch (IllegalArgumentException e) {
                        // Some models have geometry with negative scale components (e.g. mirrored
                        // meshes from Blender). HullCollisionShape rejects negative scales, so
                        // fall back to a merged-box approximation which has no such restriction.
                        modelShape = CollisionShapeFactory.createMergedBoxShape(parentNode);
                    }
                }

                RigidBodyControl modelCtl = new RigidBodyControl(modelShape, mass);
                modelCtl.setKinematic(!isPhysical);
                parentNode.addControl(modelCtl);

                if (modelInst.varDef.calibration != null) {

                    float cx = ((Double) new ActionLogicalExpressionVm(modelInst.varDef.calibration.posX, modelInst.scope).evaluate()).floatValue();
                    float cy = ((Double) new ActionLogicalExpressionVm(modelInst.varDef.calibration.posY, modelInst.scope).evaluate()).floatValue();
                    float cz = ((Double) new ActionLogicalExpressionVm(modelInst.varDef.calibration.posZ, modelInst.scope).evaluate()).floatValue();

                    mm.move(cx, cy, cz);
                }

                if (isPhysical) {
                    am.physicalControl = modelCtl;
                }

                bulletAppState.getPhysicsSpace().add(modelCtl);
                ctls.add(modelCtl);// cache control

                Vector3f pos = am.skinningControlNode.worldToLocal(modelCtl.getPhysicsLocation(), null);
                am.skinningControlNode.setLocalTranslation(pos);

            }

        }

        String geoKey = modelName + "~" + parentNode.getName();
        parentNode.setUserData("key", geoKey);
        geoName2ModelName.put(geoKey, modelName);
        geoName2EntityInst.put(geoKey, modelInst);

        return parentNode;
    }

    public int attachModelSpatial(Spatial model, final ModelInst inst) {
        if (model!=null) {
            if (inst.varDef.visible) {
                rootNode.attachChild(model);
            }

            return 0;
        }

        return -1;
    }

    public int loadModel(final String name, String resourcePath, final ModelInst modelInst) {

        Spatial parentNode = this.loadModelSpatial(name, resourcePath, modelInst);
        return this.attachModelSpatial(parentNode, modelInst);

    }

    private Spatial findSkinningControlNode(Spatial sp) {

        SkinningControl sc = sp.getControl(SkinningControl.class);
        if (sc != null) {
            return sp;
        }

        if(sp instanceof Node) {
            Node n = (Node)sp;
            for(Spatial s : n.getChildren()) {
                Spatial retval = findSkinningControlNode(s);
                if(retval!=null) {
                    return retval;
                }
            }
        }

        return null;
    }

    protected SceneMax3DGenericVehicle loadVehicleModel(String name, final ModelInst modelInst, ResourceSetup resource) {

        Quaternion entityLocalRotation = null;

        if (modelInst.rxExpr != null) {

            float rotateX = Float.parseFloat(modelInst.rxExpr.evaluate().toString());
            float rotateY = Float.parseFloat(modelInst.ryExpr.evaluate().toString());
            float rotateZ = Float.parseFloat(modelInst.rzExpr.evaluate().toString());

            // vehicle model will be rotated later
            entityLocalRotation = new Quaternion().fromAngles(rotateX * FastMath.DEG_TO_RAD, rotateY * FastMath.DEG_TO_RAD, rotateZ * FastMath.DEG_TO_RAD);


        } else if (modelInst.entityForRot != null) {
            Spatial sp = getEntitySpatial(modelInst.entityForRot.varName, modelInst.entityForRot.varDef.varType);
            // non vehicle models can be rotated immediatley
            // vehicle model will be rotated later
            entityLocalRotation = sp.getLocalRotation();

        }


        Vector3f initLocation = null;
        if (modelInst.xExpr != null) {
            float localTranslationX = Float.parseFloat(modelInst.xExpr.evaluate().toString());
            float localTranslationY = Float.parseFloat(modelInst.yExpr.evaluate().toString());
            float localTranslationZ = Float.parseFloat(modelInst.zExpr.evaluate().toString());
            initLocation=new Vector3f(localTranslationX, localTranslationY, localTranslationZ);
        } else if (modelInst.entityForPos != null) {
            Spatial sp = getEntitySpatial(modelInst.entityForPos.varName, modelInst.entityForPos.varDef.varType);
            initLocation=sp.getLocalTranslation();
        }

        SceneMax3DGenericVehicle c = switchModelToCarMode2(resource, initLocation,entityLocalRotation);
        final String modelName = name + "@" + modelInst.scope.scopeId;

        final List<java.lang.Object> ctls = new ArrayList<>();
        collisionControlsCache.put(modelName, ctls);

        AppModel am = new AppModel(c.getNode());
        am.entityInst  = modelInst;
        am.resource = resource;
        models.put(modelName, am);
        c.getNode().setName(modelName);
        c.getNode().setUserData("key",modelName);

        geoName2ModelName.put(modelName, modelName);
        geoName2EntityInst.put(modelName, modelInst);

        am.physicalControl = c;

        return c;
    }

    @Override
    public int loadSprite(SpriteInst spriteInst) {

        String fromRes=spriteInst.varDef.resName;
        String name = spriteInst.varDef.varName+"@"+spriteInst.scope.scopeId;
        ResourceSetup2D resource = assetsMapping.getSpriteSheetsIndex().get(fromRes.toLowerCase());
        if(resource==null){
            return -1;
        }

        int rows = spriteInst.spriteDef.rows;
        int cols = spriteInst.spriteDef.cols;

        if(rows==0) {
            rows=resource.rows;
        }

        if(cols==0) {
            cols=resource.cols;
        }

        float width = 1.0f, height = 1.0f;
        if (spriteInst.spriteDef.widthExpr!=null) {
            width = ((Double)new ActionLogicalExpressionVm(spriteInst.spriteDef.widthExpr,spriteInst.scope).evaluate()).floatValue();
            height = ((Double)new ActionLogicalExpressionVm(spriteInst.spriteDef.heightExpr,spriteInst.scope).evaluate()).floatValue();
        }
        SpriteEmitter sprite = new SpriteEmitter(
                name,rows,cols,width,height,
                assetManager, assetManager.loadTexture(resource.path));
        sprite.entityInst=spriteInst;
        sprite.getSpatial().setName(name);
        sprites.put(name,sprite);
        geoName2ModelName.put(name,name);
        geoName2EntityInst.put(name,spriteInst);

        if(spriteInst.xExpr!=null) {
            float localTranslationX = Float.parseFloat(spriteInst.xExpr.evaluate().toString());
            float localTranslationY = Float.parseFloat(spriteInst.yExpr.evaluate().toString());
            float localTranslationZ = Float.parseFloat(spriteInst.zExpr.evaluate().toString());
            sprite.getSpatial().setLocalTranslation(localTranslationX, localTranslationY, localTranslationZ);
        } else if(spriteInst.entityForPos!=null) {
            if(spriteInst.varDef.entityPos.entityJointName==null) {
                Spatial sp = getEntitySpatial(spriteInst.entityForPos.varName,spriteInst.entityForPos.varDef.varType);
                sprite.getSpatial().setLocalTranslation(sp.getLocalTranslation());
            } else {
                AppModel am2 = models.get(spriteInst.entityForPos.varName);
                if(am2!=null) {
                    Node n = am2.getJointAttachementNode(spriteInst.varDef.entityPos.entityJointName);
                    if(n!=null) {
                        Joint j = (Joint)n.getUserData("AttachedBone");
                        Vector3f pos = j.getModelTransform().clone().combineWithParent(am2.getSkinningControl().getSpatial().getWorldTransform()).getTranslation();
                        sprite.getSpatial().setLocalTranslation(pos);
                    }
                }

            }

        } else {
            sprite.getSpatial().setLocalTranslation(resource.localTranslationX, resource.localTranslationY, resource.localTranslationZ);
        }

        if(spriteInst.spriteDef.scaleExpr!=null) {
            Double val = (Double)new ActionLogicalExpressionVm(spriteInst.spriteDef.scaleExpr,spriteInst.scope).evaluate();
            sprite.getSpatial().setLocalScale(val.floatValue());
        }

        if(spriteInst.spriteDef.isBillboard) {
            BillboardControl control=new BillboardControl();
            sprite.getSpatial().addControl(control);
        }

        if (spriteInst.spriteDef.hasCollisionShape) {
            CollisionShape modelShape =
                    CollisionShapeFactory.createBoxShape(sprite.getSpatial());
            RigidBodyControl modelCtl = new RigidBodyControl(modelShape, 1);
            modelCtl.setKinematic(true);
            sprite.getSpatial().addControl(modelCtl);
            bulletAppState.getPhysicsSpace().add(modelCtl);
            List<java.lang.Object> ctls = new ArrayList<>();
            ctls.add(modelCtl);
            collisionControlsCache.put(name, ctls);
        }

        if (spriteInst.spriteDef.visible) {
            sprite.attachTo(rootNode);
            //rootNode.attachChild(sprite.getSpatial());
        }

        return 0;
    }


    public int registerController(SceneMaxBaseController c) {

        c.setUIProxy(this);
        c.init();
        _controllers.add(c);
        if(c.isEventHandler) {
            this.eventHandlersCount++;
        }
        return 0;
    }

    @Override
    public void print(String printChannel, String txt, String color, double x, double y, double z, String font, double fontSize, boolean append) {

        boolean attachToGui = false;
        String requestedFontKey = font != null ? font.toLowerCase(Locale.ROOT) : "";
        BitmapText hudText = _printChannels.get(printChannel);
        String existingFontKey = hudText != null ? hudText.getUserData("fontKey") : null;

        if(hudText!=null && !Objects.equals(existingFontKey, requestedFontKey)) {
            String existingText = hudText.getText();
            hudText.removeFromParent();
            hudText = null;
            _printChannels.remove(printChannel);
            if(append && existingText != null) {
                txt = existingText.concat(txt);
            }
            append = false;
        }

        if(hudText==null) {
            attachToGui=true;

            BitmapFont fnt=guiFont;
            if(font!=null) {
                ResourceFont resFont = resolveFontResource(font);
                if(resFont!=null) {
                    fnt=assetManager.loadFont(resFont.path);
                } else {
                    logger.warning("Requested font not found in assets mapping: " + font);
                }
            }
            hudText = new BitmapText(fnt, false);
            hudText.setText("");
            hudText.setUserData("fontKey", requestedFontKey);
            hudText.setUserData("renderedSize", fnt.getCharSet().getRenderedSize());
            _printChannels.put(printChannel,hudText);

            if(x==-1) {
                x=0;
                y=hudText.getLineHeight()*7;
                z=0;
            }

            if(fontSize==0) {
                fontSize=2;
            }

            if(color==null) {
                color="white";
            }
        }

        if(append) {
            String tmp = hudText.getText();
            if (tmp == null) {
                tmp = "";
            }
            hudText.setText(tmp.concat(txt));
        } else {
            hudText.setText(txt);
        }

        if(fontSize!=0) {
            Integer renderedSize = hudText.getUserData("renderedSize");
            if(renderedSize==null) {
                renderedSize = guiFont.getCharSet().getRenderedSize();
            }
            hudText.setSize(renderedSize*(float)fontSize);      // font size
        }

        if(x!=-1) {
            float yPos = settings.getHeight()-(float)y;
            hudText.setLocalTranslation((float)x,yPos , (float)z); // position//hudText.getLineHeight()*5
        }

        if(color!=null) {
            hudText.setColor(colorFromString(color));
        }

        if(attachToGui) {
            guiNode.attachChild(hudText);
        }

    }

    private ResourceFont resolveFontResource(String font) {
        if(font==null || assetsMapping==null) {
            return null;
        }

        String fontKey = font.toLowerCase(Locale.ROOT);
        return assetsMapping.getFontsIndex().get(fontKey);
    }


    public void showHideSprite(String varName, ActionCommandShowHide show) {
        SpriteEmitter se = sprites.get(varName);
        if(se!=null) {

            if(show.axisX || show.axisY || show.axisZ) {
                attachCoordinateAxes(se.getSpatial().getWorldTranslation(),show,rootNode);
                return;
            }

            if(!show.show) {
                se.hide();
            }else {
                se.show(rootNode);
            }
        }
    }


    public void showHideModel(String varName, ActionCommandShowHide show) {

        AppModel am = models.get(varName);

        if(am!=null) {

            if(show.axisX || show.axisY || show.axisZ) {
                attachCoordinateAxes(am.model.getWorldTranslation(),show,rootNode);
                return;
            }

            if(show.info) {

                List<String> rows=new ArrayList<>();

                rows.add(am.resource.name+" Information");
                rows.add("--------------------------------------");

                String animations = am.getAnimationsList();
                if(animations.length()>0) {
                    rows.add("");
                    rows.add("Animations:");
                    rows.add(animations);
                }

//                String joints = am.getJointsList();
//                if(animations.length()>0) {
//                    rows.add("");
//                    rows.add("Joints:");
//                    if(joints==null || joints.length()==0) {
//                        joints = "No joints found for this model";
//                    }
//                    rows.add(joints);
//                }

                String msg = prepareFloatingMessage(rows);
                showFloatingMessage(msg, "OK", 10);
                if(show.infoDumpFile!=null) {

                    Util.writeFile(show.infoDumpFile,msg);
                }

                return;
            }

            if(show.wireframe) {
                setGeometryWirframeMode(am.model,show.show);
                return;
            }

            if(show.speedo) {
                showHideSpeedo(am, show.show);
                return;
            }

            if(show.tacho) {
                showHideTacho(am,show.show);
                return;
            }

            if(show.joints) {
                showHideJoints(am,show.show, show.showJointsSizeVal);
                return;
            }

            if(show.outline) {
                showHideOutline(am.model,show.show);
                return;
            }

            ////// order is important - this should be the last check ////////////
            if(!show.show) {

                removeOutlineFilter(am.model);
                am.model.removeFromParent();
                removeCollisionControlsFromPhysics(varName);

            } else {
                rootNode.attachChild(am.model);
                addCollisionControlsToPhysics(varName);
            }

        }

    }

    private void removeOutlineFilter(Spatial sp) {
        OutlineFilter filter = sp.getUserData("OutlineFilter");
        if(filter!=null){
            sp.setUserData("OutlineFilter",null);
            filter.getOutlineViewPort().detachScene(sp);
        }
    }

    private void showHideTacho(AppModel am, boolean show) {

        if (am.physicalControl instanceof Vehicle) {

            SceneMax3DGenericVehicle v = (SceneMax3DGenericVehicle) am.physicalControl;

            if(show) {
                v.showTacho(stateManager);
            } else {
                v.removeTacho(stateManager);
            }


        }

    }

    private void showHideSpeedo(AppModel am, boolean show) {

        if (am.physicalControl instanceof Vehicle) {

            SceneMax3DGenericVehicle v = (SceneMax3DGenericVehicle) am.physicalControl;

            if(show) {
                v.showSpeedo(stateManager);
            } else {
                v.removeSpeedo(stateManager);
            }

        }

    }

    private void showHideJoints(AppModel model, final boolean showJoints, Double size) {
        String retval = model.getJointsList();
        if(retval!=null && retval.length()>0) {
            String joints[] = retval.split(",");
            for(int i=0;i<joints.length;++i) {
                String jointName = joints[i];

                Node n = model.getJointAttachementNode(jointName);
                BillboardControl control=new BillboardControl();
                n.addControl(control);

                Label l = new Label(jointName);

                float fontSize=2f;
                if(size!=null) {
                    fontSize=size.floatValue();
                }
                l.setFontSize(fontSize);
                l.setPreferredSize(new Vector3f(20f,1f,0));
                n.attachChild(l);

            }
        }
    }

    private void setGeometryWirframeMode(Node model, final boolean wireframe) {

        SceneGraphVisitor visitor = new SceneGraphVisitor() {

            @Override
            public void visit(Spatial spatial) {
                if(spatial instanceof Geometry) {
                    Geometry geometry = (Geometry)spatial;
                    setGeometryWireframeMode(geometry,wireframe);
                }
            }
        };
        model.breadthFirstTraversal(visitor);

    }

    private void setGeometryWireframeMode(Geometry g, boolean wireframe) {
        g.getMaterial().getAdditionalRenderState().setWireframe(wireframe);
    }

    private void addCollisionControlsToPhysics(String varName) {
        List<java.lang.Object> ctls = collisionControlsCache.get(varName);
        addCollisionControlsToPhysics(ctls);
    }

    private void addCollisionControlsToPhysics(List<java.lang.Object> ctls) {
        for(java.lang.Object ctl:ctls){
            bulletAppState.getPhysicsSpace().add(ctl);
        }
    }

    private void removeCollisionControlsFromPhysics(String varName) {
        List<java.lang.Object> ctls = collisionControlsCache.get(varName);
        if (ctls!=null) {
            removeCollisionControlsFromPhysics(ctls);
        }
    }

    private void removeCollisionControlsFromPhysics(List<java.lang.Object> ctls) {
        if (ctls == null || bulletAppState == null || bulletAppState.getPhysicsSpace() == null) {
            return;
        }

        for(java.lang.Object ctl:ctls){
            if (ctl != null) {
                bulletAppState.getPhysicsSpace().remove(ctl);
            }
        }
    }

    private ColorRGBA colorFromString(String color) {

        color=color.toLowerCase();
        if(color.equals("black")) {
            return ColorRGBA.Black;
        } else if(color.equals("blue")) {
            return ColorRGBA.Blue;
        } else if(color.equals("brown")) {
            return ColorRGBA.Brown;
        } else if(color.equals("cyan")) {
            return ColorRGBA.Cyan;
        } else if(color.equals("darkGray")) {
            return ColorRGBA.DarkGray;
        } else if(color.equals("gray")) {
            return ColorRGBA.Gray;
        } else if(color.equals("green")) {
            return ColorRGBA.Green;
        } else if(color.equals("lightGray")) {
            return ColorRGBA.LightGray;
        } else if(color.equals("magenta")) {
            return ColorRGBA.Magenta;
        } else if(color.equals("orange")) {
            return ColorRGBA.Orange;
        } else if(color.equals("pink")) {
            return ColorRGBA.Pink;
        } else if(color.equals("red")) {
            return ColorRGBA.Red;
        } else if(color.equals("white")) {
            return ColorRGBA.White;
        } else if(color.equals("yellow")) {
            return ColorRGBA.Yellow;
        }

        return ColorRGBA.White;
    }

    @Override
    public void rotateCamera(int axisNum, float direction, float rotateVal) {


        float[] ff = new float[3];
        cam.getRotation().toAngles(ff);

        if(axisNum==1) {
            ff[0]+=rotateVal * direction* FastMath.DEG_TO_RAD;
        } else if(axisNum==2) {
            ff[1]+=rotateVal * direction* FastMath.DEG_TO_RAD;
        } else if(axisNum==3) {
            ff[2]+=rotateVal * direction* FastMath.DEG_TO_RAD;
        }

        Quaternion q = new Quaternion();
        q.fromAngles(ff);
        cam.setRotation(q);
    }

    @Override
    public void moveCamera(int axisNum, float direction, float val) {

        Vector3f pos = cam.getLocation();

        if(axisNum==1) {
            cam.setLocation(pos.add(val * direction, 0, 0));
            //cam.move(val * direction, 0, 0);
        } else if(axisNum==2) {
            cam.setLocation(pos.add(0, val * direction, 0));
        } else if(axisNum==3) {
            cam.setLocation(pos.add(0, 0, val * direction));
        }

    }

    @Override
    public void moveSprite(String targetVar, int axisNum, float direction, float moveVal) {
        Spatial m = sprites.get(targetVar).getSpatial();
        if(m==null) {
            return;
        }

        if(axisNum==1) {
            m.move(moveVal * direction, 0, 0);
        } else if(axisNum==2) {
            m.move(0, moveVal * direction, 0);
        } else if(axisNum==3) {
            m.move(0, 0, moveVal * direction);
        }


    }

    private void moveGeoNode(Node g, int axisNum, float direction, float moveVal) {

        if(g==null) {
            return;
        }
        if(axisNum==1) {
            g.move(moveVal * direction, 0, 0);
        } else if(axisNum==2) {
            g.move(0, moveVal * direction, 0);
        } else if(axisNum==3) {
            g.move(0, 0, moveVal * direction);
        }
    }

    private void moveGeo(Geometry g, int axisNum, float direction, float moveVal) {

        if(g==null) {
            return;
        }
        if(axisNum==1) {
            g.move(moveVal * direction, 0, 0);
        } else if(axisNum==2) {
            g.move(0, moveVal * direction, 0);
        } else if(axisNum==3) {
            g.move(0, 0, moveVal * direction);
        }
    }

    public void moveBox(String targetVar, int axisNum, float direction, float moveVal) {

        EntityInstBase e = geoName2EntityInst.get(targetVar);
        Node g = boxes.get(targetVar);
        moveGeoNode(g, axisNum, direction, moveVal);

    }

    @Override
    public void moveSphere(String targetVar, int axisNum, float direction, float moveVal) {
        Node g = spheres.get(targetVar);
        moveGeoNode(g, axisNum, direction, moveVal);

    }

    private void rotateGeoNode(Node g,String targetVar,int axisNum,float direction,float rotateVal) {

        if(g==null) {
            return;
        }

        if(axisNum==1) {
            g.rotate(rotateVal * direction * FastMath.DEG_TO_RAD, 0, 0);
        } else if(axisNum==2) {
            g.rotate(0, rotateVal * direction * FastMath.DEG_TO_RAD, 0);
        } else if(axisNum==3) {
            g.rotate(0, 0, rotateVal * direction * FastMath.DEG_TO_RAD);
        }

    }

//    private void rotateGeo(Geometry g,String targetVar,int axisNum,float direction,float rotateVal) {
//
//        if(g==null) {
//            return;
//        }
//
//        if(axisNum==1) {
//            g.rotate(rotateVal * direction * FastMath.DEG_TO_RAD, 0, 0);
//        } else if(axisNum==2) {
//            g.rotate(0, rotateVal * direction * FastMath.DEG_TO_RAD, 0);
//        } else if(axisNum==3) {
//            g.rotate(0, 0, rotateVal * direction * FastMath.DEG_TO_RAD);
//        }
//
//    }

    public void rotateBox(String targetVar, int axisNum, float direction, float rotateVal) {

        Node g = boxes.get(targetVar);
        rotateGeoNode(g,targetVar,axisNum,direction,rotateVal);

    }


    @Override
    public void rotateSphere(String targetVar, int axisNum, float direction, float rotateVal) {

        Node g = spheres.get(targetVar);
        rotateGeoNode(g,targetVar,axisNum,direction,rotateVal);

    }

    @Override
    public void moveModel(String targetVar, int axisNum, float direction, float moveVal) {
        AppModel am = models.get(targetVar);
        if(am==null) {
            return;
        }
        Spatial m = am.model;
        if(m==null) {
            return;
        }

        if(am.isStatic) {
            List<Object> ctls = collisionControlsCache.get(am.model.getName());
            if (ctls != null) {

                float x=0,y=0,z=0;
                if(axisNum==1) {
                    x=moveVal * direction;
                } else if(axisNum==2) {
                    y=moveVal * direction;
                } else if(axisNum==3) {
                    z=moveVal * direction;
                }
                Vector3f vec = new Vector3f(x,y,z);
                int cnt = ctls.size();
                for (int i = 0; i < cnt; ++i) {
                    RigidBodyControl rbCtl = (RigidBodyControl) ctls.get(i);// am.model.getControl(i);
                    rbCtl.setPhysicsLocation(rbCtl.getPhysicsLocation().add(vec));

                }
            }
        } else {

            if(axisNum==1) {
                m.move(moveVal * direction, 0, 0);
                //x=moveVal * direction;
            } else if(axisNum==2) {
                m.move(0, moveVal * direction, 0);
                //y=moveVal * direction;
            } else if(axisNum==3) {
                m.move(0, 0, moveVal * direction);
                //z=moveVal * direction;
            }

        }


        //RigidBodyControl c = m.getControl(RigidBodyControl.class);
        //c.setPhysicsLocation(c.getPhysicsLocation().add(new Vector3f(x,y,z)));

    }

    public void physicalRotateModel(AppModel am, int axisNum, float direction, float rotateVal) {

        float x = 0, y = 0, z = 0;
        if (axisNum == 1) {
            x = rotateVal * direction * FastMath.DEG_TO_RAD;
        } else if (axisNum == 2) {
            y = rotateVal * direction * FastMath.DEG_TO_RAD;
        } else if (axisNum == 3) {
            z = rotateVal * direction * FastMath.DEG_TO_RAD;
        }

        if(am.isStatic) {
            List<Object> ctls = collisionControlsCache.get(am.model.getName());
            if(ctls!=null) {
                int cnt = ctls.size();// am.model.getNumControls();
                for (int i = 0; i < cnt; ++i) {
                    RigidBodyControl rbCtl = (RigidBodyControl) ctls.get(i);// am.model.getControl(i);
                    Quaternion rot = rbCtl.getPhysicsRotation().fromAngles(x, y, z);
                    rbCtl.setPhysicsRotation(rot.multLocal(rbCtl.getPhysicsRotation()));

                }
            }
        } else if(am.physicalControl instanceof CharacterControl) {
            CharacterControl ctl = (CharacterControl)am.physicalControl;

            Quaternion rotateL = new Quaternion().fromAngles(x,y,z);
            Vector3f rot = ctl.getViewDirection(null);
            rotateL.multLocal(rot);

            ctl.setViewDirection(rot);

        } else if(am.physicalControl instanceof RigidBodyControl) {
            RigidBodyControl ctl  = (RigidBodyControl)am.physicalControl;
            Quaternion rot = ctl.getPhysicsRotation().fromAngles(x,y,z);
            ctl.setPhysicsRotation(rot.multLocal(ctl.getPhysicsRotation()));
//            ctl.applyTorque(new Vector3f(0, 0, 0));
//            Quaternion rot = ctl.getPhysicsRotation().fromAngles(x, y, z);
//            ctl.setPhysicsRotation(ctl.getPhysicsRotation().add(rot));
        } else if (am.physicalControl instanceof SceneMax3DGenericVehicle) {
            SceneMax3DGenericVehicle ctl  = (SceneMax3DGenericVehicle)am.physicalControl;
            Quaternion rot = ctl.getVehicleControl().getPhysicsRotation().fromAngles(x,y,z);
            ctl.getVehicleControl().setPhysicsRotation(rot.multLocal(ctl.getVehicleControl().getPhysicsRotation()));
        }

    }

    @Override
    public void rotateModel(String targetVar, int axisNum, float direction, float rotateVal) {

        AppModel am = models.get(targetVar);
        if(am==null) {
            return;
        }

        if(am.physicalControl!=null || am.isStatic) {
            physicalRotateModel(am,axisNum,direction,rotateVal);
            return;
        }

        Spatial m = am.model;

        if(axisNum==1) {
            m.rotate(rotateVal * direction * FastMath.DEG_TO_RAD, 0, 0);

        } else if(axisNum==2) {
            m.rotate(0, rotateVal * direction * FastMath.DEG_TO_RAD, 0);

        } else if(axisNum==3) {
            m.rotate(0, 0, rotateVal * direction * FastMath.DEG_TO_RAD);
        }

    }

    public HashMap<String, AppModel> getModels() {
        return models;
    }

    public void animateModel(String targetVar, String animationName, String speed, AppModelAnimationController controller) {

        AppModel m = models.get(targetVar);
        if(m!=null) {
            controller.animate(m, animationName, speed);
        }

    }

    @Override
    public void spritePlayFrames(String varName, float frame, SceneMaxScope scope) {
        SpriteEmitter sprite = sprites.get(varName);

        if(sprite!=null) {
            sprite.setFrame(frame);
        }
        // else throw run time error
    }

    @Override
    public void onEndCode() {
        clearScene();
        if (_appObserver != null) _appObserver.onEndCode(null);
    }

    @Override
    public void onEndCode(List<String> errors) {
        clearScene();
        if (_appObserver != null) _appObserver.onEndCode(errors);

    }

    @Override
    public void onStartCode() {
        if (_appObserver != null) _appObserver.onStartCode();
    }

    @Override
    public void destroy(){
        this.pluginsCommunicationChannel.stop(); // call all subscribed clients to stop
        if(hostAppType==SceneMaxApp.HOST_APP_WINDOWS_ALLOW_CODE_CHANGE_BUTTON) {
            System.exit(0);
        } else {
            clearScene();
            if (_appObserver != null) {
                _appObserver.message(EVENT_DESTROY);
            }
        }
        super.destroy();
    }

    private void stopTrackedAudioNodes() {
        for (AudioNode node : _audioNodes.values()) {
            if (node == null) {
                continue;
            }
            try {
                node.stop();
            } catch (NullPointerException ex) {
                logger.log(Level.FINE, "Ignoring audio shutdown race while stopping audio node", ex);
            }
        }
        _audioNodes.clear();
    }

    @Override
    public void simpleUpdate(float tpf) {
        effekseerFrameTpf = tpf;
        runtimeShaderElapsedTime += tpf;
        updateRuntimeShaderMaterials();
        updateEnvironmentShaderOverlaySize();

        if(hasRunTimeError) {
            hasRunTimeError=false;
            _controllers.clear();
            ArrayList<String> errs = new ArrayList<String>();
            errs.add(this.runTimeError);
            showFloatingMessage(errs,"Close Application",0);
            return;
        }

        if(_controllers.size()==this.eventHandlersCount) {
            return;
        }

        for(int i=_controllers.size()-1;i>=0;--i) {

            SceneMaxBaseController ctl =  _controllers.get(i);

            // check whether this controller should be paused
            if(this.scenePaused) {
                if(ctl.adhereToPauseStatus) {
                    continue;
                }
            }

            boolean finished = ctl.run(tpf);
            if(finished) {
                if (this.switchStateCode != null) {
                    break; // step out from controllers execution. they are about to be switched
                }

                ctl.isRunning=false;
                ctl.dispose();
                _controllers.remove(i);
                if(ctl.isEventHandler) {
                    this.eventHandlersCount--;
                }
            }

              if(_controllers.size()==0) {
                  onEndCode();
              }

          }

          if(followCameraState!=null) {
              followCameraState.update(tpf);
          }

        if (this.switchStateCode != null) {
            this.switchState();
        }

      }

    private void updateRuntimeShaderMaterials() {
        for (Material material : runtimeShaderMaterials) {
            if (material == null) {
                continue;
            }

            try {
                if (material.getMaterialDef() != null && material.getMaterialDef().getMaterialParam("Time") != null) {
                    material.setFloat("Time", runtimeShaderElapsedTime);
                }
            } catch (Exception ignored) {
            }
        }
    }


    @Override
    public java.lang.Object getUserDataFieldValue(String varName, String fieldName) {

        fieldName=fieldName.toLowerCase();

        AppModel am = models.get(varName);
        if(am != null) {
            Spatial m = am.model;
            if(m!=null) {
                return m.getUserData(fieldName);
            }
        }

        Node sphere = spheres.get(varName);
        if(sphere != null) {
            return sphere.getUserData(fieldName);
        }

        Node box = boxes.get(varName);
        if(box != null) {
            return box.getUserData(fieldName);
        }

        SpriteEmitter se = sprites.get(varName);
        if(se != null) {
            Spatial g = se.getSpatial();
            if(g!=null) {
                return g.getUserData(fieldName);
            }
        }

        EffekseerInst effect = effekseerEffects.get(varName);
        if (effect != null) {
            return effect.node.getUserData(fieldName);
        }

        return 0;

    }

    @Override
    public void simpleRender(RenderManager rm) {
        super.simpleRender(rm);
    }

    private void ensureEffekseerRenderProcessor() {
        if (effekseerRenderProcessor == null) {
            effekseerRenderProcessor = new EffekseerRenderProcessor(this);
            viewPort.addProcessor(effekseerRenderProcessor);
        }
    }

    public String getSpatialTransitionRecord(Spatial sp) {

        Vector3f trans = sp.getWorldTranslation();

        sp.getWorldRotation().toAngles(_tmpAngles);
        float[] angles = _tmpAngles;
        float degX = angles[0]*FastMath.RAD_TO_DEG;
        if(degX<0) {
            degX = 360+degX;
        }

        float degY = angles[1]*FastMath.RAD_TO_DEG;
        if(degY<0) {
            degY = 360+degY;
        }

        float degZ = angles[2]*FastMath.RAD_TO_DEG;
        if(degZ<0) {
            degZ = 360+degZ;
        }

        String rec=","+trans.x+","+trans.y+","+trans.z+","+degX+","+degY+","+degZ;

        return rec;
    }

    @Override
    public java.lang.Object getFieldValue(String varName, String fieldName) {

        fieldName=fieldName.toLowerCase();

        AppModel am = models.get(varName);
        if(am != null) {
            Spatial m = am.model;
            if(fieldName.equals("x")) {
                return m.getWorldTranslation().x;
            }

            if(fieldName.equals("y")) {
                return m.getWorldTranslation().y;
            }

            if(fieldName.equals("z")) {
                return m.getWorldTranslation().z;
            }

            if(fieldName.equals("rx")) {
                m.getLocalRotation().toAngles(_tmpAngles);
                float deg = _tmpAngles[0]*FastMath.RAD_TO_DEG;
                if(deg<0) {
                    deg = 360+deg;
                }
                return deg;
            }

            if(fieldName.equals("ry")) {
                m.getLocalRotation().toAngles(_tmpAngles);
                float deg = _tmpAngles[1]*FastMath.RAD_TO_DEG;
                if(deg<0) {
                    deg = 360+deg;
                }
                return deg;
            }

            if(fieldName.equals("rz")) {
                m.getLocalRotation().toAngles(_tmpAngles);
                float deg = _tmpAngles[2]*FastMath.RAD_TO_DEG;
                if(deg<0) {
                    deg = 360+deg;
                }
                return deg;
            }

            if(fieldName.equals("anim_percent")) {
                AnimComposer ac = am.getAnimComposer();
                if(ac!=null && am.currentAction!=null) {
                    return ac.getTime("Default")/am.currentAction.getLength()*100;
                } else {
                    return 0;
                }
            }

            if(fieldName.equals("replay_index")) {
                Object ri = m.getUserData("replay_index");
                if(ri!=null) {
                    return ri;
                }

                return 0;
            }

        }

        Node sphere = spheres.get(varName);
        if(sphere != null) {
            return getGeometryNodeFieldValue(sphere,fieldName);
        }

        Node box = boxes.get(varName);
        if(box != null) {
            return getGeometryNodeFieldValue(box,fieldName);
        }

        SpriteEmitter se = sprites.get(varName);
        if(se != null) {
            return getGeometryFieldValue(se.getSpatial(),fieldName);
        }

        EffekseerInst effect = effekseerEffects.get(varName);
        if (effect != null) {
            return getGeometryNodeFieldValue(effect.node, fieldName);
        }

        if(fieldName.equalsIgnoreCase("hit")) {
            GroupInst g = groups.get(varName);
            if(g != null) {
                return g.lastClosestRayCheck;
            }
        }

        return 0;
    }


    private java.lang.Object getGeometryNodeFieldValue(Node g, String fieldName) {
        if(fieldName.equals("x")) {
            return g.getWorldTranslation().x;
        }

        if(fieldName.equals("y")) {
            return g.getWorldTranslation().y;
        }

        if(fieldName.equals("z")) {
            return g.getWorldTranslation().z;
        }

        if(fieldName.equals("rx")) {
            g.getLocalRotation().toAngles(_tmpAngles);
            float deg = _tmpAngles[0]*FastMath.RAD_TO_DEG;
            if(deg<0) {
                deg = 360+deg;
            }
            return deg;
        }

        if(fieldName.equals("ry")) {
            g.getLocalRotation().toAngles(_tmpAngles);
            float deg = _tmpAngles[1]*FastMath.RAD_TO_DEG;
            if(deg<0) {
                deg = 360+deg;
            }
            return deg;
        }

        if(fieldName.equals("rz")) {
            g.getLocalRotation().toAngles(_tmpAngles);
            float deg = _tmpAngles[2]*FastMath.RAD_TO_DEG;
            if(deg<0) {
                deg = 360+deg;
            }
            return deg;
        }

        return 0f;
    }

    private java.lang.Object getGeometryFieldValue(Spatial g, String fieldName) {
        if(fieldName.equals("x")) {
            return g.getWorldTranslation().x;
        }

        if(fieldName.equals("y")) {
            return g.getWorldTranslation().y;
        }

        if(fieldName.equals("z")) {
            return g.getWorldTranslation().z;
        }

        return 0;
    }

    @Override
    public void handleRuntimeError(String err) {
        this.hasRunTimeError=true;
        this.runTimeError=err;
    }


    public void showSkyBox(String skyboxMaterial) {

        final SkyBoxResource sb = assetsMapping.getSkyboxesIndex().get(skyboxMaterial.toLowerCase());
        if (sb == null) {
            this.handleRuntimeError("Error: SkyBox '" + skyboxMaterial + "' doesn't exist");
            return;
        }

        if(sb.up!=null) {
            skybox = SkyFactory.createSky(getAssetManager(),
                    assetManager.loadTexture(sb.left),
                    assetManager.loadTexture(sb.right),
                    assetManager.loadTexture(sb.front),
                    assetManager.loadTexture(sb.back),
                    assetManager.loadTexture(sb.up),
                    assetManager.loadTexture(sb.down));

            getRootNode().attachChild(skybox);
        }

    }

    public boolean checkCollision(EntityInstBase obj1, EntityInstBase obj2) {
        String var1 = obj1.getVarRunTimeName();
        String var2 = obj2.getVarRunTimeName();

        Spatial s1 = getEntitySpatial(var1, obj1.varDef.varType);
        Spatial s2 = getEntitySpatial(var2, obj2.varDef.varType);
        if (s1 == null || s2 == null) {
            return false; // todo: throw exception
        }
        _tmpCollisionResults.clear();
        s1.collideWith(s2.getWorldBound(), _tmpCollisionResults);

        return _tmpCollisionResults.size() > 0;
    }

    public boolean checkCollision(String var1, String var2, String jointNameA, String jointNameB) {

        String name1 = geoName2ModelName.get(var1);
        if(name1==null) {
            return false;
        }
        String name2 = geoName2ModelName.get(var2);
        if(name2==null) {
            return false;
        }

        if(name1.equals(name2)) {
            return false;
        }

        setModelUserData(name1,"hit_joint",jointNameA);
        setModelUserData(name2,"hit_joint",jointNameB);

        String key1 = name1.concat(name2);
        CollisionHandler c = collisionHandlers.get(key1);
        if(c!=null && !c.doBlock.isRunning && c.checkSpritesCollision() && c.checkGoExpr()) {

            boolean runHandler = false;

            if(c.joint1==null && c.joint2==null) {
                runHandler=true;
            } else if(c.joint1!=null && c.joint1.equals(jointNameA)) {
                runHandler=true;
            } else if(c.joint2!=null && c.joint2.equals(jointNameB)) {
                runHandler=true;
            }

            if(runHandler==true) {
                registerController(c.doBlock);// run on its own thread
                c.doBlock.isRunning = true;
                return true;
            }

        }

//        String key2 = var2.concat(var1);
//        c = collisionHandlers.get(key2);
//        if(c!=null && !c.doBlock.isRunning && c.checkSpritesCollision()) {
//            registerController(c.doBlock);// run on its own thread
//            c.doBlock.isRunning=true;
//            return true;
//        }


        return false;

    }

    private final AnalogListener actionListenerAnalog = new AnalogListener() {

        @Override
        public void onAnalog(String name, float val, float tpf) {
            UserInputDoBlockController c = inputHandlers.get(name);
            if (c != null && !c.isRunning && c.checkTargetEntityClicked() && c.checkGoExpr()) {
                registerController(c);// run on its own thread
                c.isRunning = true;

            }
        }
    };

    private final ActionListener actionListener = new ActionListener() {
        @Override
        public void onAction(String name, boolean keyPressed, float tpf) {

            UserInputDoBlockController c = null;
            if(keyPressed) {
                c = inputHandlers.get(name);
            } else {
                c = releaseInputHandlers.get(name);
            }

            if (c != null && !c.isRunning && c.checkTargetEntityClicked() && c.checkGoExpr()) {
                registerController(c);// run on its own thread
                c.isRunning = true;
            }

        }
    };

    public void addInputHandler(InputStatementCommand cmd, UserInputDoBlockController c) {

        String key = cmd.inputKey;
        if(c.targetVar!=null) {
            key+="_"+c.targetVar;
        }

        if (!inputManager.hasMapping(key)) {
            if(cmd.inputType.equals("mouse")) {
                inputManager.addMapping(key, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
                if(cmd.targetVar!=null) { // draw channel
                    PictureExt pic = _drawChannels.get(cmd.targetVar);
                    if(pic!=null) {
                        c.targetPicture = pic;
                    }
                }
            } else {
                inputManager.addMapping(key, new KeyTrigger(keyMapping.get(cmd.inputKey)));
            }

        }

        if(cmd.once) {
            inputManager.addListener(actionListener, key);
        } else {
            inputManager.addListener(actionListenerAnalog, key);
        }

        if (cmd.released) {
            releaseInputHandlers.put(key, c);
        } else {
            inputHandlers.put(key, c);
        }

    }

    public void addCollisionHandler(String var1, String var2,
                                    DoBlockController c, EntityInstBase inst1, EntityInstBase inst2,
                                    String joint1, String joint2, SceneMaxParser.Logical_expressionContext goCondition) {

        String key = var1.concat(var2);
        if(!collisionHandlers.containsKey(key)) {

            CollisionHandler ch = new CollisionHandler();
            ch.goExpr = goCondition;
            ch.joint1=joint1;
            ch.joint2=joint2;

            if(inst1!=null && inst2!=null) {
                if(inst1 instanceof SpriteInst && inst2 instanceof SpriteInst) {
                    SpriteEmitter sp1 = sprites.get(var1);
                    SpriteEmitter sp2 = sprites.get(var2);
                    ch.sprite1=sp1;
                    ch.sprite2=sp2;
                }
            }

            ch.doBlock=c;
            collisionHandlers.put(key, ch);
            collisionHandlers.put(var2.concat(var1), ch);
        }
    }

    private void showHideGeoNode(Node g,String targetVar,boolean show) {

        if(g==null) {
            return;
        }

        if(!show) {
            removeOutlineFilter(g);
            g.removeFromParent();
            removeCollisionControlsFromPhysics(targetVar);
        }else {
            rootNode.attachChild(g);
            addCollisionControlsToPhysics(targetVar);
        }

    }

    private void showHideGeo(Geometry g,String targetVar,boolean show) {

        if(g==null) {
            return;
        }

        if(!show) {
            removeOutlineFilter(g);
            g.removeFromParent();
            removeCollisionControlsFromPhysics(targetVar);
        }else {
            rootNode.attachChild(g);
            addCollisionControlsToPhysics(targetVar);
        }

    }

    public void showHideBox(String targetVar, ActionCommandShowHide show) {

        Node g = boxes.get(targetVar);

        if(show.axisX || show.axisY || show.axisZ) {
            attachCoordinateAxes(g.getWorldTranslation(),show,rootNode);
            return;
        }

        if(show.wireframe) {
            setGeometryWireframeMode((Geometry)g.getChild(0),show.show);
            return;
        }

        if(show.outline) {
            showHideOutline(g,show.show);
            return;
        }

        showHideGeoNode(g,targetVar,show.show);
    }

    public void showHideSphere(String targetVar, ActionCommandShowHide show) {

        Node g = spheres.get(targetVar);

        if(show.axisX || show.axisY || show.axisZ) {
            attachCoordinateAxes(g.getWorldTranslation(),show,rootNode);
            return;
        }

        if(show.wireframe) {
            setGeometryWireframeMode((Geometry)g.getChild(0),show.show);
            return;
        }

        if(show.outline) {
            showHideOutline(g,show.show);
            return;
        }

        showHideGeoNode(g,targetVar,show.show);
    }


    public void posModel(String targetVar, Double valX, Double valY, Double valZ, RunTimeVarDef varForPos, Vector3f calculatedPosition) {

        AppModel am = models.get(targetVar);
        if(am==null) {
            return;
        }

        Spatial m = am.model;
        if(m==null) {
            return;
        }

        if(calculatedPosition!=null) {
            m.setLocalTranslation(calculatedPosition);
        } else if(varForPos!=null) {
            Spatial sp = getEntitySpatial(varForPos.varName,varForPos.varDef.varType);
            if(am.physicalControl instanceof SceneMax3DGenericVehicle) {
                SceneMax3DGenericVehicle v = (SceneMax3DGenericVehicle) am.physicalControl;
                v.getVehicleControl().setPhysicsLocation(sp.getLocalTranslation());

            } else {
                m.setLocalTranslation(sp.getLocalTranslation());
            }
        } else {
            if(am.physicalControl instanceof SceneMax3DGenericVehicle) {
                SceneMax3DGenericVehicle v = (SceneMax3DGenericVehicle)am.physicalControl;
                v.getVehicleControl().setPhysicsLocation(new Vector3f(valX.floatValue(), valY.floatValue(), valZ.floatValue()));
               // v.setLocation(new Vector3f(valX.floatValue(), valY.floatValue(), valZ.floatValue()));
            } else {
                m.setLocalTranslation(valX.floatValue(), valY.floatValue(), valZ.floatValue());
            }
        }

    }

    public void posSprite(String targetVar, Double valX, Double valY, Double valZ, RunTimeVarDef varForPos, Vector3f calculatedPosition) {

        SpriteEmitter se = sprites.get(targetVar);
        if (se==null) {
            this.showFloatingMessage("[posSprite] Run-time error: Sprite '"+targetVar+"' not found");
            return;
        }
        Spatial m = se.getSpatial();
        if(m==null) {
            return;
        }

        if(calculatedPosition!=null) {
            m.setLocalTranslation(calculatedPosition);
        } else if(varForPos!=null) {
            Spatial sp = getEntitySpatial(varForPos.varName,varForPos.varDef.varType);
            m.setLocalTranslation(sp.getLocalTranslation());
        } else {
            m.setLocalTranslation(valX.floatValue(), valY.floatValue(), valZ.floatValue());
        }

    }

    public void posCamera(Double valX, Double valY, Double valZ, RunTimeVarDef varForPos, Vector3f calculatedPosition) {

        if(calculatedPosition!=null) {
            cam.setLocation(calculatedPosition);
        } else if(varForPos!=null) {
            Spatial sp = getEntitySpatial(varForPos.varName,varForPos.varDef.varType);
            cam.setLocation(sp.getLocalTranslation());
        } else {
            cam.setLocation(new Vector3f(valX.floatValue(), valY.floatValue(), valZ.floatValue()));
        }
    }

    public void posBox(String targetVar, Double valX, Double valY, Double valZ, RunTimeVarDef varForPos, Vector3f calculatedPosition) {
        Node g = boxes.get(targetVar);
        if(g==null) {
            return;
        }

        if(calculatedPosition!=null) {
            g.setLocalTranslation(calculatedPosition);
        } else if(varForPos!=null) {
            Spatial sp = getEntitySpatial(varForPos.varName,varForPos.varDef.varType);
            g.setLocalTranslation(sp.getLocalTranslation());
        } else {
            g.setLocalTranslation(valX.floatValue(), valY.floatValue(), valZ.floatValue());
        }

    }

    public void posSphere(String targetVar, Double valX, Double valY, Double valZ, RunTimeVarDef varForPos, Vector3f calculatedPosition) {
        Node g = spheres.get(targetVar);
        if(g==null) {
            return;
        }

        if(calculatedPosition!=null) {
            g.setLocalTranslation(calculatedPosition);
        } else if(varForPos!=null) {
            Spatial sp = getEntitySpatial(varForPos.varName,varForPos.varDef.varType);
            g.setLocalTranslation(sp.getLocalTranslation());
        } else {
            g.setLocalTranslation(valX.floatValue(), valY.floatValue(), valZ.floatValue());
        }

    }

    public void stopSound(String sound) {
        AudioNode node = _audioNodes.get(sound);
        if(node!=null) {
            node.stop();
        }
    }

    public void playSound(String sound, PlayStopSoundCommand cmd) {
        AudioNode node = _audioNodes.get(sound);
        if(node==null) {
            PlayStopSoundCommand cmd2 = new PlayStopSoundCommand();
            cmd2.sound = sound;
            cmd2.volumeExpr=cmd.volumeExpr;
            loadAudioResource(cmd2);
            node = _audioNodes.get(sound);
        }

        if(node!=null) {
            if(cmd.loop) {
                node.setLooping(cmd.loop);
                node.play();
            } else {
                node.playInstance();
            }
        }
    }


    public void showWater(WaterShowCommand cmd) {
//
        float initialWaterHeight = 10f;//0.8f;
        Vector3f lightDir = new Vector3f(-4.9236743f, -1.27054665f, 5.896916f);
//
//
////        DirectionalLight sun = new DirectionalLight();
////        sun.setDirection(lightDir);
////        sun.setColor(ColorRGBA.White.clone().multLocal(1f));
////        rootNode.addLight(sun);
//
////        AmbientLight al = new AmbientLight();
////        al.setColor(new ColorRGBA(0.1f, 0.1f, 0.1f, 1.0f));
////        rootNode.addLight(al);
//
//

        //Water Filter
        water = new WaterFilter(rootNode, lightDir);
        water.setCenter(new Vector3f(0, -20, 0));
        water.setRadius(260);

        water.setWaterColor(new ColorRGBA().setAsSrgb(0.0078f, 0.3176f, 0.5f, 1.0f));
        water.setDeepWaterColor(new ColorRGBA().setAsSrgb(0.0039f, 0.00196f, 0.145f, 1.0f));
        water.setUnderWaterFogDistance(80);
        water.setWaterTransparency(0.12f);
        water.setFoamIntensity(0.4f);
        water.setFoamHardness(0.3f);
        water.setFoamExistence(new Vector3f(0.8f, 8f, 1f));
        water.setReflectionDisplace(50);
        water.setRefractionConstant(0.25f);
        water.setColorExtinction(new Vector3f(30, 50, 70));
        water.setCausticsIntensity(0.4f);
        water.setWaveScale(0.003f);
        water.setMaxAmplitude(2f);
        water.setFoamTexture((Texture2D) assetManager.loadTexture("Common/MatDefs/Water/Textures/foam2.jpg"));
        water.setRefractionStrength(0.2f);
        water.setWaterHeight(initialWaterHeight);


        //Bloom Filter
//        BloomFilter bloom = new BloomFilter();
//        bloom.setExposurePower(55);
//        bloom.setBloomIntensity(1.0f);

        //Light Scattering Filter
//        LightScatteringFilter lsf = new LightScatteringFilter(lightDir.mult(-300));
//        lsf.setLightDensity(0.5f);

        //Depth of field Filter
        DepthOfFieldFilter dof = new DepthOfFieldFilter();
        dof.setFocusDistance(0);
        dof.setFocusRange(100);


        FilterPostProcessor fpp = new FilterPostProcessor(assetManager);
        fpp.addFilter(water);
//        fpp.addFilter(bloom);
        fpp.addFilter(dof);
//        fpp.addFilter(lsf);
        fpp.addFilter(new FXAAFilter());

        //fpp.setNumSamples(0);// test other than 0
        viewPort.addProcessor(fpp);



        // we create a water processor
//        SimpleWaterProcessor waterProcessor = new SimpleWaterProcessor(assetManager);
//        waterProcessor.setReflectionScene(rootNode);
//
//        Vector3f waterLocation=new Vector3f(cmd.posX,cmd.posY,cmd.posZ);
//        waterProcessor.setPlane(new Plane(Vector3f.UNIT_Y, waterLocation.dot(Vector3f.UNIT_Y)));
//        viewPort.addProcessor(waterProcessor);
//
//        waterProcessor.setWaterDepth(cmd.depthVal);         //40 transparency of water
//        waterProcessor.setDistortionScale(cmd.strengthVal); //0.05f strength of waves
//        waterProcessor.setWaveSpeed(cmd.speedVal);       // speed of waves
//
//
//        Quad quad = new Quad(cmd.sizeWidth,cmd.sizeHeight);//400,400
//        quad.scaleTextureCoordinates(new Vector2f(6f,6f));
//
//
//        Geometry water=new Geometry("water", quad);
//        water.setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_X));
//        water.setLocalTranslation(-200, -6, 250);
//        water.setShadowMode(RenderQueue.ShadowMode.Receive);
//        water.setMaterial(waterProcessor.getMaterial());
//        rootNode.attachChild(water);

    }

    public ParticleEmitter createRoundSpark(ParticleSystemCommand cmd){
        ParticleEmitter roundspark = new ParticleEmitter("RoundSpark", ParticleMesh.Type.Triangle, 20 );
        roundspark.setStartColor(new ColorRGBA(1f, 0.29f, 0.34f, (float) (1.0 )));
        roundspark.setEndColor(new ColorRGBA(0, 0, 0, (0.5f )));
        roundspark.setStartSize(0.2f);//1.2f
        roundspark.setEndSize(0.8f);//1.8f
        roundspark.setShape(new EmitterSphereShape(Vector3f.ZERO, 2f));//2f
        //roundspark.setParticlesPerSec(0);
        roundspark.setGravity(0, -.5f, 0);
        roundspark.setLowLife(0.5f);//1.8f
        roundspark.setHighLife(2f);//2f
        roundspark.getParticleInfluencer()
                .setInitialVelocity(new Vector3f(0, 3, 0));
        roundspark.getParticleInfluencer().setVelocityVariation(.5f);
        roundspark.setImagesX(1);
        roundspark.setImagesY(1);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Particle.j3md");
        mat.setTexture("Texture", assetManager.loadTexture("Effects/Explosion/roundspark.png"));
        //mat.setBoolean("PointSprite", POINT_SPRITE);
        roundspark.setMaterial(mat);
        rootNode.attachChild(roundspark);
        roundspark.setLocalTranslation(cmd.posX,cmd.posY,cmd.posZ);

        return roundspark;
    }

    public ParticleEmitter createSpark(ParticleSystemCommand cmd){
        ParticleEmitter spark = new ParticleEmitter("Spark", ParticleMesh.Type.Triangle, 30 );
        spark.setStartColor(new ColorRGBA(1f, 0.8f, 0.36f, (float) (1.0f )));
        spark.setEndColor(new ColorRGBA(1f, 0.8f, 0.36f, 0f));
        spark.setStartSize(.5f);
        spark.setEndSize(.5f);
        spark.setFacingVelocity(true);
        //spark.setParticlesPerSec(0);
        spark.setGravity(0, 5, 0);
        spark.setLowLife(1.1f);
        spark.setHighLife(1.5f);
        spark.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 20, 0));
        spark.getParticleInfluencer().setVelocityVariation(1);
        spark.setImagesX(1);
        spark.setImagesY(1);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Particle.j3md");
        mat.setTexture("Texture", assetManager.loadTexture("Effects/Explosion/spark.png"));
        spark.setMaterial(mat);
        //rootNode.attachChild(spark);
        attachParticleEmitterToTargetNode(spark,cmd);
        spark.setLocalTranslation(cmd.posX,cmd.posY,cmd.posZ);
        return spark;
    }

    public ParticleEmitter createSmokeTrail(ParticleSystemCommand cmd){
        ParticleEmitter smoketrail = new ParticleEmitter("SmokeTrail", ParticleMesh.Type.Triangle, 22 );
        smoketrail.setStartColor(new ColorRGBA(1f, 0.8f, 0.36f, (float) (1.0f )));
        smoketrail.setEndColor(new ColorRGBA(1f, 0.8f, 0.36f, 0f));
        smoketrail.setStartSize(.2f);
        smoketrail.setEndSize(1f);

//        smoketrail.setShape(new EmitterSphereShape(Vector3f.ZERO, 1f));
        smoketrail.setFacingVelocity(true);
        //smoketrail.setParticlesPerSec(0);
        smoketrail.setGravity(0, 1, 0);
        smoketrail.setLowLife(.4f);
        smoketrail.setHighLife(.5f);
        smoketrail.getParticleInfluencer()
                .setInitialVelocity(new Vector3f(0, 12, 0));
        smoketrail.getParticleInfluencer().setVelocityVariation(1);
        smoketrail.setImagesX(1);
        smoketrail.setImagesY(3);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Particle.j3md");
        mat.setTexture("Texture", assetManager.loadTexture("Effects/Explosion/smoketrail.png"));
        smoketrail.setMaterial(mat);
        //rootNode.attachChild(smoketrail);
        attachParticleEmitterToTargetNode(smoketrail,cmd);

        smoketrail.setLocalTranslation(cmd.posX,cmd.posY,cmd.posZ);
        return smoketrail;
    }

    protected Material getPartMat(String path) {
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Particle.j3md");
        mat.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
        Texture tex = assetManager.loadTexture(path);
        mat.setTexture("Texture", tex);
        return mat;
    }


    public void createParticleDestination(ParticleSystemCommand cmd) {

        Emitter emitter = new Emitter("test", getPartMat("Effects/Particles/part_light.png"), 100,
                new SizeInfluencer(),
                new PreferredDestinationInfluencer()
        );

        emitter.getInfluencer(PreferredDestinationInfluencer.class)
                .setPreferredDestination(new VectorValueType(new Vector3f(5, 0, 0)));

        emitter.getInfluencer(SizeInfluencer.class).setSizeOverTime(new ValueType(new Curve()
                .addControlPoint(null, new Vector2f(0.0f, 0.0f), new Vector2f(0.3f, 0.1f))
                .addControlPoint(new Vector2f(0.3f, 1.0f), new Vector2f(0.5f, 1.0f), new Vector2f(0.7f, 1.0f))
                .addControlPoint(new Vector2f(0.7f, 1.0f), new Vector2f(1.0f, 1.0f), null)
        ));

        emitter.setStartColor(new ColorValueType());
        emitter.setStartSize(new ValueType(0.7f));
        emitter.setStartSpeed(new ValueType(6.5f));
        emitter.setLifeMinMax(new ValueType(4.0f), new ValueType(4.0f));
        emitter.setEmissionsPerSecond(20);
        emitter.setParticlesPerEmission(5);
        emitter.setShape(new EmitterCone());

        emitter.setLocalTranslation(-2.5f, 0.5f, 0);

        rootNode.attachChild(emitter);
    }

    public Node createParticleTimeORB(ParticleSystemCommand cmd) {

        Node test = new Node("TimeOrb");
        rootNode.attachChild(test);
        attachParticleEmitterToTargetNode(test,cmd);

        Emitter emitter = new Emitter("orb", getPartMat("Effects/Particles/part_circle_glow.png"), 3,
                new ColorInfluencer(),
                new SizeInfluencer()
        );
        emitter.setStartSpeed(new ValueType(0.0f));

        float startSize = cmd.startSizeVal>0?cmd.startSizeVal:2.0f;
        float endSize = cmd.endSizeVal>0?cmd.endSizeVal:2.4f;

        ParticleHelper.minMaxSize(emitter, startSize, endSize);
        emitter.getInfluencer(ColorInfluencer.class).setColorOverTime(new ColorValueType(new Gradient()
                .addGradPoint(new ColorRGBA(0.6f, 0.1f, 0.95f, 0.0f), 1.0f)
                .addGradPoint(new ColorRGBA(0.6f, 0.1f, 0.95f, 0.8f), 0.8f)
                .addGradPoint(new ColorRGBA(0.6f, 0.1f, 0.95f, 0.6f), 0.5f)
                .addGradPoint(new ColorRGBA(0.6f, 0.1f, 0.95f, 0.0f), 0.0f)
        ));

        emitter.setLifeMinMax(new ValueType(1.0f), new ValueType(1.0f));
        emitter.setEmissionsPerSecond(2);
        emitter.setParticlesPerEmission(1);
        emitter.setEnabled(true);

        EmitterSphere sphere = new EmitterSphere();
        sphere.setRadius(0.001f);
        emitter.setShape(sphere);
        test.attachChild(emitter);
        test.setLocalTranslation(cmd.posX, cmd.posY, cmd.posZ);


        emitter = new Emitter("orbClouds",
                getPartMat("Effects/Particles/part_light.png"),
                50,
                new ColorInfluencer(),
                new SizeInfluencer(),
                new RotationLifetimeInfluencer()
        );
        emitter.setStartSpeed(new ValueType(0.0f));
        ParticleHelper.minMaxSize(emitter, 1.8f, 0.3f);
        emitter.getInfluencer(ColorInfluencer.class).setColorOverTime(new ColorValueType(new Gradient()
                .addGradPoint(new ColorRGBA(0.6f, 0.4f, 0.95f, 0.0f), 1.0f)
                .addGradPoint(new ColorRGBA(0.6f, 0.4f, 0.95f, 0.3f), 0.8f)
                .addGradPoint(new ColorRGBA(0.6f, 0.4f, 0.95f, 0.3f), 0.5f)
                .addGradPoint(new ColorRGBA(0.6f, 0.4f, 0.95f, 0.0f), 0.0f)
        ));
        emitter.getInfluencer(RotationLifetimeInfluencer.class).setSpeedOverLifetime(new VectorValueType(new Vector3f(0, 0, 1)));

        emitter.setLifeMinMax(new ValueType(1.0f), new ValueType(1.0f));

        Float emissionsPerSec = cmd.emissionsPerSecondVal>0?cmd.emissionsPerSecondVal:30;
        emitter.setEmissionsPerSecond(emissionsPerSec.intValue());
        Float particlesPerEmission = cmd.particlesPerEmissionVal>0?cmd.particlesPerEmissionVal:1;
        emitter.setParticlesPerEmission(particlesPerEmission.intValue());

        //emitter.setEmissionsPerSecond(30);
        //emitter.setParticlesPerEmission(1);

        emitter.setEnabled(true);
        emitter.getMaterial().getAdditionalRenderState().setBlendMode(RenderState.BlendMode.AlphaAdditive);
        //Mesh box = new Box(0.01f, 0.01f, 0.01f);

        float radius = cmd.radiusVal>0?cmd.radiusVal:2.2f;
        Mesh box = new Sphere(12, 12, radius);
        emitter.setShape(new EmitterMesh(box));
        test.attachChild(emitter);

        return test;

    }

    public void createParticleGradient(ParticleSystemCommand cmd) {
        Emitter emitter = new Emitter("test", getPartMat("Effects/Particles/part_light.png"), 100,
                new ColorInfluencer());

        emitter.getInfluencer(ColorInfluencer.class).setColorOverTime(new ColorValueType(
                new Gradient().addGradPoint(new ColorRGBA(1.0f, 0.0f, 0.0f, 1.0f), 0.0f)
                        .addGradPoint(new ColorRGBA(0.0f, 1.0f, 0.0f, 1.0f), 0.5f)
                        .addGradPoint(new ColorRGBA(0.0f, 0.0f, 1.0f, 1.0f), 1.0f)
        ));

        emitter.setStartSpeed(new ValueType(4.5f));
        emitter.setLifeMinMax(new ValueType(0.9f), new ValueType(0.9f));
        emitter.setEmissionsPerSecond(5);
        emitter.setParticlesPerEmission(11);
        emitter.setShape(new EmitterCone());

        emitter.setLocalTranslation(0, 0.5f, 0);
        rootNode.attachChild(emitter);
    }

    public void createParticleOrbital(ParticleSystemCommand cmd) {

        Emitter emitter = new Emitter("test", getPartMat("Effects/Particles/part_light.png"), 100,
                new ColorInfluencer(),
                new SizeInfluencer(),
                new VelocityInfluencer()
        );

        Curve y = new Curve()
                .addControlPoint(null, new Vector2f(0.0f, 3.0f), new Vector2f(0.2f, 3.0f))
                .addControlPoint(new Vector2f(0.8f, 3.0f), new Vector2f(1.0f, 3.0f), null);

        Curve xz = new Curve()
                .addControlPoint(null, new Vector2f(0.0f, 1.0f), new Vector2f(0.2f, 1.0f))
                .addControlPoint(new Vector2f(0.8f, 1.0f), new Vector2f(1.0f, 1.0f), null);

        VectorValueType valueType = new VectorValueType();
        valueType.setCurve(xz.clone(), y, xz);
        emitter.getInfluencer(VelocityInfluencer.class).setOrbital(valueType);
        emitter.getInfluencer(VelocityInfluencer.class).setOrbitalRotations(new VectorValueType(new Vector3f(8, 8, 8)));

        emitter.getInfluencer(SizeInfluencer.class).setSizeOverTime(new ValueType(new Curve()
                .addControlPoint(null, new Vector2f(0.0f, 0.0f), new Vector2f(0.3f, 0.0f))
                .addControlPoint(new Vector2f(0.3f, 1.0f), new Vector2f(0.5f, 1.0f), new Vector2f(0.7f, 1.0f))
                .addControlPoint(new Vector2f(0.7f, 0.0f), new Vector2f(1.0f, 0.0f), null)
        ));

        emitter.getInfluencer(ColorInfluencer.class).setColorOverTime(new ColorValueType(
                new Gradient().addGradPoint(new ColorRGBA(0.8f, 0.6f, 0.0f, 0.6f), 0.0f)
                        .addGradPoint(new ColorRGBA(0.8f, 0.0f, 0.0f, 0.5f), 0.8f)
                        .addGradPoint(new ColorRGBA(0.2f, 0.2f, 0.2f, 0.0f), 1.0f)
        ));

        emitter.setStartSize(new ValueType(0.7f));
        emitter.setStartSpeed(new ValueType(3.5f));
        emitter.setLifeMinMax(new ValueType(3.0f), new ValueType(3.0f));
        emitter.setEmissionsPerSecond(20);
        emitter.setParticlesPerEmission(5);
        emitter.setShape(new EmitterCone());

        emitter.setLocalTranslation(-0.5f, 0.5f, -0.5f);

        attachParticleEmitterToTargetNode(emitter,cmd);
        //rootNode.attachChild(emitter);
    }


    public Node createParticleFlame(ParticleSystemCommand cmd) {

        Emitter emitter = new Emitter("test", getPartMat("Effects/Particles/part_flame.png"), 200,
                new ColorInfluencer(),
                new SpriteInfluencer(),
                new SizeInfluencer(),
                new RandomInfluencer(),
                new RotationLifetimeInfluencer(),
                new VelocityInfluencer()
        );

        Curve x = new Curve()
                .addControlPoint(null, new Vector2f(0.0f, 1.0f), new Vector2f(0.2f, 1.0f))
                .addControlPoint(new Vector2f(0.8f, 0.0f), new Vector2f(1.0f, 0.0f), null);

        VectorValueType valueType = new VectorValueType();
        valueType.setCurve(x, x.clone(), x.clone());
        emitter.getInfluencer(VelocityInfluencer.class).setLinear(valueType);
        emitter.getInfluencer(SpriteInfluencer.class).setSpriteCols(2);
        emitter.getInfluencer(SpriteInfluencer.class).setSpriteRows(2);
        emitter.getInfluencer(RotationLifetimeInfluencer.class).setSpeedOverLifetime(new VectorValueType(new Vector3f(0, 0, 3.0f)));

        emitter.getInfluencer(ColorInfluencer.class).setColorOverTime(new ColorValueType(
                new Gradient().addGradPoint(new ColorRGBA(0.8f, 0.6f, 0.0f, 0.6f), 0.0f)
                        .addGradPoint(new ColorRGBA(0.8f, 0.0f, 0.0f, 0.5f), 0.8f)
                        .addGradPoint(new ColorRGBA(0.2f, 0.2f, 0.2f, 0.0f), 1.0f)
        ));


        emitter.getInfluencer(SizeInfluencer.class).setSizeOverTime(new ValueType(new Curve()
                .addControlPoint(null, new Vector2f(0.0f, 1.0f), new Vector2f(0.3f, 1.0f))
                .addControlPoint(new Vector2f(0.7f, 0.0f), new Vector2f(1.0f, 0.0f), null)
        ));

        emitter.setStartSpeed(new ValueType(6.5f));
        emitter.setLifeMinMax(new ValueType(0.5f), new ValueType(1.0f));

        Float emissionsPerSec = cmd.emissionsPerSecondVal>0?cmd.emissionsPerSecondVal:30;
        emitter.setEmissionsPerSecond(emissionsPerSec.intValue());
        Float particlesPerEmission = cmd.particlesPerEmissionVal>0?cmd.particlesPerEmissionVal:4;
        emitter.setParticlesPerEmission(particlesPerEmission.intValue());

        float radius = cmd.radiusVal>0?cmd.radiusVal:3.0f;
        emitter.setShape(new EmitterLine(radius));
        emitter.setStartRotation(new VectorValueType(new Vector3f(0, 0, -FastMath.PI), new Vector3f(0, 0, FastMath.PI)));

        emitter.setLocalTranslation(cmd.posX, cmd.posY, cmd.posZ);

        attachParticleEmitterToTargetNode(emitter,cmd);
        return emitter;
    }

    private void attachParticleEmitterToTargetNode(Spatial emitter, ParticleSystemCommand cmd) {
        if(cmd.attachToEntityVarDef!=null) {
            Spatial sp = getEntitySpatial(((RunTimeVarDef)cmd.attachToEntityVarDef).varName, ((RunTimeVarDef)cmd.attachToEntityVarDef).varDef.varType);
            if(sp!=null && sp instanceof Node) {
                Node scaleNode = new Node(); // we need this node to preserve scaling of the attached spatial
                scaleNode.attachChild(emitter);
                float ratio = 1.0f/sp.getWorldScale().getX();
                scaleNode.setLocalScale(ratio);
                ((Node)sp).attachChild(scaleNode);
            } else {
                rootNode.attachChild(emitter);
            }
        } else {
            rootNode.attachChild(emitter);
        }
    }

    public ParticleEmitter createDebris(ParticleSystemCommand cmd){

        ParticleEmitter debris = new ParticleEmitter("Debris", ParticleMesh.Type.Triangle, 15 );
        debris.setSelectRandomImage(true);
        debris.setRandomAngle(true);
        debris.setRotateSpeed(FastMath.TWO_PI * 4);
        debris.setStartColor(new ColorRGBA(1f, 0.59f, 0.28f, (float) (1.0f )));
        debris.setEndColor(new ColorRGBA(.5f, 0.5f, 0.5f, 0f));

        float startSize = cmd.startSizeVal>0?cmd.startSizeVal:0.2f;
        float endSize = cmd.endSizeVal>0?cmd.endSizeVal:0.2f;

        debris.setStartSize(startSize);
        debris.setEndSize(endSize);

        debris.setGravity(0, 12f, 0);
        debris.setLowLife(1.4f);
        debris.setHighLife(1.5f);
        debris.getParticleInfluencer()
                .setInitialVelocity(new Vector3f(0, 15, 0));
        debris.getParticleInfluencer().setVelocityVariation(.60f);
        debris.setImagesX(3);
        debris.setImagesY(3);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Particle.j3md");
        mat.setTexture("Texture", assetManager.loadTexture("Effects/Explosion/Debris.png"));
        debris.setMaterial(mat);
        //rootNode.attachChild(debris);
        attachParticleEmitterToTargetNode(debris,cmd);
        debris.setLocalTranslation(cmd.posX,cmd.posY,cmd.posZ);
        return debris;

    }

    public ParticleEmitter setFireAnimationBackground(ParticleSystemCommand cmd) {
        ParticleEmitter fire =
                new ParticleEmitter("Emitter", ParticleMesh.Type.Triangle, 30);
        Material mat_red = new Material(assetManager,
                "Common/MatDefs/Misc/Particle.j3md");
        mat_red.setTexture("Texture", assetManager.loadTexture(
                "Effects/Explosion/flame.png"));
        fire.setMaterial(mat_red);
        fire.setImagesX(2);
        fire.setImagesY(2); // 2x2 texture animation
        fire.setEndColor(  new ColorRGBA(1f, 0f, 0f, 0.5f));   // red
        fire.setStartColor(new ColorRGBA(1f, 1f, 0f, 0.1f)); // yellow
        fire.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 2, 0));
        fire.setStartSize(1.5f);
        fire.setEndSize(0.1f);
        fire.setGravity(0, 0, 0);
        fire.setLowLife(1f);
        fire.setHighLife(3f);
        fire.getParticleInfluencer().setVelocityVariation(0.3f);

        rootNode.attachChild(fire);
        //fire.move(0,-4,0);
        if(cmd!=null) {
            fire.setLocalTranslation(cmd.posX, cmd.posY, cmd.posZ);
        }
        return fire;
    }

    public ParticleEmitter createExplosion(ParticleSystemCommand cmd){
        ParticleEmitter shockwave = new ParticleEmitter("Shockwave", ParticleMesh.Type.Triangle, 30 );
//        shockwave.setRandomAngle(true);
        //shockwave.setFaceNormal(Vector3f.UNIT_Y);
        shockwave.setStartColor(new ColorRGBA(.48f, 0.17f, 0.01f, (float) (.8f )));
        shockwave.setEndColor(new ColorRGBA(.48f, 0.17f, 0.01f, 0f));

        shockwave.setStartSize(0f);
        shockwave.setEndSize(7f);

        //shockwave.setParticlesPerSec(0);
        shockwave.setGravity(0, 0, 0);
        shockwave.setLowLife(1f);//0.5
        shockwave.setHighLife(3f);//0.5
        shockwave.getParticleInfluencer()
                .setInitialVelocity(new Vector3f(0, 0, 0));
        shockwave.getParticleInfluencer().setVelocityVariation(0f);
        shockwave.setImagesX(2);
        shockwave.setImagesY(2);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Particle.j3md");
        mat.setTexture("Texture", assetManager.loadTexture("Effects/Explosion/flame.png"));//shockwave.png
        shockwave.setMaterial(mat);

        attachParticleEmitterToTargetNode(shockwave,cmd);
        //rootNode.attachChild(shockwave);

        shockwave.setLocalTranslation(cmd.posX, cmd.posY, cmd.posZ);


        return  shockwave;
    }

    public ParticleEmitter createShockwave(ParticleSystemCommand cmd){
        ParticleEmitter shockwave = new ParticleEmitter("Shockwave", ParticleMesh.Type.Triangle, 1 );
//        shockwave.setRandomAngle(true);
        shockwave.setFaceNormal(Vector3f.UNIT_Y);
        shockwave.setStartColor(new ColorRGBA(.48f, 0.17f, 0.01f, (float) (.8f )));
        shockwave.setEndColor(new ColorRGBA(.48f, 0.17f, 0.01f, 0f));

        shockwave.setStartSize(0f);
        shockwave.setEndSize(7f);

        //shockwave.setParticlesPerSec(0);
        shockwave.setGravity(0, 0, 0);
        shockwave.setLowLife(0.5f);//0.5
        shockwave.setHighLife(0.5f);//0.5
        shockwave.getParticleInfluencer()
                .setInitialVelocity(new Vector3f(0, 0, 0));
        shockwave.getParticleInfluencer().setVelocityVariation(0f);
        shockwave.setImagesX(1);
        shockwave.setImagesY(1);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Particle.j3md");
        mat.setTexture("Texture", assetManager.loadTexture("Effects/Explosion/flame.png"));//shockwave.png
        shockwave.setMaterial(mat);
        //rootNode.attachChild(shockwave);
        attachParticleEmitterToTargetNode(shockwave,cmd);

        shockwave.setLocalTranslation(cmd.posX,cmd.posY,cmd.posZ);
        return  shockwave;
    }

    public ParticleEmitter createFlash(ParticleSystemCommand cmd){
        ParticleEmitter flash = new ParticleEmitter("Flash", ParticleMesh.Type.Triangle, 24 );
        flash.setSelectRandomImage(true);
        flash.setStartColor(new ColorRGBA(1f, 0.8f, 0.36f, (float) (1f )));
        flash.setEndColor(new ColorRGBA(1f, 0.8f, 0.36f, 0f));
        flash.setStartSize(cmd.startSizeVal);
        flash.setEndSize(cmd.endSizeVal);
        flash.setShape(new EmitterSphereShape(Vector3f.ZERO, .05f));
        //flash.setParticlesPerSec(0);
        flash.setGravity(cmd.gravityX, cmd.gravityY, cmd.gravityZ);
        flash.setLowLife(.2f);
        flash.setHighLife(.2f);
        flash.getParticleInfluencer()
                .setInitialVelocity(new Vector3f(0, 5f, 0));
        flash.getParticleInfluencer().setVelocityVariation(1);
        flash.setImagesX(2);
        flash.setImagesY(2);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Particle.j3md");
        mat.setTexture("Texture", assetManager.loadTexture("Effects/Explosion/flash.png"));
        //mat.setBoolean("PointSprite", POINT_SPRITE);
        flash.setMaterial(mat);
        flash.setLocalTranslation(cmd.posX,cmd.posY,cmd.posZ);
        //rootNode.attachChild(flash);
        attachParticleEmitterToTargetNode(flash,cmd);
        return flash;
    }

    public void removeNewParticleEmitter(Node n) {
        n.removeFromParent();

    }

    public void removeParticleEmitter(ParticleEmitter pe) {
        pe.removeFromParent();

    }

    public void runOnGlThread(Runnable r) {
        this.enqueue(r);
    }

    public void loadTerrain(String terrainName) {
        TerrainResource terrainRes = assetsMapping.getTerrainsIndex().get(terrainName);
        if(terrainRes==null) return;// log error here

        terrainHandler.init(terrainRes,bulletAppState);
    }

    public Spatial getEntitySpatial(String varName) {

        if(models.containsKey(varName)) {
            return models.get(varName).model;
        }

        if(spheres.containsKey(varName)) {
            return spheres.get(varName);
        }

        if(boxes.containsKey(varName)) {
            return boxes.get(varName);
        }

        if(sprites.containsKey(varName)) {
            return sprites.get(varName).getSpatial();
        }

        if(effekseerEffects.containsKey(varName)) {
            return effekseerEffects.get(varName).node;
        }

        return null;

    }

    public ISceneMax3dObjectWrapper getEntityWrapper(String targetVar, int varType) {
        if(varType==VariableDef.VAR_TYPE_2D) {
            return sprites.get(targetVar);
        }

        return null;
    }

    public Spatial getEntitySpatial(String targetVar, int varType) {
        Spatial m=null;

        if(varType==VariableDef.VAR_TYPE_3D) {
            AppModel am = models.get(targetVar);
            if (am == null) {
                return null;
            }
            m = am.model;
        } else if(varType==VariableDef.VAR_TYPE_2D) {
            SpriteEmitter sp = sprites.get(targetVar);
            if(sp==null) return null;
            m=sp.getSpatial();
        } else if(varType==VariableDef.VAR_TYPE_SPHERE) {
            m=spheres.get(targetVar);
        } else if(varType==VariableDef.VAR_TYPE_BOX) {
            m=boxes.get(targetVar);
        } else if(varType==VariableDef.VAR_TYPE_CYLINDER) {
            m=cylinders.get(targetVar);
        } else if(varType==VariableDef.VAR_TYPE_HOLLOW_CYLINDER) {
            m=hollowCylinders.get(targetVar);
        } else if(varType==VariableDef.VAR_TYPE_QUAD) {
            m=quads.get(targetVar);
        } else if(varType==VariableDef.VAR_TYPE_EFFEKSEER) {
            EffekseerInst inst = effekseerEffects.get(targetVar);
            m = inst != null ? inst.node : null;
        }

        return m;

    }

    public void setChaseCameraOn(String targetVar, int varType, ChaseCameraCommand cmd) {

        turnOffCameraStates();

        Spatial m=null;

        if(varType==VariableDef.VAR_TYPE_3D) {
            AppModel am = models.get(targetVar);
            if (am == null) {
                return;
            }

            m = am.model;
            if (m == null) {
                return;
            }
        } else if(varType==VariableDef.VAR_TYPE_2D) {
            SpriteEmitter sp = sprites.get(targetVar);
            if(sp==null) return;
            m=sp.getSpatial();
        } else if(varType==VariableDef.VAR_TYPE_SPHERE) {
            m=spheres.get(targetVar);
        } else if(varType==VariableDef.VAR_TYPE_BOX) {
            m=boxes.get(targetVar);
        }

        if(m!=null) {

//            if(chaseCam!=null) {
//                setChaseCameraAttributes(chaseCam, cmd);
//                chaseCam.setSpatial(m);
//                chaseCam.setEnabled(true);
//                return;
//            }

            SceneMaxChaseCamera cm = new SceneMaxChaseCamera(cam, m, inputManager);//
            cm.setSmoothMotion(true);
            cm.setTrailingEnabled(true);
            cm.setChasingSensitivity(1.5f);
            cm.setTrailingSensitivity(5f);
            setChaseCameraAttributes(cm, cmd);
            cm.setSpatial(m);
            cm.setUpVector(Vector3f.UNIT_Y);
            chaseCam = cm;

        }

    }

    private void setChaseCameraAttributes(SceneMaxChaseCamera cm, ChaseCameraCommand cmd) {

        if (cmd.havingAttributesExists) {

            if (!cmd.trailing) {
                cm.setTrailingEnabled(false);
            }

            if (cmd.verticalRotationVal != null) {
                cm.setDefaultVerticalRotation(cmd.verticalRotationVal.floatValue() * FastMath.DEG_TO_RAD);
            }

            if (cmd.horizontalRotationVal != null) {
                cm.setDefaultHorizontalRotation(cmd.horizontalRotationVal.floatValue() * FastMath.DEG_TO_RAD);
            }

            if (cmd.rotationSpeedVal != null) {
                cm.setRotationSpeed(cmd.rotationSpeedVal.floatValue());
            }

            if (cmd.minDistanceVal != null) {
                cm.setMinDistance(cmd.minDistanceVal.floatValue());
            }

            if (cmd.maxDistanceVal != null) {
                cm.setMaxDistance(cmd.maxDistanceVal.floatValue());
            }

        }

    }

    public void setAttachCameraOff() {

        if(attachCameraNode!=null) {

            Node scaleNode = attachCameraNode.getParent();

            attachCameraNode.setEnabled(false);
            attachCameraNode.removeFromParent();

            if(scaleNode!=null) {
                scaleNode.removeFromParent();
            }

            attachCameraNode=null;

        }

    }

    public Boolean isEntityStatic(String var, int varType) {

        List<java.lang.Object> ctls = collisionControlsCache.get(var);
        if(ctls==null || ctls.size()==0) return null;

        java.lang.Object ctl = ctls.get(0);//check is active on the first control
        if(ctl instanceof RigidBodyControl) {
            return(ctl!=null && !((RigidBodyControl)ctl).isActive());
        } else if (ctl instanceof DynamicAnimControl) {
            DynamicAnimControl dac = (DynamicAnimControl)ctl;
            PhysicsRigidBody[] rbs = dac.listRigidBodies();
            if(rbs!=null && rbs.length>0) {

                for(int i=0;i<rbs.length;++i) {
                    if(!rbs[i].isActive()) {
                        return false;
                    }
                }

            }

        } else if (ctl instanceof CharacterControl) {
            return ((CharacterControl)ctl).onGround();
        }

        return true;

    }


    public SceneMax3DGenericVehicle switchModelToCarMode2(ResourceSetup res, Vector3f initLocation, Quaternion initRotate) {
        SceneMax3DGenericVehicle vehicle = new SceneMax3DGenericVehicle(res);
        //vehicle.attachToScene(rootNode, bulletAppState.getPhysicsSpace());

        VehicleWorld w = new DefaultVehicleWorld(this, this.getRootNode());

        vehicle.addToWorld(w, () -> {
            return 0.1f;
        });

        if(initLocation!=null) {
            vehicle.getVehicleControl().setPhysicsLocation(initLocation);
        }

        if(initRotate!=null) {
            vehicle.getVehicleControl().setPhysicsRotation(initRotate);
        }

        // add some controls
        BasicVehicleInputState basicVehicleInputState = new BasicVehicleInputState(vehicle);
        getStateManager().attach(basicVehicleInputState);
        vehicle.startEngine();

        return vehicle;
    }

    private Geometry findGeom(Spatial spatial, String name) {
        if (spatial instanceof Node) {
            Node node = (Node) spatial;
            for (int i = 0; i < node.getQuantity(); i++) {
                Spatial child = node.getChild(i);
                Geometry result = findGeom(child, name);
                if (result != null) {
                    return result;
                }
            }
        } else if (spatial instanceof Geometry) {
            if (spatial.getName().startsWith(name)) {
                return (Geometry) spatial;
            }
        }
        return null;
    }

    public void switchModelToFloatingMode(String targetVar) {

        Node model;
        AppModel am = models.get(targetVar);
        if (am == null) {
            return;
        }

        model = am.model;
        if (model == null) {
            return;
        }

        DynamicAnimControl dac = am.model.getChild(0).getControl(DynamicAnimControl.class);
        if(dac!=null) {
            dac.setDynamicSubtree(dac.getTorsoLink(), Vector3f.ZERO,
                    false);

            //ac.dropAttachments();
            //dac.bindSubtree(dac.getTorsoLink(), 2f);

        }

    }

    public void switchModelToKinematicMode(String targetVar) {

        Node model;
        AppModel am = models.get(targetVar);
        if (am == null) {
            return;
        }

        model = am.model;
        if (model == null) {
            return;
        }

        if(am.skinningControlNode!=null) {
            DynamicAnimControl dac = am.skinningControlNode.getControl(DynamicAnimControl.class);
            if (dac != null) {

                dac.blendToKinematicMode(1f, am.resetTransform);

                TorsoLink torso = dac.getTorsoLink();
                KinematicSubmode bindPose = KinematicSubmode.Animated;
                float blendInterval = 1f; // in seconds
                torso.blendToKinematicMode(bindPose, blendInterval, am.resetTransform);
                Collection<BoneLink> boneLinks = dac.listLinks(BoneLink.class);
                for (BoneLink boneLink : boneLinks) {
                    boneLink.blendToKinematicMode(bindPose, blendInterval);
                }

            }
        }

    }

    public void switchModelToRagdollMode(String targetVar) {

        Node model;
        AppModel am = models.get(targetVar);
        if (am == null) {
            return;
        }

        model = am.model;
        if (model == null) {
            return;
        }

        if(am.skinningControlNode!=null) {
            DynamicAnimControl dac = am.skinningControlNode.getControl(DynamicAnimControl.class);

            if (dac != null) {
                am.resetTransform = am.skinningControlNode.getLocalTransform().clone();
                dac.setRagdollMode();

            }

        }

    }

    public void switchModelToCharacterMode(String targetVar, SwitchModeCommand cmd) {

        Node parentNode;
        AppModel am = models.get(targetVar);
        if (am == null) {
            return;
        }

        parentNode = am.model;
        if (parentNode == null) {
            return;
        }

        Control ctl = null;

        if(am.physicalControl==null) {
            ctl = parentNode.getControl(RigidBodyControl.class);

        } else {
            ctl = (Control)am.physicalControl ;
        }

        // remove current control
        if(ctl!=null) {
            parentNode.removeControl(ctl);
            bulletAppState.getPhysicsSpace().remove(ctl);
        }

        float scaleRatio = parentNode.getLocalScale().getY() / am.resource.scaleY;
        //float calibrateMoveRatio = 1.0f/parentNode.getLocalScale().getY();

        Spatial child = parentNode.getChild(0);
        Object isCalibrated = child.getUserData("__is_char_ctrl_calibrated");
        if(isCalibrated==null) {
            child.setUserData("__is_char_ctrl_calibrated",true);
            child.move(am.resource.calibrateX, am.resource.calibrateY, am.resource.calibrateZ); // adjust position to ensure precise collision
        }

        CapsuleCollisionShape capsule = new CapsuleCollisionShape(am.resource.capsuleRadius*scaleRatio, am.resource.capsuleHeight*scaleRatio);
        CharacterControl charCtl = new CharacterControl(capsule, am.resource.stepHeight);
        charCtl.setJumpSpeed(20f);
        charCtl.setGravity(cmd.gravityVal.floatValue());//

        if(am.skinningControlNode!=null) {
            DynamicAnimControl dctl = am.skinningControlNode.getControl(DynamicAnimControl.class);
            if(dctl!=null) {
                for(int i=0;i<dctl.listRigidBodies().length;++i) {
                    charCtl.getCharacter().addToIgnoreList(dctl.listRigidBodies()[i]);
                }
            }
        }

        parentNode.addControl(charCtl);
        bulletAppState.getPhysicsSpace().add(charCtl);
        am.physicalControl = charCtl;

    }

    public void moveDirectional(String targetVar, int direction, Double dist) {

        AppModel am = models.get(targetVar);
        if (am == null) {
            return;
        }

        //Vector3f pos = am.model.getWorldTranslation().clone();
        Quaternion rot = am.model.getWorldRotation();
        Vector3f dir = rot.getRotationColumn(2);

        // make it XZ only
        Vector3f camPos = new Vector3f(dir);
        camPos.setY(0);
        camPos.normalizeLocal();

        if(direction==DirectionalMoveCommand.BACKWARD) {
            camPos.negateLocal();
        }
        camPos.multLocal(dist.floatValue());

        if(am.physicalControl instanceof CharacterControl) {
            CharacterControl cc = (CharacterControl)am.physicalControl;
            cc.setWalkDirection(camPos);

        } else if(am.physicalControl instanceof RigidBodyControl) {

        }

    }

    public void applyBoxMass(String targetVar, Double mass) {
        Node g = boxes.get(targetVar);
        if(g!=null) {
            setGeometryMass(g, mass);
        }
    }

    public void applySphereMass(String targetVar, Double mass) {
        Node g = spheres.get(targetVar);
        if(g!=null) {
            setGeometryMass(g, mass);
        }
    }

    private void setGeometryMass(Spatial g,Double mass) {
        RigidBodyControl c = g.getControl(RigidBodyControl.class);
        float massVal = mass.floatValue();
        if(massVal<1) massVal=1; // one kg is minumum for a model
        c.setMass(massVal);

        boolean isPhysical = mass>1;
        c.setKinematic(!isPhysical); // physical forces apply if greater than 1 kg
    }

    public void applyModelMass(String targetVar, Double mass) {

        Node model;
        AppModel am = models.get(targetVar);
        if (am == null) {
            return;
        }

        model = am.model;
        if (model == null) {
            return;
        }

        Control ctl = null;

        if(am.physicalControl==null) {
            ctl = model.getControl(RigidBodyControl.class);

        } else {
            ctl = (Control)am.physicalControl ;
        }

        // mass works only with rigid body control
        if(ctl instanceof RigidBodyControl) {
            RigidBodyControl c = (RigidBodyControl)ctl;
            float massVal = mass.floatValue();
            if(massVal<1) massVal=1; // one kg is minumum for a model
            c.setMass(massVal);

            boolean isPhysical = mass>1;
            c.setKinematic(!isPhysical); // physical forces apply if greater than 1 kg

            if(isPhysical) {
                am.physicalControl=ctl;
            } else {
                am.physicalControl=null;
            }

        }

    }

    public void setDebugMode(boolean debugOn) {
        bulletAppState.setDebugEnabled(debugOn);
        this.setDisplayFps(debugOn);
        this.setDisplayStatView(debugOn);
    }

    public void setChaseCameraOff() {
        if(chaseCam!=null && chaseCam.isEnabled()) {
            chaseCam.setEnabled(false);
        }
    }

    public void setModelUserData(String targetVar, String fieldName, java.lang.Object data) {
        AppModel am = models.get(targetVar);
        if(am==null) {
            return;
        }

        if(data instanceof Double) {
            data = ((Double)data).floatValue();
        }

        am.model.setUserData(fieldName,data);
    }

    public void setSpriteUserData(String targetVar, String fieldName, java.lang.Object data) {
        SpriteEmitter se = sprites.get(targetVar);
        if(se==null) {
            return;
        }

        setGeometryUserData(se.getSpatial(), fieldName,data);

    }

    public void setBoxUserData(String targetVar, String fieldName, java.lang.Object data) {
        Node g = boxes.get(targetVar);
        setGeometryUserData(g, fieldName,data);
    }

    public void setSphereUserData(String targetVar, String fieldName, java.lang.Object data) {
        Node g = spheres.get(targetVar);
        setGeometryUserData(g, fieldName,data);
    }

    public void setGeometryUserData(Spatial g, String fieldName, java.lang.Object data) {
        if(g==null) {
            return;
        }

        if(data instanceof Double) {
            data = ((Double)data).floatValue();
        }

        g.setUserData(fieldName,data);
    }

    public void AddEntityToGroup(int varType, String targetVar, String targetGroup, ProgramDef prg, SceneMaxScope scope) {

        String concreteGroupName = targetGroup;
        GroupInst gi = scope.getGroup(targetGroup);

        if(gi==null) {
            GroupDef gd = prg.getGroup(targetGroup);
            gi=new GroupInst(gd, mainScope,new Node());// always add group to the main scope
            concreteGroupName=concreteGroupName+"@"+gi.scope.scopeId;
            groups.put(concreteGroupName,gi);
            rootNode.attachChild(gi.node);
        }

        Spatial child = null;

        if(varType==VariableDef.VAR_TYPE_3D) {
            AppModel am = models.get(targetVar);
            if(am!=null) {
                child=am.model;
                //gi.node.attachChild(am.model);
            }
        } else if(varType==VariableDef.VAR_TYPE_2D) {
            SpriteEmitter se = sprites.get(targetVar);
            if(se!=null) {
                child = se.getSpatial();
            }
        } else if(varType==VariableDef.VAR_TYPE_BOX) {
            child = boxes.get(targetVar);
        } else if(varType==VariableDef.VAR_TYPE_SPHERE) {
            child = spheres.get(targetVar);
        } else if(varType==VariableDef.VAR_TYPE_CYLINDER) {
            child = cylinders.get(targetVar);
        } else if(varType==VariableDef.VAR_TYPE_HOLLOW_CYLINDER) {
            child = hollowCylinders.get(targetVar);
        } else if(varType==VariableDef.VAR_TYPE_QUAD) {
            child = quads.get(targetVar);
        }

        if(child!=null) {
            gi.node.attachChild(child);
        }

    }


    public boolean rayCastCheck(Spatial sp) {

        Vector2f click2d = inputManager.getCursorPosition().clone();

        Vector3f click3d = cam.getWorldCoordinates(
                click2d, 0f).clone();

        Vector3f dir = cam.getWorldCoordinates(
                click2d, 1f).subtractLocal(click3d).normalizeLocal();

        Ray ray = new Ray(click3d, dir);
        CollisionResults results = new CollisionResults();
        sp.collideWith(ray, results);
        return (results.size() > 0);

    }

    public boolean rayCastCheck(GroupInst g, Vector3f pos, Vector3f direction) {

        if(g==null) {
            return false;
        }

        CollisionResults results = new CollisionResults();

        if(pos!=null) {

            Ray ray = new Ray(pos, direction);
            g.node.collideWith(ray, results);


        } else {

            Vector2f click2d = inputManager.getCursorPosition().clone();

            Vector3f click3d = cam.getWorldCoordinates(
                    click2d, 0f).clone();

            Vector3f dir = cam.getWorldCoordinates(
                    click2d, 1f).subtractLocal(click3d).normalizeLocal();

            Ray ray = new Ray(click3d, dir);
            g.node.collideWith(ray, results);
        }

        if (results.size() > 0){
            CollisionResult closest = results.getClosestCollision();
            Spatial root = findRootEntity(closest.getGeometry());

            EntityInstBase eb = geoName2EntityInst.get(root.getName());
            g.lastClosestRayCheck=eb;
            return true;
        }

        return false;

    }

    private Spatial findParentEntity(Spatial sp) {
        Node parent = sp.getParent();
        if (parent==null) {
            return null;
        }

        if(parent==rootNode) {
            return sp;
        } else {
            return findParentEntity(parent);
        }
    }

    private Spatial findRootEntity(Spatial sp) {

        if(sp==null) {
            return null;
        }

        String name = sp.getName();
        if(geoName2ModelName.get(name)!=null) {
            return sp;
        }

        return findRootEntity(sp.getParent());

    }

    public void attachCoordinateAxes(Vector3f pos, ActionCommandShowHide show, Node parent) {

        Arrow arrow;

        if(show.axisX) {
            arrow = new Arrow(Vector3f.UNIT_X.mult(4));
            putShape(arrow, ColorRGBA.Red,parent).setLocalTranslation(pos);
        }

        if(show.axisY) {
            arrow = new Arrow(Vector3f.UNIT_Y.mult(4));
            putShape(arrow, ColorRGBA.Green,parent).setLocalTranslation(pos);
        }

        if(show.axisZ) {
            arrow = new Arrow(Vector3f.UNIT_Z.mult(4));
            putShape(arrow, ColorRGBA.Blue,parent).setLocalTranslation(pos);
        }

    }

    private Geometry putShape(Mesh shape, ColorRGBA color, Node parent) {
        Geometry g = new Geometry("coordinate axis", shape);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.getAdditionalRenderState().setWireframe(true);
        mat.getAdditionalRenderState().setLineWidth(4);
        mat.setColor("Color", color);
        g.setMaterial(mat);
        parent.attachChild(g);
        return g;
    }
    public RunTimeVarDef findVarRuntime(ProgramDef prg, SceneMaxScope scope, String varName) {

        String varInstName = "";
        VariableDef varInstDef=null;
        if (varName.equals("camera")) {
            return this.cameraRuntimeVarDef;
        }

        if(prg==null) {
            prg=this.prg;
        }

        if(scope==null) {
            scope=this.mainScope;
        }

        // first check function params
        Object objVal = scope.getFuncScopeParam(varName);

        if(objVal!=null) {

            if(objVal instanceof EntityInstBase) {
                EntityInstBase vinst = (EntityInstBase) objVal;
                varInstName = vinst.varDef.varName + "@" + vinst.scope.scopeId;
                varInstDef = new VariableDef();// in order to avoid overriding varType
                varInstDef.varType = vinst.varDef.varType;
            } else {
                return null;
            }

        } else { // check regular entities

            EntityInstBase inst = scope.getEntityInst(varName);
            if (inst == null) {
                return null;
            }

            varInstDef = inst.varDef;//  prg.getVar(varName);

            if (varInstDef.varType == VariableDef.VAR_TYPE_SPHERE || varInstDef.varType == VariableDef.VAR_TYPE_BOX
                    || varInstDef.varType == VariableDef.VAR_TYPE_CYLINDER || varInstDef.varType == VariableDef.VAR_TYPE_QUAD) {
                int scopeId = this.getEntityScopeId(scope, varInstDef.varName, varInstDef.varType);
                varInstName = varInstDef.varName + "@" + scopeId;
            } else if (varInstDef.varType == VariableDef.VAR_TYPE_OBJECT) {
                EntityInstBase obj = (EntityInstBase) scope.getFuncScopeParam(varInstDef.varName);

                if (obj == null) {
                    this.handleRuntimeError("Function argument '" + varInstDef.varName + "' is undefined");
                    return null;
                }

                varInstName = obj.varDef.varName + "@" + obj.scope.scopeId;
                varInstDef = new VariableDef();// in order to avoid overriding varType
                varInstDef.varType = obj.varDef.varType;
            } else if (varInstDef.varType != VariableDef.VAR_TYPE_CAMERA) {
                int scopeId = this.getEntityScopeId(scope, varInstDef.varName);
                varInstName = varInstDef.varName + "@" + scopeId;
            }

        }

        RunTimeVarDef runTimeVar = new RunTimeVarDef(varInstDef);
        runTimeVar.varName=varInstName;

        return runTimeVar;

    }

    public void setFollowCameraOn(SceneMaxScope scope, String var, VariableDef vd, FpsCameraCommand cmd) {
        // turn off existing camera states
        turnOffCameraStates();

        Node sp = (Node) getEntitySpatial(var, vd.varType);
        followCameraState = new FollowCameraAppState(this,scope,cmd, sp);
        followCameraState.initialize(this);
        //getStateManager().attach(followCameraState);

    }

    public void setDungeonCameraOn(SceneMaxScope scope, String var, VariableDef vd, FpsCameraCommand cmd) {

        // turn off existing camera states
        turnOffCameraStates();

        Node sp = (Node) getEntitySpatial(var, vd.varType);
        dungeonCameraState = new DungeonCameraAppState(scope,cmd, sp);
        getStateManager().attach(dungeonCameraState);

    }

    private void turnOffCameraStates() {
        setChaseCameraOff();
        setAttachCameraOff();
        setDungeonCameraOff();
        setFollowCameraOff();
    }

    private void setFollowCameraOff() {
        if(followCameraState!=null) {
            //getStateManager().detach(followCameraState);
            followCameraState=null;
        }
    }

    private void setDungeonCameraOff() {
        if(dungeonCameraState!=null) {
            getStateManager().detach(dungeonCameraState);
            dungeonCameraState=null;
        }
    }

    public void setFpsCameraOn(String var, VariableDef vd, Vector3f offsetPos, Vector3f offsetRot) {

        turnOffCameraStates();// turn off chase camera if its activated
        Node sp = (Node) getEntitySpatial(var, vd.varType);


        attachCameraNode = new CameraNode("fps_cam_node", cam);
        attachCameraNode.setControlDir(CameraControl.ControlDirection.SpatialToCamera);

        Node scaleNode = new Node(); // we need this node to preserve scaling of the attached spatial
        scaleNode.attachChild(attachCameraNode);
        float ratio = 1.0f/sp.getWorldScale().getX();
        scaleNode.setLocalScale(ratio);

        sp.attachChild(scaleNode);
        attachCameraNode.setEnabled(true);

        if (offsetPos != null) {
            attachCameraNode.setLocalTranslation(offsetPos);
        } else {
            attachCameraNode.setLocalTranslation(new Vector3f(0, 3, -15));
        }

        attachCameraNode.lookAt(sp.getLocalTranslation(), Vector3f.UNIT_Y);

        if(offsetRot!=null) {
            attachCameraNode.rotate(offsetRot.getX()* FastMath.DEG_TO_RAD,offsetRot.getY()* FastMath.DEG_TO_RAD,offsetRot.getZ()* FastMath.DEG_TO_RAD);
        }

    }

    public void setFpsCameraOff() {
        //this.flyCam.setEnabled(false);
    }

    public void pauseScene() {
        this.scenePaused=true;
    }

    public void resumeScene() {
        this.scenePaused=false;
    }

    public void attachEntity2(RunTimeVarDef attachedEntity, String attachedEntityJointName,
                             RunTimeVarDef attachToEntity, String attachToEntityJointName,
                             Double xPos, Double yPos, Double zPos) {

        Spatial attachToSpatial = getEntitySpatial(attachToEntity.varName,attachToEntity.varDef.varType);
        if(attachToSpatial==null) {
            return;// error not found
        }

        ISceneMax3dObjectWrapper wrapper = getEntityWrapper(attachedEntity.varName,attachedEntity.varDef.varType);
        Spatial attachedSpatial = wrapper != null ? wrapper.getSpatial() : getEntitySpatial(attachedEntity.varName,attachedEntity.varDef.varType);
        if(attachedSpatial==null){
            return;// error not found
        }

        Node attachedNode = (Node)attachedSpatial;
        if(attachedEntityJointName!=null && attachedEntity.varDef.varType==VariableDef.VAR_TYPE_3D) {
            attachedNode = getAttachJointNode(attachedEntity, attachedEntityJointName);
        }

        Node attachToNode = (Node)attachToSpatial;
        if(attachToEntityJointName!=null && attachToEntity.varDef.varType==VariableDef.VAR_TYPE_3D) {
            attachToNode = getAttachJointNode(attachToEntity, attachToEntityJointName);
        }

        Node scaleNode = new Node(); // we need this node to preserve scaling of the attached spatial
        if (wrapper!=null) {
            wrapper.attachTo(scaleNode);
        } else {
            scaleNode.attachChild(attachedNode);
        }

        float ratio = 1.0f/attachToNode.getWorldScale().getX();
        scaleNode.setLocalScale(ratio);

        attachToNode.attachChild(scaleNode);
        Spatial attachToParentNode = findParentEntity(attachToNode);

        if (attachToParentNode!=null) {
            // character control should ignore the attached object
            CharacterControl ctl = attachToParentNode.getControl(CharacterControl.class);
            if (ctl != null) {
                SceneGraphVisitor visitor = new SceneGraphVisitor() {
                    @Override
                    public void visit(Spatial sp) {
                        RigidBodyControl rctl = sp.getControl(RigidBodyControl.class);
                        if (rctl != null) {
                            ctl.getCharacter().addToIgnoreList(rctl);
                        }
                    }
                };

                attachedSpatial.breadthFirstTraversal(visitor);
            }
        }

        attachedNode.setLocalTranslation(xPos.floatValue(),yPos.floatValue(),zPos.floatValue());

    }

    private Node getAttachJointNode(RunTimeVarDef entityVarDef, String jointName) {
        AppModel model = models.get(entityVarDef.varName);
        try {
            return model.getJointAttachementNode(jointName);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    public void attachEntity(RunTimeVarDef targetEntity, RunTimeVarDef entityToAttach, String targetJointName, Double xPos, Double yPos, Double zPos) {

        Spatial target = getEntitySpatial(targetEntity.varName,targetEntity.varDef.varType);
        if(target==null) {
            return;// error not found
        }
        Spatial attachEntity = getEntitySpatial(entityToAttach.varName,entityToAttach.varDef.varType);
        if(attachEntity==null){
            return;// error not found
        }

        Node targetNode = (Node)target;

        if(targetJointName!=null && targetEntity.varDef.varType==VariableDef.VAR_TYPE_3D) {
            AppModel model = models.get(targetEntity.varName);

            try {
                Node n = model.getJointAttachementNode(targetJointName);
                if (n != null) {
                    targetNode = n;
                }

            }catch(Exception e) {
                e.printStackTrace();
            }
        }

        Node scaleNode = new Node(); // we need this node to preserve scaling of the attached spatial
        scaleNode.attachChild(attachEntity);
        float ratio = 1.0f/targetNode.getWorldScale().getX();
        scaleNode.setLocalScale(ratio);

        targetNode.attachChild(scaleNode);
        Spatial parentNode = findParentEntity(targetNode);

        // character control should ignore the attached object
        CharacterControl ctl = parentNode.getControl(CharacterControl.class);
        if(ctl!=null) {
            SceneGraphVisitor visitor = new SceneGraphVisitor() {

                @Override
                public void visit(Spatial sp) {

                    RigidBodyControl rctl = sp.getControl(RigidBodyControl.class);
                    if (rctl != null) {
                        ctl.getCharacter().addToIgnoreList(rctl);
                    }

                }
            };

            attachEntity.breadthFirstTraversal(visitor);
        }

        attachEntity.setLocalTranslation(xPos.floatValue(),yPos.floatValue(),zPos.floatValue());

    }

    public void moveModelToDirection(String targetVar, int verbalCommand, float val) {

        AppModel am = models.get(targetVar);
        if(am==null) {
            return;
        }


        if(am.isStatic) {
            moveStaticModelInDirection(am.model, verbalCommand, val);

        } else {

            Spatial sp = am.model;
            if(sp==null) {
                return;
            }

            moveSpatialInDirection(sp, verbalCommand, val);
        }

    }

    protected void moveStaticModelInDirection(Node sp, int verbalCommand, float val) {

        List<Object> ctls = collisionControlsCache.get(sp.getName());
        if (ctls != null) {
            int cnt = ctls.size();
            for (int i = 0; i < cnt; ++i) {
                RigidBodyControl rbCtl = (RigidBodyControl) ctls.get(i);// am.model.getControl(i);
                if(verbalCommand== ActionCommandMove.VERBAL_MOVE_FORWARD) {
                    Vector3f forward = rbCtl.getPhysicsLocation(null).mult(Vector3f.UNIT_Z);
                    rbCtl.setPhysicsLocation(rbCtl.getPhysicsLocation().add(forward.mult(val)));
                } else if(verbalCommand==ActionCommandMove.VERBAL_MOVE_BACKWARD) {
                    Vector3f forward = sp.getLocalRotation().mult(Vector3f.UNIT_Z);
                    rbCtl.setPhysicsLocation(rbCtl.getPhysicsLocation().add(forward.mult(val).negate()));
                } else if(verbalCommand==ActionCommandMove.VERBAL_MOVE_LEFT) {
                    Vector3f left = sp.getLocalRotation().mult(Vector3f.UNIT_X);
                    rbCtl.setPhysicsLocation(rbCtl.getPhysicsLocation().add(left.mult(val)));
                } else if(verbalCommand==ActionCommandMove.VERBAL_MOVE_RIGHT) {
                    Vector3f left = sp.getLocalRotation().mult(Vector3f.UNIT_X);
                    rbCtl.setPhysicsLocation(rbCtl.getPhysicsLocation().add(left.mult(val).negate()));
                } else if(verbalCommand==ActionCommandMove.VERBAL_MOVE_UP) {
                    Vector3f left = sp.getLocalRotation().mult(Vector3f.UNIT_Y);
                    rbCtl.setPhysicsLocation(rbCtl.getPhysicsLocation().add(left.mult(val)));
                } else if(verbalCommand==ActionCommandMove.VERBAL_MOVE_DOWN) {
                    Vector3f left = sp.getLocalRotation().mult(Vector3f.UNIT_Y);
                    rbCtl.setPhysicsLocation(rbCtl.getPhysicsLocation().add(left.mult(val).negate()));
                }

            }
        }

    }

    protected void moveSpatialInDirection(Spatial sp, int verbalCommand, float val) {
        if(verbalCommand== ActionCommandMove.VERBAL_MOVE_FORWARD) {
            Vector3f forward = sp.getLocalRotation().mult(Vector3f.UNIT_Z);
            sp.move(forward.mult(val));
        } else if(verbalCommand==ActionCommandMove.VERBAL_MOVE_BACKWARD) {
            Vector3f forward = sp.getLocalRotation().mult(Vector3f.UNIT_Z);
            sp.move(forward.mult(val).negate());
        } else if(verbalCommand==ActionCommandMove.VERBAL_MOVE_LEFT) {
            Vector3f left = sp.getLocalRotation().mult(Vector3f.UNIT_X);
            sp.move(left.mult(val));
        } else if(verbalCommand==ActionCommandMove.VERBAL_MOVE_RIGHT) {
            Vector3f left = sp.getLocalRotation().mult(Vector3f.UNIT_X);
            sp.move(left.mult(val).negate());
        } else if(verbalCommand==ActionCommandMove.VERBAL_MOVE_UP) {
            Vector3f left = sp.getLocalRotation().mult(Vector3f.UNIT_Y);
            sp.move(left.mult(val));
        } else if(verbalCommand==ActionCommandMove.VERBAL_MOVE_DOWN) {
            Vector3f left = sp.getLocalRotation().mult(Vector3f.UNIT_Y);
            sp.move(left.mult(val).negate());
        }
    }

    public void moveSpriteToDirection(String targetVar, int verbalCommand, float val) {
        Spatial m = sprites.get(targetVar).getSpatial();
        if(m==null) {
            return;
        }

        moveSpatialInDirection(m, verbalCommand, val);
    }

    public void moveCameraToDirection(String targetVar, int verbalCommand, float val) {

        Vector3f vel = new Vector3f();
        Vector3f pos = cam.getLocation().clone();

        if(verbalCommand== ActionCommandMove.VERBAL_MOVE_FORWARD) {
            cam.getDirection(vel);
            vel.mult(val);
            pos.addLocal(vel);

        } else if(verbalCommand==ActionCommandMove.VERBAL_MOVE_BACKWARD) {
            cam.getDirection(vel);
            vel.mult(val);
            pos.addLocal(vel.negate());

        } else if(verbalCommand==ActionCommandMove.VERBAL_MOVE_LEFT) {
            cam.getLeft(vel);
            vel.multLocal(val, 0, val);
            pos.addLocal(vel);
        } else if(verbalCommand==ActionCommandMove.VERBAL_MOVE_RIGHT) {
            cam.getLeft(vel);
            vel.multLocal(val, 0, val);
            pos.addLocal(vel.negate());
        } else if(verbalCommand==ActionCommandMove.VERBAL_MOVE_UP) {
            cam.getUp(vel);
            vel.multLocal(0, val, 0);
            pos.addLocal(vel);
        } else if(verbalCommand==ActionCommandMove.VERBAL_MOVE_DOWN) {
            cam.getUp(vel);
            vel.multLocal(0, val, 0);
            pos.addLocal(vel.negate());
        }

        if(chaseCam!=null && chaseCam.isEnabled()) {
            chaseCam.setPosition(pos);
        } else {
            cam.setLocation(pos);
        }

    }

    public void moveSphereToDirection(String targetVar, int verbalCommand, float val) {

        Node g = spheres.get(targetVar);
        if(g==null) {
            return;
        }

        moveSpatialInDirection(g, verbalCommand, val);
    }

    public void moveBoxToDirection(String targetVar, int verbalCommand, float val) {

        EntityInstBase e = geoName2EntityInst.get(targetVar);
        if(e.varDef.isStatic) {
            List<Object> ctls = collisionControlsCache.get(targetVar);
            RigidBodyControl ctl = (RigidBodyControl)ctls.get(0);
            moveControlInDirection(ctl, verbalCommand, val);
        } else {
            Node g = boxes.get(targetVar);
            if(g==null) {
                return;
            }
            moveSpatialInDirection(g, verbalCommand, val);
        }
    }

    protected void moveControlInDirection(RigidBodyControl sp, int verbalCommand, float val) {
        if(verbalCommand== ActionCommandMove.VERBAL_MOVE_FORWARD) {

            Vector3f forward = sp.getPhysicsRotation().mult(Vector3f.UNIT_Z);
            sp.setPhysicsLocation(sp.getPhysicsLocation().add(forward.mult(val)));


        } else if(verbalCommand==ActionCommandMove.VERBAL_MOVE_BACKWARD) {
            Vector3f forward = sp.getPhysicsRotation().mult(Vector3f.UNIT_Z);
            sp.setPhysicsLocation(sp.getPhysicsLocation().add(forward.mult(val).negate()));
        } else if(verbalCommand==ActionCommandMove.VERBAL_MOVE_LEFT) {
            Vector3f left = sp.getPhysicsRotation().mult(Vector3f.UNIT_X);
            sp.setPhysicsLocation(sp.getPhysicsLocation().add(left.mult(val)));
        } else if(verbalCommand==ActionCommandMove.VERBAL_MOVE_RIGHT) {
            Vector3f left = sp.getPhysicsRotation().mult(Vector3f.UNIT_X);
            sp.setPhysicsLocation(sp.getPhysicsLocation().add(left.mult(val).negate()));
        } else if(verbalCommand==ActionCommandMove.VERBAL_MOVE_UP) {
            Vector3f left = sp.getPhysicsRotation().mult(Vector3f.UNIT_Y);
            sp.setPhysicsLocation(sp.getPhysicsLocation().add(left.mult(val)));
        } else if(verbalCommand==ActionCommandMove.VERBAL_MOVE_DOWN) {
            Vector3f left = sp.getPhysicsRotation().mult(Vector3f.UNIT_Y);
            sp.setPhysicsLocation(sp.getPhysicsLocation().add(left.mult(val).negate()));
        }
    }

    public void carAccelerate(RunTimeVarDef entity, Double accelerate) {

        AppModel am = models.get(entity.varName);
        if(am==null) {
            return;
        }

        if(am.physicalControl instanceof VehicleControl) {
            am.accelerate+=accelerate.floatValue();
            ((VehicleControl)am.physicalControl).accelerate(am.accelerate);
        }

    }

    public void carSteer(RunTimeVarDef entity, Double steer) {

        AppModel am = models.get(entity.varName);
        if(am==null) {
            return;
        }

        if(am.physicalControl instanceof VehicleControl) {
            am.steer+=steer.floatValue();
            ((VehicleControl)am.physicalControl).steer(am.steer);
        }


    }

    public void carBrake(RunTimeVarDef entity, Double brake) {

        AppModel am = models.get(entity.varName);
        if(am==null) {
            return;
        }

        if(am.physicalControl instanceof VehicleControl) {

            //((VehicleControl)am.physicalControl).brake(brake.floatValue());

            VehicleControl vc = (VehicleControl)am.physicalControl;
            for(int i=0;i<4;++i) {
                vc.brake(i, brake.floatValue());
            }

        }

    }

    public void carTurbo(RunTimeVarDef entity, Double x, Double y, Double z) {

        AppModel am = models.get(entity.varName);
        if(am==null) {
            return;
        }

        if(am.physicalControl instanceof VehicleControl) {

            ((VehicleControl)am.physicalControl).applyImpulse(new Vector3f(x.floatValue(),y.floatValue(),z.floatValue()), Vector3f.ZERO);
        }


    }

    public void setupVehicle(RunTimeVarDef entity, VehicleSetupCommand cmd) {

        AppModel am = models.get(entity.varName);
        if (am == null) {
            return;
        }

        if (am.physicalControl instanceof SceneMax3DGenericVehicle) {

            SceneMax3DGenericVehicle v = (SceneMax3DGenericVehicle) am.physicalControl;

            if(cmd.setupEngine) {
                if(cmd.enginePowerVal!=null) {
                    v.getEngine().setMaxOutputWatts(cmd.enginePowerVal.floatValue()*HP_TO_WATT);
                } else if(cmd.engineBreakingVal!=null) {
                    //v.getEngine().setBraking(cmd.engineBreakingVal.floatValue());
                } else if(cmd.engineOnOff!=null) {
                    if(cmd.engineOnOff.toLowerCase().equals("start")) {
                        v.startEngine();//setEngineStarted(true);
                    } else {
                        v.stopEngine();// setEngineStarted(false);
                    }
                }
            } else if(cmd.inputOnOffCommand !=null) {

                if(v.inputState!=null) {
                    if(cmd.inputOnOffCommand.toLowerCase().equals("on")) {
                        v.inputState.addStateListener();
                    } else {
                        v.inputState.removeStateListener();
                    }
                }
            } else if (cmd.setupInput) {
                v.inputState.setInputSource(cmd.inputSource);

//                BasicVehicleInputState currInput = getStateManager().getState(BasicVehicleInputState.class);
//                if(currInput!=null) {
//                    getStateManager().detach(currInput);
//                }
//                SceneMaxVehicleInputState inputState = new SceneMaxVehicleInputState(v, cmd.inputSource);
//                getStateManager().attach(inputState);
            } else {
                Wheel w1, w2;
                if (cmd.setupFront) {
                    w1 = v.getWheel(0);
                    w2 = v.getWheel(1);
                } else {
                    w1 = v.getWheel(2);
                    w2 = v.getWheel(3);
                }

                if (cmd.frictionVal != null) {
                    w1.setGrip(cmd.frictionVal.floatValue());
                    w2.setGrip(cmd.frictionVal.floatValue());
                }

                if (cmd.stiffnessVal != null) {
                    w1.getSuspension().setStiffness(cmd.stiffnessVal.floatValue());
                    w2.getSuspension().setStiffness(cmd.stiffnessVal.floatValue());
                }

                if (cmd.compressionVal != null) {
                    w1.getSuspension().setCompressDamping(cmd.compressionVal.floatValue());
                    w2.getSuspension().setCompressDamping(cmd.compressionVal.floatValue());
                }

                if (cmd.dampingVal != null) {
                    w1.getSuspension().setRelaxDamping(cmd.dampingVal.floatValue());
                    w2.getSuspension().setRelaxDamping(cmd.dampingVal.floatValue());
                }

                if (cmd.lengthVal != null) {
                    w1.getSuspension().setRestLength(cmd.lengthVal.floatValue());
                    w2.getSuspension().setRestLength(cmd.lengthVal.floatValue());
                }
            }

        }

    }

    public void setEntryScriptFileName(String entryScriptFileName) {
        this.entryScriptFileName = entryScriptFileName;
    }

    public void setWorkingFolder(String workingFolder) {
        this.workingFolder=workingFolder;
    }

    public String getWorkingFolder() {
        return this.workingFolder;
    }

    /**
     * Returns the UI system manager for .smui document rendering.
     */
    public com.scenemaxeng.common.ui.widget.UIManager getUIManager() {
        return uiManager;
    }

    /**
     * Loads a .smui document at runtime, creating all UI layers and widgets.
     */
    public void loadUIDocument(java.io.File uiFile) {
        try {
            if (uiManager != null) {
                uiManager.load(uiFile);
            }
        } catch (Exception e) {
            handleRuntimeError("Failed to load UI document: " + e.getMessage());
        }
    }

    public void loadUIDocument(String uiName, InputStream inputStream) {
        try (InputStream in = inputStream) {
            if (uiManager != null && in != null) {
                uiManager.load(in, "running/" + uiName + ".smui");
            }
        } catch (Exception e) {
            handleRuntimeError("Failed to load UI document: " + e.getMessage());
        }
    }

    public boolean loadPackagedUiDocument(String uiName) {
        if (uiName == null || uiName.trim().isEmpty()) {
            return false;
        }

        String resourcePath = "running/" + uiName + ".smui";
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);
        if (inputStream == null) {
            inputStream = SceneMaxApp.class.getClassLoader().getResourceAsStream(resourcePath);
        }
        if (inputStream == null) {
            inputStream = SceneMaxApp.class.getResourceAsStream("/" + resourcePath);
        }
        if (inputStream == null) {
            return false;
        }

        loadUIDocument(uiName, inputStream);
        return true;
    }

    /**
     * Unloads all UI systems. Called during scene cleanup.
     */
    public void unloadAllUI() {
        if (uiManager != null) {
            uiManager.unloadAll();
        }
    }

    public java.lang.Object calcAngle(String obj1, String obj2) {

        Spatial s1 = getEntitySpatial(obj1);
        if(s1==null) return 0;

        Spatial s2 = getEntitySpatial(obj2);
        if(s2==null) return 0;

        Vector3f v1 = s1.getWorldTranslation();
        Vector3f v2 = s2.getWorldTranslation();

        Vector2f vv1 = new Vector2f(v1.x,v1.z);
        Vector2f vv2 = new Vector2f(v2.x,v2.z);
        float ang = FastMath.RAD_TO_DEG * vv1.angleBetween(vv2);
        return ang;

        //float ang = FastMath.RAD_TO_DEG * v1.normalize().angleBetween(v2.normalize());

        //return ang;
    }

    public java.lang.Object calcDistance(String obj1, String obj2) {

        Spatial s1 = getEntitySpatial(obj1);
        if(s1==null) return 0;

        Spatial s2 = getEntitySpatial(obj2);
        if(s2==null) return 0;

        Vector3f v1 = s1.getWorldTranslation();
        Vector3f v2 = s2.getWorldTranslation();

        return v1.distance(v2);
    }


    public void showSolarSystemSkyBox(SkyBoxCommand cmd) {

        float cloudFlattening = cmd.cloudFlatteningVal!=null?cmd.cloudFlatteningVal.floatValue():0.9f;
        float cloudiness = cmd.cloudinessVal!=null?cmd.cloudinessVal.floatValue():0.5f;
        skyControl = new SkyControl(assetManager, cam, cloudFlattening, StarsOption.Cube, true);
        skyControl.setCloudiness(cloudiness);
        if(cmd.hourOfDayVal!=null) {
            skyControl.getSunAndStars().setHour(cmd.hourOfDayVal.floatValue());
        }
        rootNode.addControl(skyControl);
        skyControl.setEnabled(true);


//        for (Light light : rootNode.getLocalLightList()) {
//            String name=light.getName();
//            if (name!=null && name.equals("ambient")) {
//                skyControl.getUpdater().setAmbientLight((AmbientLight) light);
//            } else if (name!=null && name.equals("main")) {
//                skyControl.getUpdater().setMainLight((DirectionalLight) light);
//            }
//        }

    }

    public void setupSkyControl(SkyBoxCommand cmd) {

            if(skyControl!=null) {

            if(cmd.hourOfDayVal!=null) {
                skyControl.getSunAndStars().setHour(cmd.hourOfDayVal.floatValue());
            }

            if(cmd.cloudinessVal!=null) {
                skyControl.setCloudiness(cmd.cloudinessVal.floatValue());
            }


        }
    }

    public void changeModelScale(String targetVar, Double scale) {

        Node model;
        AppModel am = models.get(targetVar);
        if (am == null) {
            return;
        }

        model = am.model;
        if (model == null) {
            return;
        }

        float sf = scale.floatValue();
        model.setLocalScale(sf);
        model.updateModelBound();

        if(am.physicalControl!=null) {
            if (am.physicalControl instanceof RigidBodyControl) {
                RigidBodyControl ctl = (RigidBodyControl) am.physicalControl;

                //bulletAppState.getPhysicsSpace().remove(ctl);
                ctl.setEnabled(false);
                CollisionShape cs = CollisionShapeFactory.createDynamicMeshShape(model);
                cs.setScale(new Vector3f(sf, sf, sf));
                ctl.setCollisionShape(cs);
                ctl.setEnabled(true);

                //bulletAppState.getPhysicsSpace().add(ctl);
            }

        } else {
            RigidBodyControl ctl = model.getControl(RigidBodyControl.class);
            if(ctl!=null) {
                ctl.setEnabled(false);
                CollisionShape cs = CollisionShapeFactory.createDynamicMeshShape(model);
                cs.setScale(new Vector3f(sf, sf, sf));
                ctl.setCollisionShape(cs);
                ctl.setEnabled(true);
            }
        }

    }

    public void changeSphereScale(String targetVar, Double scale) {

        Node g = spheres.get(targetVar);
        updateGeometryNodeScale(g,targetVar,scale,false);

    }

    private void updateGeometryNodeScale(Node n, String targetVar, Double scale, boolean isBoxShape) {

        float sf = scale.floatValue();
        n.setLocalScale(sf);
        n.updateModelBound();


//        ctl.setEnabled(false);
//        CollisionShape cs = CollisionShapeFactory.createDynamicMeshShape(model);
//        cs.setScale(new Vector3f(sf, sf, sf));
//        ctl.setCollisionShape(cs);
//        ctl.setEnabled(true);


        RigidBodyControl ctl = n.getControl(RigidBodyControl.class);
        ctl.setEnabled(false);

        EntityInstBase inst = geoName2EntityInst.get(targetVar);
        CollisionShape cs;
        Geometry g = (Geometry)n.getChild(0);
        if(inst.varDef.isStatic) {
            cs = new MeshCollisionShape(g.getMesh());
        } else if(isBoxShape){
            cs = CollisionShapeFactory.createBoxShape(g);
        } else {
            cs = CollisionShapeFactory.createDynamicMeshShape(g);
        }

        //cs.setScale(new Vector3f(sf, sf, sf));
        ctl.setCollisionShape(cs);
        ctl.setEnabled(true);

    }

    private void updateGeometryScale(Geometry g, String targetVar, Double scale, boolean isBoxShape) {

        float sf = scale.floatValue();
        g.setLocalScale(sf);
        g.updateModelBound();

        RigidBodyControl ctl = g.getControl(RigidBodyControl.class);
        ctl.setEnabled(false);

        EntityInstBase inst = geoName2EntityInst.get(targetVar);
        CollisionShape cs;
        if(inst.varDef.isStatic) {
            cs = new MeshCollisionShape(g.getMesh());
        } else if(isBoxShape){
            cs = CollisionShapeFactory.createBoxShape(g);
        } else {
            cs = CollisionShapeFactory.createDynamicMeshShape(g);
        }

        cs.setScale(new Vector3f(sf, sf, sf));
        ctl.setCollisionShape(cs);
        ctl.setEnabled(true);

    }

    public void changeBoxScale(String targetVar, Double scale) {
        Node g = boxes.get(targetVar);
        updateGeometryNodeScale(g,targetVar,scale,true);
    }


    public void characterJump(String targetVar, Double speed) {

        Node model;

        AppModel am = models.get(targetVar);
        if (am == null) {
            return;
        }

        model = am.model;
        if (model == null) {
            return;
        }

        if (am.physicalControl instanceof CharacterControl) {
            CharacterControl ctl = (CharacterControl) am.physicalControl;
            if(speed!=null) {
                ctl.setJumpSpeed(speed.floatValue());
            }

            ctl.jump();
        }

    }

    public void addLightProbe(String name, float x, float y, float z) {

        HashMap<String,String> probes = new HashMap<>();
        probes.put("1","1.j3o");
        probes.put("2","2.j3o");
        probes.put("3","3.j3o");
        probes.put("4","4.j3o");
        probes.put("5","5.j3o");
        if(probes.containsKey(name)) {
            String val = probes.get(name);
            Node probeHolder = (Node) assetManager.loadModel("probes/"+val);
            LightProbe probe = (LightProbe) probeHolder.getLocalLightList().get(0);
            Vector3f pos = new Vector3f(x,y,z);
            probe.setPosition(pos);
            probeHolder.removeLight(probe);

            //float r = probe.getArea().getRadius();
            probe.getArea().setRadius(800f);

            rootNode.addLight(probe);

        }
    }

    public void lookAt(RunTimeVarDef lookingObject, RunTimeVarDef lookAtTarget) {

        Spatial sp = getEntitySpatial(lookAtTarget.varName,lookAtTarget.varDef.varType);
        if(sp == null) {
            return;
        }

        Vector3f lookToVec = sp.getWorldTranslation().clone();

        if(lookingObject.varDef.varType==VariableDef.VAR_TYPE_CAMERA) {
            this.cam.lookAt(lookToVec, Vector3f.UNIT_Y);
            return;
        }

        Spatial sp2 = getEntitySpatial(lookingObject.varName,lookingObject.varDef.varType);
        if (sp2 == null) {
            return;
        }

        CharacterControl ctl = sp2.getControl(CharacterControl.class);
        if(ctl==null) {
            sp2.lookAt(lookToVec, Vector3f.UNIT_Y);
        } else {

            Vector3f currLoc = sp2.getWorldTranslation().clone();
            ctl.setViewDirection(lookToVec.subtract(currLoc).setY(0));
        }

    }

    public void lookAt(RunTimeVarDef lookingObject, Vector3f lookAtTarget) {

        if(lookingObject.varDef.varType==VariableDef.VAR_TYPE_CAMERA) {
            this.cam.lookAt(lookAtTarget, Vector3f.UNIT_Y);
            return;
        }

        Spatial sp = getEntitySpatial(lookingObject.varName,lookingObject.varDef.varType);
        CharacterControl ctl = sp.getControl(CharacterControl.class);
        if(ctl==null) {
            sp.lookAt(lookAtTarget,Vector3f.UNIT_Y);
        } else {
            Vector3f currLoc = sp.getWorldTranslation().clone();
            ctl.setViewDirection(lookAtTarget.subtract(currLoc).setY(0));
        }

    }

    public void clearCharacterControl(String targetVar, VariableDef vardef) {
        if(vardef.varType==VariableDef.VAR_TYPE_3D) {
            AppModel am = models.get(targetVar);
            if(am==null) {
                return;
            }

            Node parentNode = am.model;
            if (parentNode == null) {
                return;
            }

            Control ctl = (Control)am.physicalControl ;

            if(ctl!=null && ctl instanceof CharacterControl) {
                parentNode.removeControl(ctl);
                bulletAppState.getPhysicsSpace().remove(ctl);

                am.physicalControl = null;
            }

        }
    }

    public void detachModelFromParent(String targetVar) {

        AppModel am = models.get(targetVar);
        if(am==null) {
            return;
        }

        Spatial parent = am.model.getParent();
        if(parent!=null && parent!=rootNode) {

            Vector3f pos = rootNode.worldToLocal(am.model.getWorldTranslation(),null);
            Quaternion rot = am.model.getWorldRotation();

            rootNode.attachChild(am.model);
            am.model.setLocalRotation(rot);
            am.model.setLocalTranslation(pos);

            parent.removeFromParent(); // free scale node

        }


    }

    public void detachSphereFromParent(String targetVar) {
        Node g = spheres.get(targetVar);
        if(g!=null && g.getParent()!=null && g.getParent()!=rootNode) {
            Vector3f pos = rootNode.worldToLocal(g.getWorldTranslation(),null);
            Quaternion rot = g.getWorldRotation();

            rootNode.attachChild(g);
            g.setLocalRotation(rot);
            g.setLocalTranslation(pos);

        }
    }

    public void detachBoxFromParent(String targetVar) {
        Node g = boxes.get(targetVar);
        if(g!=null && g.getParent()!=null && g.getParent()!=rootNode) {
            Vector3f pos = rootNode.worldToLocal(g.getWorldTranslation(),null);
            Quaternion rot = g.getWorldRotation();

            rootNode.attachChild(g);
            g.setLocalRotation(rot);
            g.setLocalTranslation(pos);
        }
    }

    public void detachSpriteFromParent(String targetVar) {
        SpriteEmitter se = sprites.get((targetVar));
        if(se==null ) {
            return;
        }

        Spatial g = se.getSpatial();
        if(g.getParent()!=null && g.getParent()!=rootNode) {

            Vector3f pos = rootNode.worldToLocal(g.getWorldTranslation(),null);
            Quaternion rot = g.getWorldRotation();

            rootNode.attachChild(g);
            g.setLocalRotation(rot);
            g.setLocalTranslation(pos);

        }
    }

    public void channelDraw(ChannelDrawCommand cmd) {

        PictureExt pic = _drawChannels.get(cmd.channelName);
        String fromRes=cmd.resourceName;
        boolean clear = fromRes.equalsIgnoreCase("clear");
        ResourceSetup2D resource = null;
        if(!clear) {
            resource = assetsMapping.getSpriteSheetsIndex().get(fromRes.toLowerCase());
            if (resource == null) {
                return;
            }
        }

        if(pic==null) {
            if(clear) {
                return;
            }

            pic = new PictureExt(cmd.resourceName+"_"+_drawChannels.size());
            _drawChannels.put(cmd.channelName,pic);
            setTexture2D(cmd,resource,pic);
            guiNode.attachChild(pic);

        } else {
            if(clear) {
                pic.clear=true;
                guiNode.detachChild(pic);
                return;
            } else {

                if(pic.clear) {
                    pic.clear=false;
                    guiNode.attachChild(pic);
                }

            }
            setTexture2D(cmd,resource,pic);
        }

        pic.setPosition(cmd.posXVal, settings.getHeight() - pic.getLocalScale().getY() - cmd.posYVal);

    }

    private void setTexture2D(ChannelDrawCommand cmd, ResourceSetup2D resource, PictureExt pic) {

        BufferedImage img = null;

        try {
            if(!resource.path.equals(pic.resPath)) {
                img = ImageIO.read(assetManager.locateAsset(new AssetKey(resource.path)).openStream());
                pic.width = img.getWidth()/resource.cols;
                pic.height = img.getHeight()/resource.rows;
                pic.setImage(assetManager,resource.path,true);
            }
        } catch (IOException ex) {
            //LOGGER.log(Level.SEVERE, "Failed to load the backdrop image with " + image + "!", ex);
        }

        int targetWidth = pic.width;
        int targetHeight = pic.height;

        if(cmd.heightExpr!=null) {
            targetHeight = cmd.heightVal;
            targetWidth = cmd.widthVal;
        }

        if(cmd.frameNumVal<0) {
            cmd.frameNumVal = 0;
        } else if(cmd.frameNumVal>resource.cols*resource.rows-1) {
            cmd.frameNumVal = resource.cols*resource.rows-1;
        }

        int row = cmd.frameNumVal / resource.cols ;
        int col = cmd.frameNumVal % resource.cols ;

        updateTextureCoords(pic,col,row,resource.cols,resource.rows);
        pic.setWidthEx(targetWidth);
        pic.setHeightEx(targetHeight);

    }

    public void updateTextureCoords(Picture pic, int colPosition, int rowPosition, int columns, int rows) {

        rowPosition = rows-rowPosition-1 ;
        float colSize = 1f / (float) columns;
        float rowSize = 1f / (float) rows;

        pic.getMesh().setBuffer(VertexBuffer.Type.TexCoord, 2, new float[]{
                colSize*colPosition,rowSize*rowPosition,
                colSize*colPosition+colSize,rowSize*rowPosition,
                colSize*colPosition+colSize,rowSize*rowPosition+rowSize,
                colSize*colPosition,rowSize*rowPosition+rowSize});


    }


    public void showHideOutline(Spatial sp, boolean show) {
        if(outliner==null) {
            initOutliner();
        }

        if(show) {
            outliner.select(sp);
        } else {
            outliner.deselect(sp);
        }

    }


    private void initOutliner() {
        FilterPostProcessor fpp = new FilterPostProcessor(assetManager);
        viewPort.addProcessor(fpp);
        outliner=new SelectObjectOutliner();
        outliner.initOutliner(SelectObjectOutliner.OUTLINER_TYPE_FILTER, 2, ColorRGBA.Yellow,rootNode,fpp, renderManager, assetManager, cam);

    }

    public void messageHost(int type, Object msg) {
        _appObserver.message(type,msg);
    }

    public void saveFile(String path, String prg) {

        prg = prg.replaceAll("\r", "");
        try {
            Files.write(Paths.get(path), prg.getBytes(StandardCharsets.UTF_8));
        }catch(Exception e) {
            e.printStackTrace();
        }


//        BufferedWriter writer = null;
//        try {
//            writer = new BufferedWriter(new FileWriter(path));
//            writer.write(prg);
//            writer.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

    }


    public void ShowHideMiniMap(MiniMapCommand cmd, String followEntity, VariableDef followEntityVarDef) {

        float height = cmd.heightExpr!=null?cmd.heightVal:64;
        int size = cmd.sizeExpr!=null?cmd.sizeVal:200; // the size of the minimap in pixels.
        Spatial n = null;

        if(followEntity!=null) {
            n = this.getEntitySpatial(followEntity,followEntityVarDef.varType);
        }

        if(cmd.show) {
            miniMapState = new MiniMapState(rootNode, height, size, n);
            stateManager.attach(miniMapState);
        } else {
            if(miniMapState!=null) {
                stateManager.detach(miniMapState);
            }
        }

    }

    public Vector2f getCursorPosition() {
        return inputManager.getCursorPosition().clone();
    }

    public void setBoxMaterial(String boxName, String material) {
        Node g = boxes.get(boxName);
        setGeometryMaterial((Geometry)g.getChild(0),material);
    }

    public void setSphereMaterial(String sphereName, String material) {
        Node g = spheres.get(sphereName);
        setGeometryMaterial((Geometry)g.getChild(0),material);
    }

    public void setCylinderMaterial(String cylinderName, String material) {
        Node g = cylinders.get(cylinderName);
        setGeometryMaterial((Geometry)g.getChild(0),material);
    }

    public void setHollowCylinderMaterial(String hcName, String material) {
        Node g = hollowCylinders.get(hcName);
        setGeometryMaterial((Geometry)g.getChild(0),material);
    }

    public void setQuadMaterial(String quadName, String material) {
        Node g = quads.get(quadName);
        setGeometryMaterial((Geometry)g.getChild(0),material);
    }

    public void setEntityShader(String targetVar, int varType, String shaderName) {
        Spatial target = getEntitySpatial(targetVar, varType);
        if (target == null) {
            handleRuntimeError("Cannot find object '" + targetVar + "'");
            return;
        }

        if (!setSpatialShader(target, shaderName, false)) {
            if (shaderName == null || shaderName.trim().isEmpty()) {
                handleRuntimeError("Cannot reset shader for '" + targetVar + "'");
            } else {
                handleRuntimeError("Cannot find shader resource named: '" + shaderName + "'");
            }
        }
    }

    public void setUIWidgetShader(com.scenemaxeng.common.ui.widget.UIWidgetNode widget, String shaderName) {
        if (widget == null) {
            handleRuntimeError("UI widget not found");
            return;
        }

        if (!setSpatialShader(widget.getShaderTarget(), shaderName, true)) {
            if (shaderName == null || shaderName.trim().isEmpty()) {
                handleRuntimeError("Cannot reset shader for UI widget '" + widget.getName() + "'");
            } else {
                handleRuntimeError("Cannot find shader resource named: '" + shaderName + "'");
            }
        }
    }

    public void setEnvironmentShader(String shaderName) {
        if (shaderName == null || shaderName.trim().isEmpty()) {
            clearEnvironmentShader();
            return;
        }

        if (assetsMapping == null) {
            handleRuntimeError("Cannot find shader resource named: '" + shaderName + "'");
            return;
        }

        ResourceShader shader = assetsMapping.getShadersIndex().get(shaderName.toLowerCase(Locale.ROOT));
        if (shader == null) {
            handleRuntimeError("Cannot find shader resource named: '" + shaderName + "'");
            return;
        }

        Material shaderMaterial = buildShaderMaterial(shader, null, true);
        if (shaderMaterial == null) {
            handleRuntimeError("Cannot load shader resource named: '" + shaderName + "'");
            return;
        }

        Picture overlay = ensureEnvironmentShaderOverlay();
        overlay.setMaterial(shaderMaterial);
        updateEnvironmentShaderOverlaySize();
    }

    private Picture ensureEnvironmentShaderOverlay() {
        if (environmentShaderOverlay == null) {
            environmentShaderOverlay = new Picture("SceneEnvironmentShaderOverlay");
            environmentShaderOverlay.setPosition(0, 0);
            environmentShaderOverlay.setQueueBucket(RenderQueue.Bucket.Gui);
            environmentShaderOverlay.setCullHint(Spatial.CullHint.Never);
        }

        if (environmentShaderOverlay.getParent() != guiNode) {
            environmentShaderOverlay.removeFromParent();
            guiNode.attachChildAt(environmentShaderOverlay, 0);
        }

        return environmentShaderOverlay;
    }

    private void updateEnvironmentShaderOverlaySize() {
        if (environmentShaderOverlay == null || cam == null) {
            return;
        }

        environmentShaderOverlay.setWidth(cam.getWidth());
        environmentShaderOverlay.setHeight(cam.getHeight());
    }

    private void clearEnvironmentShader() {
        if (environmentShaderOverlay != null) {
            environmentShaderOverlay.removeFromParent();
            environmentShaderOverlay = null;
        }
    }

    public List<EntityInstBase> getAllEntities(int entityType, String name, String nameComparator) {
        List<EntityInstBase> retval = new ArrayList<>();
        if(entityType== VariableDef.VAR_TYPE_3D) {
            getAllModelEntities(name, retval);
        } else if(entityType== VariableDef.VAR_TYPE_2D) {
            getAllSpriteEntities(name,retval);
        } else if(entityType== VariableDef.VAR_TYPE_BOX) {
            getAllBoxEntities(name,retval);
        } else if(entityType== VariableDef.VAR_TYPE_SPHERE) {
            getAllSphereEntities(name,retval);
        } else if(entityType== VariableDef.VAR_TYPE_CYLINDER) {
            getAllCylinderEntities(name,retval);
        } else if(entityType== VariableDef.VAR_TYPE_HOLLOW_CYLINDER) {
            getAllHollowCylinderEntities(name,retval);
        } else if(entityType== VariableDef.VAR_TYPE_QUAD) {
            getAllQuadEntities(name,retval);
        } else {
            getAllModelEntities(name,retval);
            getAllSpriteEntities(name,retval);
            getAllBoxEntities(name,retval);
            getAllSphereEntities(name,retval);
            getAllCylinderEntities(name,retval);
            getAllHollowCylinderEntities(name,retval);
            getAllQuadEntities(name,retval);
        }

        return retval;
    }

    private void getAllModelEntities(String name, List<EntityInstBase> retval) {

        if(name==null) {
            retval.addAll(this.modelInstances.values());
            return;
        }

        this.modelInstances.forEach((k, v) -> {
            if(k.contains(name)) {
                retval.add(v);
            }
        });

    }

    private void getAllSpriteEntities(String name, List<EntityInstBase> retval) {

        if(name==null) {
            retval.addAll(this.spriteInstances.values());
            return;
        }

        this.spriteInstances.forEach((k, v) -> {
            if(k.contains(name)) {
                retval.add(v);
            }
        });

    }

    private void getAllBoxEntities(String name,List<EntityInstBase> retval) {

        if(name==null) {
            retval.addAll(this.boxInstances.values());
            return;
        }

        this.boxInstances.forEach((k, v) -> {
            if(k.contains(name)) {
                retval.add(v);
            }
        });

    }

    private void getAllSphereEntities(String name,List<EntityInstBase> retval) {

        if(name==null) {
            retval.addAll(this.sphereInstances.values());
            return;
        }

        this.sphereInstances.forEach((k, v) -> {
            if(k.contains(name)) {
                retval.add(v);
            }
        });

    }

    private void getAllCylinderEntities(String name,List<EntityInstBase> retval) {

        if(name==null) {
            retval.addAll(this.cylinderInstances.values());
            return;
        }

        this.cylinderInstances.forEach((k, v) -> {
            if(k.contains(name)) {
                retval.add(v);
            }
        });

    }

    private void getAllHollowCylinderEntities(String name,List<EntityInstBase> retval) {

        if(name==null) {
            retval.addAll(this.hollowCylinderInstances.values());
            return;
        }

        this.hollowCylinderInstances.forEach((k, v) -> {
            if(k.contains(name)) {
                retval.add(v);
            }
        });

    }

    private void getAllQuadEntities(String name,List<EntityInstBase> retval) {

        if(name==null) {
            retval.addAll(this.quadInstances.values());
            return;
        }

        this.quadInstances.forEach((k, v) -> {
            if(k.contains(name)) {
                retval.add(v);
            }
        });

    }

    private void clearSpatialGeometries(String varName, Spatial root) {
        SceneGraphVisitor visitorRemoveGeo = spatial -> {
            if (spatial instanceof Geometry) {
                Geometry geometry = (Geometry) spatial;
                String geoKey = varName + "~" + geometry.getName();
                geoName2ModelName.remove(geoKey);
                geoName2EntityInst.remove(geoKey);

            }
        };
        visitorRemoveGeo.visit(root);

    }

    public void killModel(String varName) {

        AppModel am = models.get(varName);
        if (am!=null) {

            if (am.skinningControlNode != null) {
                DynamicAnimControl ac = am.skinningControlNode.getControl(DynamicAnimControl.class);
                if (ac !=null) {
                    ac.setPhysicsSpace(null);
                }
            }
            removeOutlineFilter(am.model);
            am.model.removeFromParent();
            removeCollisionControlsFromPhysics(varName);
            collisionControlsCache.remove(varName); // this shouldn't be in removeCollisionControlsFromPhysics()
            models.remove(varName);
            modelInstances.remove(am.entityInst.entityKey);
            clearSpatialGeometries(varName, am.model);

        }

    }

    public void killSprite(String varName) {
        SpriteEmitter se = sprites.get(varName);
        if(se!=null) {
            se.getSpatial().removeFromParent();
            removeCollisionControlsFromPhysics(varName);
            sprites.remove(varName);
            spriteInstances.remove(se.entityInst.entityKey);
        }
    }

    public void killBox(String varName) {

        Node g = boxes.get(varName);
        if(g!=null) {
            g.removeFromParent();
            boxes.remove(varName);
            EntityInstBase inst = geoName2EntityInst.get(varName);
            boxInstances.remove(inst.entityKey);
            removeCollisionControlsFromPhysics(varName);
            collisionControlsCache.remove(varName);
            clearSpatialGeometries(varName, g);

        }
    }

    public void killSphere(String varName) {

        Node g = spheres.get(varName);
        if(g!=null) {
            g.removeFromParent();
            spheres.remove(varName);
            EntityInstBase inst = geoName2EntityInst.get(varName);
            sphereInstances.remove(inst.entityKey);
            removeCollisionControlsFromPhysics(varName);
            collisionControlsCache.remove(varName);
            clearSpatialGeometries(varName, g);

        }
    }

    public void applyModelVelocity(String name, Double velocity) {
        AppModel am = models.get(name);
        if(am==null) {
            return;
        }

        if(am.physicalControl instanceof RigidBodyControl) {
            RigidBodyControl ctl = (RigidBodyControl)am.physicalControl;
            Vector3f forward = am.model.getLocalRotation().mult(Vector3f.UNIT_Z);
            ctl.setLinearVelocity(forward.mult(velocity.floatValue()));
        }


    }

    public AppModel getAppModel(String id) {
        return models.get(id);

    }

    public void applyAnimationOptions(String target, AnimateOptionsCommand cmd) {

        AppModel am = models.get(target);
        if(am==null) {
            return;
        }

        am.getAnimComposer().setGlobalSpeed(cmd.speedVal.floatValue());

    }

    public void resetAnimationOptions(String target) {

        AppModel am = models.get(target);
        if(am==null) {
            return;
        }

        am.getAnimComposer().setGlobalSpeed(1.0f);

    }

    public HashMap<String, Integer> getKeyMapping() {
        return keyMapping;
    }

    public void carReset(RunTimeVarDef entity) {
        AppModel am = models.get(entity.varName);
        if(am==null) {
            return;
        }

        if(am.physicalControl instanceof SceneMax3DGenericVehicle) {
            SceneMax3DGenericVehicle v = (SceneMax3DGenericVehicle) am.physicalControl;
            v.reset();
        }

    }


    public boolean physicalRotateResetNode(Node n, float x, float y, float z) {

        if(n==null) {
            return true;
        }

        RigidBodyControl ctl = n.getControl(RigidBodyControl.class);
        if(ctl!=null) {
            Quaternion rot = ctl.getPhysicsRotation().fromAngles(x,y,z);
            ctl.setPhysicsRotation(rot);
            return true;
        } else {
            return false;
        }
    }

    public void rotateResetCamera(float x, float y, float z) {
        this.cam.setRotation(new Quaternion().fromAngles(x * FastMath.DEG_TO_RAD, y * FastMath.DEG_TO_RAD, z * FastMath.DEG_TO_RAD));
    }

    public void rotateResetSphere(String targetVar, float x, float y, float z) {
        Node g = spheres.get(targetVar);
        if(!physicalRotateResetNode(g,x,y,z)) {
            g.setLocalRotation(new Quaternion().fromAngles(x * FastMath.DEG_TO_RAD, y * FastMath.DEG_TO_RAD, z * FastMath.DEG_TO_RAD));
        }
    }

    public void rotateResetBox(String targetVar, float x, float y, float z) {
        Node g = boxes.get(targetVar);
        if(!physicalRotateResetNode(g,x,y,z)) {
            g.setLocalRotation(new Quaternion().fromAngles(x * FastMath.DEG_TO_RAD, y * FastMath.DEG_TO_RAD, z * FastMath.DEG_TO_RAD));
        }

    }

    public void rotateResetModel(String targetVar, float x, float y, float z) {

        AppModel am = models.get(targetVar);

        if(am!=null) {
            if (am.physicalControl != null) {
                rotateResetPhysicalModel(am, x, y, z);
                return;
            }

            am.model.setLocalRotation(new Quaternion().fromAngles(x * FastMath.DEG_TO_RAD, y * FastMath.DEG_TO_RAD, z * FastMath.DEG_TO_RAD));
        }

    }

    private void rotateResetPhysicalModel(AppModel am, float x, float y, float z) {

        if(am.isStatic) {
            List<Object> ctls = collisionControlsCache.get(am.model.getName());
            if(ctls!=null) {
                int cnt = ctls.size();// am.model.getNumControls();
                for (int i = 0; i < cnt; ++i) {
                    RigidBodyControl rbCtl = (RigidBodyControl) ctls.get(i);// am.model.getControl(i);
                    Quaternion rot = rbCtl.getPhysicsRotation().fromAngles(x, y, z);
                    rbCtl.setPhysicsRotation(rot);

                }
            }
        } else if(am.physicalControl instanceof CharacterControl) {
            CharacterControl ctl = (CharacterControl)am.physicalControl;
            ctl.setViewDirection(new Vector3f(x,y,z));

        } else if(am.physicalControl instanceof RigidBodyControl) {
            RigidBodyControl ctl  = (RigidBodyControl)am.physicalControl;
            Quaternion rot = ctl.getPhysicsRotation().fromAngles(x,y,z);
            ctl.setPhysicsRotation(rot);
        } else if (am.physicalControl instanceof SceneMax3DGenericVehicle) {
            SceneMax3DGenericVehicle ctl  = (SceneMax3DGenericVehicle)am.physicalControl;
            Quaternion rot = ctl.getVehicleControl().getPhysicsRotation().fromAngles(x,y,z);
            ctl.getVehicleControl().setPhysicsRotation(rot);
        }


    }

    public void setProjectName(String projectName) {
        this.logger.info("setProjectName: "+projectName);
        this.projectName=projectName;
    }


    public SpriteInst createSpriteInst(SceneMaxScope scope, CreateSpriteCommand cmd) {
        SpriteInst inst = new SpriteInst(cmd.spriteDef, cmd.varDef, scope);
        String key = cmd.varDef.varName + "_" + ++entityInstCounter;
        inst.entityKey = key;
        spriteInstances.put(key, inst);

        VariableDef var = cmd.varDef;
        if (var.entityPos != null) {
            inst.entityForPos = findVarRuntime(prg, scope, var.entityPos.entityName);
        }

        if (var.entityRot != null) {
            inst.entityForRot = findVarRuntime(prg, scope, var.entityRot);
        }

        return inst;
    }

    public EffekseerInst createEffekseerInst(ProgramDef prg, VariableDef var, SceneMaxScope scope) {
        String effectResourceName = var.resName != null
                ? var.resName
                : new ActionLogicalExpressionVm(var.resNameExpr, scope).evaluate().toString();
        String assetId = effectResourceName;
        if (effectResourceName.toLowerCase().startsWith("effects.effekseer.")) {
            assetId = effectResourceName.substring("effects.effekseer.".length());
        }
        String effectPath = resolveEffekseerEffectPath(assetId);
        if (effectPath == null) {
            handleRuntimeError("Effekseer effect '" + assetId + "' was not found under resources/effects");
            return null;
        }

        EffekseerInst inst = new EffekseerInst(var, scope, effectResourceName, assetId, effectPath);
        String key = var.varName + "_" + ++entityInstCounter;
        inst.entityKey = key;
        effekseerInstances.put(key, inst);
        effekseerEffects.put(inst.node.getName(), inst);
        geoName2EntityInst.put(inst.node.getName(), inst);

        if (var.entityPos != null) {
            inst.entityForPos = findVarRuntime(prg, scope, var.entityPos.entityName);
        }
        if (var.entityRot != null) {
            inst.entityForRot = findVarRuntime(prg, scope, var.entityRot);
        }

        applyEffekseerInitialTransform(inst);
        if (var.visible) {
            rootNode.attachChild(inst.node);
        }
        return inst;
    }

    private void applyEffekseerInitialTransform(EffekseerInst inst) {
        if (inst == null) {
            return;
        }

        if (inst.entityForRot != null) {
            Spatial sp = getEntitySpatial(inst.entityForRot.varName, inst.entityForRot.varDef.varType);
            if (sp != null) {
                inst.node.setLocalRotation(sp.getLocalRotation());
            }
        } else if (inst.varDef.useVerbalTurn) {
            float rx = inst.varDef.rxExpr != null ? Float.parseFloat(new ActionLogicalExpressionVm(inst.varDef.rxExpr, inst.scope).evaluate().toString()) * inst.varDef.rotDir : 0f;
            float ry = inst.varDef.ryExpr != null ? Float.parseFloat(new ActionLogicalExpressionVm(inst.varDef.ryExpr, inst.scope).evaluate().toString()) * inst.varDef.rotDir : 0f;
            float rz = inst.varDef.rzExpr != null ? Float.parseFloat(new ActionLogicalExpressionVm(inst.varDef.rzExpr, inst.scope).evaluate().toString()) * inst.varDef.rotDir : 0f;
            inst.node.setLocalRotation(new Quaternion().fromAngles(rx * FastMath.DEG_TO_RAD, ry * FastMath.DEG_TO_RAD, rz * FastMath.DEG_TO_RAD));
        } else if (inst.varDef.rxExpr != null) {
            float rx = Float.parseFloat(new ActionLogicalExpressionVm(inst.varDef.rxExpr, inst.scope).evaluate().toString());
            float ry = Float.parseFloat(new ActionLogicalExpressionVm(inst.varDef.ryExpr, inst.scope).evaluate().toString());
            float rz = Float.parseFloat(new ActionLogicalExpressionVm(inst.varDef.rzExpr, inst.scope).evaluate().toString());
            inst.node.setLocalRotation(new Quaternion().fromAngles(rx * FastMath.DEG_TO_RAD, ry * FastMath.DEG_TO_RAD, rz * FastMath.DEG_TO_RAD));
        }

        if (inst.entityForPos != null) {
            Spatial sp = getEntitySpatial(inst.entityForPos.varName, inst.entityForPos.varDef.varType);
            if (sp != null) {
                inst.node.setLocalTranslation(sp.getLocalTranslation());
            }
        } else if (inst.varDef.xExpr != null) {
            float x = Float.parseFloat(new ActionLogicalExpressionVm(inst.varDef.xExpr, inst.scope).evaluate().toString());
            float y = Float.parseFloat(new ActionLogicalExpressionVm(inst.varDef.yExpr, inst.scope).evaluate().toString());
            float z = Float.parseFloat(new ActionLogicalExpressionVm(inst.varDef.zExpr, inst.scope).evaluate().toString());
            inst.node.setLocalTranslation(x, y, z);
        }
    }

    private String resolveEffekseerEffectPath(String assetId) {
        File resourcesFolder = resolveRuntimeResourcesFolder();
        if (resourcesFolder == null) {
            return null;
        }
        File assetFolder = new File(resourcesFolder, "effects/" + assetId);
        if (!assetFolder.isDirectory()) {
            return null;
        }
        File[] preferred = assetFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".efkefc") || name.toLowerCase().endsWith(".efk"));
        if (preferred != null && preferred.length > 0) {
            return preferred[0].getAbsolutePath();
        }
        return null;
    }

    private File resolveRuntimeResourcesFolder() {
        try {
            if (projectName != null && !projectName.isBlank()) {
                return new File("./projects/" + projectName + "/resources").getCanonicalFile();
            }
            if (workingFolder != null && !workingFolder.isBlank()) {
                File folder = new File(workingFolder);
                if (folder.isFile()) {
                    folder = folder.getParentFile();
                }
                File current = folder.getCanonicalFile();
                while (current != null) {
                    File resources = new File(current, "resources");
                    if (resources.isDirectory()) {
                        return resources;
                    }
                    current = current.getParentFile();
                }
            }
            File local = new File("./resources").getCanonicalFile();
            return local.isDirectory() ? local : null;
        } catch (Exception ex) {
            return null;
        }
    }

    public void loadEffekseerEffect(EffekseerInst inst) {
        if (inst == null || inst.loaded) {
            return;
        }
        if (!EffekseerNativeBridge.isAvailable()) {
            handleRuntimeError("Effekseer native runtime is unavailable: " + EffekseerNativeBridge.getLoadMessage());
            return;
        }
        long context = EffekseerNativeBridge.createPreviewContext(8192);
        if (context == 0L) {
            handleRuntimeError("Failed to create Effekseer runtime context for '" + inst.assetId + "'");
            return;
        }
        if (!EffekseerNativeBridge.loadEffect(context, inst.effectPath, new File(inst.effectPath).getParent())) {
            EffekseerNativeBridge.destroyPreviewContext(context);
            handleRuntimeError("Failed to load Effekseer effect '" + inst.assetId + "'");
            return;
        }
        EffekseerNativeBridge.setLooping(context, false);
        EffekseerNativeBridge.setCompositeEnabled(context, true);
        inst.nativeContextHandle = context;
        inst.loaded = true;
    }

    public void playEffekseerEffect(String targetVar, Double x, Double y, Double z, RunTimeVarDef posEntity, float playbackSpeed, float[] dynamicInputs) {
        EffekseerInst inst = effekseerEffects.get(targetVar);
        if (inst == null) {
            return;
        }
        if (!inst.loaded) {
            loadEffekseerEffect(inst);
        }
        if (!inst.loaded) {
            return;
        }

        if (x != null) {
            inst.node.setLocalTranslation(x.floatValue(), y.floatValue(), z.floatValue());
        } else if (posEntity != null) {
            Spatial sp = getEntitySpatial(posEntity.varName, posEntity.varDef.varType);
            if (sp != null) {
                inst.node.setLocalTranslation(sp.getWorldTranslation());
            }
        }

        inst.playbackSpeed = playbackSpeed;
        System.arraycopy(dynamicInputs, 0, inst.dynamicInputs, 0, inst.dynamicInputs.length);
        inst.pendingPlay = true;
        inst.playing = false;
        if (inst.node.getParent() == null && inst.visible) {
            rootNode.attachChild(inst.node);
        }
    }

    public void renderEffekseerEffects(float tpf, ViewPort viewPort) {
        if (!EffekseerNativeBridge.isAvailable() || effekseerEffects.isEmpty() || viewPort == null) {
            return;
        }

        Camera activeCamera = viewPort.getCamera();
        Matrix4f view = activeCamera.getViewMatrix().clone();
        Matrix4f projection = activeCamera.getProjectionMatrix().clone();
        float[] cameraPosition = new float[] {activeCamera.getLocation().x, activeCamera.getLocation().y, activeCamera.getLocation().z};

        for (EffekseerInst inst : effekseerEffects.values()) {
            if (inst == null || !inst.visible || inst.node.getParent() == null || !inst.loaded) {
                continue;
            }

            EffekseerNativeBridge.setCamera(inst.nativeContextHandle, view, projection, cameraPosition);
            Vector3f worldPos = inst.node.getWorldTranslation();
            Matrix4f worldMatrix = new Matrix4f();
            inst.node.getWorldTransform().toTransformMatrix(worldMatrix);
            EffekseerNativeBridge.setEffectLocation(inst.nativeContextHandle, worldPos.x, worldPos.y, worldPos.z);
            EffekseerNativeBridge.setEffectTransform(inst.nativeContextHandle, worldMatrix);
            if (inst.pendingPlay) {
                EffekseerNativeBridge.playEffect(inst.nativeContextHandle);
                EffekseerNativeBridge.setEffectLocation(inst.nativeContextHandle, worldPos.x, worldPos.y, worldPos.z);
                EffekseerNativeBridge.setEffectTransform(inst.nativeContextHandle, worldMatrix);
                EffekseerNativeBridge.setPlaybackSpeed(inst.nativeContextHandle, inst.playbackSpeed);
                for (int i = 0; i < inst.dynamicInputs.length; i++) {
                    EffekseerNativeBridge.setDynamicInput(inst.nativeContextHandle, i, inst.dynamicInputs[i]);
                }
                inst.pendingPlay = false;
                inst.playing = true;
            }
            EffekseerNativeBridge.update(inst.nativeContextHandle, tpf);
            if (inst.playing && !EffekseerNativeBridge.isEffectPlaying(inst.nativeContextHandle)) {
                inst.playing = false;
                inst.node.removeFromParent();
                continue;
            }
            EffekseerNativeBridge.render(inst.nativeContextHandle, activeCamera.getWidth(), activeCamera.getHeight());
        }
    }

    public void posEffekseer(String targetVar, Double valX, Double valY, Double valZ, RunTimeVarDef varForPos, Vector3f calculatedPosition) {
        EffekseerInst inst = effekseerEffects.get(targetVar);
        if (inst == null) {
            return;
        }
        if (calculatedPosition != null) {
            inst.node.setLocalTranslation(calculatedPosition);
        } else if (varForPos != null) {
            Spatial sp = getEntitySpatial(varForPos.varName, varForPos.varDef.varType);
            if (sp != null) {
                inst.node.setLocalTranslation(sp.getWorldTranslation());
            }
        } else {
            inst.node.setLocalTranslation(valX.floatValue(), valY.floatValue(), valZ.floatValue());
        }
    }

    public void showHideEffekseer(String targetVar, ActionCommandShowHide show) {
        EffekseerInst inst = effekseerEffects.get(targetVar);
        if (inst == null) {
            return;
        }
        inst.visible = show.show;
        if (!show.show) {
            inst.playing = false;
            inst.node.removeFromParent();
        } else if (inst.node.getParent() == null) {
            rootNode.attachChild(inst.node);
        }
    }

    public void killEffekseerEffect(String varName) {
        EffekseerInst inst = effekseerEffects.get(varName);
        if (inst == null) {
            return;
        }
        if (inst.nativeContextHandle != 0L) {
            EffekseerNativeBridge.stopEffect(inst.nativeContextHandle);
            EffekseerNativeBridge.unloadEffect(inst.nativeContextHandle);
            EffekseerNativeBridge.destroyPreviewContext(inst.nativeContextHandle);
            inst.nativeContextHandle = 0L;
        }
        inst.playing = false;
        inst.node.removeFromParent();
        effekseerEffects.remove(varName);
        effekseerInstances.remove(inst.entityKey);
        geoName2EntityInst.remove(varName);
    }

    public void detachEffekseerFromParent(String targetVar) {
        EffekseerInst inst = effekseerEffects.get(targetVar);
        if (inst == null) {
            return;
        }
        Vector3f worldPos = inst.node.getWorldTranslation().clone();
        Quaternion worldRot = inst.node.getWorldRotation().clone();
        inst.node.removeFromParent();
        rootNode.attachChild(inst.node);
        inst.node.setLocalTranslation(worldPos);
        inst.node.setLocalRotation(worldRot);
    }

    public void ignoreJoints(String targetVar, RunTimeVarDef ignoreVarDef) {

        Node model;
        AppModel am = models.get(targetVar);
        if (am == null) {
            return;
        }

        model = am.model;
        if (model == null) {
            return;
        }

        if (am.physicalControl instanceof CharacterControl) {
            CharacterControl ctl = (CharacterControl) am.physicalControl;
            DynamicAnimControl dctl = this.getDynamicAnimControl(ignoreVarDef);
            if(dctl!=null) {

                int normalGroup = PhysicsCollisionObject.COLLISION_GROUP_01;
                int bccGroup = PhysicsCollisionObject.COLLISION_GROUP_02;
                int dacGroup = PhysicsCollisionObject.COLLISION_GROUP_03;
                ctl.getCharacter().setCollisionGroup(bccGroup);
                ctl.getCharacter().setCollideWithGroups(normalGroup | bccGroup);
                PhysicsRigidBody[] rbs = dctl.listRigidBodies();
                for (int i = 0; i < rbs.length; ++i) {
                    rbs[i].setCollisionGroup(dacGroup);
                }
            }

        }

    }

    private DynamicAnimControl getDynamicAnimControl(RunTimeVarDef vardef) {

        Node model;
        AppModel am = models.get(vardef.varName);
        if (am == null) {
            return null;
        }

        model = am.model;
        if (model == null) {
            return null;
        }

        DynamicAnimControl dctl = am.skinningControlNode.getControl(DynamicAnimControl.class);

        return dctl;
    }

    public void prepareToSwitchState(String code, String level) {
        this.currentLevel = level;
        this.switchStateCode = code;
    }

    public void switchState() {
        this.turnOffCameraStates();
        this.chaseCam = null; // force re-creating it next time it will be required
        this.detachState(SkidMarksState.class);
        this.detachState(BasicVehicleInputState.class);
        this.detachState(MiniMapState.class);
        this.miniMapState = null;
        this.detachState(ChunkManager.class);

        this.clearScene();
        this.mainScope.reset();
        bulletAppState.getPhysicsSpace().removeCollisionListener(this.collisionListener);
        collisionListener.setEnabled(false);
        collisionListener = null;
        this.detachState(BulletAppState.class);
        bulletAppState = null;
        this.initBulletAppState();
        this.getStateManager().attach(new ChunkManager());
        this.eventHandlersCount = 0;

        this.run(this.switchStateCode);
        this.switchStateCode = null;
    }

    private <T extends AppState> void detachState(Class<T> stateClass) {
        AppState state = this.getStateManager().getState(stateClass);
        if (state != null) {
            this.getStateManager().detach(state);
        }
    }

}
