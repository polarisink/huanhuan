package cn.huanhuan.app;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

import java.io.IOException;
import java.nio.file.*;

final class SettingsDialog extends Dialog<Void> {
    SettingsDialog(Window owner, AppSettings settings) {
        initOwner(owner);
        setTitle(AppBranding.NAME + " · 设置");
        setResizable(true);
        DialogPane pane = getDialogPane();
        pane.setId("settings-dialog");
        AppStyles.apply(pane);
        pane.setPrefWidth(600);
        Label title = new Label("结果保存目录");
        title.getStyleClass().add("section-title");
        Label description = new Label("处理完成后自动保存到此目录，下次打开应用时继续使用。");
        description.getStyleClass().add("muted");
        description.setWrapText(true);
        TextField path = new TextField(settings.outputDirectory().toString());
        path.setId("settings-output-path");
        path.setAccessibleText("结果保存目录");
        Button choose = new Button("选择目录");
        choose.setId("choose-output");
        choose.setMinWidth(105);
        choose.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("选择结果保存目录");
            try {
                Path initial = Path.of(path.getText());
                if (Files.isDirectory(initial)) chooser.setInitialDirectory(initial.toFile());
            } catch (InvalidPathException ignored) { /* 用户可重新选择有效目录 */ }
            var selected = chooser.showDialog(pane.getScene().getWindow());
            if (selected != null) path.setText(selected.getAbsolutePath());
        });
        HBox selection = new HBox(10, path, choose);
        HBox.setHgrow(path, Priority.ALWAYS);
        Label error = new Label();
        error.setId("settings-error");
        error.getStyleClass().add("error-text");
        error.setWrapText(true);
        path.textProperty().addListener((observable, before, after) -> error.setText(""));
        VBox content = new VBox(12, title, description, selection, error);
        content.setPadding(new Insets(18, 14, 4, 14));
        pane.setContent(content);
        ButtonType save = new ButtonType("保存", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(ButtonType.CANCEL, save);
        Button cancelButton = (Button) pane.lookupButton(ButtonType.CANCEL);
        cancelButton.setText("取消");
        cancelButton.setId("cancel-settings");
        Button saveButton = (Button) pane.lookupButton(save);
        saveButton.setId("save-settings");
        saveButton.getStyleClass().add("primary");
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                if (path.getText().isBlank()) throw new IOException("请选择保存目录。");
                settings.saveOutputDirectory(Path.of(path.getText()));
            } catch (InvalidPathException e) {
                error.setText("目录路径不合法，请重新选择。");
                event.consume();
            } catch (IOException e) {
                error.setText(e.getMessage());
                event.consume();
            }
        });
    }
}
