package chatapp.client;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.File;

/**
 * Phát âm thanh trực tiếp trong ứng dụng bằng Java Sound Clip
 */
public class AudioPlayer {
    private static Clip activeClip;

    public static synchronized void play(byte[] wavData) {
        if (wavData == null || wavData.length == 0) return;
        try {
            stop();
            ByteArrayInputStream bais = new ByteArrayInputStream(wavData);
            AudioInputStream ais = AudioSystem.getAudioInputStream(bais);
            activeClip = AudioSystem.getClip();
            activeClip.open(ais);
            activeClip.start();
        } catch (Exception e) {
            System.err.println("[AudioPlayer] Lỗi phát âm thanh: " + e.getMessage());
        }
    }

    public static synchronized void playFile(File file) {
        if (file == null || !file.exists()) return;
        try {
            stop();
            AudioInputStream ais = AudioSystem.getAudioInputStream(file);
            activeClip = AudioSystem.getClip();
            activeClip.open(ais);
            activeClip.start();
        } catch (Exception e) {
            System.err.println("[AudioPlayer] Lỗi phát file âm thanh: " + e.getMessage());
        }
    }

    public static synchronized void stop() {
        if (activeClip != null && activeClip.isRunning()) {
            activeClip.stop();
            activeClip.close();
        }
    }
}

