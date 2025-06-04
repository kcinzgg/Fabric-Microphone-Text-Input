package github.jaffe2718.mcmti.util;

import java.io.ByteArrayInputStream;

import org.jetbrains.annotations.NotNull;


import javax.sound.sampled.AudioInputStream;

import static github.jaffe2718.mcmti.util.AudioRecorder.AUDIO_FORMAT;

/**
 * 语音识别器类
 * 负责使用 Whisper 库进行语音识别
 * 提供语音识别、编码修复等功能
 */
public final class SpeechRecognizer {

    /**
     * 单例实例
     */
    private static volatile SpeechRecognizer INSTANCE;

    /**
     * 获取语音识别器实例
     *
     * @return 当前活动的语音识别器实例
     */
    public static SpeechRecognizer instance() {
        return INSTANCE;
    }

    /**
     * 初始化语音识别器
     * 加载模型和语法文件
     */
    public static void init() {
        destroy();
        if (INSTANCE == null) {
            INSTANCE = new SpeechRecognizer();
        }
    }

    /**
     * 销毁语音识别器实例
     * 释放资源并清理上下文
     */
    public static void destroy() {
        if (INSTANCE != null) {
            INSTANCE = null;
        }
    }

    /**
     * 识别音频数据
     * 将音频数据转换为文本
     *
     * @param audio 音频数据，浮点数组形式
     * @return 识别结果文本
     */
    public static @NotNull String recognizeWithDouBao(byte[] audio) {
        ByteArrayInputStream bais = new ByteArrayInputStream(audio);
        AudioInputStream ais = new AudioInputStream(bais, AUDIO_FORMAT, audio.length / AUDIO_FORMAT.getFrameSize());
        return DBRecognizer.recognize(ais);
    }
}
