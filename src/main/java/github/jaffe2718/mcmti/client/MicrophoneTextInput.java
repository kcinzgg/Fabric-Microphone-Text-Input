package github.jaffe2718.mcmti.client;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.midnightdust.lib.config.MidnightConfig;
import github.jaffe2718.mcmti.config.McmtiConfig;
import github.jaffe2718.mcmti.util.AudioRecorder;
import github.jaffe2718.mcmti.util.EventSystem;
import github.jaffe2718.mcmti.util.SpeechRecognizer;
//import io.github.givimad.whisperjni.WhisperJNI;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/**
 * 麦克风文本输入模组的主类
 * 实现了 Fabric 模组的客户端初始化接口
 * 负责初始化配置、加载库、注册按键绑定等核心功能
 */
public class MicrophoneTextInput implements ClientModInitializer {

    /** 模组ID，用于标识和引用 */
    public static final String MOD_ID = "mcmti";
    
    /** 日志记录器，用于输出模组运行日志 */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    /** 
     * 语音识别按键绑定
     * 默认使用 V 键
     * 用于触发语音录制和识别
     */
    public static final KeyBinding RECOGNIZE_KEY = new KeyBinding(
        "key.mcmti.recognize", 
        InputUtil.Type.KEYSYM, 
        InputUtil.GLFW_KEY_V, 
        "key.categories.mcmti"
    );

    /**
     * 高级配置状态标志
     * 用于监听配置变更
     * @see McmtiConfig#advancedConfig
     */
    public static volatile boolean advancedConfig = false;

    /**
     * 模组初始化方法
     * 在 Minecraft 客户端启动时调用
     * 负责初始化所有必要的组件和配置
     */
    @Override
    public void onInitializeClient() {
        // 初始化配置系统
        MidnightConfig.init(MOD_ID, McmtiConfig.class);
        advancedConfig = McmtiConfig.advancedConfig;
        
//        try {
            // 如果启用了高级配置且指定了自定义库目录，则设置系统属性
            if (McmtiConfig.advancedConfig && !McmtiConfig.whisperjniLibdir.isBlank()) {
                System.setProperty("io.github.givimad.whisperjni.libdir", McmtiConfig.whisperjniLibdir);
            }
            // 加载 Whisper 库并设置日志级别
//            WhisperJNI.loadLibrary(McmtiConfig.whisperLogLevel::log);
//            WhisperJNI.setLibraryLogger(McmtiConfig.whisperLogLevel::log);
//        } catch (IOException ignored) {}
        
        // 注册按键绑定
        KeyBindingHelper.registerKeyBinding(RECOGNIZE_KEY);
        
        // 初始化音频录制器
        AudioRecorder.init();
        
        // 初始化语音识别器
        SpeechRecognizer.init();
        
        // 注册事件系统
        EventSystem.register();
    }
}
