package com.scenemax.desktop;

import com.scenemaxeng.common.types.ResourceAudio;
import com.scenemaxeng.common.types.ResourceSetup;
import com.scenemaxeng.common.types.ResourceSetup2D;
import com.scenemaxeng.common.types.SkyBoxResource;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class ResourceMenuAction extends AbstractAction  {

    private SkyBoxResource itemSkybox;
    private ResourceSetup item3d=null;
    private ResourceSetup2D item2d=null;
    private ResourceAudio itemAudio=null;
    private MainApp app=null;

    public ResourceMenuAction(MainApp mainApp, String name, SkyBoxResource item) {
        super(name);
        this.app=mainApp;
        this.itemSkybox=item;
    }

    public ResourceMenuAction(MainApp mainApp, String name, ResourceAudio item) {
        super(name);
        this.app=mainApp;
        this.itemAudio=item;
    }

    public ResourceMenuAction(MainApp mainApp, String name, ResourceSetup2D item) {
        super(name);
        this.app=mainApp;
        this.item2d=item;
    }

    public ResourceMenuAction(MainApp mainApp, String name, ResourceSetup item) {
        super(name);
        this.app=mainApp;
        this.item3d=item;


    }

    @Override
    public void actionPerformed(ActionEvent e) {


        if(item3d!=null) {
            String name="item";
            if(item3d.name.length()>2) {
                name=item3d.name.substring(0,2);
            }

            String code="\n"+name+" is a "+item3d.name;
            if(item3d.isVehicle) {
                code+=" vehicle";
            }
            app.addCodeSnippet(code);

        } else if(item2d!=null) {
            String name="item";
            if(item2d.name.length()>2) {
                name=item2d.name.substring(0,2);
            }

            String code="\n"+name+" is a "+item2d.name+ " sprite";

            app.addCodeSnippet(code);
        } else if(itemAudio!=null) {
            String code="\naudio.play \""+itemAudio.name+ "\"";
            app.addCodeSnippet(code);

        } else if (itemSkybox!=null) {
            String code="\nskybox.show \""+itemSkybox.name+ "\"";
            app.addCodeSnippet(code);
        }



    }


}
