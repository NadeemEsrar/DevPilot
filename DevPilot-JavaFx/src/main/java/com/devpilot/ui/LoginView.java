package com.devpilot.ui;

import com.devpilot.model.User;
import com.devpilot.service.GitHubAuthService;
import com.devpilot.service.GitHubAuthService.DeviceCode;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/** Device Flow login. Three states: ready, asking for a code, waiting for approval. */
public class LoginView extends VBox {
    private final GitHubAuthService auth;
    private final Consumer<User> onLoggedIn;
    private final Consumer<String> openUrl;

    private final Label status = new Label();
    private final VBox content = new VBox(16);
    private Task<?> task;

    public LoginView(GitHubAuthService auth, Consumer<User> onLoggedIn, Consumer<String> openUrl, String message) {
        this.auth = auth;
        this.onLoggedIn = onLoggedIn;
        this.openUrl = openUrl;

        Label title = new Label("DevPilot");
        title.getStyleClass().add("title");
        status.setId("status");
        status.setWrapText(true);
        content.setAlignment(Pos.CENTER);
        setAlignment(Pos.CENTER);
        setSpacing(16);
        getChildren().addAll(title, status, content);
        showReady(message);
    }

    private void showReady(String message) {
        status.setText(message);
        Button loginButton = button("login", "Log in with GitHub", this::requestCode);
        loginButton.setDefaultButton(true);
        content.getChildren().setAll(loginButton);
    }

    private void requestCode() {
        status.setText("Contacting GitHub...");
        content.getChildren().setAll(spinner(), cancelButton());
        task = Background.run(auth::requestDeviceCode, this::showCode, this::showError);
    }

    private void showCode(DeviceCode code) {
        status.setText("Enter this code at " + code.verificationUri());
        Label codeLabel = new Label(code.userCode());
        codeLabel.setId("code");
        codeLabel.getStyleClass().add("title");
        Button openButton = button("open-github", "Copy code and open GitHub", () -> {
            ClipboardContent clip = new ClipboardContent();
            clip.putString(code.userCode());
            Clipboard.getSystemClipboard().setContent(clip);
            openUrl.accept(code.verificationUri());
        });
        content.getChildren().setAll(codeLabel, openButton, spinner(), cancelButton());
        task = Background.run(() -> auth.fetchUser(auth.waitForToken(code)), onLoggedIn, this::showError);
    }

    private void showError(Throwable error) {
        showReady(error instanceof GitHubAuthService.AuthException
                ? error.getMessage()
                : "Could not log in. Check your internet connection and try again.");
    }

    private Button cancelButton() {
        return button("cancel", "Cancel", () -> {
            task.cancel();
            showReady("Login cancelled.");
        });
    }

    private static Button button(String id, String text, Runnable action) {
        Button button = new Button(text);
        button.setId(id);
        button.setOnAction(e -> action.run());
        return button;
    }

    private static ProgressIndicator spinner() {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(32, 32);
        return spinner;
    }
}
