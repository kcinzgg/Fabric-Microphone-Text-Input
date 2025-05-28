package github.jaffe2718.mcmti.client.gui.screen;

import eu.midnightdust.lib.config.MidnightConfig;
import github.jaffe2718.mcmti.client.MicrophoneTextInput;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * 高级配置警告屏幕
 * 在用户启用高级配置选项时显示警告信息
 * 提醒用户高级配置可能带来的风险
 */
@Environment(EnvType.CLIENT)
public class AdvancedConfigWarningScreen extends ConfirmScreen {
    /** 父级屏幕，用于返回操作 */
    private final Screen parent;

    /**
     * 构造函数
     * 初始化警告屏幕的标题、内容和按钮
     * @param parent 父级屏幕
     */
    public AdvancedConfigWarningScreen(Screen parent) {
        super(AdvancedConfigWarningScreen::checkConfirmed,
                Text.translatable("mcmti.gui.config.advanced.warn.title"),
                Text.translatable("mcmti.gui.config.advanced.warn"),
                Text.translatable("gui.proceed").withColor(0xFF5555),
                Text.translatable("gui.cancel"));
        this.parent = parent;
    }

    private static void checkConfirmed(boolean confirmed) {
        if (!confirmed) {
            MidnightConfig.loadValuesFromJson(MicrophoneTextInput.MOD_ID);
        } else {
            MicrophoneTextInput.LOGGER.warn("Advanced config enabled");
        }
        if (MinecraftClient.getInstance().currentScreen instanceof AdvancedConfigWarningScreen screen) {
            screen.close();
        }
    }

    /**
     * 关闭屏幕
     * 重写父类方法，确保返回父级屏幕
     */
    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        } else {
            super.close();
        }
    }
}
