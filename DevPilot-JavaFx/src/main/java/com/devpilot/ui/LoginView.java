package com.devpilot.ui;

import com.devpilot.model.User;
import com.devpilot.service.GitHubAuthService;
import com.devpilot.service.GitHubAuthService.DeviceCode;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

import java.util.function.Consumer;

/** Device Flow login. Three states: ready, asking for a code, waiting for approval. */
public class LoginView extends BorderPane {
    private static final String GITHUB_MARK =
            "M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49"
            + "-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58"
            + " 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31"
            + "-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27s1.36.09 2"
            + " .27c1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87"
            + " 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0"
            + " 0 0 16 8c0-4.42-3.58-8-8-8z";

    private final GitHubAuthService auth;
    private final Consumer<User> onLoggedIn;
    private final Consumer<String> openUrl;

    private final VBox card = new VBox();
    private Task<?> task;

    /** @param error shown above the login button; null for none */
    public LoginView(GitHubAuthService auth, Consumer<User> onLoggedIn, Consumer<String> openUrl, String error) {
        this.auth = auth;
        this.onLoggedIn = onLoggedIn;
        this.openUrl = openUrl;

        ImageView logo = new ImageView(
                new Image(LoginView.class.getResource("/logo.png").toExternalForm()));
        logo.setPreserveRatio(true);
        logo.setFitHeight(56);
        logo.setSmooth(true);
        logo.setAccessibleText("DevPilot");
        HBox header = new HBox(logo);
        header.setPadding(new Insets(32, 36, 0, 36));

        card.getStyleClass().add("login-card");
        card.setMaxWidth(440);
        card.setMaxHeight(Region.USE_PREF_SIZE); // as tall as its content
        StackPane center = new StackPane(card);
        center.setPadding(new Insets(24, 16, 104, 16));

        getStyleClass().add("login");
        setTop(header);
        setCenter(center);
        showReady(error);
    }

    private void showReady(String error) {
        SVGPath mark = new SVGPath();
        mark.setContent(GITHUB_MARK);
        mark.getStyleClass().add("github-mark");
        mark.setScaleX(1.5); // drawn at 16px, shown at 24px
        mark.setScaleY(1.5);
        StackPane icon = new StackPane(mark);
        icon.setMinSize(24, 24);

        Button loginButton = button("login", "Log in with GitHub", this::requestCode);
        loginButton.setGraphic(icon);
        loginButton.setDefaultButton(true);

        card.getChildren().setAll(title("Log in"));
        if (error != null) {
            Label message = text(error, "login-error");
            message.setId("status");
            card.getChildren().add(message);
        }
        card.getChildren().add(loginButton);
    }

    private void requestCode() {
        card.getChildren().setAll(title("Log in"),
                progress("Contacting GitHub...", Pos.CENTER_LEFT), cancelButton());
        task = Background.run(auth::requestDeviceCode, this::showCode, this::showError);
    }

    private void showCode(DeviceCode code) {
        Hyperlink link = new Hyperlink(code.verificationUri().replaceFirst("^https://", ""));
        link.setId("device-link");
        link.setOnAction(e -> {
            openUrl.accept(code.verificationUri());
            link.setVisited(false);
        });
        HBox goTo = new HBox(5, text("Go to", "login-text"), link);
        goTo.setAlignment(Pos.BASELINE_LEFT);

        Label codeLabel = text(code.userCode(), "login-code");
        codeLabel.setId("code");
        codeLabel.setMaxWidth(Double.MAX_VALUE);

        Button openButton = button("open-github", "Copy code and open GitHub", () -> {
            ClipboardContent clip = new ClipboardContent();
            clip.putString(code.userCode());
            Clipboard.getSystemClipboard().setContent(clip);
            openUrl.accept(code.verificationUri());
        });
        openButton.getStyleClass().add("primary");

        card.getChildren().setAll(
                new VBox(8, title("Enter this code on GitHub"), goTo),
                codeLabel,
                new VBox(12, openButton, cancelButton()),
                progress("Waiting for approval", Pos.CENTER));
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
            showReady(null);
        });
    }

    private static Label title(String text) {
        return text(text, "login-title");
    }

    private static Label text(String text, String styleClass) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE); // show every line
        label.getStyleClass().add(styleClass);
        return label;
    }

    private static Button button(String id, String text, Runnable action) {
        Button button = new Button(text);
        button.setId(id);
        button.setMaxWidth(Double.MAX_VALUE); // fill the card's width
        button.setMinHeight(50);
        button.setOnAction(e -> action.run());
        return button;
    }

    private static Node progress(String text, Pos alignment) {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(20, 20);
        HBox row = new HBox(12, spinner, text(text, "login-text"));
        row.setAlignment(alignment);
        return row;
    }
}
