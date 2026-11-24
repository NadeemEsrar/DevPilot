package com.devpilot.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Reads application.properties once. */
public final class AppConfig {
    private static final Properties PROPS = load();

    private AppConfig() { }

    private static Properties load() {
        try (InputStream in = AppConfig.class.getResourceAsStream("/application.properties")) {
            if (in == null) throw new IllegalStateException("application.properties not found");
            Properties props = new Properties();
            props.load(in);
            return props;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read application.properties", e);
        }
    }

    public static String get(String key) {
        String value = PROPS.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing setting: " + key);
        return value.trim();
    }
}
