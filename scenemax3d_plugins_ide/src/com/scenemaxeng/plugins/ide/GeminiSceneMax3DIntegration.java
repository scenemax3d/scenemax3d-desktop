package com.scenemaxeng.plugins.ide;

import com.scenemaxeng.common.types.PluginBase;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.awt.*;
import java.io.IOException;
import java.util.Random;

public class GeminiSceneMax3DIntegration extends PluginBase {

    private SessionContext ctx = new SessionContext();
    private GeminiUserInput userInputDialog;
    private boolean allowExportToAndroid = true;

    @Override
    public int start(Object... args) {
        this.observer.run("add_toolbar_button", "google-gemini-icon-24x24", "show_gemini_dialog", "Open Gemini AI dialog");
        this.observer.run("install_assets", "./plugins/assets.zip");
        TextToSpeech.init();
        PartialSentenceReactionManager.init();
        return 0;
    }

    @Override
    public int run(Object... args) {
        String command = (String)args[0];
        if (command.equals("show_gemini_dialog")) {
            this.setupAndRunGeminiScene();
            this.userInputDialog = new GeminiUserInput(this);
            this.userInputDialog.setPreferredSize(new Dimension(400, 200));
            this.userInputDialog.pack();
            this.userInputDialog.setLocation(1000,600);
            this.userInputDialog.setVisible(true);
            this.allowExportToAndroid = true; // allow export to Android again
        } else if(command.equals("on_user_input")) {
            this.onUserInput((String)args[1]);
        } else if(command.equals("run_partial_text_code")) {
            this.sendCode((String)args[1]);
        } else if(command.equals("prepare_to_switch_game")) {
            System.out.println("prepare to switch state: \ncode = "+args[1]);
            this.observer.run("gemini_update_running_main_file", args[1]);
            TextToSpeech.speak("Let's create a new game!");
        }
        return 0;
    }

    private void setupAndRunGeminiScene() {
        String mainFileCode = Utils.readResourceText("/gemini_scene_initial_main_file.txt");
        this.observer.run("gemini_create_scene_folder", mainFileCode);
        this.observer.run("gemini_play_scene");
        TextToSpeech.speak("Welcome to Gemini AI. Tell me what kind of game would you like to create.");
        this.ctx.data.put("show_lights", 1); // update state about physical lights that were added
                                             //  - by the gemini_scene_initial_main_file.txt file
    }

    private void sendCode(String code) {

        SceneMaxEngRestApi.post(code, new Callback() {
            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
            }

            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
            }
        });
    }

    private void onUserInput(String userInput) {
        this.sendCode("processing.draw processing_text: size(512,132), pos (10,250);");
        if (PartialSentenceReactionManager.timePassedSinceLastReaction() > 10000) {
            speakAccepted();
        }
        String sysInstruction = PromptManager.getSysInstruction(ctx);
        String prompt = PromptManager.createPrompt(userInput);
        System.out.println(prompt);
        String config = GeminiApi.post(prompt, sysInstruction);
        if (config.length() == 0) {
            return;
        }
        System.out.println(config);
        JSONObject configJSON = new JSONObject(config);
        ConfigurationToCodeConverter converter = new ConfigurationToCodeConverter(this);
        String code = converter.convert(ctx, configJSON);
        System.out.println(code);
        this.sendCode("processing.draw clear;");
        if(code.length()>0) {
            this.sendCode(code);
        }

        if (this.shouldExportToAndroid(converter.getGameConfig())) {
            this.allowExportToAndroid = false; // no more exports
            this.userInputDialog.terminate();
            this.observer.run("export_to_android");
        }
    }

    private boolean shouldExportToAndroid(JSONObject config) {
        return config.has("export_to_android") && this.allowExportToAndroid;
    }

    private void speakAccepted() {
        String[] okEquivalents = {
                "OK",
                "Alright",
                "Sure",
                "Fine",
                "Cool",
                "Got it",
                "Okay",
                "Understood",
                "No problem",
                "Okey-dokey",
                "A-OK",
                "Sounds good",
                "Right",
                "Yep",
                "Accepted",
                "Agreed"
        };
        TextToSpeech.speak(okEquivalents[new Random().nextInt(okEquivalents.length)]);
    }

}
