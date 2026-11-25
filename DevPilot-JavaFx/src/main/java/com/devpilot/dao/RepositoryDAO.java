package com.devpilot.dao;

import com.devpilot.model.Repository;
import com.devpilot.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** All SQL for the "repositories" table. */
public class RepositoryDAO {

    // Shared by save and saveAll so they can never drift apart
    private static final String UPSERT_SQL =
            "INSERT INTO repositories (user_id, gh_repo_id, owner, name, full_name, is_private, "
                   + "default_branch, language, html_url, description) VALUES (?,?,?,?,?,?,?,?,?,?) "
                   + "ON CONFLICT(user_id, gh_repo_id) DO UPDATE SET owner=excluded.owner, name=excluded.name, "
                   + "full_name=excluded.full_name, is_private=excluded.is_private, "
                   + "default_branch=excluded.default_branch, language=excluded.language, "
                   + "html_url=excluded.html_url, description=excluded.description";

    /**
     * Insert or update one repo. "Upsert" matters because the list is synced
     * from GitHub repeatedly and must not create duplicates.
     */
    public Repository save(Repository repo) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {
            bind(ps, repo);
            ps.executeUpdate();
        }
        // Read back the id (works for both the insert and the update case)
        findByGhRepoId(repo.getUserId(), repo.getGhRepoId()).ifPresent(r -> repo.setId(r.getId()));
        return repo;
    }

    /**
     * Saves many repos in ONE transaction: either all succeed or none do,
     * and it is much faster than committing each row separately.
     */
    public void saveAll(List<Repository> repos) throws SQLException {
        inTransaction(conn -> upsertAll(conn, repos));
    }

    /**
     * Makes the user's rows match this COMPLETE list: upserts every repo in it and
     * deletes the rest. Deleting a repo also deletes its chats (ON DELETE CASCADE).
     */
    public void sync(int userId, List<Repository> repos) throws SQLException {
        Set<Long> keep = new HashSet<>();
        for (Repository r : repos) keep.add(r.getGhRepoId());

        inTransaction(conn -> {
            upsertAll(conn, repos);
            List<Integer> missing = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, gh_repo_id FROM repositories WHERE user_id = ?")) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        if (!keep.contains(rs.getLong("gh_repo_id"))) missing.add(rs.getInt("id"));
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM repositories WHERE id = ?")) {
                for (int id : missing) {
                    ps.setInt(1, id);
                    ps.executeUpdate();
                }
            }
        });
    }

    public List<Repository> findByUserId(int userId) throws SQLException {
        List<Repository> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM repositories WHERE user_id = ? ORDER BY full_name")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public Optional<Repository> findById(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM repositories WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    public Optional<Repository> findByGhRepoId(int userId, long ghRepoId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM repositories WHERE user_id = ? AND gh_repo_id = ?")) {
            ps.setInt(1, userId);
            ps.setLong(2, ghRepoId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    public void deleteByUserId(int userId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM repositories WHERE user_id = ?")) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }

    // ---- helpers ----

    private interface SqlWork {
        void run(Connection conn) throws SQLException;
    }

    /** Commits if the work succeeds, rolls back if it throws anything. */
    private void inTransaction(SqlWork work) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                work.run(conn);
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private void upsertAll(Connection conn, List<Repository> repos) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {
            for (Repository r : repos) {
                bind(ps, r);
                ps.executeUpdate();
            }
        }
    }

    private void bind(PreparedStatement ps, Repository r) throws SQLException {
        ps.setInt(1, r.getUserId());
        ps.setLong(2, r.getGhRepoId());
        ps.setString(3, r.getOwner());
        ps.setString(4, r.getName());
        ps.setString(5, r.getFullName());
        ps.setInt(6, r.isPrivate() ? 1 : 0); // SQLite has no BOOLEAN type
        ps.setString(7, r.getDefaultBranch());
        ps.setString(8, r.getLanguage());
        ps.setString(9, r.getHtmlUrl());
        ps.setString(10, r.getDescription());
    }

    private Repository mapRow(ResultSet rs) throws SQLException {
        Repository r = new Repository();
        r.setId(rs.getInt("id"));
        r.setUserId(rs.getInt("user_id"));
        r.setGhRepoId(rs.getLong("gh_repo_id"));
        r.setOwner(rs.getString("owner"));
        r.setName(rs.getString("name"));
        r.setFullName(rs.getString("full_name"));
        r.setPrivate(rs.getInt("is_private") == 1);
        r.setDefaultBranch(rs.getString("default_branch"));
        r.setLanguage(rs.getString("language"));
        r.setHtmlUrl(rs.getString("html_url"));
        r.setDescription(rs.getString("description"));
        return r;
    }
}
