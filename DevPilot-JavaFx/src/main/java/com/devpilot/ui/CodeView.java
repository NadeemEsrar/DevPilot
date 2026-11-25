package com.devpilot.ui;

import com.devpilot.service.GitHubFileService;
import com.devpilot.service.GitHubFileService.RepoFile;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

import java.util.Arrays;
import java.util.function.Consumer;

/** Shows one file, read-only, with line numbers. Hidden until a file is opened. */
class CodeView extends VBox {
    private static final int MAX_LINE_LENGTH = 2000; // very long lines are slow to draw

    /** Fetches a file's text; may be slow. */
    interface Loader {
        String load(RepoFile file) throws Exception;
    }

    private final Loader loader;
    private final Consumer<Throwable> onError;
    private final Label name = new Label();
    private final Label folder = new Label();
    private final StackPane body = new StackPane();
    private RepoFile file;
    private Task<?> task;

    /** @param onError told about every failed load, after this view has shown a message */
    CodeView(Loader loader, Runnable onClose, Consumer<Throwable> onError) {
        this.loader = loader;
        this.onError = onError;
        setId("code-view");
        getStyleClass().add("code-view");
        setMinWidth(0);

        name.setId("code-name");
        name.getStyleClass().add("strong");
        folder.getStyleClass().addAll("muted", "code-folder");
        VBox titles = new VBox(name, folder);
        titles.setMinWidth(0);
        HBox.setHgrow(titles, Priority.ALWAYS);
        SVGPath cross = new SVGPath();
        cross.setContent("M4 4l8 8M12 4l-8 8");
        cross.getStyleClass().add("line-icon");
        Button close = new Button();
        close.setId("close-file");
        close.setGraphic(cross);
        close.setAccessibleText("Close file");
        close.getStyleClass().add("icon-button");
        close.setOnAction(e -> {
            close();
            onClose.run();
        });
        HBox header = new HBox(10, titles, close);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("code-header");
        VBox.setVgrow(body, Priority.ALWAYS);
        getChildren().addAll(header, body);
        managedProperty().bind(visibleProperty()); // hidden = takes no space
        setVisible(false);
    }

    RepoFile file() {
        return file;
    }

    void open(RepoFile wanted) {
        stop();
        file = wanted;
        int slash = wanted.path().lastIndexOf('/');
        name.setText(wanted.path().substring(slash + 1));
        folder.setText(slash < 0 ? "Repository root" : wanted.path().substring(0, slash));
        setVisible(true);
        if (wanted.size() > GitHubFileService.MAX_FILE_BYTES) {
            showMessage("This file is too large to show.");
            return;
        }
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(20, 20);
        show(spinner);
        task = Background.run(() -> loader.load(wanted), this::showText, error -> {
            showMessage("Could not load this file. Click it to try again.");
            onError.accept(error);
        });
    }

    void close() {
        stop();
        file = null;
        setVisible(false);
        body.getChildren().clear();
    }

    void stop() {
        if (task != null) task.cancel();
    }

    private void showText(String text) {
        if (text.indexOf('\0') >= 0 || text.indexOf('�') >= 0) { // not valid text
            showMessage("This file isn't text, so it can't be shown.");
        } else if (text.isEmpty()) {
            showMessage("This file is empty.");
        } else {
            String[] lines = text.replace("\r\n", "\n").replaceFirst("\n$", "").split("\n", -1);
            ListView<String> list = new ListView<>(FXCollections.observableList(Arrays.asList(lines)));
            list.setId("code-lines");
            list.getStyleClass().add("code-lines");
            list.setFixedCellSize(21);
            list.setCellFactory(view -> new LineCell());
            show(list);
        }
    }

    private void showMessage(String text) {
        Label label = new Label(text);
        label.setId("code-message");
        label.setWrapText(true);
        label.getStyleClass().add("muted");
        show(label);
    }

    private void show(Node node) {
        StackPane.setAlignment(node, node instanceof ListView ? Pos.CENTER : Pos.TOP_LEFT);
        body.getStyleClass().setAll(node instanceof ListView ? "code-body" : "code-padded");
        body.getChildren().setAll(node);
    }

    private static class LineCell extends ListCell<String> {
        private final Label number = new Label();

        LineCell() {
            number.getStyleClass().add("line-number");
        }

        @Override
        protected void updateItem(String line, boolean empty) {
            super.updateItem(line, empty);
            if (empty || line == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            String shown = line.replace("\t", "    ");
            setText(shown.length() > MAX_LINE_LENGTH ? shown.substring(0, MAX_LINE_LENGTH) + "…" : shown);
            number.setText(String.valueOf(getIndex() + 1));
            setGraphic(number);
        }
    }
}
