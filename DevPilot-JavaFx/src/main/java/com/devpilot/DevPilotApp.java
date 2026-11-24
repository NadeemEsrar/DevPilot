package com.devpilot;

import com.devpilot.model.Repository;
import com.devpilot.ui.ChatView;
import com.devpilot.ui.DashboardView;
import com.devpilot.ui.LoginView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Entry point. Owns the window and decides which screen it shows. */
public class DevPilotApp extends Application {
    private Scene scene;

    @Override
    public void start(Stage stage) {
        scene = new Scene(new LoginView(this::showDashboard), 900, 600);
        scene.getStylesheets().add(DevPilotApp.class.getResource("/styles.css").toExternalForm());
        stage.setTitle("DevPilot");
        stage.setScene(scene);
        stage.show();
    }

    // Changing screens = swapping the root of the one scene

    private void showLogin() {
        scene.setRoot(new LoginView(this::showDashboard));
    }

    private void showDashboard() {
        scene.setRoot(new DashboardView(this::showChat, this::showLogin));
    }

    private void showChat(Repository repo) {
        scene.setRoot(new ChatView(repo, this::showDashboard));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
