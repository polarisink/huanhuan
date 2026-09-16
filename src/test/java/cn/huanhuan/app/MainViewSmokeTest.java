package cn.huanhuan.app;

import cn.huanhuan.core.Column;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.UUID;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

/** 需要桌面会话；默认测试不启动窗口。 */
@EnabledIfSystemProperty(named = "uiTests", matches = "true")
class MainViewSmokeTest {
    @TempDir Path directory;

    @Test void displaysValidationErrorThenProcessesAndShowsResult() throws Exception {
        CountDownLatch initialized = new CountDownLatch(1);
        Platform.startup(initialized::countDown);
        assertTrue(initialized.await(10, TimeUnit.SECONDS));
        Preferences preferences = Preferences.userRoot().node("/cn/huanhuan/tests/" + UUID.randomUUID());
        AppSettings settings = new AppSettings(preferences, directory);
        Path selectedDirectory = Files.createDirectory(directory.resolve("保存结果"));
        Stage stage = fx(() -> {
            Stage window = new Stage();
            AppBranding.configureWindow(window);
            MainView view = new MainView(window, settings, path -> {});
            Scene scene = new Scene(view, 840, 580);
            window.setScene(scene);
            window.show();
            return window;
        });
        try {
            capture(stage, "initial");
            assertEquals("Noto Sans CJK SC", fx(() -> ((Label) stage.getScene().lookup(".subtitle")).getFont().getFamily()));
            assertEquals("Noto Sans CJK SC", fx(() -> ((Button) stage.getScene().lookup("#choose-input")).getFont().getName()));
            assertEquals("Noto Sans CJK SC Bold", fx(() -> ((Button) stage.getScene().lookup("#start-processing")).getFont().getName()));
            assertNull(fx(() -> stage.getScene().lookup("#output-path")));
            DialogPane dialog = openSettings(stage);
            assertEquals(directory.toString(), fx(() -> ((TextField) dialog.lookup("#settings-output-path")).getText()));
            fx(() -> {
                ((TextField) dialog.lookup("#settings-output-path")).setText(selectedDirectory.toString());
                ((Button) dialog.lookup("#cancel-settings")).fire();
                return null;
            });
            assertEquals(directory, settings.outputDirectory());
            DialogPane savedDialog = openSettings(stage);
            fx(() -> {
                ((TextField) savedDialog.lookup("#settings-output-path")).setText(directory.resolve("不存在").toString());
                ((Button) savedDialog.lookup("#save-settings")).fire();
                return null;
            });
            assertTrue(fx(() -> savedDialog.getScene().getWindow().isShowing()));
            assertFalse(fx(() -> ((Label) savedDialog.lookup("#settings-error")).getText()).isBlank());
            fx(() -> {
                ((TextField) savedDialog.lookup("#settings-output-path")).setText(selectedDirectory.toString());
                return null;
            });
            capture(fx(() -> (Stage) savedDialog.getScene().getWindow()), "settings");
            assertEquals("Noto Sans CJK SC", fx(() -> ((TextField) savedDialog.lookup("#settings-output-path")).getFont().getName()));
            fx(() -> { ((Button) savedDialog.lookup("#save-settings")).fire(); return null; });
            assertTrue(fx(() -> savedDialog.getScene() == null || !savedDialog.getScene().getWindow().isShowing()));
            assertEquals(selectedDirectory, new AppSettings(preferences, directory).outputDirectory());
            Path source = fixture(false);
            fx(() -> {
                ((TextField) stage.getScene().lookup("#input-path")).setText(source.toString());
                ((Button) stage.getScene().lookup("#start-processing")).fire();
                return null;
            });
            awaitIdle(stage);
            assertTrue(fx(() -> ((Label) stage.getScene().lookup("#status-message")).getText()).contains("第 2 行"));
            assertEquals("处理未完成", fx(() -> ((Label) stage.getScene().lookup("#result-state")).getText()));
            capture(stage, "invalid-data");
            fixture(true);
            fx(() -> { ((Button) stage.getScene().lookup("#start-processing")).fire(); return null; });
            awaitIdle(stage);
            assertEquals("处理完成", fx(() -> ((Label) stage.getScene().lookup("#result-state")).getText()));
            assertTrue(fx(() -> ((Label) stage.getScene().lookup("#status-message")).getText()).contains("1 条记录"));
            try (var files = Files.list(selectedDirectory)) {
                assertEquals(1, files.filter(p -> p.getFileName().toString().startsWith("时效统计_")).count());
            }
            capture(stage, "success");
        } finally {
            fx(() -> { stage.close(); return null; });
            Platform.exit();
            preferences.removeNode();
            preferences.flush();
        }
    }

    private static DialogPane openSettings(Stage stage) throws Exception {
        return fx(() -> {
            ((Button) stage.getScene().lookup("#open-settings")).fire();
            return Window.getWindows().stream().filter(Window::isShowing)
                    .map(window -> window.getScene().lookup("#settings-dialog"))
                    .filter(node -> node != null).map(node -> (DialogPane) node).findFirst().orElseThrow();
        });
    }

    private Path fixture(boolean valid) throws Exception {
        Path file = directory.resolve("界面测试.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            var sheet = wb.createSheet("明细");
            var header = sheet.createRow(0);
            for (int c = 0; c < 25; c++) header.createCell(c).setCellValue("字段" + c);
            for (Column column : Column.values()) header.getCell(column.index()).setCellValue(column.title());
            var row = sheet.createRow(1);
            for (int c = 0; c < 25; c++) row.createCell(c);
            row.getCell(8).setCellValue("2026/8/3 12:00:00");
            row.getCell(9).setCellValue("2026/8/1 12:00:00");
            row.getCell(12).setCellValue("科室B");
            if (valid) row.getCell(13).setCellValue("科室A");
            row.getCell(16).setCellValue("2026/8/2 13:00:00");
            row.getCell(18).setCellValue("2026/8/3 13:00:00");
            try (var out = Files.newOutputStream(file)) { wb.write(out); }
        }
        return file;
    }

    private static void awaitIdle(Stage stage) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (fx(() -> ((MainView) stage.getScene().getRoot()).isProcessing())) {
            if (System.nanoTime() > deadline) fail("UI 处理超时");
            Thread.sleep(80);
        }
    }

    private static void capture(Stage stage, String name) throws Exception {
        BufferedImage image = fx(() -> {
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            WritableImage snapshot = stage.getScene().snapshot(null);
            BufferedImage result = new BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(), BufferedImage.TYPE_INT_ARGB);
            var pixels = snapshot.getPixelReader();
            for (int y = 0; y < result.getHeight(); y++)
                for (int x = 0; x < result.getWidth(); x++) result.setRGB(x, y, pixels.getArgb(x, y));
            return result;
        });
        Path output = Files.createDirectories(Path.of("target/ui-verification")).resolve(name + ".png");
        ImageIO.write(image, "png", output.toFile());
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(15, TimeUnit.SECONDS);
    }
}
