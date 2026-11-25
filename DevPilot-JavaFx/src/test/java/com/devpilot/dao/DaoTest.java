package com.devpilot.dao;

import com.devpilot.model.Repository;
import com.devpilot.model.User;
import com.devpilot.util.AppConfig;
import com.devpilot.util.DBConnection;
import org.junit.jupiter.api.BeforeEach;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Shared setup: a separate test database with empty tables before every test. */
abstract class DaoTest {
    final UserDAO userDAO = new UserDAO();
    final RepositoryDAO repoDAO = new RepositoryDAO();
    final ChatHistoryDAO chatDAO = new ChatHistoryDAO();

    @BeforeEach
    void emptyTestDatabase() throws SQLException {
        // Never touch the real database
        assertEquals("jdbc:sqlite:target/devpilot-test.db", AppConfig.get("db.url"));
        DBConnection.initSchema();
        try (Connection conn = DBConnection.getConnection(); Statement st = conn.createStatement()) {
            st.execute("DELETE FROM chat_history");
            st.execute("DELETE FROM repositories");
            st.execute("DELETE FROM users");
        }
    }

    User newUser(long githubId, String username) throws SQLException {
        return userDAO.save(new User(githubId, username, "Name of " + username,
                "https://example.test/a.png", "token-" + username, "read:user,repo"));
    }

    /** Not saved yet. */
    static Repository repo(User user, long ghRepoId, String name) {
        return new Repository(user.getId(), ghRepoId, "owner", name, false, "main",
                "Java", "https://example.test/" + name, "About " + name);
    }
}
