package com.devpilot.ui;

import com.devpilot.model.Repository;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Chat about one repository. Phase 7 connects the input to the RAG service. */
public class ChatView extends BorderPane {

    public ChatView(Repository repo, Runnable onBack) {
        Button backButton = new Button("Back");
        backButton.setId("back");
        backButton.setOnAction(e -> onBack.run());
        Label title = new Label(repo.getFullName());
        title.getStyleClass().add("heading");
        HBox header = new HBox(12, backButton, title);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox messages = new VBox(10,
                message("Sample question: where is the main method?", "question"),
                message("Sample answer: real answers arrive in Phase 7.", "answer"));
        messages.setPadding(new Insets(10));
        ScrollPane scroll = new ScrollPane(messages);
        scroll.setFitToWidth(true);

        TextField input = new TextField();
        input.setPromptText("Ask about this repository");
        HBox.setHgrow(input, Priority.ALWAYS);
        Button sendButton = new Button("Send");
        sendButton.setDisable(true); // no logic until Phase 7
        HBox inputBar = new HBox(12, input, sendButton);

        setPadding(new Insets(20));
        setTop(header);
        setCenter(scroll);
        setBottom(inputBar);
        BorderPane.setMargin(scroll, new Insets(16, 0, 16, 0));
    }

    private static Label message(String text, String styleClass) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().addAll("message", styleClass);
        return label;
    }
}
