package github.jaffe2718.mcmti.util;

import javax.sound.sampled.*;
import java.io.*;

import static github.jaffe2718.mcmti.util.AudioRecorder.AUDIO_FORMAT;

public class AudioPlayer {
    public static void playByteArray(byte[] audioBytes) throws LineUnavailableException, IOException {
        try (
                ByteArrayInputStream bais = new ByteArrayInputStream(audioBytes);
                AudioInputStream audioStream = new AudioInputStream(bais, AUDIO_FORMAT, audioBytes.length / AUDIO_FORMAT.getFrameSize())
        ) {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT);
            try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
                line.open(AUDIO_FORMAT);
                line.start();

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = audioStream.read(buffer)) != -1) {
                    line.write(buffer, 0, bytesRead);
                }

                line.drain();
                line.stop();
            }
        }
    }
}

