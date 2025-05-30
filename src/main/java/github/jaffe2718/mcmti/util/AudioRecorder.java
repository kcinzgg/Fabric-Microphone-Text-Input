package github.jaffe2718.mcmti.util;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

import org.jetbrains.annotations.NotNull;

import github.jaffe2718.mcmti.client.MicrophoneTextInput;
import github.jaffe2718.mcmti.config.McmtiConfig;

/**
 * 音频录制器类
 * 负责从麦克风捕获音频数据
 * 提供固定周期和动态录音两种模式
 */
public final class AudioRecorder {
    /** 音频格式配置：16kHz采样率，16位深度，单声道，有符号，小端序 */
    public static final AudioFormat AUDIO_FORMAT = new AudioFormat(16000, 16, 1, true, false);
    
    /** 单例实例 */
    private static AudioRecorder INSTANCE;
    
    /** 音频输入设备 */
    private final TargetDataLine line;

    /**
     * 销毁音频录制器实例
     * 关闭音频输入设备并清理资源
     */
    public static void destroy() {
        if (INSTANCE != null) {
            INSTANCE.line.close();
            INSTANCE = null;
        }
    }

    /**
     * 初始化音频录制器
     * 创建新的实例并设置音频输入设备
     */
    public static void init() {
        destroy();
        try {
            INSTANCE = new AudioRecorder();
        } catch (LineUnavailableException e) {
            MicrophoneTextInput.LOGGER.error("Failed to initialize audio recorder", e);
        }
    }

    /**
     * 获取音频录制器实例
     * @return 当前活动的音频录制器实例
     */
    public static AudioRecorder instance() {
        return INSTANCE;
    }

    /**
     * 私有构造函数
     * 初始化音频输入设备
     * @throws LineUnavailableException 如果音频设备不可用
     */
    private AudioRecorder() throws LineUnavailableException {
        line = AudioSystem.getTargetDataLine(AUDIO_FORMAT);
        line.open(AUDIO_FORMAT);
    }

    /**
     * 将字节数组转换为浮点数组
     * 用于音频数据处理
     * @param data 输入的字节数组
     * @return 转换后的浮点数组，值范围在[-1.0, 1.0]之间
     */
    private static float @NotNull [] toFloatArray(byte @NotNull [] data) {
        float[] result = new float[data.length / 2];
        ShortBuffer shortBuffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        for (int i = 0; i < result.length; i++) {
            result[i] = Float.max(-1f, Float.min(((float) shortBuffer.get()) / (float) Short.MAX_VALUE, 1f));
        }
        return result;
    }

    /**
     * 固定周期录音
     * 用于 AUTO_SEND 模式
     * @return 录制的音频数据，以浮点数组形式返回
     */
    public static float @NotNull [] recordCycle() {
        INSTANCE.line.start();
        byte[] buf = new byte[McmtiConfig.recordCycleMs * 32];
        int read = INSTANCE.line.read(buf, 0, buf.length);
        INSTANCE.line.stop();
        INSTANCE.line.flush();
        if (read > 0) {
            return toFloatArray(buf);
        }
        return new float[0];
    }

//    /**
//     * 动态录音
//     * 用于 RELEASE_KEY_TO_SEND 和 RELEASE_KEY_TO_INPUT 模式
//     * 持续录音直到按键释放
//     * @return 录制的音频数据，以浮点数组形式返回
//     */
//    public static float @NotNull [] record() {
//        assert McmtiConfig.mode != McmtiConfig.Mode.AUTO_SEND;
//        ByteArrayOutputStream dynamicBuffer = new ByteArrayOutputStream();
//        byte[] chunk = new byte[McmtiConfig.recordBufferSize];
//        INSTANCE.line.start();
//        while (MicrophoneTextInput.RECOGNIZE_KEY.isPressed()) {
//            int read = INSTANCE.line.read(chunk, 0, chunk.length);
//            if (read > 0) {
//                dynamicBuffer.write(chunk, 0, read);
//            }
//        }
//        INSTANCE.line.stop();
//        INSTANCE.line.flush();
//        return toFloatArray(dynamicBuffer.toByteArray());
//    }
    /**
     * 动态录音
     * 用于 RELEASE_KEY_TO_SEND 和 RELEASE_KEY_TO_INPUT 模式
     * 持续录音直到按键释放
     * @return 录制的音频数据，以浮点数组形式返回
     */
    public static byte @NotNull [] record() {
//        assert McmtiConfig.mode != McmtiConfig.Mode.AUTO_SEND;
        ByteArrayOutputStream dynamicBuffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[McmtiConfig.recordBufferSize];
        INSTANCE.line.start();
        while (MicrophoneTextInput.RECOGNIZE_KEY.isPressed()) {
            int read = INSTANCE.line.read(chunk, 0, chunk.length);
            if (read > 0) {
                dynamicBuffer.write(chunk, 0, read);
            }
        }
        INSTANCE.line.stop();
        INSTANCE.line.flush();
        return dynamicBuffer.toByteArray();
    }
}
