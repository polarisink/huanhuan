package cn.huanhuan.app;

import cn.huanhuan.core.*;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.IOException;
import java.nio.file.*;
import java.util.function.Consumer;

public final class MainView extends VBox {
    private final Window owner;
    private final Consumer<Path> openPath;
    private final AppSettings settings;
    private final TextField input = new TextField();
    private final Button chooseInput = new Button("选择文件");
    private final Button settingsButton = new Button("设置");
    private final Button start = new Button("开始处理");
    private final Label state = new Label("准备就绪");
    private final Label statusMessage = new Label("选择一份 Excel 文件，生成处理明细和科室统计。");
    private final ProgressBar progress = new ProgressBar(0);
    private final VBox resultFiles = new VBox(6);
    private final Label badge = new Label("本地处理");
    private boolean processing;

    public MainView(Window owner, AppSettings settings, Consumer<Path> openPath) {
        AppStyles.apply(this);
        this.owner = owner;
        this.settings = settings;
        this.openPath = openPath;
        setId("main-view");
        setSpacing(22);
        setPadding(new Insets(30, 34, 24, 34));
        getStyleClass().add("page");

        ImageView icon = new ImageView(AppBranding.icon(57));
        icon.setId("app-icon");
        icon.setAccessibleText("欢欢图标");
        Rectangle iconClip = new Rectangle(57, 57);
        iconClip.setArcWidth(18);
        iconClip.setArcHeight(18);
        icon.setClip(iconClip);
        Label title = new Label(AppBranding.NAME);
        title.getStyleClass().add("title");
        Label subtitle = new Label("入出院记录时效统计 · 各科室 24 小时完成率");
        subtitle.getStyleClass().add("subtitle");
        VBox titles = new VBox(7, title, subtitle);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        badge.getStyleClass().add("badge");
        settingsButton.setId("open-settings");
        settingsButton.getStyleClass().add("settings-button");
        settingsButton.setOnAction(event -> new SettingsDialog(owner, settings).show());
        HBox header = new HBox(16, icon, titles, spacer, settingsButton);
        header.setAlignment(Pos.CENTER_LEFT);

        input.setId("input-path");
        input.setPromptText("请选择待处理的 .xlsx 文件");
        input.setEditable(false);
        input.setAccessibleText("输入 Excel 文件路径");
        chooseInput.setId("choose-input");
        chooseInput.setOnAction(event -> chooseFile());
        VBox form = fileSection("选择数据文件", "选择 XLSX，生成包含处理明细与科室统计的结果文件", input, chooseInput);
        form.getStyleClass().add("form-card");
        form.setPadding(new Insets(24));

        Label note = new Label("超时记录整行标红  ·  入院、出院分别按所属科室统计");
        note.getStyleClass().add("muted");
        note.setWrapText(true);
        start.setId("start-processing");
        start.getStyleClass().add("primary");
        start.setDefaultButton(true);
        start.setMinWidth(150);
        start.setOnAction(event -> process());
        Region actionSpace = new Region();
        HBox.setHgrow(actionSpace, Priority.ALWAYS);
        HBox actions = new HBox(16, note, actionSpace, start);
        actions.setAlignment(Pos.CENTER_LEFT);

        state.setId("result-state");
        state.getStyleClass().add("state-title");
        statusMessage.setId("status-message");
        statusMessage.setWrapText(true);
        statusMessage.getStyleClass().add("status-message");
        progress.setId("processing-progress");
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setPrefHeight(5);
        Region statusSpacer = new Region();
        HBox.setHgrow(statusSpacer, Priority.ALWAYS);
        HBox statusHeading = new HBox(state, statusSpacer, badge);
        statusHeading.setAlignment(Pos.CENTER_LEFT);
        VBox status = new VBox(12, statusHeading, statusMessage, progress, resultFiles);
        status.setId("status-card");
        status.getStyleClass().add("status-card");
        status.setPadding(new Insets(22, 24, 18, 24));
        VBox.setVgrow(status, Priority.ALWAYS);
        Label footer = new Label("原始文件保留  ·  数据仅在本机处理");
        footer.getStyleClass().add("footer");
        getChildren().addAll(header, form, actions, status, footer);
    }

    private VBox fileSection(String title, String hint, TextField path, Button button) {
        Label heading = new Label(title);
        heading.getStyleClass().add("section-title");
        Label description = new Label(hint);
        description.getStyleClass().add("muted");
        HBox selection = new HBox(10, path, button);
        HBox.setHgrow(path, Priority.ALWAYS);
        button.setMinWidth(102);
        return new VBox(10, heading, description, selection);
    }

    private void chooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择待处理的 Excel 文件");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel 工作簿 (*.xlsx)", "*.xlsx"));
        if (!input.getText().isBlank()) {
            Path parent = Path.of(input.getText()).getParent();
            if (parent != null && Files.isDirectory(parent)) chooser.setInitialDirectory(parent.toFile());
        }
        var file = chooser.showOpenDialog(owner);
        if (file != null) { input.setText(file.getAbsolutePath()); resetStatus(); }
    }

    private void resetStatus() {
        state.setText("准备就绪");
        statusMessage.setText("点击“开始处理”，校验完成后将自动保存结果文件。");
        state.getStyleClass().removeAll("error-text", "success-text");
        progress.setProgress(0);
        resultFiles.getChildren().clear();
    }

    private void process() {
        if (processing) return;
        if (input.getText().isBlank()) { showError("尚未选择文件", "请先选择一份待处理的 XLSX 文件。"); return; }
        final Path source;
        final Path destination;
        try {
            source = Path.of(input.getText());
            destination = settings.outputDirectory();
        } catch (InvalidPathException e) { showError("路径不合法", "请重新选择输入文件。"); return; }
        if (!Files.isDirectory(destination)) {
            showError("保存目录不可用", "请打开右上角“设置”，重新选择有效的保存目录。");
            return;
        }
        resetStatus();
        setProcessing(true);
        state.setText("正在处理");
        Task<ProcessingResult> task = new Task<>() {
            @Override protected ProcessingResult call() throws Exception {
                return new WorkbookProcessor().process(source, destination, (fraction, message) -> {
                    updateProgress(fraction, 1);
                    updateMessage(message);
                });
            }
        };
        progress.progressProperty().bind(task.progressProperty());
        statusMessage.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(event -> {
            unbindTask();
            setProcessing(false);
            ProcessingResult result = task.getValue();
            state.setText("处理完成");
            state.getStyleClass().add("success-text");
            statusMessage.setText("共处理 " + result.recordCount() + " 条记录，" + result.lateRecordCount()
                    + " 条存在超时，汇总 " + result.departments().size() + " 个科室。");
            progress.setProgress(1);
            addResultLink(result.outputFile());
            Hyperlink open = new Hyperlink("打开输出目录 →");
            open.setOnAction(action -> openPath.accept(destination));
            resultFiles.getChildren().add(open);
        });
        task.setOnFailed(event -> {
            unbindTask();
            setProcessing(false);
            Throwable error = task.getException();
            String reason = error instanceof DataValidationException || error instanceof IOException
                    ? error.getMessage() : "文件无法处理，请检查文件是否损坏、加密，或被其他程序占用。";
            showError("处理未完成", reason == null ? "文件无法读取或保存，请检查文件及目录权限。" : reason);
        });
        Thread worker = new Thread(task, "workbook-processing");
        worker.setDaemon(false);
        worker.start();
    }

    private void addResultLink(Path path) {
        Hyperlink link = new Hyperlink(path.getFileName().toString());
        Tooltip tooltip = new Tooltip(path.toString());
        tooltip.setFont(AppStyles.regularFont());
        link.setTooltip(tooltip);
        link.setOnAction(event -> openPath.accept(path));
        resultFiles.getChildren().add(link);
    }

    private void unbindTask() {
        progress.progressProperty().unbind();
        statusMessage.textProperty().unbind();
    }

    private void showError(String title, String message) {
        state.setText(title);
        state.getStyleClass().removeAll("error-text", "success-text");
        state.getStyleClass().add("error-text");
        statusMessage.setText(message);
        progress.setProgress(0);
        resultFiles.getChildren().clear();
    }

    private void setProcessing(boolean value) {
        processing = value;
        chooseInput.setDisable(value);
        settingsButton.setDisable(value);
        start.setDisable(value);
        start.setText(value ? "处理中…" : "开始处理");
        badge.setText(value ? "处理中" : "本地处理");
    }

    public boolean isProcessing() { return processing; }

    public void explainBusy() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "正在处理文件，请等待完成后再关闭窗口。", ButtonType.OK);
        AppStyles.apply(alert.getDialogPane());
        alert.initOwner(owner);
        alert.setHeaderText("正在处理");
        alert.show();
    }
}
