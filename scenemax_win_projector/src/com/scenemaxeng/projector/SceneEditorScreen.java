package com.scenemaxeng.projector;

import com.scenemaxeng.common.types.*;
import com.jme3.app.Application;
import com.jme3.app.state.AbstractAppState;
import com.jme3.app.state.AppStateManager;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.FlyByCamera;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.math.FastMath;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.SceneGraphVisitor;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Command;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.Label;
import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.NiftyEventSubscriber;
import de.lessvoid.nifty.controls.ListBox;
import de.lessvoid.nifty.controls.ListBoxSelectionChangedEvent;
import de.lessvoid.nifty.controls.NiftyInputControl;
import de.lessvoid.nifty.controls.TextField;
import de.lessvoid.nifty.controls.textfield.TextFieldControl;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.input.NiftyInputEvent;
import de.lessvoid.nifty.input.NiftyStandardInputEvent;
import de.lessvoid.nifty.screen.Screen;
import de.lessvoid.nifty.screen.ScreenController;

import javax.annotation.Nonnull;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class SceneEditorScreen extends AbstractAppState implements ScreenController {

    Screen screen;
    SceneMaxApp app = null;
    private Nifty nifty;
    AssetsMapping assetsMapping;
    int entIndex = 0;
    private String selectedObject="";

    private BitmapText crossHairs;
    private Container modifyEntityWindow;
    private Label lblModifierWindowHeader;
    private RunTimeVarDef selectedObjectVar;

    @Override
    public void initialize(AppStateManager stateManager, Application app) {
        super.initialize(stateManager, app);

//        DropDown cboResourceTypes = screen.findNiftyControl("dropDownResourceTypes", DropDown.class);
//        cboResourceTypes.selectItemByIndex(0);

        this.app = (SceneMaxApp)app;
        assetsMapping = this.app.getAssetsMapping();

        initCrossHairs();
        initModifiersUI();
        initMousePicking();
        enableFlyCamera();
        showModelsList("");
        showEntitiesInScene();
    }

    protected void enableFlyCamera() {
        this.app.setChaseCameraOff();
        FlyByCamera flyCam = this.app.getFlyByCamera();
        flyCam.setEnabled(true);
        flyCam.setDragToRotate(true);
        flyCam.setMoveSpeed(10);
    }

    private final ActionListener actionListener = new ActionListener() {
        @Override
        public void onAction(String name, boolean keyPressed, float tpf) {
            rayCastCheck();
        }

    };

     private void initMousePicking() {

        app.getInputManager().addMapping("mouse_pick", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        app.getInputManager().addListener(actionListener, "mouse_pick");

    }

    private void clearMousePicking() {

        app.getInputManager().deleteMapping("mouse_pick");
        app.getInputManager().removeListener(actionListener);

    }

    private void initModifiersUI() {

         if(modifyEntityWindow==null) {
             modifyEntityWindow = new Container();
             lblModifierWindowHeader = new Label("Selected Model:");
             modifyEntityWindow.addChild(lblModifierWindowHeader, 0, 0);

             Container buttonsCont = new Container();
             Button b1 = buttonsCont.addChild(new Button("X-"), 1, 0);
             b1.addClickCommands(new Command<Button>() {
                 @Override
                 public void execute(Button source) {
                     String code = selectedObject + ".move (x-1) in 0.1 second";
                     app.runPartialCode(code, null, false);
                     updateSelectedEntity("_position",1);
                 }
             });

             Button b2 = buttonsCont.addChild(new Button("X+"), 1, 1);
             b2.addClickCommands(new Command<Button>() {
                 @Override
                 public void execute(Button source) {
                     String code = selectedObject + ".move (x+1) in 0.1 second";
                     app.runPartialCode(code, null, false);
                     updateSelectedEntity("_position",1);
                 }
             });

             Button b3 = buttonsCont.addChild(new Button("Y-"), 2, 0);
             b3.addClickCommands(new Command<Button>() {
                 @Override
                 public void execute(Button source) {
                     String code = selectedObject + ".move (y-1) in 0.1 second";
                     app.runPartialCode(code, null, false);
                     updateSelectedEntity("_position",1);
                 }
             });

             Button b4 = buttonsCont.addChild(new Button("Y+"), 2, 1);
             b4.addClickCommands(new Command<Button>() {
                 @Override
                 public void execute(Button source) {
                     String code = selectedObject + ".move (y+1) in 0.1 second";
                     app.runPartialCode(code, null, false);
                     updateSelectedEntity("_position",1);
                 }
             });

             Button b5 = buttonsCont.addChild(new Button("Z-"), 3, 0);
             b5.addClickCommands(new Command<Button>() {
                 @Override
                 public void execute(Button source) {
                     String code = selectedObject + ".move (z-1) in 0.1 second";
                     app.runPartialCode(code, null, false);
                     updateSelectedEntity("_position",1);
                 }
             });

             Button b6 = buttonsCont.addChild(new Button("Z+"), 3, 1);
             b6.addClickCommands(new Command<Button>() {
                 @Override
                 public void execute(Button source) {
                     String code = selectedObject + ".move (z+1) in 0.1 second";
                     app.runPartialCode(code, null, false);
                     updateSelectedEntity("_position",1);
                 }
             });

             Button b7 = buttonsCont.addChild(new Button("<"), 4, 0);
             b7.addClickCommands(new Command<Button>() {
                 @Override
                 public void execute(Button source) {
                     String code = selectedObject + ".rotate (y-10) in 0.1 seconds";
                     app.runPartialCode(code, null, false);
                     updateSelectedEntity("_rotation",1);
                 }
             });


             Button b8 = buttonsCont.addChild(new Button(">"), 4, 1);
             b8.addClickCommands(new Command<Button>() {
                 @Override
                 public void execute(Button source) {
                     String code = selectedObject + ".rotate (y+10) in 0.1 seconds";
                     app.runPartialCode(code, null, false);
                     updateSelectedEntity("_rotation",1);
                 }
             });

             Button b9 = buttonsCont.addChild(new Button("Scale +"), 5, 0);
             b9.addClickCommands(new Command<Button>() {
                 @Override
                 public void execute(Button source) {
                     RunTimeVarDef vd = app.findVarRuntime(null, null, selectedObject);
                     Spatial sp = app.getEntitySpatial(vd.varName);
                     float scale=sp.getLocalScale().x;
                     scale+=0.1f;
                     sp.setLocalScale(scale);
                     updateSelectedEntity("_scale",1);
                 }
             });


             Button b10 = buttonsCont.addChild(new Button("Scale -"), 5, 1);
             b10.addClickCommands(new Command<Button>() {
                 @Override
                 public void execute(Button source) {
                     RunTimeVarDef vd = app.findVarRuntime(null, null, selectedObject);
                     Spatial sp = app.getEntitySpatial(vd.varName);
                     float scale=sp.getLocalScale().x;
                     scale-=0.1f;
                     sp.setLocalScale(scale);
                     updateSelectedEntity("_scale",1);
                 }
             });

             Button b11 = buttonsCont.addChild(new Button("View: Up"), 6, 0);
             b11.addClickCommands(new Command<Button>() {
                 @Override
                 public void execute(Button source) {
                     Vector3f pos = app.getCamera().getLocation();
                     if (selectedObject != null) {
                         RunTimeVarDef vd = app.findVarRuntime(null, null, selectedObject);
                         Spatial sp = app.getEntitySpatial(vd.varName);
                         pos = sp.getWorldTranslation();
                     }
                     app.getCamera().setLocation(new Vector3f(pos.x, pos.y + 20, pos.z));
                     app.getCamera().lookAt(new Vector3f(pos.x, -1, pos.z), Vector3f.UNIT_Y);

                 }
             });

             Button b12 = buttonsCont.addChild(new Button("View: Side"), 6, 1);
             b12.addClickCommands(new Command<Button>() {
                 @Override
                 public void execute(Button source) {
                     Vector3f pos = app.getCamera().getLocation();
                     if (selectedObject != null) {
                         RunTimeVarDef vd = app.findVarRuntime(null, null, selectedObject);
                         Spatial sp = app.getEntitySpatial(vd.varName);
                         pos = sp.getWorldTranslation();
                     }
                     app.getCamera().setLocation(new Vector3f(pos.x, pos.y + 2, pos.z + 10));
                     app.getCamera().lookAt(new Vector3f(pos.x, pos.y + 2, pos.z - 1), Vector3f.UNIT_Y);
                 }
             });

             Button b13 = buttonsCont.addChild(new Button("Cam: Fly"), 7, 0);
             b13.addClickCommands(new Command<Button>() {
                 @Override
                 public void execute(Button source) {
                     enableFlyCamera();
                 }

             });

             Button b14 = buttonsCont.addChild(new Button("Cam: Chase"), 7, 1);
             b14.addClickCommands(new Command<Button>() {
                 @Override
                 public void execute(Button source) {

                     FlyByCamera flyCam = SceneEditorScreen.this.app.getFlyByCamera();
                     flyCam.setEnabled(false);

                     if(selectedObject!=null && selectedObject.length()>0) {
                         String code = "camera.chase " + selectedObject + ": trailing = false";
                         app.runPartialCode(code, null, false);
                     }

                 }

             });


             modifyEntityWindow.addChild(buttonsCont);
             app.getGuiNode().attachChild(modifyEntityWindow);
         }

        float posX = app.getContext().getSettings().getWidth() - modifyEntityWindow.getSize().getX();
        float posY = app.getContext().getSettings().getHeight() - 5;
        modifyEntityWindow.setLocalTranslation(posX,posY,0);
    }

    private void updateSelectedEntity(String key, int val) {
        RunTimeVarDef vd = app.findVarRuntime(null, null, selectedObject);
        Spatial sp = app.getEntitySpatial(vd.varName);
        sp.setUserData(key,val);
        displaySelectedEntityProps(sp);
    }

    private void displaySelectedEntityProps(Spatial sp) {
        if (sp == null) {
            return;
        }

        TimerTask task = new TimerTask() {
            public void run() {
                SceneEditorScreen.this.app.enqueue(() -> {
                    de.lessvoid.nifty.controls.TextField txtSelectedEntityProps = screen.findNiftyControl("txtSelectedEntityProps", TextField.class);
                    Vector3f pos = sp.getWorldTranslation();
                    String data = selectedObject + ": pos (" + pos.x + ", "+pos.y +", "+pos.z+ ")";
                    txtSelectedEntityProps.setText(data);
                });
            }
        };
        Timer timer = new Timer("Timer");

        long delay = 500;
        timer.schedule(task, delay);
    }

    @Override
    public void update(float tpf) {

//        if(selectedObjectVar==null && selectedObject!=null && selectedObject.length()>0) {
//            selectedObjectVar = app.findVarRuntime(null, null, selectedObject);
//            Node sp = (Node)this.app.getEntitySpatial(selectedObjectVar.varName);
//            if(sp!=null) {
//                sp.attachChild(modifyEntityWindow);
//                modifyEntityWindow.setLocalTranslation(2, 5, 1);
//            } else {
//                selectedObjectVar=null;
//            }
//        }

    }

    @Override
    public void cleanup() {
        super.cleanup();
        //TODO: clean up what you initialized in the initialize method,

        app.getGuiNode().detachChild(crossHairs);
        app.getGuiNode().detachChild(modifyEntityWindow);

        clearMousePicking();
        hideSelectedObjectOutline();

    }

    protected void initCrossHairs() {
        //guiNode.detachAllChildren();
        BitmapFont guiFont = app.getAssetManager().loadFont("Interface/Fonts/Default.fnt");
        crossHairs = new BitmapText(guiFont, false);
        crossHairs.setSize(guiFont.getCharSet().getRenderedSize() * 2);
        crossHairs.setText("+");        // fake crosshairs :)
        crossHairs.setLocalTranslation( // center
                (float) app.getContext().getSettings().getWidth() / 2 - guiFont.getCharSet().getRenderedSize() / 3 * 2,
                (float) app.getContext().getSettings().getHeight() / 2 + crossHairs.getLineHeight() / 2, 0);
        app.getGuiNode().attachChild(crossHairs);
    }


    @Override
    public void bind(@Nonnull Nifty nifty, @Nonnull Screen screen) {

        this.screen=screen;
        this.nifty=nifty;

    }

//    @NiftyEventSubscriber(pattern="dropDownResourceTypes")
//    public void onAllCheckBoxChanged(final String id, final DropDownSelectionChangedEvent event) {
//        DropDown cboResourceTypes = screen.findNiftyControl("dropDownResourceTypes", DropDown.class);
//        String item = cboResourceTypes.getSelection().toString();
//
//        if(item.equals("3D Models")) {
//            showModelsList();
//        }
//    }

    @NiftyEventSubscriber(id="lstEntitiesInScene")
    public void onListBoxSelectionChanged(final String id, final ListBoxSelectionChangedEvent changed) {
        ListBox lstProgram = screen.findNiftyControl("lstEntitiesInScene", ListBox.class);

        String entity = lstProgram.getFocusItem().toString();
        highlightSelectedEntity(entity);

    }

    private void showEntitiesInScene() {
        ListBox lst = screen.findNiftyControl("lstEntitiesInScene", ListBox.class);
        lst.clear();

        this.app.getModels().forEach((name, appModel) -> {
            lst.addItem(appModel.entityInst.varDef.varName);
        });

        lst.sortAllItems();
        lst.refresh();
    }

    private void showModelsList(String filter) {

        ListBox lstEntities = screen.findNiftyControl("lstEntities", ListBox.class);
        lstEntities.clear();

        HashMap<String, ResourceSetup> models = assetsMapping.get3DModelsIndex();
        for(ResourceSetup model:models.values()) {
            if(model.isVehicle) {
                //lstEntities.addItem("Vehicle: "+model.name);
            } else {
                if(filter.length()==0 || model.name.contains(filter)) {
                    lstEntities.addItem(model.name);
                }
            }
        }

        lstEntities.sortAllItems();

    }

    @NiftyEventSubscriber(id="txtSearch")
    public void onInputEvent(final String id, final NiftyInputEvent event) {

        Element niftyElement = screen.findElementById("txtSearch");
        NiftyInputControl ic = niftyElement.getAttachedInputControl();
        TextFieldControl ctl = (TextFieldControl)ic.getController();
        String filter=ctl.getText();
        NiftyStandardInputEvent e = (NiftyStandardInputEvent) event;
        if(event.toString().toLowerCase().equals("backspace")) {
            if(filter.length()>0) {
                filter=filter.substring(0,filter.length()-1);
            }
        } else {
            filter += e.getCharacter();
        }
        showModelsList(filter.trim());
    }

    @Override
    public void onStartScreen() {

//        Element niftyElement = screen.findElementById("txtSearch");
//        NiftyInputControl ic = niftyElement.getAttachedInputControl();
//
//        ic.addInputHandler(new KeyInputHandler() {
//            @Override
//            public boolean keyEvent(@Nonnull NiftyInputEvent niftyInputEvent) {
//
//                Element niftyElement = screen.findElementById("txtSearch");
//                NiftyInputControl ic = niftyElement.getAttachedInputControl();
//                TextFieldControl ctl = (TextFieldControl)ic.getController();
//                String filter = ctl.getText().trim();
//
//                showModelsList(filter);
//                return false;
//            }
//        });

    }

    public void onSaveClick(String arg) {
        updateProgram();
    }

    public void hideSelectedObjectOutline() {

        if(selectedObject.length()>0) {
            String code = selectedObject+".hide outline";
            app.runPartialCode(code,null,false);
        }

    }

    public void onAddEntityClick(String arg) {

        hideSelectedObjectOutline();

        ListBox lstEntities = screen.findNiftyControl("lstEntities", ListBox.class);
        String ent = lstEntities.getFocusItem().toString();
        String name = ent.replace("Model: ","").replace("Vehicle: ","");
        Vector3f pos = app.getCamera().getLocation().clone();

        pos=pos.add(app.getCamera().getDirection().mult(10));

        entIndex++;
        DecimalFormat nf = new DecimalFormat("0.00");
        String entity = "e"+entIndex;
        String code = entity+" => "+name + ": pos ("+nf.format(pos.x)+","+nf.format(pos.y)+","+nf.format(pos.z)+")\n"+
                entity+".data.added=1\n" +
                entity+".data.added_res_name=\""+name+"\"\n";
        //codeLines.put(selectedObject,name);
        //code+=selectedObject+".show outline";

        app.runPartialCode(code,null,false);

        setModifierWindowText("Entity: "+selectedObject);
        initModifiersUI();

        TimerTask task = new TimerTask() {
            public void run() {
                SceneEditorScreen.this.app.enqueue(() -> {
                    showEntitiesInScene();
                    highlightSelectedEntity(entity);
                });
            }
        };
        Timer timer = new Timer("Timer");
        long delay = 3000;
        timer.schedule(task, delay);

    }

    private void setModifierWindowText(String s) {
        lblModifierWindowHeader.setText(s);
    }

    private void updateProgram() {

        DecimalFormat nf = new DecimalFormat("0.00");
        StringBuilder prg = new StringBuilder();

        SceneGraphVisitor visitor = new SceneGraphVisitor() {

            @Override
            public void visit(Spatial n) {

                Float new_added = n.getUserData("added");
                Integer mod_pos = n.getUserData("_position");
                Integer mod_rot = n.getUserData("_rotation");
                Integer mod_scale = n.getUserData("_scale");

                if (new_added == null && mod_pos == null && mod_rot == null && mod_scale==null) {
                    return;
                }

                Vector3f pos = n.getLocalTranslation();
                float[] angles = new float[3];
                n.getLocalRotation().toAngles(angles);
                float angX = angles[0] * FastMath.RAD_TO_DEG;
                float angY = angles[1] * FastMath.RAD_TO_DEG;
                float angZ = angles[2] * FastMath.RAD_TO_DEG;

                String name = n.getName();
                int index = name.indexOf("@");
                if (index != -1) {
                    name = name.substring(0, index);
                }

                if (new_added != null) {
                    n.setUserData("added", null);
                    n.setUserData("_scale", null);
                    n.setUserData("_position", null);
                    n.setUserData("_rotation", null);

                    String added_res_name = n.getUserData("added_res_name");
                    String rotate = "";
                    String scale = "";
                    if(mod_rot!=null) {
                        rotate=" and rotate (" + nf.format(angX) + "," + nf.format(angY) + "," + nf.format(angZ) + ") ";
                    }
                    if(mod_scale!=null) {
                        rotate=" and scale "+n.getLocalScale().getX() + " ";
                    }
                    String code = name + " is a " + added_res_name + ": pos (" + nf.format(pos.x) + "," + nf.format(pos.y) + "," + nf.format(pos.z) + ") " +
                            rotate +
                            scale +
                            "\n";

                    prg.append(code);

                } else {
                    if (mod_pos != null) {
                        n.setUserData("_position", null);
                        String code = name + ".pos (" + nf.format(pos.x) + "," + nf.format(pos.y) + "," + nf.format(pos.z) + ")\n";
                        prg.append(code);
                    }

                    if (mod_rot != null) {
                        n.setUserData("_rotation", null);
                        String code = name + ".rotate (" + nf.format(angX) + "," + nf.format(angY) + "," + nf.format(angZ) + ")\n";
                        prg.append(code);
                    }

                    if(mod_scale!=null) {
                        n.setUserData("_scale", null);
                        String code = name+".scale = "+n.getLocalScale().getX() + " \n";
                        prg.append(code);
                    }

                }

            }

        };

        app.getRootNode().breadthFirstTraversal(visitor);


//        for(String key:codeLines.keySet()) {
//
//            RunTimeVarDef vd = app.findVarRuntime(null,null,key);
//            Node sp = (Node)app.getEntitySpatial(vd.varName);
//            Vector3f pos = sp.getLocalTranslation();
//            float[] angles = new float[3];
//            sp.getLocalRotation().toAngles(angles);
//            float angX = angles[0]* FastMath.RAD_TO_DEG;
//            float angY = angles[1]* FastMath.RAD_TO_DEG;
//            float angZ = angles[2]* FastMath.RAD_TO_DEG;
//
//            String code = key + " is a "+codeLines.get(key)+": pos ("+nf.format(pos.x)+","+nf.format(pos.y)+","+nf.format(pos.z)+") "+
//                    " and rotate ("+ nf.format(angX)+","+nf.format(angY)+","+nf.format(angZ)+") "+
//                    "\n";
//
//            prg=prg.concat(code) ;
//        }

        //String dir = Paths.get(".").toAbsolutePath().normalize().toString();
        File f = new File("tmp_script.txt");
        String finalCode = prg.toString();
        app.saveFile(f.getAbsolutePath(),finalCode);
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().edit(f);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    public void onEndScreen() {

    }

    public void exit() {
        nifty.exit();
    }


    public boolean rayCastCheck() {

        CollisionResults results = new CollisionResults();
        Vector2f click2d = this.app.getInputManager().getCursorPosition().clone();
        Vector3f click3d = this.app.getCamera().getWorldCoordinates(
                click2d, 0f).clone();

        Vector3f dir = this.app.getCamera().getWorldCoordinates(
                click2d, 1f).subtractLocal(click3d).normalizeLocal();
        Ray ray = new Ray(click3d, dir);
        this.app.getRootNode().collideWith(ray, results);

        if (results.size() > 0){
            CollisionResult closest = results.getClosestCollision();
            Spatial sp = findRootEntity(closest.getGeometry());

            if(sp==null) {
                return false;
            }

            String name = sp.getName();
            int index = name.indexOf("@");
            if(index!=-1) {
                name=name.substring(0,index);
            }

            highlightSelectedEntity(name);



            return true;
        }

        return false;

    }

    private Spatial getSelectedEntitySpatial() {
        RunTimeVarDef vd = app.findVarRuntime(null, null, selectedObject);
        return app.getEntitySpatial(vd.varName);
    }

    private void highlightSelectedEntity(String name) {
        if(selectedObject.length()>0) {
            String code = selectedObject+".hide outline";
            app.runPartialCode(code,null,false);
        }

        selectedObject= name;
        String code = name +".show outline";
        this.app.runPartialCode(code,null,false);

        initModifiersUI();
        setModifierWindowText("Entity: "+selectedObject);
        Spatial sp = getSelectedEntitySpatial();
        displaySelectedEntityProps(sp);
    }

    private Spatial findRootEntity(Spatial sp) {

        if(sp==null) {
            return null;
        }

        String name = sp.getName();
        if(this.app.geoName2ModelName.get(name)!=null) {
            return sp;
        }

        return findRootEntity(sp.getParent());

    }

}
