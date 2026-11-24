package com.devpilot.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Login screen. Phase 6 adds the device code and real login. */
public class LoginView extends VBox {

    public LoginView(Runnable onLogin) {
        Label title = new Label("DevPilot");
        title.getStyleClass().add("title");
        Label subtitle = new Label("Ask questions about your GitHub repositories");

        Button loginButton = new Button("Log in with GitHub");
        loginButton.setId("login");
        loginButton.setDefaultButton(true); // Enter triggers it
        loginButton.setOnAction(e -> onLogin.run());

        setAlignment(Pos.CENTER);
        setSpacing(16);
        getChildren().addAll(title, subtitle, loginButton);
    }
}
