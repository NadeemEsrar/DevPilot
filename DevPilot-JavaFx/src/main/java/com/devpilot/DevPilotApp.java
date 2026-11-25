package com.devpilot;

import com.devpilot.dao.ChatHistoryDAO;
import com.devpilot.dao.UserDAO;
import com.devpilot.model.Repository;
import com.devpilot.model.User;
import com.devpilot.service.GitHubAuthService;
import com.devpilot.service.GitHubRepoService;
import com.devpilot.service.RagClientService;
import com.devpilot.service.RepoSyncService;
import com.devpilot.ui.ChatView;
import com.devpilot.ui.DashboardView;
import com.devpilot.ui.LoginView;
import com.devpilot.util.DBConnection;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.util.Optional;

/** Entry point. Owns the window, the logged-in user, and which screen is shown. */
public class DevPilotApp extends Application {
    private final Scene scene = new Scene(new StackPane(), 1280, 780);
    private final ChatHistoryDAO chatDAO = new ChatHistoryDAO();
    private final UserDAO userDAO = new UserDAO();
    private final GitHubAuthService auth;
    private final RepoSyncService syncService;
    private final RagClientService rag = new RagClientService();
    private User user;

    public DevPilotApp() {
        this(new GitHubAuthService(), new GitHubRepoService());
    }

    /** Lets tests supply fake services. */
    DevPilotApp(GitHubAuthService auth, GitHubRepoService github) {
        this.auth = auth;
        this.syncService = new RepoSyncService(auth, github);
    }

    @Override
    public void start(Stage stage) throws SQLException {
        DBConnection.initSchema();
        scene.getStylesheets().add(DevPilotApp.class.getResource("/styles.css").toExternalForm());

        Optional<User> saved = userDAO.findLoggedIn();
        if (saved.isEmpty()) {
            showLogin(null);
        } else if (!GitHubAuthService.hasRequiredScope(saved.get().getTokenScope())) {
            showLogin("DevPilot needs new permissions. Please log in again.");
        } else {
            user = saved.get();
            showDashboard();
        }

        stage.setTitle("DevPilot");
        stage.setScene(scene);
        stage.show();
    }

    private void showLogin(String message) {
        show(new LoginView(auth, this::loggedIn, url -> getHostServices().showDocument(url), message));
    }

    private void showDashboard() {
        show(new DashboardView(user, syncService, this::showChat,
                () -> logout(null),
                () -> logout("Your GitHub session has ended. Please log in again.")));
    }

    private void showChat(Repository repo) {
        show(new ChatView(user, repo, rag, chatDAO, this::showDashboard));
    }

    private void show(Parent screen) {
        scene.setRoot(screen);
    }

    private void loggedIn(User newUser) {
        if (!GitHubAuthService.hasRequiredScope(newUser.getTokenScope())) {
            showLogin("GitHub did not grant the permissions DevPilot needs.");
            return;
        }
        try {
            user = userDAO.save(newUser);
            showDashboard();
        } catch (SQLException e) {
            showLogin("Could not save your login to the local database.");
        }
    }

    private void logout(String message) {
        try {
            userDAO.clearToken(user.getId());
            user = null;
            showLogin(message);
        } catch (SQLException e) {
            user = null;
            showLogin("Your saved login could not be removed from the local database.");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
