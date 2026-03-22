package com.scenemaxeng.plugins.ide;

import javax.sound.sampled.*;
import java.io.*;

public class AudioRecorder {

    // Define the audio format
    private static AudioFormat getAudioFormat() {
        float sampleRate = 44100;
        int sampleSizeInBits = 16;
        int channels = 2;
        boolean signed = true;
        boolean bigEndian = true;
        return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
    }

    // Method to start recording
    public static TargetDataLine startRecording(File outputFile) {
        AudioFormat format = getAudioFormat();
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        if (!AudioSystem.isLineSupported(info)) {
            System.err.println("Line not supported");
            return null;
        }

        try (TargetDataLine targetLine = (TargetDataLine) AudioSystem.getLine(info)) {
            targetLine.open(format);
            targetLine.start();

            // Create a thread to capture the audio data and write it to a file
            Thread captureThread = new Thread(() -> {
                try (AudioInputStream audioStream = new AudioInputStream(targetLine)) {
                    AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, outputFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            captureThread.start();

            // Let the recording run for a specified time or until stopped
            System.out.println("Recording started. Press enter to stop.");
            //System.in.read();  // Wait for the user to press enter to stop recording

            return targetLine;
        } catch (LineUnavailableException ex) {
            ex.printStackTrace();
        }

        return null;
    }

    public static void main(String[] args) {
        File outputFile = new File("recording.wav");
        startRecording(outputFile);
    }
}
