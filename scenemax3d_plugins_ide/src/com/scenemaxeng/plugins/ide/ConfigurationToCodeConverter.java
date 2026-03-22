package com.scenemaxeng.plugins.ide;

import com.scenemaxeng.common.types.ISceneMaxPlugin;
import org.json.JSONArray;
import org.json.JSONObject;


public class ConfigurationToCodeConverter {

    public final int SESSION_TYPE_NEW = 1;
    public final int SESSION_TYPE_UPDATE = 2;

    private final ISceneMaxPlugin hostApp;
    private JSONObject config;

    public ConfigurationToCodeConverter(ISceneMaxPlugin hostApp) {
        this.hostApp = hostApp;
    }

    public JSONObject getGameConfig() {
        return this.config;
    }

    public String convert(SessionContext ctx, JSONObject config) {

        String code = "";
        try {

            JSONArray candidates = config.getJSONArray("candidates");
            JSONObject candidate = candidates.getJSONObject(0);
            if (!candidate.has("content")) {
                System.out.println("No candidate content has received from Gemini AI");
                return "";
            }
            JSONObject content = candidate.getJSONObject("content");
            JSONArray parts = content.getJSONArray("parts");

            String gameConfigText = parts.getJSONObject(0).getString("text");
            int index = gameConfigText.indexOf("```json");
            if(index!=-1) {
                int index2 = gameConfigText.lastIndexOf("```");
                System.out.println("index2 = " + index2);
                gameConfigText = gameConfigText.substring(index+7, index2);
            }

            JSONObject gameConfig = new JSONObject(gameConfigText);
            this.config = gameConfig;
            CodeConverterBase converter = this.getConverter(ctx, gameConfig);
            code = converter.convert();

            if(converter.startNewGame) {
                // we need to persist the converter code and restart the engine
                this.hostApp.run("prepare_to_switch_game", code);
                return "switch to \"main\";";
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return code;
    }

    private CodeConverterBase getConverter(SessionContext ctx, JSONObject config) {
        return ctx.getConverter(config);

    }
}

