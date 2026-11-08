package chatapp.client;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Ghi âm giọng nói (Voice Note) từ Microphone sử dụng chuẩn Java Sound API
 */
public class AudioRecorder {
    private TargetDataLine targetLine;
    private ByteArrayOutputStream recordStream;
    private boolean isRecording = false;
    private Thread recordThread;

    public static AudioFormat getAudioFormat() {
        float sampleRate = 16000;
        int sampleSizeInBits = 16;
        int channels = 1; // Mono
        boolean signed = true;
        boolean bigEndian = false;
        return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
    }

    public synchronized boolean startRecording() {
        if (isRecording) return false;

        AudioFormat format = getAudioFormat();
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        if (!AudioSystem.isLineSupported(info)) {
            System.err.println("[AudioRecorder] Micro không được hỗ trợ trên thiết bị này!");
            return false;
        }

        try {
            targetLine = (TargetDataLine) AudioSystem.getLine(info);
            targetLine.open(format);
            targetLine.start();

            recordStream = new ByteArrayOutputStream();
            isRecording = true;

            recordThread = new Thread(() -> {
                byte[] buffer = new byte[2048];
                while (isRecording) {
                    int count = targetLine.read(buffer, 0, buffer.length);
                    if (count > 0) {
                        recordStream.write(buffer, 0, count);
                    }
                }
            });
            recordThread.start();
            return true;
        } catch (LineUnavailableException e) {
            System.err.println("[AudioRecorder] Lỗi mở micro: " + e.getMessage());
            return false;
        }
    }

    /**
     * Dừng ghi âm và xuất ra định dạng WAV hoàn chỉnh kèm Header
     */
    public synchronized byte[] stopRecording() {
        if (!isRecording) return null;
        isRecording = false;

        if (targetLine != null) {
            targetLine.stop();
            targetLine.close();
        }

        try {
            if (recordThread != null) {
                recordThread.join(1000);
            }
        } catch (InterruptedException ignored) {}

        byte[] rawPcmData = recordStream.toByteArray();
        if (rawPcmData.length == 0) return null;

        // Đóng gói thành định dạng chuẩn WAV để mọi player đều đọc được
        return pcmToWav(rawPcmData, getAudioFormat());
    }

    public boolean isRecording() {
        return isRecording;
    }

    private byte[] pcmToWav(byte[] pcmData, AudioFormat format) {
        long totalDataLen = pcmData.length + 36;
        long totalAudioLen = pcmData.length;
        long longSampleRate = (long) format.getSampleRate();
        int channels = format.getChannels();
        long byteRate = 16 * longSampleRate * channels / 8;

        byte[] header = new byte[44];
        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0;
        header[20] = 1; header[21] = 0; // PCM
        header[22] = (byte) channels; header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (channels * 16 / 8); header[33] = 0;
        header[34] = 16; header[35] = 0; // bits per sample
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(header);
            out.write(pcmData);
        } catch (IOException ignored) {}
        return out.toByteArray();
    }
}

