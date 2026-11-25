package com.devpilot.ui;

import com.devpilot.dao.ChatHistoryDAO;
import com.devpilot.model.ChatMessage;
import com.devpilot.model.Repository;
import com.devpilot.model.User;
import com.devpilot.service.GitHubFileService;
import com.devpilot.service.GitHubFileService.FileList;
import com.devpilot.service.GitHubFileService.RepoFile;
import com.devpilot.service.RagClientService;
import com.devpilot.service.RagClientService.IndexStatus;
import com.devpilot.util.HttpClientWrapper.HttpStatusException;
import com.devpilot.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.Callable;

/** Chat about one repository. The conversation is saved in the local database. */
public class ChatView extends BorderPane {
    private static final int MAX_QUESTION_LENGTH = 2000; // same limit as the RAG service
    private static final double COLUMN_WIDTH = 760;

    private final User user;
    private final Repository repo;
    private final RagClientService rag;
    private final GitHubFileService fileService;
    private final ChatHistoryDAO history;
    private final Runnable onSessionExpired;

    private final FileTreeView tree = new FileTreeView(this::openFile);
    private final VBox filesNotice = new VBox(10); // loading, errors, or a shortened list
    private final CodeView code;
    private final VBox messages = new VBox(22);
    private final VBox notice = new VBox(14); // what to do before asking, or why it isn't possible
    private final ScrollPane scroll = new ScrollPane();
    private final TextField input = new TextField();
    private final Button sendButton = new Button("Send");
    private final Label lastEmbedded = new Label();
    private final Button indexButton = new Button();
    private final ContextMenu menu = new ContextMenu();
    private final PauseTransition pollDelay = new PauseTransition(Duration.seconds(3));

    // What is true right now; update() derives the screen from these
    private IndexStatus index; // null = not known (yet)
    private boolean checking;  // asking the service what exists
    private boolean creating;  // embeddings are being made
    private boolean asking;
    private String indexError; // null = no problem
    private String filesRef;   // the commit or branch the file tree shows
    private Task<?> askTask;
    private Task<?> indexTask;
    private Task<?> filesTask;

    public ChatView(User user, Repository repo, RagClientService rag, GitHubFileService fileService,
                    ChatHistoryDAO history, Runnable onBack, Runnable onSessionExpired) {
        this.user = user;
        this.repo = repo;
        this.rag = rag;
        this.fileService = fileService;
        this.history = history;
        this.onSessionExpired = onSessionExpired;

        getStyleClass().addAll("app", "chat");
        setLeft(sidePanel(onBack));
        code = new CodeView(file -> fileService.read(user, repo, file), tree::clearFileSelection, error -> {
            if (isStatus(error, 401)) expire();
        });
        Node chat = chatPanel();
        HBox.setHgrow(code, Priority.ALWAYS);
        HBox.setHgrow(chat, Priority.ALWAYS);
        setCenter(new HBox(code, chat));
        pollDelay.setOnFinished(e -> loadIndex());
        loadHistory();
        loadIndex();
    }

    // ---- layout ----

    private Node sidePanel(Runnable onBack) {
        SVGPath arrow = new SVGPath();
        arrow.setContent("M10 3 5 8l5 5");
        arrow.getStyleClass().add("line-icon");
        Button back = new Button();
        back.setId("back");
        back.setGraphic(arrow);
        back.setAccessibleText("Back to repositories");
        back.getStyleClass().add("icon-button");
        back.setOnAction(e -> {
            stop();
            onBack.run();
        });
        Label owner = new Label(repo.getOwner());
        owner.getStyleClass().add("muted");
        Label name = new Label(repo.getName());
        name.getStyleClass().add("strong");
        name.setWrapText(true);
        name.setMinHeight(USE_PREF_SIZE);
        HBox header = new HBox(10, back, new VBox(owner, name));
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("side-header");

        filesNotice.setId("files-notice");
        filesNotice.getStyleClass().add("files-notice");
        filesNotice.managedProperty().bind(filesNotice.visibleProperty());
        filesNotice.setVisible(false);
        VBox.setVgrow(tree, Priority.ALWAYS);

        Avatar avatar = new Avatar(36);
        avatar.show(user.getAvatarUrl());
        boolean unnamed = user.getDisplayName() == null || user.getDisplayName().isBlank();
        Button profile = new Button(unnamed ? user.getGithubUsername() : user.getDisplayName(), avatar);
        profile.setId("profile");
        profile.getStyleClass().add("profile-button");
        profile.setMaxWidth(Double.MAX_VALUE);
        profile.setOnAction(e -> {
            if (menu.isShowing()) menu.hide();
            else menu.show(profile, Side.TOP, 0, -8);
        });
        buildMenu();
        StackPane footer = new StackPane(profile);
        footer.getStyleClass().add("side-footer");

        VBox panel = new VBox(header, filesNotice, tree, footer);
        panel.getStyleClass().add("side");
        panel.setMinWidth(288);
        panel.setPrefWidth(288);
        panel.setMaxWidth(288);
        return panel;
    }

    private void buildMenu() {
        Label title = new Label("Last embedded");
        title.getStyleClass().add("muted");
        lastEmbedded.setId("last-embedded");
        lastEmbedded.getStyleClass().add("strong");
        lastEmbedded.setWrapText(true);
        indexButton.setId("index");
        indexButton.setMaxWidth(Double.MAX_VALUE);
        indexButton.setOnAction(e -> {
            menu.hide();
            createIndex();
        });
        VBox content = new VBox(title, lastEmbedded, indexButton);
        VBox.setMargin(indexButton, new Insets(12, 0, 0, 0));
        content.setPrefWidth(214); // the menu is 250 wide, minus its padding
        CustomMenuItem item = new CustomMenuItem(content, false);
        item.getStyleClass().add("profile-info");
        menu.getItems().add(item);
        menu.getStyleClass().addAll("profile-menu", "app");
    }

    private Node chatPanel() {
        messages.setId("messages");
        messages.managedProperty().bind(messages.visibleProperty());
        notice.setId("notice");
        notice.managedProperty().bind(notice.visibleProperty());
        VBox column = new VBox(22, messages, notice);
        column.getStyleClass().add("column");
        column.setMaxWidth(COLUMN_WIDTH);
        StackPane centered = new StackPane(column);
        centered.setAlignment(Pos.TOP_CENTER);
        scroll.setContent(centered);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        input.setId("input");
        input.setPromptText("Ask about this repository");
        input.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().length() <= MAX_QUESTION_LENGTH ? change : null));
        HBox.setHgrow(input, Priority.ALWAYS);
        sendButton.setId("send");
        sendButton.getStyleClass().addAll("small", "primary");
        sendButton.setMinWidth(USE_PREF_SIZE);
        sendButton.setMaxHeight(Double.MAX_VALUE);
        sendButton.setDefaultButton(true); // Enter sends
        sendButton.setOnAction(e -> send());
        HBox bar = new HBox(10, input, sendButton);
        bar.getStyleClass().add("ask-bar");
        bar.setMaxWidth(COLUMN_WIDTH);
        StackPane ask = new StackPane(bar);
        ask.setMinHeight(USE_PREF_SIZE);
        VBox panel = new VBox(scroll, ask);
        panel.setMinWidth(320);
        // Equal preferred widths: the code and chat share the space evenly
        panel.setPrefWidth(480);
        code.setPrefWidth(480);
        return panel;
    }

    // ---- files ----

    /** Shows the files the answers are based on, or the newest ones if there are no embeddings. */
    private void loadFiles() {
        String ref = index != null && index.exists() ? index.commit()
                : repo.getDefaultBranch() == null ? "HEAD" : repo.getDefaultBranch();
        if (ref.equals(filesRef)) return; // already shown or loading
        filesRef = ref;
        if (filesTask != null) filesTask.cancel();
        showFilesNotice(progress("Loading files..."));
        filesTask = Background.run(() -> fileService.list(user, repo, ref), this::showFiles, error -> {
            if (isStatus(error, 401)) {
                expire();
                return;
            }
            filesRef = null; // allow another try
            showFilesNotice(text(isStatus(error, 409) ? "This repository is empty."
                            : "Could not load the files from GitHub.", "muted"),
                    button("retry-files", "Retry", false, this::loadFiles));
        });
    }

    private void showFiles(FileList list) {
        tree.show(list.files());
        if (list.truncated()) showFilesNotice(text("Very large repository: not all files are listed.", "muted"));
        else showFilesNotice();
        if (code.file() == null) return;
        // The commit may have changed: show the open file's version in it
        String path = code.file().path();
        list.files().stream().filter(f -> f.path().equals(path)).findFirst().ifPresentOrElse(now -> {
            if (!now.sha().equals(code.file().sha())) code.open(now);
        }, code::close);
    }

    private void showFilesNotice(Node... nodes) {
        filesNotice.getChildren().setAll(nodes);
        filesNotice.setVisible(nodes.length > 0);
    }

    private void openFile(RepoFile file) {
        code.open(file);
    }

    // ---- embeddings ----

    private void loadIndex() {
        checking = true;
        runIndexTask(() -> rag.indexStatus(user, repo));
    }

    private void createIndex() {
        creating = true;
        runIndexTask(() -> rag.createIndex(user, repo));
    }

    private void runIndexTask(Callable<IndexStatus> work) {
        indexError = null;
        update();
        indexTask = Background.run(work, this::showIndex, this::showIndexError);
    }

    private void showIndex(IndexStatus status) {
        index = status;
        checking = false;
        creating = status.building();
        loadFiles();
        if (creating) pollDelay.playFromStart(); // started earlier: check again until it finishes
        update();
        if (!creating && status.exists()) input.requestFocus();
    }

    private void showIndexError(Throwable error) {
        if (isStatus(error, 401)) {
            expire();
            return;
        }
        if (isStatus(error, 409) && creating) {
            creating = false;
            loadIndex(); // already being created: wait for it
            return;
        }
        checking = false;
        creating = false;
        if (index != null && index.building()) index = null; // unknown until Retry
        indexError = errorText(error);
        loadFiles();
        update();
    }

    private static String describe(IndexStatus status) {
        String when = OffsetDateTime.parse(status.createdAt())
                .atZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH));
        String commit = status.commit();
        return when + "\ncommit " + commit.substring(0, Math.min(7, commit.length()));
    }

    private void update() {
        boolean exists = index != null && index.exists();
        boolean busy = checking || creating || asking;
        indexButton.setText(exists ? "Re-create embeddings" : "Create vector embeddings");
        indexButton.setDisable(index == null || busy);
        lastEmbedded.setText(creating ? "Creating..." : checking && index == null ? "Checking..."
                : index == null ? "Unknown" : exists ? describe(index) : "Not created yet");
        input.setDisable(!exists || busy);
        sendButton.setDisable(!exists || busy);
        showNotice(exists);
    }

    private void showNotice(boolean exists) {
        notice.getChildren().clear();
        if (creating) {
            notice.getChildren().add(progress("Creating embeddings..."));
        } else if (checking) {
            if (index == null) notice.getChildren().add(progress("Checking..."));
        } else {
            if (indexError != null) {
                notice.getChildren().add(text(exists
                        ? indexError + " Still using the earlier embeddings." : indexError, "error-text"));
            }
            if (index == null) {
                notice.getChildren().add(button("retry", "Retry", false, this::loadIndex));
            } else if (!exists) {
                Button create = button("create", "Create vector embeddings", true, this::createIndex);
                notice.getChildren().addAll(text("No embeddings yet", "notice-title"),
                        text("Create them to start asking about this repository.", "muted"), create);
            }
        }
        notice.setVisible(!notice.getChildren().isEmpty());
        if (notice.isVisible()) Platform.runLater(this::scrollToEnd);
    }

    // ---- questions ----

    private void loadHistory() {
        try {
            for (ChatMessage saved : history.findByUserAndRepo(user.getId(), repo.getId())) {
                addMessage(question(saved.getQuestion()));
                addMessage(new AnswerView(saved.getAnswer()));
            }
        } catch (SQLException e) {
            addMessage(error("Could not load the saved conversation."));
        }
        messages.setVisible(!messages.getChildren().isEmpty());
        Platform.runLater(this::scrollToEnd); // after this screen is in the window
    }

    private void save(String question, String answer) {
        try {
            history.save(new ChatMessage(user.getId(), repo.getId(), question, answer));
        } catch (SQLException e) {
            addMessage(error("This answer could not be saved."));
        }
    }

    private void send() {
        String question = input.getText().strip();
        if (question.isEmpty()) return;

        addMessage(question(question));
        Node thinking = progress("Thinking...");
        addMessage(thinking);
        input.clear();
        asking = true;
        update();
        askTask = Background.run(() -> rag.ask(user, repo, question),
                text -> {
                    finish(thinking, new AnswerView(text));
                    save(question, text);
                },
                error -> {
                    if (isStatus(error, 401)) {
                        expire();
                        return;
                    }
                    finish(thinking, error(errorText(error)));
                    if (input.getText().isEmpty()) input.setText(question); // easy to retry
                });
    }

    private void finish(Node thinking, Node reply) {
        messages.getChildren().remove(thinking);
        addMessage(reply);
        asking = false;
        update();
        input.requestFocus();
        Platform.runLater(this::scrollToEnd);
    }

    private static String errorText(Throwable error) {
        if (error instanceof ConnectException) return "Can't reach the RAG service. Is it running?";
        if (error instanceof HttpTimeoutException) return "The RAG service took too long to answer.";
        if (error instanceof HttpStatusException http) {
            return serviceMessage(http);
        }
        return "Could not get an answer.";
    }

    /** FastAPI puts our message in "detail"; validation errors use a list there instead. */
    private static String serviceMessage(HttpStatusException error) {
        try {
            JsonNode detail = JsonUtil.parse(error.getBody()).path("detail");
            if (detail.isTextual()) return detail.asText();
        } catch (JsonProcessingException e) {
            // not JSON: use the general message
        }
        return "The RAG service returned an error (HTTP " + error.getStatusCode() + ").";
    }

    private static boolean isStatus(Throwable error, int status) {
        return error instanceof HttpStatusException http && http.getStatusCode() == status;
    }

    // ---- leaving ----

    /** Stop background work so it can't act on a screen we have left. */
    private void stop() {
        if (askTask != null) askTask.cancel();
        if (indexTask != null) indexTask.cancel();
        if (filesTask != null) filesTask.cancel();
        code.stop();
        pollDelay.stop();
        menu.hide();
    }

    private void expire() {
        stop();
        onSessionExpired.run();
    }

    // ---- pieces ----

    private void addMessage(Node message) {
        messages.getChildren().add(message);
        messages.setVisible(true);
        scrollToEnd();
    }

    private static Node question(String text) {
        Label bubble = text(text, "question-bubble");
        bubble.setMaxWidth(COLUMN_WIDTH * 0.8);
        HBox row = new HBox(bubble);
        row.setAlignment(Pos.CENTER_RIGHT);
        return row;
    }

    private static Label error(String text) {
        Label label = text(text, "error-box");
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private static Label text(String text, String styleClass) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE); // show all lines instead of cutting off with "..."
        label.getStyleClass().add(styleClass);
        return label;
    }

    private static Button button(String id, String text, boolean primary, Runnable action) {
        Button button = new Button(text);
        button.setId(id);
        button.getStyleClass().add("small");
        if (primary) button.getStyleClass().add("primary");
        button.setOnAction(e -> action.run());
        return button;
    }

    private static Node progress(String text) {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(20, 20);
        HBox row = new HBox(12, spinner, text(text, "muted"));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void scrollToEnd() {
        applyCss();
        layout(); // the new message must have a size before we can scroll to it
        scroll.setVvalue(1.0);
    }
}
