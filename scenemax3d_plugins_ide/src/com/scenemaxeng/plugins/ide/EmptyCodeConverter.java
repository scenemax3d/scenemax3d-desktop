package com.scenemaxeng.plugins.ide;

import org.json.JSONObject;

public class EmptyCodeConverter extends CodeConverterBase{

    public EmptyCodeConverter(SessionContext ctx, JSONObject config) {
        super(ctx, config);
    }

    @Override
    public String convert() {
        super.convert();
        String code = "gemini3.print \"Empty game - " +
                "Which type of game would you like to create?\":pos (20,20,0),size 2;" +
                "wait 10 seconds;gemini3.print \"\";";

        return code;
    }

}
