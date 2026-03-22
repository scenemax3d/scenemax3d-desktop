package com.scenemaxeng.plugins.ide;


import org.json.JSONObject;

public class SessionContext {

    private final int GAME_TYPE_RACING = 1;
    private final int GAME_TYPE_FIGHTING = 2;
    private final int GAME_TYPE_EMPTY = 3;

    public enum GameTypes {
        Empty,
        Racing,
        Fighting,
        Adventure
    }

    public GameTypes gameType = GameTypes.Empty;
    public JSONObject data = new JSONObject();

    public SessionContext() {
        clearGameContextState();
    }

    private void clearGameContextState() {
        this.data = new JSONObject();
        data.put("game_objects", new JSONObject());
    }

    public String getDataField(String key) {
        if(data.has(key)) {
            return data.getString(key);
        }

        return "";
    }

    public CodeConverterBase getConverter(JSONObject config) {
        int gameType = 0;

        if(config.has("new_game")) {
            this.gameType = GameTypes.Empty;
        }

        if(config.has("game_type")) {
            gameType = config.getInt("game_type"); // 0 empty, 1 racing, 2 fighting, 3 adventure
            this.switchGameType(gameType, config);
        }

        switch (this.gameType) {
            case Racing:
                return new RacingCodeConverter(this, config);
            case Fighting:
                return new FightingCodeConverter(this, config);
            case Empty:
                return new EmptyCodeConverter(this, config);

        }
        return null;
    }

    private void switchGameType(int gameType, JSONObject config) {

        if(gameType != this.gameType.ordinal()) {
            config.put("new_game", true); // this will enforce "switch state" in code
            clearGameContextState();
        }

        switch (gameType) {
            case GAME_TYPE_RACING:
                this.gameType = GameTypes.Racing;
                break;
            case GAME_TYPE_FIGHTING:
                this.gameType = GameTypes.Fighting;
                break;
            case GAME_TYPE_EMPTY:
                this.gameType = GameTypes.Empty;

        }
    }
}
