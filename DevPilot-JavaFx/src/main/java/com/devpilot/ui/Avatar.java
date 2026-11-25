package com.devpilot.ui;

import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;

/** A round profile picture. Shows a person outline until (or unless) the picture loads. */
class Avatar extends StackPane {
    private final Circle circle;
    private final SVGPath person = new SVGPath();

    Avatar(double size) {
        circle = new Circle(size / 2);
        circle.getStyleClass().add("avatar");
        person.setContent("M18 8a6 6 0 1 0 0 12a6 6 0 1 0 0-12zM8 30c0-5 4-8 10-8s10 3 10 8z");
        person.getStyleClass().add("avatar-person");
        getChildren().addAll(circle, person);
    }

    void show(String url) {
        if (url == null || url.isBlank()) return;
        Image image;
        try {
            image = new Image(url, true); // true = load in the background
        } catch (IllegalArgumentException e) {
            return; // not a usable address: keep the outline
        }
        image.progressProperty().addListener((obs, old, progress) -> {
            if (progress.doubleValue() >= 1 && !image.isError()) fill(image);
        });
        if (image.getProgress() >= 1 && !image.isError()) fill(image); // it was already loaded
    }

    private void fill(Image image) {
        circle.getStyleClass().remove("avatar"); // its CSS color would replace the picture
        circle.setFill(new ImagePattern(image));
        person.setVisible(false);
    }
}
