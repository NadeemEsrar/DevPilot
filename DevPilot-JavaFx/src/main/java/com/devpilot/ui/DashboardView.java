package com.devpilot.ui;

import com.devpilot.model.Repository;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.function.Consumer;

/** Repository list. Phase 6 replaces the sample data with the real repos. */
public class DashboardView extends BorderPane {

    public DashboardView(Consumer<Repository> onOpen, Runnable onLogout) {
        Label title = new Label("Your repositories");
        title.getStyleClass().add("heading");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS); // pushes the button to the right
        Button logoutButton = new Button("Log out");
        logoutButton.setId("logout");
        logoutButton.setOnAction(e -> onLogout.run());
        HBox header = new HBox(12, title, spacer, logoutButton);
        header.setAlignment(Pos.CENTER_LEFT);

        // The ListView watches this list and redraws when it changes
        ObservableList<Repository> repos = FXCollections.observableArrayList(
                new Repository(1, 1L, "sample", "hello-world", false, "main", "Java", null, null),
                new Repository(1, 2L, "sample", "secret-project", true, "main", "Python", null, null));
        ListView<Repository> list = new ListView<>(repos);
        list.setId("repos");
        list.setCellFactory(view -> new RepoCell());

        Button openButton = new Button("Open chat");
        openButton.setId("open");
        openButton.setDefaultButton(true);
        // Binding: the button stays disabled while nothing is selected
        openButton.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        openButton.setOnAction(e -> onOpen.accept(list.getSelectionModel().getSelectedItem()));

        setPadding(new Insets(20));
        setTop(header);
        setCenter(list);
        setBottom(openButton);
        BorderPane.setMargin(list, new Insets(16, 0, 16, 0));
    }

    /** Decides how one Repository looks in the list. */
    private static class RepoCell extends ListCell<Repository> {
        @Override
        protected void updateItem(Repository repo, boolean empty) {
            super.updateItem(repo, empty);
            if (empty || repo == null) { // cells are reused, so clear old text
                setText(null);
                return;
            }
            String language = repo.getLanguage() == null ? "" : "  ·  " + repo.getLanguage();
            setText(repo.getFullName() + language + (repo.isPrivate() ? "  ·  Private" : ""));
        }
    }
}
