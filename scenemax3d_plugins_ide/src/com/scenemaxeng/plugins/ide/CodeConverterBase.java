package com.scenemaxeng.plugins.ide;

import org.json.JSONObject;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CodeConverterBase {
    protected final SessionContext ctx;
    public boolean startNewGame = false;

    protected JSONObject config;
    protected String code = "";
    protected Set<String> recentlyAdded = new HashSet<>();

    public CodeConverterBase(SessionContext ctx, JSONObject config) {
        this.ctx = ctx;
        this.config = config;

        if (config.has("new_game")) {
            this.startNewGame = true;
        }
    }

    public String convert() {
        this.genExportToAndroid();
        this.genBackground();
        this.genLights();

        return null;
    }

    private void genLights() {
        if(this.ctx.data.has("show_lights")) {
            return;
        }

        this.appendCode("lights.add probe \"5\";");
        this.ctx.data.put("show_lights", 1);
    }

    private void genExportToAndroid() {
        //export_to_android
        if(this.config.has("export_to_android")) {
            this.invalidateAll();
            this.startNewGame = true; // force updating the 'running/main' file
        }
    }

    public void appendCode(String code) {
        this.code += code + "\n";
    }

    public String genEntity(String entityName,
                            String entityTypeKey,
                            String defaultType,
                            String postTypeDetials,
                            String preTypeDetails, Map<String, String>typeAttributes) {
        String code = "";
        String dataKey = entityTypeKey;// + "_" + entityName;
        if(this.ctx.data.has(dataKey) && !this.startNewGame) {
            JSONObject data = this.ctx.data.getJSONObject(dataKey);
            String entityObjectName = data.getString("objName");
            code = entityObjectName + ".delete;\n";
        }

        code += entityName;
        String entityType = this.config.has(entityTypeKey) ? this.config.getString(entityTypeKey) : defaultType;
        entityType = this.translateEntityType(entityType);
        String attributes = (typeAttributes != null && typeAttributes.containsKey(entityType)) ? ": "+typeAttributes.get(entityType) : "";

        JSONObject data = new JSONObject();
        data.put("objName", entityName);
        data.put("objType", entityType);
        data.put("confObjType", this.config.getString(entityTypeKey));
        this.ctx.data.put(dataKey, data);


        this.ctx.data.getJSONObject("game_objects").put(entityName, entityType);
        this.recentlyAdded.add(entityTypeKey);
        return code + "=>" + preTypeDetails + " " + entityType + " " + postTypeDetials + attributes + ";\n";
    }

    protected String translateEntityType(String entityType) {
        return entityType;
    }

    public boolean isNullOrEmpty(String s) {
        return s==null || s.length()==0;
    }

    protected boolean checkConfig(String key, String entityName, String setDefaultWhenGeneralConfigIsEmpty) {
        if(this.config.has(key)) {
            return true;
        }

        String dataKey = key;//+"_"+entityName;
        if(setDefaultWhenGeneralConfigIsEmpty != null && !this.ctx.data.has(dataKey)) {
            this.config.put(key, setDefaultWhenGeneralConfigIsEmpty);
            return true;
        }

        return false;
    }

    protected boolean hasGameObject(String gameObject) {
        return this.ctx.data.getJSONObject("game_objects").has(gameObject);
    }

    protected String getGameObject(String gameObject, String defaultResult) {
        if (this.hasGameObject((gameObject))) {
            return this.ctx.data.getJSONObject("game_objects").getString(gameObject);
        }

        return defaultResult;
    }

    protected boolean hasGameObjects() {
        return !this.ctx.data.getJSONObject("game_objects").isEmpty();
    }

    protected void invalidateAll() {
        String[] fields = this.ctx.data.keySet().toArray(new String[0]);
        System.out.println("Going to invalidate: \ndata = " + this.ctx.data.toString(2));
        this.invalidate(fields);
    }

    protected void invalidate(String...configFields) {
        for (String field : configFields) {
            if(field.equals("game_objects")) {
                continue; // ignore game objects
            }
            if(!this.config.has(field)) {
                if(this.ctx.data.has(field)) {
                    Object obj = this.ctx.data.get(field);
                    if(obj instanceof JSONObject) {
                        this.config.put(field, ((JSONObject)obj).getString("confObjType"));
                    } else {
                        this.config.put(field, obj);
                    }
                }
            }
        }
    }

    protected void genBackground() {
        if(!this.config.has("show_background") || this.ctx.data.has("show_background")) {
            return;
        }

        this.appendCode("skybox.show solar system;");
    }
}
