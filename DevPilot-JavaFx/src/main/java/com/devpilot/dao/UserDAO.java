package com.devpilot.dao;

import com.devpilot.model.User;
import com.devpilot.util.DBConnection;

import java.sql.*;
import java.util.Optional;

/** All SQL for the "users" table lives here, and nowhere else. */
public class UserDAO {

    /**
     * Insert the user, or update them if this GitHub account was seen before.
     * Sets the generated id on the passed object and returns it.
     */
    public User save(User user) throws SQLException {
        Optional<User> existing = findByGithubId(user.getGithubId());
        if (existing.isPresent()) {
            user.setId(existing.get().getId());
            update(user);
            return user;
        }
        String sql = "INSERT INTO users (github_id, github_username, display_name, avatar_url, "
                   + "access_token, token_scope) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, user.getGithubId());
            ps.setString(2, user.getGithubUsername());
            ps.setString(3, user.getDisplayName());
            ps.setString(4, user.getAvatarUrl());
            ps.setString(5, user.getAccessToken());
            ps.setString(6, user.getTokenScope());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) user.setId(keys.getInt(1));
            }
        }
        return user;
    }

    public void update(User user) throws SQLException {
        String sql = "UPDATE users SET github_username=?, display_name=?, avatar_url=?, "
                   + "access_token=?, token_scope=? WHERE id=?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getGithubUsername());
            ps.setString(2, user.getDisplayName());
            ps.setString(3, user.getAvatarUrl());
            ps.setString(4, user.getAccessToken());
            ps.setString(5, user.getTokenScope());
            ps.setInt(6, user.getId());
            ps.executeUpdate();
        }
    }

    public Optional<User> findById(int id) throws SQLException {
        return findOne("SELECT * FROM users WHERE id = ?", id);
    }

    public Optional<User> findByGithubId(long githubId) throws SQLException {
        return findOne("SELECT * FROM users WHERE github_id = ?", githubId);
    }

    /** The most recently added user that still has a token, if any. */
    public Optional<User> findLoggedIn() throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM users WHERE access_token IS NOT NULL ORDER BY id DESC LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
        }
    }

    /** Clears the token on logout but keeps the user's row (and their chat history). */
    public void clearToken(int userId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE users SET access_token = NULL, token_scope = NULL WHERE id = ?")) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // ---- helpers ----

    private Optional<User> findOne(String sql, Object param) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    /** Turns the current ResultSet row into a User object. */
    private User mapRow(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getInt("id"));
        u.setGithubId(rs.getLong("github_id"));
        u.setGithubUsername(rs.getString("github_username"));
        u.setDisplayName(rs.getString("display_name"));
        u.setAvatarUrl(rs.getString("avatar_url"));
        u.setAccessToken(rs.getString("access_token"));
        u.setTokenScope(rs.getString("token_scope"));
        return u;
    }
}
