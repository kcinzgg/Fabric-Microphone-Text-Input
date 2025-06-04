package github.jaffe2718.mcmti.util;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.locks.LockSupport;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import org.jetbrains.annotations.Nullable;

import eu.midnightdust.lib.config.MidnightConfig;
import github.jaffe2718.mcmti.client.MicrophoneTextInput;
import github.jaffe2718.mcmti.client.gui.screen.AdvancedConfigWarningScreen;
import github.jaffe2718.mcmti.config.McmtiConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;

import javax.sound.sampled.AudioFormat;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * 事件系统类
 * 负责处理模组的所有事件和状态管理
 * 包括配置变更、状态显示、语音识别等
 */
public abstract class EventSystem {

    /**
     * 注册所有事件监听器
     * 包括配置变更、世界状态、客户端停止等事件
     * 并启动语音识别任务线程
     */
    public static void register() {
        // 注册配置变更监听器
        ClientTickEvents.END_CLIENT_TICK.register(EventSystem::onConfigAltered);
        // 注册世界状态监听器
        ClientTickEvents.END_WORLD_TICK.register(EventSystem::showRecognizeStatus);
        // 注册客户端停止事件监听器
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            SpeechRecognizer.destroy();
            AudioRecorder.destroy();
        });
        // 启动语音识别任务线程
        Thread.ofVirtual().start(EventSystem::recognizeTask).setName("thread.mcmti.recognizer.loop");

        // 聊天消息监听
        ClientReceiveMessageEvents.CHAT.register((text, signedMessage, gameProfile, parameters, instant) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            String myName = client.player.getName().getString();
            String msg = text.getString();
            // sender 可能为 null（如系统消息），需判空
            if (gameProfile != null) {
                String senderName = gameProfile.getName();
                if (!senderName.equals(myName)) {
//                if (senderName.equals("andy")) {
                    // 你可以在这里做进一步处理
                    msg = msg.substring(msg.lastIndexOf(">"));
                    DBTextToSpeech.toSpeechAsync(msg);
                }
            }
        });
    }

    /**
     * 显示语音识别状态
     * 在游戏世界中显示当前状态信息
     *
     * @param world 当前客户端世界
     */
    private static void showRecognizeStatus(ClientWorld world) {
        if (MinecraftClient.getInstance().player instanceof ClientPlayerEntity player
                && MinecraftClient.getInstance().currentScreen == null) {
            // 检查音频设备状态
            if (AudioRecorder.instance() == null) {
                player.sendMessage(Text.translatable("message.mcmti.audioInputDeviceLoadFailed"), true);
            }
            // 检查语音识别模型状态
            else if (SpeechRecognizer.instance() == null) {
                player.sendMessage(Text.translatable("message.mcmti.whisperModelLoadFailed"), true);
            }
            // 显示录音状态
            else if (MicrophoneTextInput.RECOGNIZE_KEY.isPressed()) {
                player.sendMessage(Text.translatable("message.mcmti.recordingAudio"), true);
            }
        }
    }

    /**
     * 处理配置变更
     * 同步配置状态并重新初始化必要的组件
     *
     * @param client Minecraft客户端实例
     */
    private static void onConfigAltered(MinecraftClient client) {
        // 处理高级配置警告
//        if (McmtiConfig.advancedConfig
//                && !MicrophoneTextInput.advancedConfig
//                && MinecraftClient.getInstance().currentScreen instanceof MidnightConfig.MidnightConfigScreen) {
//            MinecraftClient.getInstance().setScreen(new AdvancedConfigWarningScreen(MinecraftClient.getInstance().currentScreen));
//        }
        // 检查模型路径变更
//        if (!McmtiConfig.model.equals(SpeechRecognizer.modelPath)
//                || !McmtiConfig.grammar.equals(SpeechRecognizer.grammarPath)) {
//            SpeechRecognizer.init();
//        }
        // 同步高级配置状态
//        MicrophoneTextInput.advancedConfig = McmtiConfig.advancedConfig;
    }

    /**
     * 语音识别任务
     * 持续运行的后台任务，处理语音识别逻辑
     * 根据不同的模式执行相应的操作
     */
    @SuppressWarnings("InfiniteLoopStatement")
    private static void recognizeTask() {
        MicrophoneTextInput.LOGGER.info("Recognize thread started");
        @Nullable Thread vthread = null;
        while (true) {
            try {
                if (MinecraftClient.getInstance().player instanceof ClientPlayerEntity player
                        && MinecraftClient.getInstance().currentScreen == null
                        && AudioRecorder.instance() != null
                        && SpeechRecognizer.instance() != null) {
                    // 根据不同模式处理语音识别
                    switch (McmtiConfig.mode) {
//                        case AUTO_SEND -> {
//                            // 自动发送模式：固定周期录音并发送
//                            float[] audio = AudioRecorder.recordCycle();
//                            Thread.ofVirtual().start(() -> {
//                                String result = SpeechRecognizer.recognize(audio);
//                                if (!result.isEmpty()) {
//                                    player.sendMessage(Text.translatable("message.mcmti.messageSent"), true);
//                                    player.networkHandler.sendChatMessage(McmtiConfig.prefix + result);
//                                }
//                            });
//                        }
                        case RELEASE_KEY_TO_SEND -> {
                            // 松开按键发送模式：持续录音直到按键释放
                            if (MicrophoneTextInput.RECOGNIZE_KEY.isPressed()) {
//                                float[] audio = AudioRecorder.record();
                                byte[] audio = AudioRecorder.record();
                                AudioPlayer.play(audio);
                                vthread = Thread.ofVirtual().start(() -> {
//                                    String result = SpeechRecognizer.recognize(audio);
                                    String result = SpeechRecognizer.recognizeWithDouBao(audio);
                                    MicrophoneTextInput.LOGGER.info("recognized result: {}", result);
                                    if (!result.isEmpty()) {
                                        player.sendMessage(Text.translatable("message.mcmti.messageSent"), true);
//                                        player.networkHandler.sendChatMessage(McmtiConfig.prefix + result);
                                        player.networkHandler.sendChatMessage(result);
                                    }
                                });
                            } else if (vthread != null && vthread.isAlive()) {
                                player.sendMessage(Text.translatable("message.mcmti.recognizing"), true);
                            }
                        }
//                        case RELEASE_KEY_TO_INPUT -> {
//                            // 松开按键输入模式：持续录音直到按键释放，然后打开聊天框
//                            if (MicrophoneTextInput.RECOGNIZE_KEY.isPressed()) {
//                                float[] audio = AudioRecorder.record();
//                                vthread = Thread.ofVirtual().start(() -> {
//                                    String result = SpeechRecognizer.recognize(audio);
//                                    if (!result.isEmpty()) {
//                                        MinecraftClient.getInstance().setScreen(new ChatScreen(McmtiConfig.prefix + result));
//                                    }
//                                });
//                            } else if (vthread != null && vthread.isAlive()) {
//                                player.sendMessage(Text.translatable("message.mcmti.recognizing"), true);
//                            }
//                        }
                    }
                } else {
                    // 当条件不满足时，暂停线程以降低CPU使用率
                    LockSupport.parkNanos(10000000L);
                }
            } catch (Throwable t) {
                MicrophoneTextInput.LOGGER.error("Error in recognize task", t);
            }
        }
    }
}
