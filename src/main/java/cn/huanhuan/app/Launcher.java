package cn.huanhuan.app;

import javafx.application.Application;

/** 独立入口确保使用 classpath 的 EXE / app 启动器能正确加载 JavaFX。 */
public final class Launcher {
    private Launcher() {}
    public static void main(String[] args) { Application.launch(TimelinessApplication.class, args); }
}
