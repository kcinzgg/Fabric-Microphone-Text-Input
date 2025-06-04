package github.jaffe2718.mcmti.config;

import eu.midnightdust.lib.config.MidnightConfig;


public class McmtiConfig extends MidnightConfig {

    public enum Mode {
//        AUTO_SEND,
        RELEASE_KEY_TO_SEND,
//        RELEASE_KEY_TO_INPUT,
    }

    @Entry(category = "general")
    public static Mode mode = Mode.RELEASE_KEY_TO_SEND;

    @Entry(category = "general", min = 1024, max = 65536)
    @Condition(requiredOption = "mode", requiredValue = "AUTO_SEND")
    public static int recordCycleMs = 5000;    // unit: ms, (sampleRate = 16000Hz), default: 5s

    @Entry(category = "general", min = 64, max = 4096)
    @Condition(requiredOption = "mode", requiredValue = {"RELEASE_KEY_TO_SEND", "RELEASE_KEY_TO_INPUT"})
    public static int recordBufferSize = 1024;    // unit: byte, default: 1024 bytes

    @Entry(category = "general")
    public static LLMModel llmModel = LLMModel.DOUBAO;

    @Entry(category = "general")
    public static VoiceType voiceType = VoiceType.BEIJING_XIAOYE;

    public enum LLMModel {
        DOUBAO, OPENAI
    }

    public enum VoiceType {
        BEIJING_XIAOYE("北京小爷", "zh_male_beijingxiaoye_emo_v2_mars_bigtts"),
        ROUMEINVYOU("柔美女友", "zh_female_roumeinvyou_emo_v2_mars_bigtts"),
        MEILINVYOU("魅力女友", "zh_female_meilinvyou_emo_v2_mars_bigtts"),
        XIAOHE("小何", "zh_female_wanwanxiaohe_moon_bigtts"),
        YANGGUANG_QINGNIAN("阳光青年", "zh_male_yangguangqingnian_emo_v2_mars_bigtts"),
        ;

        public final String name;         // 音色中文名
        public final String code;         // voice_type参数

        VoiceType(String name, String code) {
            this.name = name;
            this.code = code;
        }
    }
}
