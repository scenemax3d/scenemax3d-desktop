package com.scenemaxeng.projector.outliner;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.post.Filter;
import com.jme3.post.FilterPostProcessor;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

public class SelectObjectOutliner {
    
    private FilterPostProcessor fpp;
    private RenderManager renderManager;
    private AssetManager assetManager;
    private Camera cam;
    public static int OUTLINER_TYPE_FILTER=0;
    public static int OUTLINER_TYPE_MATERIAL=1;
    private Material wireMaterial;
    private Node rootNode;
    int outlinerType=OUTLINER_TYPE_FILTER;
    private  int width=5;
    private ColorRGBA color=ColorRGBA.Yellow;
    public SelectObjectOutliner()
    {
        
    }
    
    public void initOutliner(int type,  int width, ColorRGBA color,Node rootNode, FilterPostProcessor fpp, RenderManager renderManager,AssetManager assetManager, Camera cam)
    {
        outlinerType=type;
        this.fpp=fpp;
        this.renderManager=renderManager;
        this.assetManager=assetManager;
        this.cam=cam;
        this.rootNode=rootNode;
        this.width=width;
        this.color=color;
        if(outlinerType==OUTLINER_TYPE_MATERIAL)
        {
         wireMaterial= new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md"); 
         wireMaterial.setColor("Color", color);//color
         wireMaterial.getAdditionalRenderState().setWireframe(true); //we want wireframe
         wireMaterial.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);//that's just because we add an alpha pulse to the selection later, this is not mandatory
         wireMaterial.getAdditionalRenderState().setLineWidth(width); //you can play with this param to increase the line thickness
         wireMaterial.getAdditionalRenderState().setPolyOffset(-3f,-3f); //this is trick one, offsetting the polygons
         wireMaterial.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Front); // trick 2 we hide the front faces to not see the wireframe on top of the geom
   
        }
    }
    
   
    public void deselect(Spatial model)
    {
          if(outlinerType==OUTLINER_TYPE_FILTER)
             hideOutlineFilterEffect(model);
          else
             hideOutlineMaterialEffect(model);  
          
            model.setUserData("OutlineSelected", false);
    }
    
   public void select(Spatial model) 
   {
         if(outlinerType==OUTLINER_TYPE_FILTER)
           showOutlineFilterEffect(  model,   width,   color);
         else
            showOutlineMaterialEffect(  model,   width,   color);     
         
          model.setUserData("OutlineSelected", true);
   }
   
   public boolean isSelected(Spatial model) 
   {
        if(model.getUserData("OutlineSelected")!=null && ((Boolean)model.getUserData("OutlineSelected"))==true) 
           return  true;
       else
           return  false;
   }
      private void hideOutlineMaterialEffect(Spatial model) {
          
           Spatial geo= (Spatial)model.getUserData("OutlineGeo"); 
           if (geo != null)
               rootNode.detachChild(geo);
      }
      private void showOutlineMaterialEffect(Spatial model, int width, ColorRGBA color) {
          
          Spatial geo=model.clone(false);
          geo.setMaterial(wireMaterial);
          model.setUserData("OutlineGeo", geo);
          rootNode.attachChild(geo);
      }
    private void hideOutlineFilterEffect(Spatial model) {
		OutlineFilter outlineFilter = model.getUserData("OutlineFilter");
		if (outlineFilter != null) {
		    outlineFilter.setEnabled(false);
			outlineFilter.getOutlinePreFilter().setEnabled(false);
		}
	}

	public void removeFilter(Filter f) {
        fpp.removeFilter(f);
    }

	private void showOutlineFilterEffect(Spatial model, int width, ColorRGBA color) {
        OutlineFilter outlineFilter = model.getUserData("OutlineFilter");
		OutlinePreFilter outlinePreFilter;
		if (outlineFilter == null) {
			ViewPort outlineViewport = renderManager.createPreView("outlineViewport", cam);
			FilterPostProcessor outlinefpp = new FilterPostProcessor(assetManager);
			outlinePreFilter = new OutlinePreFilter();
			outlinefpp.addFilter(outlinePreFilter);
			outlineViewport.attachScene(model);
			outlineViewport.addProcessor(outlinefpp);
                        //
                         outlineFilter = new OutlineFilter(outlinePreFilter);
                        ((OutlineFilter)outlineFilter).setOutlineColor(color);
                        ((OutlineFilter)outlineFilter).setOutlineWidth(width);
                         model.setUserData("OutlineFilter", outlineFilter);
            outlineFilter.setOutlineViewPort(outlineViewport);
			
			fpp.addFilter(outlineFilter);
		} else {
			outlineFilter.setEnabled(true);
			  //
                         ((OutlineFilter)outlineFilter).getOutlinePreFilter().setEnabled(true);
                         
		}
	}
        
      

	 
}
