package com.devpilot.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * The single place that knows HOW to reach the database.
 * Every DAO asks this class for a Connection and closes it when done
 * (try-with-resources). SQLite connections are cheap to open, so we
 * open one per operation instead of sharing one global connection.
 */
public final class DBConnection {

    private static final String URL = loadUrl();

    private DBConnection() { } // utility class: no instances

    private static String loadUrl() {
        Properties props = new Properties();
        try (InputStream in = DBConnection.class.getResourceAsStream("/application.properties")) {
            if (in != null) props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read application.properties", e);
        }
        return props.getProperty("db.url", "jdbc:sqlite:devpilot.db");
    }

    public static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(URL);
        // SQLite ignores FOREIGN KEY / ON DELETE CASCADE unless switched on per connection
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
        return conn;
    }

    /** Runs schema.sql once at startup to create any missing tables. */
    public static void initSchema() {
        try (InputStream in = DBConnection.class.getResourceAsStream("/schema.sql")) {
            if (in == null) throw new IllegalStateException("schema.sql not found on classpath");
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("(?m)--.*$", ""); // strip comments
            try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
                for (String stmt : sql.split(";")) {
                    if (!stmt.isBlank()) st.execute(stmt);
                }
            }
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Failed to initialise database schema", e);
        }
    }
}
