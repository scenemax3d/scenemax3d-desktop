package com.scenemaxeng.projector;

import java.util.HashMap;

public class SceneMaxScope {

    public static final int SCOPE_TYPE_LOOPER = 10;
    public static final int SCOPE_TYPE_RETURN_POINT = 20;

    public SceneMaxScope parent = null;
    private static int scopeSeq=0;
    public final int scopeId;
    public CompositeController mainController = null;
    public HashMap<String, VarInst> vars_index = new HashMap<String, VarInst>();
    public SceneMaxScope sequenceCreatorScope;
    public HashMap<String, EntityInstBase> entities = new HashMap<>();
    public HashMap<String, GroupInst> groups = new HashMap<>();

    //
    public HashMap<String, Object> funcScopeParams;
    public int type;
    private SceneMaxBaseController _creatorController;
    public boolean isReturnPoint=false; // mark scope as possible return point when using "return" command
    public boolean isSecondLevelReturnPoint = false;

    public SceneMaxScope() {
        mainController = new CompositeController();
        this.scopeId=++scopeSeq;
    }

    public void add(SceneMaxBaseController c) {
        if(c!=null) {
            mainController.add(c);
        }
    }

    public void reset() {

        for (Object key : this.vars_index.keySet().toArray()) {
            if (!this.vars_index.get(key).varDef.isShared) {
                this.vars_index.remove(key);
            }
        }

        this.mainController = new CompositeController();
        // Keep only ModelInst entries (not SphereInst/BoxInst subclasses or SpriteInst)
        this.entities.entrySet().removeIf(e -> e.getValue().getClass() != ModelInst.class);
        this.groups = new HashMap<>();
    }

    public VarInst getVar(String targetVar) {

        if(funcScopeParams!=null) {
            Object val = funcScopeParams.get(targetVar);
            if (val instanceof VarInst) {
                return (VarInst) val;
            }
        }

        VarInst def = vars_index.get(targetVar);
        if(def==null){
            if(parent==null) {
                return null;
            }

            return parent.getVar(targetVar);
        } else {
            return def;
        }


    }

    public GroupInst getGroup(String varName) {

        if(funcScopeParams!=null) {
            Object val = funcScopeParams.get(varName);
            if (val instanceof GroupInst) {
                return (GroupInst) val;
            }
        }

        GroupInst def = groups.get(varName);
        if(def==null){
            if(parent==null) {
                return null;
            }

            return parent.getGroup(varName);
        } else {
            return def;
        }

    }

    public Object getFuncScopeParam(String varName) {
        Object var = null;
        if(funcScopeParams!=null) {
            var = funcScopeParams.get(varName);
        }
        if(var==null) {
            if(parent==null) {
                return null;
            }

            return parent.getFuncScopeParam(varName);
        } else {
            return var;
        }
    }

    public EntityInstBase getEntityInst(String var) {

        // Check function scope params first
        if (funcScopeParams != null) {
            Object val = funcScopeParams.get(var);
            if (val instanceof EntityInstBase) return (EntityInstBase) val;
            if (val instanceof VarInst && ((VarInst) val).value instanceof EntityInstBase) {
                return (EntityInstBase) ((VarInst) val).value;
            }
        }

        // Single unified lookup in this scope
        EntityInstBase e = entities.get(var);
        if (e != null) return e;

        // Check if a variable holds an entity reference
        VarInst vi = vars_index.get(var);
        if (vi != null && vi.value instanceof EntityInstBase) return (EntityInstBase) vi.value;

        // Check sequence creator scope
        if (sequenceCreatorScope != null) {
            EntityInstBase se = sequenceCreatorScope.getEntityInst(var);
            if (se != null) return se;
        }

        // Walk up to parent scope
        if (parent != null) return parent.getEntityInst(var);
        return null;
    }

    public ModelInst getModel(String varName) {
        EntityInstBase e = getEntityInst(varName);
        return (e instanceof ModelInst) ? (ModelInst) e : null;
    }

    public SpriteInst getSprite(String varName) {
        EntityInstBase e = getEntityInst(varName);
        return (e instanceof SpriteInst) ? (SpriteInst) e : null;
    }

    public SphereInst getSphere(String varName) {
        EntityInstBase e = getEntityInst(varName);
        return (e instanceof SphereInst) ? (SphereInst) e : null;
    }

    public BoxInst getBox(String varName) {
        EntityInstBase e = getEntityInst(varName);
        return (e instanceof BoxInst) ? (BoxInst) e : null;
    }

    public int getVariableScopeId(String var) {

        EntityInstBase eb = getEntityInst(var);
        if(eb!=null) {
            return eb.scope.scopeId;
        }

        GroupInst gri = this.getGroup(var);
        if(gri==null) {
            return 0;// should throw error instance not found
        } else {
            return gri.scope.scopeId;
        }

    }

    public SceneMaxScope getSecondLevelReturnPointScope() {
        if(this.isSecondLevelReturnPoint) {
            return this;
        } else if(this.parent!=null) {
            return this.parent.getSecondLevelReturnPointScope();
        } else {
            return null;
        }
    }

    public SceneMaxScope getFirstReturnPointScope() {
        if(this.isReturnPoint) {
            return this;
        } else if(this.parent!=null) {
            return this.parent.getFirstReturnPointScope();
        } else {
            return null;
        }
    }

    public SceneMaxScope getFirstLooperScope() {
        if(this.type== SceneMaxScope.SCOPE_TYPE_LOOPER) {
            return this;
        } else if(this.parent!=null) {
            return this.parent.getFirstLooperScope();
        } else {
            return null;
        }
    }

    public void forceStop() {
        if(mainController!=null) {
            mainController.forceStop();
        }
    }

    public SceneMaxBaseController getCreatorController() {
        return _creatorController;
    }

    public void setCreatorController(SceneMaxBaseController c) {
        _creatorController=c;
    }

}
