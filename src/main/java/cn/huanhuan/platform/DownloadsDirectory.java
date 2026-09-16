package cn.huanhuan.platform;

import com.sun.jna.Platform;
import com.sun.jna.platform.win32.KnownFolders;
import com.sun.jna.platform.win32.Shell32Util;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public final class DownloadsDirectory {
    private DownloadsDirectory() {}

    public static Path resolve() {
        Path home = Path.of(System.getProperty("user.home"));
        try {
            if (Platform.isWindows()) {
                Path known = Path.of(Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_Downloads));
                if (Files.isDirectory(known)) return known;
            } else if (Platform.isMac()) {
                Process process = new ProcessBuilder("/usr/bin/osascript", "-e", "POSIX path of (path to downloads folder)").start();
                if (process.waitFor(2, TimeUnit.SECONDS)) {
                    if (process.exitValue() == 0) {
                        String value = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
                        if (!value.isBlank() && Files.isDirectory(Path.of(value))) return Path.of(value);
                    }
                } else process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception | LinkageError ignored) {
            // 系统查询失败时回退到常规下载目录；用户始终可手动改选。
        }
        return home.resolve("Downloads");
    }
}
