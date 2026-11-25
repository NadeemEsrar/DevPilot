package com.devpilot.ui;

import com.devpilot.model.Repository;
import com.devpilot.model.User;
import com.devpilot.service.RepoSyncService;
import com.devpilot.util.HttpClientWrapper.HttpStatusException;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** The user's repositories: saved ones at once, then refreshed from GitHub. */
public class DashboardView extends BorderPane {
    private static final double ROW_HEIGHT = 46;
    private static final Map<String, String> LANGUAGE_COLORS = Map.ofEntries(
            Map.entry("Java", "#b07219"), Map.entry("Python", "#3572a5"),
            Map.entry("JavaScript", "#d4b106"), Map.entry("TypeScript", "#3178c6"),
            Map.entry("HTML", "#e34c26"), Map.entry("CSS", "#663399"),
            Map.entry("Jupyter Notebook", "#da5b0b"), Map.entry("C", "#555555"),
            Map.entry("C++", "#f34b7d"), Map.entry("C#", "#178600"),
            Map.entry("Go", "#00add8"), Map.entry("Rust", "#a0714f"),
            Map.entry("Kotlin", "#a97bff"), Map.entry("PHP", "#4f5d95"),
            Map.entry("Ruby", "#701516"), Map.entry("TeX", "#3d6117"), Map.entry("Shell", "#89e051"));

    private final User user;
    private final RepoSyncService syncService;
    private final Consumer<Repository> onOpen;
    private final Runnable onSessionExpired;

    private final ObservableList<Repository> repos = FXCollections.observableArrayList();
    private final FilteredList<Repository> shown = new FilteredList<>(repos);
    private final ListView<Repository> list = new ListView<>(shown);
    private final Label empty = new Label();
    private final Label name = new Label();
    private final Label menuUsername = new Label();
    private final Label menuName = new Label();
    private final Avatar avatar = new Avatar(36);
    private final Avatar menuAvatar = new Avatar(36);
    private final TextField filter = new TextField();
    private final Button refreshButton = new Button("Refresh");
    private final ProgressIndicator spinner = new ProgressIndicator();
    private final Label status = new Label();
    private String avatarUrl;
    private Task<?> task;

    public DashboardView(User user, RepoSyncService syncService, Consumer<Repository> onOpen,
                         Runnable onLogout, Runnable onSessionExpired) {
        this.user = user;
        this.syncService = syncService;
        this.onOpen = onOpen;
        this.onSessionExpired = onSessionExpired;

        getStyleClass().addAll("app", "dashboard");
        setTop(topBar(onLogout));
        setCenter(content());
        showProfile(user);
        refresh();
    }

    // ---- layout ----

    private HBox topBar(Runnable onLogout) {
        ImageView logo = new ImageView(
                new Image(DashboardView.class.getResource("/logo.png").toExternalForm()));
        logo.setPreserveRatio(true);
        logo.setFitHeight(40);
        logo.setSmooth(true);
        logo.setAccessibleText("DevPilot");

        name.setId("name");
        name.getStyleClass().add("medium");
        Button profileButton = new Button();
        profileButton.setId("profile");
        profileButton.getStyleClass().add("avatar-button");
        profileButton.setGraphic(avatar);
        profileButton.setAccessibleText("Open profile menu");

        menuUsername.getStyleClass().add("strong");
        menuName.getStyleClass().add("muted");
        HBox who = new HBox(12, menuAvatar, new VBox(menuUsername, menuName));
        who.setAlignment(Pos.CENTER_LEFT);
        CustomMenuItem whoItem = new CustomMenuItem(who, false); // information only
        whoItem.getStyleClass().add("profile-info");
        MenuItem logout = new MenuItem("Log out");
        logout.setId("logout");
        logout.setOnAction(e -> leave(onLogout));
        ContextMenu menu = new ContextMenu(whoItem, new SeparatorMenuItem(), logout);
        menu.getStyleClass().add("profile-menu");
        profileButton.setOnAction(e -> {
            if (menu.isShowing()) menu.hide();
            else menu.show(profileButton, Side.BOTTOM, -200, 8);
        });

        HBox bar = new HBox(12, logo, spacer(), name, profileButton);
        bar.getStyleClass().add("top-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private VBox content() {
        Label title = new Label("Repositories");
        title.getStyleClass().add("page-title");
        filter.setId("filter");
        filter.setPromptText("Filter by name or language");
        filter.setPrefWidth(300);
        filter.textProperty().addListener((obs, old, text) -> applyFilter());
        refreshButton.setId("refresh");
        refreshButton.getStyleClass().add("small");
        refreshButton.setMinWidth(USE_PREF_SIZE);
        refreshButton.setOnAction(e -> refresh());
        HBox header = new HBox(12, title, spacer(), filter, refreshButton);
        header.setAlignment(Pos.CENTER_LEFT);

        list.setId("repos");
        list.setFixedCellSize(ROW_HEIGHT);
        list.setCellFactory(view -> new RepoCell());
        empty.setId("empty");
        empty.getStyleClass().add("muted");
        empty.setWrapText(true);
        list.setPlaceholder(empty);
        // As tall as its rows; the VBox shrinks it (and it scrolls) when space runs out
        list.prefHeightProperty().bind(
                Bindings.max(2, Bindings.size(shown)).multiply(ROW_HEIGHT).add(22));
        list.setMaxHeight(USE_PREF_SIZE);
        list.setMinHeight(120);
        VBox.setVgrow(list, Priority.ALWAYS);

        Button openButton = new Button("Open chat");
        openButton.setId("open");
        openButton.getStyleClass().addAll("small", "primary");
        openButton.setMinWidth(USE_PREF_SIZE);
        openButton.setDefaultButton(true);
        openButton.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        openButton.setOnAction(e -> open(list.getSelectionModel().getSelectedItem()));
        spinner.setPrefSize(20, 20);
        spinner.managedProperty().bind(spinner.visibleProperty());
        status.setId("status");
        status.setWrapText(true);
        HBox footer = new HBox(16, openButton, spinner, status);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setMinHeight(USE_PREF_SIZE);

        VBox content = new VBox(20, header, list, footer);
        content.getStyleClass().add("page");
        content.setMaxWidth(1040);
        return content;
    }

    private static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    // ---- data ----

    private void refresh() {
        if (repos.isEmpty()) {
            try {
                showRepos(syncService.cached(user)); // local and quick: fine on this thread
            } catch (SQLException e) {
                showStatus("Could not read the local database.", true);
                return;
            }
        }
        refreshButton.setDisable(true);
        spinner.setVisible(true);
        showStatus("Refreshing...", false);
        updateEmptyText();
        task = Background.run(() -> syncService.sync(user), this::showResult, this::showError);
    }

    private void showResult(RepoSyncService.Result result) {
        finishRefresh();
        showProfile(result.user());
        showRepos(result.repos());
        int count = result.repos().size();
        showStatus(count + (count == 1 ? " repository" : " repositories"), false);
    }

    private void showError(Throwable error) {
        finishRefresh();
        if (error instanceof HttpStatusException http && http.getStatusCode() == 401) {
            onSessionExpired.run();
        } else if (error instanceof SQLException) {
            showStatus("Could not save to the local database.", true);
        } else if (error instanceof HttpStatusException http) {
            showStatus("GitHub returned an error (HTTP " + http.getStatusCode()
                    + "). Showing saved repositories.", true);
        } else {
            showStatus("Could not refresh from GitHub. Showing saved repositories.", true);
        }
    }

    private void finishRefresh() {
        refreshButton.setDisable(false);
        spinner.setVisible(false);
        updateEmptyText();
    }

    private void showStatus(String text, boolean error) {
        status.setText(text);
        status.getStyleClass().setAll("label", error ? "error-text" : "muted");
    }

    /** Replaces the list but keeps the same repo selected if it is still shown. */
    private void showRepos(List<Repository> fresh) {
        Repository selected = list.getSelectionModel().getSelectedItem();
        repos.setAll(fresh);
        select(selected);
        updateEmptyText();
    }

    private void applyFilter() {
        Repository selected = list.getSelectionModel().getSelectedItem();
        String wanted = filter.getText().strip().toLowerCase(Locale.ROOT);
        shown.setPredicate(repo -> wanted.isEmpty()
                || repo.getFullName().toLowerCase(Locale.ROOT).contains(wanted)
                || repo.getLanguage() != null
                    && repo.getLanguage().toLowerCase(Locale.ROOT).contains(wanted));
        select(selected);
        updateEmptyText();
    }

    private void select(Repository wanted) {
        list.getSelectionModel().clearSelection();
        if (wanted == null) return;
        for (Repository repo : shown) {
            if (repo.getId() == wanted.getId()) list.getSelectionModel().select(repo);
        }
    }

    private void updateEmptyText() {
        if (!repos.isEmpty()) empty.setText("No repository matches \"" + filter.getText().strip() + "\".");
        else empty.setText(spinner.isVisible() ? "Loading repositories..." : "No repositories to show.");
    }

    private void showProfile(User profile) {
        boolean unnamed = profile.getDisplayName() == null || profile.getDisplayName().isBlank();
        name.setText(unnamed ? profile.getGithubUsername() : profile.getDisplayName());
        menuUsername.setText(profile.getGithubUsername());
        menuName.setText(unnamed ? "" : profile.getDisplayName());
        if (profile.getAvatarUrl() != null && !profile.getAvatarUrl().equals(avatarUrl)) {
            avatarUrl = profile.getAvatarUrl();
            avatar.show(avatarUrl);
            menuAvatar.show(avatarUrl);
        }
    }

    // ---- leaving ----

    private void open(Repository repo) {
        leave(() -> onOpen.accept(repo));
    }

    /** Stop any refresh first so it can't act on a screen we have left. */
    private void leave(Runnable next) {
        if (task != null) task.cancel();
        next.run();
    }

    private class RepoCell extends ListCell<Repository> {
        private final Label owner = new Label();
        private final Label repoName = new Label();
        private final Circle dot = new Circle(5);
        private final Label language = new Label();
        private final Label tag = new Label("Private");
        private final HBox row;

        RepoCell() {
            owner.getStyleClass().add("muted");
            owner.setMinWidth(USE_PREF_SIZE);
            repoName.getStyleClass().add("strong");
            language.getStyleClass().add("muted");
            tag.getStyleClass().add("tag");
            HBox title = new HBox(owner, repoName);
            title.setAlignment(Pos.CENTER_LEFT);
            title.setMinWidth(0);
            HBox.setHgrow(title, Priority.ALWAYS);
            HBox languageBox = new HBox(8, dot, language);
            languageBox.setAlignment(Pos.CENTER_LEFT);
            languageBox.setMinWidth(160);
            languageBox.setPrefWidth(160);
            HBox tagBox = new HBox(tag);
            tagBox.setAlignment(Pos.CENTER_RIGHT);
            tagBox.setMinWidth(70);
            row = new HBox(16, title, languageBox, tagBox);
            row.setAlignment(Pos.CENTER_LEFT);
            setPrefWidth(0); // fit the list instead of scrolling sideways
            setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !isEmpty()) open(getItem());
            });
        }

        @Override
        protected void updateItem(Repository repo, boolean isEmpty) {
            super.updateItem(repo, isEmpty);
            if (isEmpty || repo == null) { // cells are reused, so clear old content
                setGraphic(null);
                setAccessibleText(null);
                return;
            }
            owner.setText(repo.getOwner() + "/");
            repoName.setText(repo.getName());
            boolean known = repo.getLanguage() != null;
            dot.setVisible(known);
            dot.setFill(Color.web(LANGUAGE_COLORS.getOrDefault(known ? repo.getLanguage() : "", "#8a8a90")));
            language.setText(known ? repo.getLanguage() : "");
            tag.setVisible(repo.isPrivate());
            setAccessibleText(repo.getFullName());
            setGraphic(row);
        }
    }
}
