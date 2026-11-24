package com.devpilot.ui;

import javafx.concurrent.Task;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Runs slow work off the JavaFX thread. Both callbacks run ON the JavaFX thread. */
final class Background {
    private Background() { }

    static <T> Task<T> run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e -> onError.accept(task.getException()));
        Thread thread = new Thread(task, "devpilot-background");
        thread.setDaemon(true); // don't keep the app alive after the window closes
        thread.start();
        return task;
    }
}
