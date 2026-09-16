package cn.huanhuan.app;

import cn.huanhuan.platform.DownloadsDirectory;

import java.io.IOException;
import java.nio.file.*;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** 用户设置独立于安装目录保存，升级应用后继续沿用。 */
public final class AppSettings {
    private static final String OUTPUT_DIRECTORY = "outputDirectory";
    private final Preferences preferences;
    private final Path defaultDirectory;

    public AppSettings(Preferences preferences, Path defaultDirectory) {
        this.preferences = preferences;
        this.defaultDirectory = defaultDirectory.toAbsolutePath().normalize();
    }

    public static AppSettings userSettings() {
        return new AppSettings(Preferences.userRoot().node("/cn/huanhuan/timeliness"), DownloadsDirectory.resolve());
    }

    public Path outputDirectory() {
        String stored = preferences.get(OUTPUT_DIRECTORY, "");
        if (stored.isBlank()) return defaultDirectory;
        try { return Path.of(stored); }
        catch (InvalidPathException ignored) { return defaultDirectory; }
    }

    public void saveOutputDirectory(Path directory) throws IOException {
        Path normalized = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) throw new IOException("目录不存在，请选择有效的保存目录。");
        String previous = preferences.get(OUTPUT_DIRECTORY, null);
        preferences.put(OUTPUT_DIRECTORY, normalized.toString());
        try { preferences.flush(); }
        catch (BackingStoreException e) {
            if (previous == null) preferences.remove(OUTPUT_DIRECTORY);
            else preferences.put(OUTPUT_DIRECTORY, previous);
            throw new IOException("设置未能保存，请重试。", e);
        }
    }
}
