package com.scenemaxeng.plugins.ide;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FightingCodeConverter extends CodeConverterBase {
    private static int opponentAttackIndex = 0;
    public FightingCodeConverter(SessionContext ctx, JSONObject config) {
        super(ctx, config);
    }

    @Override
    public String convert() {

        super.convert();

        this.genGameArena();
        this.genPlayerObject();
        this.genOpponentObject();
        this.genPlayerShowAnimations();
        this.genOpponentShowAnimations();
        this.genPlayerIdleAnimation();
        this.genOpponentIdleAnimation();
        this.genCameraChase();
        this.genLookAt();
        this.genPlayerKickAttack();
        this.genPlayerPunchAttack();
        this.genPlayerMove();
        this.genPlayerJump();
        this.genOpponentAttackLogic();

        return this.code;

    }

    private void genOpponentAttackLogic() {
        if(!this.config.has("opponent_attack_logic")) {
            return;
        }

        float power = 1f;
        if(this.config.has("opponent_attack_power")) {
            power = this.config.getFloat("opponent_attack_power");
        }
        String[] rawData = this.config.getString("opponent_attack_logic").split(";");
        int logicIndex = 0;
        String logic = "";
        for (String item : rawData) {
            String[] command = item.split(",");
            String logicPart = logicIndex == 0 ? "" : "else ";
            logicPart += "if (dist<=" + command[0] + " && prob<=" + command[2] +") {\n" +
                    (command[1].toLowerCase().contains("kick") ? "audio.play \"hu_ya1\";\n" : "audio.play \"kick1\";\n") +
                    "opponent." + command[1] + " async;" +
                    "}\n";
            logic += logicPart;
            logicIndex++;
        }

        if (logic.length() > 0) {
            String attackLogicFunc = Utils.readResourceText("/opponent_attack_logic_template.txt");
            attackLogicFunc = attackLogicFunc.replace("${index}", String.valueOf(++opponentAttackIndex));
            attackLogicFunc = attackLogicFunc.replace("${logic}", logic);
            attackLogicFunc = attackLogicFunc.replace("${interval}", "0.5");
            attackLogicFunc = attackLogicFunc.replace("${step_size}", "0.2");
            attackLogicFunc = attackLogicFunc.replace("${step_time}", "0.5");
            if(opponentAttackIndex > 1) {
                attackLogicFunc += "opponent_attack" + String.valueOf(opponentAttackIndex-1) + " = 0;\n";
            }
            this.appendCode(attackLogicFunc);
            this.ctx.data.put("opponent_attack_logic", this.config.getString("opponent_attack_logic"));
        }

    }

    private void genPlayerJump() {
        if(!this.config.has("player_jump") || this.ctx.data.has("player_jump")) {
            return;
        }
        String code = "when key Space is pressed once do\nplayer.character.jump at speed of 35;\nend do\n";
        this.appendCode(code);
        this.ctx.data.put("player_jump",1);
    }

    private void genPlayerMove() {
        if(!this.config.has("player_move") || this.ctx.data.has("player_move")) {
            return;
        }
        String code = Utils.readResourceText("/player_move_code.txt");;
        this.appendCode(code);
        this.ctx.data.put("player_move",this.config.get("player_move"));
    }

    private void genPlayerPunchAttack() {
        genPlayerReactToKey("player_punch_attack", "Z",
                "sys.print \"Press 'Z' to punch!\";wait 5 seconds;sys.print \"\";");

    }

    private void genPlayerKickAttack() {
        genPlayerReactToKey("player_kick_attack", "X",
                "sys.print \"Press 'X' to kick!\";wait 5 seconds;sys.print \"\";");

    }


    private void genPlayerReactToKey(String configField, String keyName, String notificationCode) {
        if(!this.config.has(configField) || this.ctx.data.has(configField)) {
            return;
        }

        List<String> attacks = new ArrayList<>();
        if(config.get(configField) instanceof JSONArray) {
            for (Object item : ((JSONArray) config.get(configField))) {
                attacks.add(item.toString());
            }
        } else {
            attacks.addAll(List.of(this.config.getString(configField).split(",")));
        }

        StringBuilder animCode = new StringBuilder();
        int attackIndex = 0;
        for (String attack : attacks) {
            String elseIf = attackIndex > 0 ? " else " : "";
            animCode.append(elseIf).append("if (anim==").append(attackIndex).append(") {\naudio.play \"kick1\";\nplayer.\"").append(attack.trim()).append("\" async;\n}");
            attackIndex++;
        }

        String fileTemplate = Utils.readResourceText("/player_react_to_key_code.txt");
        fileTemplate = fileTemplate.replace("${key}", keyName).replace("${rnd_range}", String.valueOf(attacks.size()));
        fileTemplate=fileTemplate.replace("${code}", animCode.toString());
        this.appendCode(fileTemplate);
        if(notificationCode.length()>0) {
            this.appendCode(notificationCode);
        }
        this.ctx.data.put(configField, this.config.get(configField));
    }


    private void genLookAt() {
        if(!this.config.has("look_at_each_other")) {
            return;
        }
        if(!(this.hasGameObject("player") && this.hasGameObject("opponent"))) {
            return;
        }

        String code = "do async\n" +
                "player.look at (opponent);\n" +
                "opponent.look at (player);\n" +
                "wait 1 second;\nwhile 1==1\n";

        this.appendCode(code);

    }

    private void genOpponentIdleAnimation() {
        //
        if(this.recentlyAdded.contains("opponent_idle_animation") ||
                !this.config.has("opponent_idle_animation")) {
            return;
        }

        appendEntityAnimation("opponent", this.config.getString("opponent_idle_animation"), true);
        this.ctx.data.put("opponent_idle_animation", this.config.getString("opponent_idle_animation"));
    }

    private void genPlayerIdleAnimation() {
        //
        if(this.recentlyAdded.contains("player_idle_animation") ||
                !this.config.has("player_idle_animation")) {
            return;
        }

        appendEntityAnimation("player", this.config.getString("player_idle_animation"), true);
        this.ctx.data.put("player_idle_animation", this.config.getString("player_idle_animation"));
    }

    private void appendEntityAnimation(String entityName, String animName, boolean loop) {
        this.appendCode(entityName+".\""+animName+"\" loop;");
    }

    private void genOpponentShowAnimations() {
        genFighterShowAnimations("opponent_show_animations", "opponent");
    }

    private void genFighterShowAnimations(String configKey, String entityName) {
        if(!this.config.has(configKey)) {
            return;
        }

        String[] anims = this.config.getString(configKey).split(",");

        if(anims.length>0) {

            String code = "";
            for (int i = 0; i < anims.length; i++) {
                String anim = "\"" + anims[i].trim() + "\"";
                code += i==0 ? anim : (" then " + anim);
            }

            if (code.length() > 0) {
                code = entityName + "." + code + " async";
                this.appendCode(code);
            }

        }

    }

    private void genPlayerShowAnimations() {
        genFighterShowAnimations("player_show_animations", "player");
    }

    private String genFighterPos(String name, Map<String, String> startPositions) {
        if(!this.config.has(name+"_position")) {
            if(this.config.has(name + "_type") ||
                    this.recentlyAdded.contains("game_arena")) {
                this.config.put(name+"_position", "start");
            } else {
                return "";
            }
        }

        String retval = "";
        String playerPos = this.config.getString(name+"_position");
        if(playerPos.equals("start")) {
            String environment = this.getGameObject("game_arena", "box");
            String pos = startPositions.getOrDefault(environment, "0,5,0");
            retval = "pos(" + pos + ")";//
            this.recentlyAdded.add(name+"_pos");
        } else if(playerPos.equals("random")) {

        }

        return retval;
    }

    private void genOpponentObject() {
        String initAttr = genFighterPos("opponent", Map.of(
                "arena2", "-10,7,4.7",
                "crime_city1", "39.95,-86.5,16.5",
                "box", "-5,5,0"));
        genFighterObject("opponent", initAttr);
    }

    private void genFighterObject(String name, String initialAttr) {
        if(!this.checkConfig(name+"_type",name, "ninja")) {
            return;
        }

        if (initialAttr.length()>0) {
            initialAttr += ",";
        }

        this.appendCode(this.genEntity(
                name,
                name+"_type", "ninja3",
                "", "dynamic",Map.of(
                        "ninja3",initialAttr + "scale 3, shadow mode cast\n" +
                                ", joints (\"mixamorig:Head\",\"mixamorig:LeftShoulder\",\n" +
                                "\"mixamorig:LeftArm\",\"mixamorig:LeftForeArm\",\"mixamorig:LeftHand\",\n" +
                                "\"mixamorig:RightArm\",\"mixamorig:RightForeArm\",\"mixamorig:RightHand\",\n" +
                                "\"mixamorig:LeftUpLeg\",\"mixamorig:LeftLeg\",\"mixamorig:LeftFoot\",\n" +
                                "\"mixamorig:RightUpLeg\",\"mixamorig:RightLeg\",\"mixamorig:RightFoot\")",

                        "old_fighter2",initialAttr + "shadow mode cast\n" +
                                ", joints (\"mixamorig:Head\",\"mixamorig:LeftShoulder\",\n" +
                                "\"mixamorig:LeftArm\",\"mixamorig:LeftForeArm\",\"mixamorig:LeftHand\",\n" +
                                "\"mixamorig:RightArm\",\"mixamorig:RightForeArm\",\"mixamorig:RightHand\",\n" +
                                "\"mixamorig:LeftUpLeg\",\"mixamorig:LeftLeg\",\"mixamorig:LeftFoot\",\n" +
                                "\"mixamorig:RightUpLeg\",\"mixamorig:RightLeg\",\"mixamorig:RightFoot\")"
                )));

        this.appendCode(name+".switch to character mode : gravity 60\n");
        // player_idle_animation
        if (this.config.has(name+"_idle_animation")) {
            this.appendCode(name+".\""+this.config.getString(name+"_idle_animation")+"\" loop");
        }
    }

    private void genCameraChase() {
        if(!this.config.has("camera_chase")) {
            if(this.recentlyAdded.contains("player_type") ||
                    this.recentlyAdded.contains("game_arena") ||
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

    private void genPlayerObject() {
        String initAttr = genFighterPos("player", Map.of(
                "arena2", "0.07,5,-4",
                "crime_city1", "39.4,-87,29",
                "box", "0,5,0"
        ));
        genFighterObject("player", initAttr);
    }

    private void genGameArena() {

        if(!this.checkConfig("game_arena", "game_arena", "box")) {
            return;
        }

        this.appendCode(this.genEntity(
                "game_arena",
                "game_arena", "box",
                "",
                "static",
                Map.of(
                        "arena2", "scale 0.1, shadow mode on",
                        "crimecity", "pos (220,-90,210), shadow mode on",
                        "box", "size (100,1,100), material=\"pond\", shadow mode receive"

                )));

        // if game arena is generated, make sure existing players are regenerated as well
//        this.invalidate("player_type",
//                "opponent_type",
//                "player_idle_animation",
//                "opponent_idle_animation");
        this.invalidateAll();
    }

    @Override
    protected String translateEntityType(String entityType) {
        return Map.of("ninja", "ninja3",
                "old_fighter", "old_fighter2",
                "arena","arena2",
                "city", "crime_city1"
                ).getOrDefault(entityType, entityType);
    }

}
