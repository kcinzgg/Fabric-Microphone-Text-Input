package github.jaffe2718.mcmti.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import github.jaffe2718.mcmti.client.MicrophoneTextInput;
import github.jaffe2718.mcmti.config.McmtiConfig;
//import io.github.givimad.whisperjni.WhisperContext;
//import io.github.givimad.whisperjni.WhisperFullParams;
//import io.github.givimad.whisperjni.WhisperGrammar;
//import io.github.givimad.whisperjni.WhisperJNI;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * 语音识别器类
 * 负责使用 Whisper 库进行语音识别
 * 提供语音识别、编码修复等功能
 */
public final class SpeechRecognizer {
    /** Whisper JNI 实例，用于与本地库交互 */
//    public static final WhisperJNI WHISPER = new WhisperJNI();

    /**
     * 单例实例
     */
    private static volatile SpeechRecognizer INSTANCE;

    /** Whisper 上下文，用于语音识别 */
//    final @NotNull WhisperContext ctx;

    /** 语法规则，用于约束识别结果 */
//    final @Nullable WhisperGrammar grammar;

    /**
     * 模型文件路径
     */
    public static String modelPath = "";

    /**
     * 语法文件路径
     */
    public static String grammarPath = "";

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
        modelPath = McmtiConfig.model;
        MicrophoneTextInput.LOGGER.info("modelPath: {}", modelPath);
        grammarPath = McmtiConfig.advancedConfig ? McmtiConfig.grammar : "";

        if (INSTANCE == null) {
//            try {
            INSTANCE = new SpeechRecognizer();
//            } catch (IOException e) {
//                MicrophoneTextInput.LOGGER.error("Failed to initialize speech recognizer", e);
//            }
        }
    }

    /**
     * 销毁语音识别器实例
     * 释放资源并清理上下文
     */
    public static void destroy() {
        if (INSTANCE != null) {
//            INSTANCE.ctx.close();
//            if (INSTANCE.grammar != null) {
//                WHISPER.free(INSTANCE.grammar);
//            }
            INSTANCE = null;
        }
    }

    /**
     * 修复文本编码
     * 在源编码和目标编码之间转换文本
     *
     * @param str         需要修复的文本
     * @param srcEncoding 源编码
     * @param dstEncoding 目标编码
     * @return 修复后的文本
     */
    @Contract("_, _, _ -> new")
    private static @NotNull String repairEncoding(@NotNull String str, String srcEncoding, String dstEncoding) {
        try {
            return new String(str.getBytes(srcEncoding), dstEncoding);
        } catch (UnsupportedEncodingException uee) {
            MicrophoneTextInput.LOGGER.error("Couldn't repair encoding, using default", uee);
            return str;
        }
    }

    /**
     * 识别音频数据
     * 将音频数据转换为文本
     * @param audio 音频数据，浮点数组形式
     * @return 识别结果文本
     */
//    public static @NotNull String recognize(float[] audio) {
//        if (INSTANCE == null) return "";
//        WhisperFullParams params = McmtiConfig.getParams();
//        params.grammar = INSTANCE.grammar;
//        int flag = WHISPER.full(INSTANCE.ctx, params, audio, audio.length);
//        if (flag == 0) {
//            String result = WHISPER.fullGetSegmentText(INSTANCE.ctx, 0);
//            if (McmtiConfig.encodingRepair) {
//                return repairEncoding(result, McmtiConfig.srcEncoding, McmtiConfig.dstEncoding);
//            } else {
//                return result;
//            }
//        }
//        return "";
//    }

    /**
     * 识别音频数据
     * 将音频数据转换为文本
     *
     * @param audio 音频数据，浮点数组形式
     * @return 识别结果文本
     */
    public static @NotNull String recognizeWithDouBao(byte[] audio) {
        AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
        ByteArrayInputStream bais = new ByteArrayInputStream(audio);
        AudioInputStream ais = new AudioInputStream(bais, format, audio.length / format.getFrameSize());
        return DBRecognizer.recognize(ais);
    }

//    /**
//     * 私有构造函数
//     * 初始化 Whisper 上下文和语法规则
//     * @throws IOException 如果模型或语法文件加载失败
//     */
//    private SpeechRecognizer() throws IOException {
//        this.ctx = WHISPER.init(Path.of(modelPath));
//        if (!grammarPath.isEmpty()) {
//            this.grammar = WHISPER.parseGrammar(grammarPath);
//        } else {
//            this.grammar = null;
//        }
//    }
}
