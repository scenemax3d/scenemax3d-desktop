package com.scenemaxeng.plugins.ide;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PartialSentenceReactionManager {

    private static JSONObject config;
    private static HashMap<Integer, Long> caseIndexesTimers = new HashMap<>();
    private static long lastReactionTime = 0;

    public static void init() {
        String configJson = Utils.readResourceText("/partial_sentence_reactions.json");
        config = new JSONObject(configJson);
    }

    public static PartialSentenceReaction react(String text) {

        PartialSentenceReaction react = new PartialSentenceReaction();
        JSONArray cases = config.getJSONArray("cases");
        for (int caseIndex=0;caseIndex<cases.length();++caseIndex) {
            JSONObject caseItem = cases.getJSONObject(caseIndex);
            JSONArray matches = caseItem.getJSONArray("match");
            for(int matchIndex=0;matchIndex<matches.length();++matchIndex) {
                String match = matches.getString(matchIndex);
                Pattern pattern = Pattern.compile(match);
                Matcher matcher = pattern.matcher(text);
                boolean isMatch = matcher.find();
                if(isMatch) {
                    long currTime = System.currentTimeMillis();
                    long caseLastTime = caseIndexesTimers.getOrDefault(caseIndex, 0L);
                    if(currTime - caseLastTime > 5000) {
                        caseIndexesTimers.put(caseIndex, currTime);
                        lastReactionTime = currTime;
                        react.code = getAnyOf(caseItem.getJSONArray("code"));
                        react.speak = getAnyOf(caseItem.getJSONArray("speak"));
                        react.code = "sys.print \"" + react.speak + "\" : pos (10, 285, 0), color yellow;"
                                + react.code + "\nsys.print \"\"";
                        return react;
                    }
                }
            }
        }
        return null;
    }

    private static String getAnyOf(JSONArray arr) {
        if (arr == null) {
            return null;
        }

        return arr.getString(new Random().nextInt(arr.length()));
    }

    public static long timePassedSinceLastReaction() {
        long currTime = System.currentTimeMillis();
        return currTime - lastReactionTime;
    }
}
