package com.scenemaxeng.plugins.ide;

public class PromptManager {

    private static String prompt = "";
    public static String createPrompt(String text) {
        PromptManager.prompt = "";
        append("the user's text: "+text);
        return PromptManager.prompt;
    }

    private static void append(String text) {
        PromptManager.prompt += text + "\n";
    }

    public static String getSysInstruction(SessionContext ctx) {

        PromptManager.prompt = Utils.readResourceText("/system_instruction.txt");
        String currGameState = "";
        if(ctx.gameType == SessionContext.GameTypes.Fighting) {
            currGameState += genObjStatus(ctx, "player_type");
            currGameState += genObjStatus(ctx, "opponent_type");
            currGameState += genStatus(ctx, "player_idle_animation");
            currGameState += genStatus(ctx, "opponent_idle_animation");
            currGameState += genObjStatus(ctx, "game_arena");
        } else if(ctx.gameType == SessionContext.GameTypes.Racing) {
            currGameState += genObjStatus(ctx, "player_car_type");
            currGameState += genObjStatus(ctx, "race_track_type");
        }

        System.out.println("\nsystem instructions current state: " + currGameState);
        if(currGameState.length()>0) {
            currGameState = "Current game state represented as JSON fields:\n" + currGameState;
        }

        PromptManager.prompt = PromptManager.prompt.replace("${current_game_state}", currGameState);
        return PromptManager.prompt;
    }

    private static String genObjStatus(SessionContext ctx, String field) {
        if(ctx.data.has(field)) {
            return field + " = " + ctx.data.getJSONObject(field).getString("confObjType") + ".\n";
        }

        return "";
    }

    private static String genStatus(SessionContext ctx, String field) {
        if(ctx.data.has(field)) {
            return field + " = " + ctx.data.getString(field) + ".\n";
        }

        return "";
    }

}
