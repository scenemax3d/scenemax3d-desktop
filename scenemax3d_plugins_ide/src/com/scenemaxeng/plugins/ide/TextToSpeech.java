package com.scenemaxeng.plugins.ide;

import com.sun.speech.freetts.Voice;
import com.sun.speech.freetts.VoiceManager;

import java.util.Locale;

public class TextToSpeech {

    public static void init() {
        // "com.sun.speech.freetts.en.us.cmu_us_kal.KevinVoiceDirectory"
        // 'com.sun.speech.freetts.en.us.cmu_us_slt_arctic.ArcticVoiceDirectory'
        // de.dfki.lt.freetts.en.us.MbrolaVoiceDirectory

        System.setProperty("freetts.voices",
                "com.sun.speech.freetts.en.us.cmu_us_kal.KevinVoiceDirectory");

    }

    public static void speak(String text) {
        // The name of the voice
        String voiceName = "kevin16"; // "kevin16"; // kevin, kevin16, mbrola_us1, mbrola_us2, or mbrola_us3
        VoiceManager voiceManager = VoiceManager.getInstance();
        Voice voice = voiceManager.getVoice(voiceName);
        if (voice != null) {
            voice.allocate();
            voice.speak(text);
            voice.deallocate();
        } else {
            System.err.println("Voice not found: " + voiceName);
        }
    }
}
