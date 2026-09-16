package cn.huanhuan.app;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

class AppSettingsTest {
    @TempDir Path directory;
    private final Preferences preferences = Preferences.userRoot().node("/cn/huanhuan/tests/" + UUID.randomUUID());

    @AfterEach void cleanup() throws Exception { preferences.removeNode(); preferences.flush(); }

    @Test void usesDownloadsUntilSavedAndRestoresSelectionInNewInstance() throws Exception {
        AppSettings settings = new AppSettings(preferences, directory);
        assertEquals(directory.toAbsolutePath(), settings.outputDirectory());
        Path selected = Files.createDirectory(directory.resolve("用户选定目录"));
        settings.saveOutputDirectory(selected);
        assertEquals(selected.toAbsolutePath(), new AppSettings(preferences, directory).outputDirectory());
    }

    @Test void invalidDirectoryDoesNotReplaceExistingSelection() throws Exception {
        AppSettings settings = new AppSettings(preferences, directory);
        settings.saveOutputDirectory(directory);
        assertThrows(IOException.class, () -> settings.saveOutputDirectory(directory.resolve("不存在")));
        assertEquals(directory.toAbsolutePath(), new AppSettings(preferences, directory).outputDirectory());
    }
}
