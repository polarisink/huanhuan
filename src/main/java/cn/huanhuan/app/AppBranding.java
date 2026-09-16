package cn.huanhuan.app;

import javafx.scene.image.Image;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.Taskbar;
import java.io.IOException;
import java.util.Objects;

final class AppBranding {
    static final String NAME = "欢欢";
    private static final String ICON = "/icons/huanhuan.png";

    private AppBranding() {}

    static Image icon(double size) {
        return new Image(Objects.requireNonNull(AppBranding.class.getResource(ICON)).toExternalForm(),
                size, size, true, true);
    }

    static void configureWindow(Stage stage) {
        stage.setTitle(NAME);
        for (int size : new int[]{16, 32, 64, 128, 256, 512}) stage.getIcons().add(icon(size));
    }

    static void configureTaskbar() {
        if (!Taskbar.isTaskbarSupported()) return;
        try {
            Taskbar taskbar = Taskbar.getTaskbar();
            if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                taskbar.setIconImage(ImageIO.read(Objects.requireNonNull(AppBranding.class.getResource(ICON))));
            }
        } catch (IOException | UnsupportedOperationException | SecurityException ignored) {
            // 不支持动态图标的平台使用打包脚本指定的原生应用图标。
        }
    }
}
