package cn.huanhuan.app;

import javafx.scene.Parent;
import javafx.scene.text.Font;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** 显式注册随应用分发的静态字体，避免依赖不同系统上的中文字体回退。 */
final class AppStyles {
    private static final Font REGULAR = loadFont("NotoSansCJKsc-Regular.otf");
    private static final Font BOLD = loadFont("NotoSansCJKsc-Bold.otf");

    private AppStyles() {}

    static Font regularFont() { return REGULAR; }

    static void apply(Parent root) {
        if (!REGULAR.getFamily().equals(BOLD.getFamily())) {
            throw new IllegalStateException("内置字体的常规体与粗体不属于同一字体家族。");
        }
        root.getStylesheets().add(Objects.requireNonNull(
                AppStyles.class.getResource("/styles/app.css")).toExternalForm());
    }

    private static Font loadFont(String file) {
        try (InputStream stream = AppStyles.class.getResourceAsStream("/fonts/" + file)) {
            if (stream == null) throw new IllegalStateException("缺少内置字体：" + file);
            Font font = Font.loadFont(stream, 13);
            if (font == null) throw new IllegalStateException("无法加载内置字体：" + file);
            return font;
        } catch (IOException e) {
            throw new IllegalStateException("无法读取内置字体：" + file, e);
        }
    }
}
