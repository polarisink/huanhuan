package cn.huanhuan.app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class TimelinessApplication extends Application {
    @Override public void start(Stage stage) {
        MainView view = new MainView(stage, AppSettings.userSettings(),
                path -> getHostServices().showDocument(path.toUri().toString()));
        Scene scene = new Scene(view, 840, 580);
        AppBranding.configureWindow(stage);
        AppBranding.configureTaskbar();
        stage.setMinWidth(760);
        stage.setMinHeight(560);
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            if (view.isProcessing()) {
                event.consume();
                view.explainBusy();
            }
        });
        stage.show();
    }
}
