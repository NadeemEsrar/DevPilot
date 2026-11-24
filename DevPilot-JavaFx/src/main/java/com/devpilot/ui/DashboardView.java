package com.devpilot.ui;

import com.devpilot.model.Repository;
import com.devpilot.model.User;
import com.devpilot.service.RepoSyncService;
import com.devpilot.util.HttpClientWrapper.HttpStatusException;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.sql.SQLException;
import java.util.List;
import java.util.function.Consumer;

/** The user's repositories: saved ones at once, then refreshed from GitHub. */
public class DashboardView extends BorderPane {
    private final User user;
    private final RepoSyncService syncService;
    private final Runnable onSessionExpired;

    private final ObservableList<Repository> repos = FXCollections.observableArrayList();
    private final ListView<Repository> list = new ListView<>(repos);
    private final Label name = new Label();
    private final Label status = new Label();
    private final ImageView avatar = new ImageView();
    private final Button refreshButton = new Button("Refresh");
    private Task<?> task;

    public DashboardView(User user, RepoSyncService syncService, Consumer<Repository> onOpen,
                         Runnable onLogout, Runnable onSessionExpired) {
        this.user = user;
        this.syncService = syncService;
        this.onSessionExpired = onSessionExpired;

        name.setId("name");
        name.getStyleClass().add("heading");
        avatar.setFitWidth(36);
        avatar.setFitHeight(36);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        refreshButton.setId("refresh");
        refreshButton.setOnAction(e -> refresh());
        Button logoutButton = new Button("Log out");
        logoutButton.setId("logout");
        logoutButton.setOnAction(e -> leave(onLogout));
        HBox header = new HBox(12, avatar, name, spacer, refreshButton, logoutButton);
        header.setAlignment(Pos.CENTER_LEFT);

        list.setId("repos");
        list.setCellFactory(view -> new RepoCell());

        Button openButton = new Button("Open chat");
        openButton.setId("open");
        openButton.setDefaultButton(true);
        openButton.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        openButton.setOnAction(e -> {
            Repository selected = list.getSelectionModel().getSelectedItem();
            leave(() -> onOpen.accept(selected));
        });
        status.setId("status");
        HBox footer = new HBox(12, openButton, status);
        footer.setAlignment(Pos.CENTER_LEFT);

        setPadding(new Insets(20));
        setTop(header);
        setCenter(list);
        setBottom(footer);
        BorderPane.setMargin(list, new Insets(16, 0, 16, 0));

        showProfile(user);
        refresh();
    }

    private void refresh() {
        if (repos.isEmpty()) {
            try {
                repos.setAll(syncService.cached(user)); // local and quick: fine on this thread
            } catch (SQLException e) {
                status.setText("Could not read the local database.");
                return;
            }
        }
        refreshButton.setDisable(true);
        status.setText("Refreshing...");
        task = Background.run(() -> syncService.sync(user), this::showResult, this::showError);
    }

    private void showResult(RepoSyncService.Result result) {
        refreshButton.setDisable(false);
        showProfile(result.user());
        showRepos(result.repos());
        status.setText(result.repos().size() + " repositories");
    }

    private void showError(Throwable error) {
        refreshButton.setDisable(false);
        if (error instanceof HttpStatusException http && http.getStatusCode() == 401) {
            onSessionExpired.run();
        } else if (error instanceof SQLException) {
            status.setText("Could not save to the local database.");
        } else if (error instanceof HttpStatusException http) {
            status.setText("GitHub returned an error (HTTP " + http.getStatusCode() + "). Showing saved repositories.");
        } else {
            status.setText("Could not refresh from GitHub. Showing saved repositories.");
        }
    }

    /** Replaces the list but keeps the same repo selected if it still exists. */
    private void showRepos(List<Repository> fresh) {
        Repository selected = list.getSelectionModel().getSelectedItem();
        repos.setAll(fresh);
        list.getSelectionModel().clearSelection();
        if (selected == null) return;
        for (Repository repo : repos) {
            if (repo.getId() == selected.getId()) list.getSelectionModel().select(repo);
        }
    }

    private void showProfile(User profile) {
        name.setText(profile.getDisplayName() == null ? profile.getGithubUsername() : profile.getDisplayName());
        String url = profile.getAvatarUrl();
        avatar.setImage(url == null ? null : new Image(url, true)); // true = load in the background
    }

    /** Stop any refresh first so it can't act on a screen we have left. */
    private void leave(Runnable next) {
        if (task != null) task.cancel();
        next.run();
    }

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
