package com.scenemaxeng.plugins.ide;

import org.json.JSONObject;

import java.util.Map;

public class RacingCodeConverter extends CodeConverterBase{

    public RacingCodeConverter(SessionContext ctx, JSONObject config) {
        super(ctx, config);
    }

    @Override
    public String convert() {
        super.convert();
        this.genRaceTrack();
        this.genPlayerObject();
        this.genPlayerPos();
        this.genTacho();
        this.genSpeedo();
        this.genCameraChase();
        return this.code;
    }

    private void genPlayerPos() {
        if(!this.config.has("player_position")) {
            if(this.recentlyAdded.contains("player_car_type") ||
                    this.recentlyAdded.contains("race_track_type")) {
                this.config.put("player_position", "start");
            } else {
                return;
            }
        }

        Map<String, String> startPositions = Map.of(
            "race_track1", "-52.00,9.79,330.18",
            "track", "0,5,0",
            "box", "0,5,0"
        );
        String playerPos = this.config.getString("player_position");
        if(playerPos.equals("start")) {
            String raceTrack = this.getGameObject("race_track", "box");
            String pos = startPositions.getOrDefault(raceTrack, "0,5,0");
            this.appendCode("player.pos(" + pos + ");");//
            this.recentlyAdded.add("player_pos");
        } else if(playerPos.equals("random")) {

        }
    }

    private void genCameraChase() {
        if(!this.config.has("camera_chase")) {
            if(this.recentlyAdded.contains("player_car_type") ||
                    this.recentlyAdded.contains("race_track_type") ||
               this.recentlyAdded.contains("player_pos")) {
                this.config.put("camera_chase", "player");
            } else {
                return;
            }
        }

        String chaseObject = this.config.getString("camera_chase");
        if(!this.hasGameObject(chaseObject)) {
            chaseObject = "player";
        }
        this.appendCode("camera.chase stop;\ncamera.chase " +chaseObject + " : max distance = 15 and vertical rotation 20");
    }

    private void genRaceTrack() {
        if(!this.checkConfig("race_track_type", "race_track", "box")) {
            return;
        }

        this.appendCode(this.genEntity(
                "race_track",
                "race_track_type", "box",
                "",
                "static",
                Map.of(
                        "race_track1", "scale 20",
                        "track", "scale 50",
                        "box", "size (100,1,100), material=\"pond\""

                )));

    }

    private void genPlayerObject() {
        if(!this.checkConfig("player_car_type","player", "gtr_nismo")) {
            return;
        }

        this.appendCode(this.genEntity(
                "player",
                "player_car_type", "gtr_nismo",
                "vehicle", "",null));

    }

    private void genTacho() {
        if(!this.config.has("show_tacho")) {
            return;
        }
        this.appendCode("player.show tacho;");
    }

    private void genSpeedo() {
        if(!this.config.has("show_speedo")) {
            return;
        }
        this.appendCode("player.show speedo;");
    }
}
