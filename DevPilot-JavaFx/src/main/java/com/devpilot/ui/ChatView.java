package com.devpilot.ui;

import com.devpilot.model.Repository;
import com.devpilot.model.User;
import com.devpilot.service.RagClientService;
import com.devpilot.util.HttpClientWrapper.HttpStatusException;
import com.devpilot.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;

/** Chat about one repository. Phase 9 saves the conversation. */
public class ChatView extends BorderPane {
    private static final int MAX_QUESTION_LENGTH = 2000; // same limit as the RAG service

    private final User user;
    private final Repository repo;
    private final RagClientService rag;
    private final VBox messages = new VBox(10);
    private final ScrollPane scroll = new ScrollPane(messages);
    private final TextField input = new TextField();
    private final Button sendButton = new Button("Send");
    private Task<?> task;

    public ChatView(User user, Repository repo, RagClientService rag, Runnable onBack) {
        this.user = user;
        this.repo = repo;
        this.rag = rag;

        Button backButton = new Button("Back");
        backButton.setId("back");
        backButton.setOnAction(e -> {
            if (task != null) task.cancel();
            onBack.run();
        });
        Label title = new Label(repo.getFullName());
        title.getStyleClass().add("heading");
        HBox header = new HBox(12, backButton, title);
        header.setAlignment(Pos.CENTER_LEFT);

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
        setTop(header);
        setCenter(scroll);
        setBottom(inputBar);
        BorderPane.setMargin(scroll, new Insets(16, 0, 16, 0));
    }

    private void send() {
        String question = input.getText().strip();
        if (question.isEmpty()) return;

        addMessage(question, "question");
        Label answer = addMessage("Thinking... (the first question about a repository takes longer)", "answer");
        input.clear();
        setBusy(true);
        task = Background.run(() -> rag.ask(user, repo, question),
                text -> finish(answer, text),
                error -> {
                    answer.getStyleClass().add("error");
                    finish(answer, errorText(error));
                    if (input.getText().isEmpty()) input.setText(question); // easy to retry
                });
    }

    private void finish(Label answer, String text) {
        answer.setText(text);
        setBusy(false);
        input.requestFocus();
        scrollToEnd();
    }

    private void setBusy(boolean busy) {
        input.setDisable(busy);
        sendButton.setDisable(busy);
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
