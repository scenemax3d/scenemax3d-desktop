package com.scenemaxeng.projector;

import com.jme3.app.Application;
import com.jme3.app.state.AbstractAppState;
import com.jme3.app.state.AppStateManager;
import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.NiftyEventSubscriber;
import de.lessvoid.nifty.controls.*;
import de.lessvoid.nifty.controls.textfield.TextFieldControl;
import de.lessvoid.nifty.controls.textfield.format.TextFieldDisplayFormat;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.elements.render.TextRenderer;
import de.lessvoid.nifty.input.NiftyInputEvent;
import de.lessvoid.nifty.screen.KeyInputHandler;
import de.lessvoid.nifty.screen.Screen;
import de.lessvoid.nifty.screen.ScreenController;

import javax.annotation.Nonnull;

public class SceneMaxScreen extends AbstractAppState implements ScreenController {
    Screen screen;
    SceneMaxApp app = null;
    private Nifty nifty;

    @Override
    public void initialize(AppStateManager stateManager, Application app) {
        super.initialize(stateManager, app);
        this.app = (SceneMaxApp)app;
    }

    @Override
    public void update(float tpf) {
        //TODO: implement behavior during runtime
    }

    @Override
    public void cleanup() {
        super.cleanup();
        //TODO: clean up what you initialized in the initialize method,
        //e.g. remove all spatials from rootNode
        //this is called on the OpenGL thread after the AppState has been detached
    }

    @Override
    public void bind(@Nonnull Nifty nifty, @Nonnull Screen screen) {
        System.out.println("hello screen");
        this.screen=screen;
        this.nifty=nifty;
    }

    @NiftyEventSubscriber(id="lstProgram")
    public void onListBoxSelectionChanged(final String id, final ListBoxSelectionChangedEvent changed) {
        ListBox lstProgram = screen.findNiftyControl("lstProgram", ListBox.class);
        TextField txtProgram = screen.findNiftyControl("txtProgram", TextField.class);

        txtProgram.setText(lstProgram.getFocusItem().toString());
    }

    @Override
    public void onStartScreen() {
        Element niftyElement = screen.findElementById("txtProgram");
        NiftyInputControl ic = niftyElement.getAttachedInputControl();
        TextFieldControl ctl = (TextFieldControl)ic.getController();

        ic.addPreInputHandler(new KeyInputHandler() {
            @Override
            public boolean keyEvent(@Nonnull NiftyInputEvent e) {
                if(e.toString().equals("SubmitText")) {
                    runCode();
                    clearCode();
                    return true;
                }
                return false;
            }
        });

    }

    private void clearCode() {
        Element niftyElement = screen.findElementById("txtProgram");
        NiftyInputControl ic = niftyElement.getAttachedInputControl();
        TextFieldControl ctl = (TextFieldControl)ic.getController();
        ctl.setText("");
    }

    private void runCode() {
        Element niftyElement = screen.findElementById("txtProgram");
        NiftyInputControl ic = niftyElement.getAttachedInputControl();
        TextFieldControl ctl = (TextFieldControl)ic.getController();
        String prg = ctl.getText();
        this.app.runPartialCode(prg,null, false);

        ListBox listBox = screen.findNiftyControl("lstProgram", ListBox.class);
        listBox.insertItem(prg,0);

//        Console console = screen.findNiftyControl("console", Console.class);
//        console.output(prg);

    }

    public void button1_click(String arg) {

    }

    @Override
    public void onEndScreen() {

    }

    public void exit() {
        nifty.exit();
    }

}
