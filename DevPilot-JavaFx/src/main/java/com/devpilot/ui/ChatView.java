package com.devpilot.ui;

import com.devpilot.dao.ChatHistoryDAO;
import com.devpilot.model.ChatMessage;
import com.devpilot.model.Repository;
import com.devpilot.model.User;
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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
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

    private final User user;
    private final Repository repo;
    private final RagClientService rag;
    private final ChatHistoryDAO history;
    private final VBox messages = new VBox(10);
    private final ScrollPane scroll = new ScrollPane(messages);
    private final TextField input = new TextField();
    private final Button sendButton = new Button("Send");
    private final Button indexButton = new Button();
    private final Button retryButton = new Button("Retry");
    private final Label indexLabel = new Label();
    private final ProgressIndicator indexSpinner = new ProgressIndicator();
    private final PauseTransition pollDelay = new PauseTransition(Duration.seconds(3));

    // What is true right now; updateControls() derives the screen from these
    private IndexStatus index; // null = not known (yet)
    private boolean indexing;
    private boolean asking;
    private Task<?> askTask;
    private Task<?> indexTask;

    public ChatView(User user, Repository repo, RagClientService rag, ChatHistoryDAO history, Runnable onBack) {
        this.user = user;
        this.repo = repo;
        this.rag = rag;
        this.history = history;

        Button backButton = new Button("Back");
        backButton.setId("back");
        backButton.setOnAction(e -> {
            if (askTask != null) askTask.cancel();
            if (indexTask != null) indexTask.cancel();
            pollDelay.stop();
            onBack.run();
        });
        Label title = new Label(repo.getFullName());
        title.getStyleClass().add("heading");
        HBox header = new HBox(12, backButton, title);
        header.setAlignment(Pos.CENTER_LEFT);

        indexButton.setId("index");
        indexButton.setOnAction(e -> createIndex());
        retryButton.setId("retry");
        retryButton.setOnAction(e -> loadIndex());
        retryButton.managedProperty().bind(retryButton.visibleProperty()); // hidden = takes no space
        indexLabel.setId("index-label");
        indexLabel.setWrapText(true);
        indexSpinner.setPrefSize(20, 20);
        indexSpinner.managedProperty().bind(indexSpinner.visibleProperty());
        pollDelay.setOnFinished(e -> loadIndex());
        HBox indexBar = new HBox(12, indexButton, indexSpinner, indexLabel, retryButton);
        indexBar.setAlignment(Pos.CENTER_LEFT);

        messages.setId("messages");
        messages.setPadding(new Insets(10));
        scroll.setFitToWidth(true);

        input.setId("input");
        input.setPromptText("Ask about this repository");
        input.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().length() <= MAX_QUESTION_LENGTH ? change : null));
        HBox.setHgrow(input, Priority.ALWAYS);
        sendButton.setId("send");
        sendButton.setDefaultButton(true); // Enter sends
        sendButton.setOnAction(e -> send());
        HBox inputBar = new HBox(12, input, sendButton);

        setPadding(new Insets(20));
        setTop(new VBox(12, header, indexBar));
        setCenter(scroll);
        setBottom(inputBar);
        BorderPane.setMargin(scroll, new Insets(16, 0, 16, 0));
        loadHistory();
        loadIndex();
    }

    // ---- embeddings ----

    private void loadIndex() {
        indexLabel.setText(index != null && index.building() ? "Creating..." : "Checking...");
        runIndexTask(() -> rag.indexStatus(user, repo));
    }

    private void createIndex() {
        indexLabel.setText("Creating...");
        runIndexTask(() -> rag.createIndex(user, repo));
    }

    private void runIndexTask(Callable<IndexStatus> work) {
        indexing = true;
        updateControls();
        indexTask = Background.run(work, this::showIndex, this::showIndexError);
    }

    private void showIndex(IndexStatus status) {
        index = status;
        indexing = status.building();
        if (status.building()) {
            indexLabel.setText("Creating...");
            pollDelay.playFromStart(); // started earlier: check again until it finishes
        } else {
            indexLabel.setText(status.exists() ? describe(status) : "Not created yet");
        }
        updateControls();
    }

    private void showIndexError(Throwable error) {
        indexing = false;
        if (error instanceof HttpStatusException http && http.getStatusCode() == 409) {
            loadIndex(); // already being created: wait for it
            return;
        }
        if (index != null && index.building()) index = null; // unknown until Retry
        String text = errorText(error);
        indexLabel.setText(index != null && index.exists() ? text + " Still using: " + describe(index) : text);
        updateControls();
    }

    private static String describe(IndexStatus status) {
        String when = OffsetDateTime.parse(status.createdAt())
                .atZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH));
        String commit = status.commit();
        return "Created " + when + " · commit " + commit.substring(0, Math.min(7, commit.length()));
    }

    private void updateControls() {
        boolean exists = index != null && index.exists();
        indexButton.setText(exists ? "Re-create embeddings" : "Create vector embeddings");
        indexButton.setDisable(index == null || indexing || asking);
        indexSpinner.setVisible(indexing);
        retryButton.setVisible(index == null && !indexing);
        input.setDisable(!exists || indexing || asking);
        sendButton.setDisable(!exists || indexing || asking);
    }

    // ---- questions ----

    private void loadHistory() {
        try {
            for (ChatMessage saved : history.findByUserAndRepo(user.getId(), repo.getId())) {
                addMessage(saved.getQuestion(), "question");
                addMessage(saved.getAnswer(), "answer");
            }
            Platform.runLater(this::scrollToEnd); // after this screen is in the window
        } catch (SQLException e) {
            addMessage("Could not load the saved conversation.", "answer").getStyleClass().add("error");
        }
    }

    private void save(String question, String answer) {
        try {
            history.save(new ChatMessage(user.getId(), repo.getId(), question, answer));
        } catch (SQLException e) {
            addMessage("This answer could not be saved.", "answer").getStyleClass().add("error");
        }
    }

    private void send() {
        String question = input.getText().strip();
        if (question.isEmpty()) return;

        addMessage(question, "question");
        Label answer = addMessage("Thinking...", "answer");
        input.clear();
        asking = true;
        updateControls();
        askTask = Background.run(() -> rag.ask(user, repo, question),
                text -> {
                    finish(answer, text);
                    save(question, text);
                },
                error -> {
                    answer.getStyleClass().add("error");
                    finish(answer, errorText(error));
                    if (input.getText().isEmpty()) input.setText(question); // easy to retry
                });
    }

    private void finish(Label answer, String text) {
        answer.setText(text);
        asking = false;
        updateControls();
        input.requestFocus();
        scrollToEnd();
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

    private Label addMessage(String text, String styleClass) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMinHeight(USE_PREF_SIZE); // show all lines instead of cutting off with "..."
        label.getStyleClass().addAll("message", styleClass);
        messages.getChildren().add(label);
        scrollToEnd();
        return label;
    }

    private void scrollToEnd() {
        applyCss();
        layout(); // the new message must have a size before we can scroll to it
        scroll.setVvalue(1.0);
    }
}
