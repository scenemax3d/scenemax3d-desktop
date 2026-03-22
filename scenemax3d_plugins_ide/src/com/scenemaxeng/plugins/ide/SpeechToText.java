package com.scenemaxeng.plugins.ide;

import com.assemblyai.api.AssemblyAI;
import com.assemblyai.api.RealtimeTranscriber;
import com.assemblyai.api.resources.transcripts.types.*;
import com.scenemaxeng.common.types.ISceneMaxPlugin;

import java.util.List;
import java.util.Optional;
import javax.sound.sampled.*;
import javax.swing.*;

public class SpeechToText {

    private static Thread thread;
    public static String currentText = "";
    private static TargetDataLine line;
    private static boolean interrupt = false;

    public static void startTranscribeRealTime(ISceneMaxPlugin host) {
        interrupt = false;
        currentText = "";
        thread = null;
        line = null;
        String apiKey = System.getenv("S3D_SPEECH_API_KEY");
        if(apiKey == null) {
            System.out.println("SceneMax3D - no speech API key found");
            return;
        }

        SpeechToText.thread = new Thread(() -> {
            try {
                RealtimeTranscriber realtimeTranscriber = RealtimeTranscriber.builder()
                        .apiKey(apiKey)
                        .sampleRate(16_000)
                        .onSessionBegins(sessionBegins -> {
                            currentText = "";
                            System.out.println(
                                    "Session opened with ID: " + sessionBegins.getSessionId());

                        })
                        .onPartialTranscript(transcript -> {
                            if (!transcript.getText().isEmpty()) {
                                SwingUtilities.invokeLater(() -> {
                                    // Update the UI
                                    host.progress(transcript.getText(), "partial");
                                });
                            }
                        })
                        .onFinalTranscript(transcript -> {
                            currentText += transcript.getText();
                            SwingUtilities.invokeLater(() -> {
                                // Update the UI
                                host.progress(currentText, "final");
                            });
                        })
                        .onError(err -> {
                            System.out.println("Error: " + err.getMessage());
                        })
                        .wordBoost(List.of("gemini", "racing", "game", "fighting", "race", "track"))
                        .build();

                System.out.println("Connecting to real-time transcript service");
                realtimeTranscriber.connect();

                System.out.println("Starting recording");
                AudioFormat format = new AudioFormat(16_000, 16, 1, true, false);
                // `line` is your microphone
                line = AudioSystem.getTargetDataLine(format);
                line.open(format);
                byte[] data = new byte[line.getBufferSize()];
                line.start();
                while (!interrupt) {
                    // Read the next chunk of data from the TargetDataLine.
                    line.read(data, 0, data.length);
                    realtimeTranscriber.sendAudio(data);
                }

                System.out.println("Stopping recording");
                line.close();

                System.out.println("Closing real-time transcript connection");
                realtimeTranscriber.close();

                host.stop();

            } catch (LineUnavailableException e) {
                throw new RuntimeException(e);
            }
        });
        thread.start();

    }

    public static boolean isInProgress() {
        return thread != null && thread.isAlive();
    }

    public static void endTranscribeRealTime() {
        interrupt = true;
    }

    public static void clearUserInputText() {
        currentText = "";
    }

}
