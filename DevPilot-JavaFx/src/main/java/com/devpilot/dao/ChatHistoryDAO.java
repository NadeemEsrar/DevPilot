package com.devpilot.dao;

import com.devpilot.model.ChatMessage;
import com.devpilot.util.DBConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** All SQL for the "chat_history" table. */
public class ChatHistoryDAO {

    public ChatMessage save(ChatMessage msg) throws SQLException {
        if (msg.getTimestamp() == null) msg.setTimestamp(LocalDateTime.now());
        String sql = "INSERT INTO chat_history (user_id, repo_id, question, answer, timestamp) "
                   + "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, msg.getUserId());
            ps.setInt(2, msg.getRepoId());
            ps.setString(3, msg.getQuestion());
            ps.setString(4, msg.getAnswer());
            ps.setString(5, msg.getTimestamp().toString()); // ISO-8601 text sorts correctly
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) msg.setId(keys.getInt(1));
            }
        }
        return msg;
    }

    /** Conversation for one user + repo, oldest first (the order a chat screen shows). */
    public List<ChatMessage> findByUserAndRepo(int userId, int repoId) throws SQLException {
        List<ChatMessage> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM chat_history WHERE user_id = ? AND repo_id = ? ORDER BY id")) {
            ps.setInt(1, userId);
            ps.setInt(2, repoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public void deleteByUserAndRepo(int userId, int repoId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM chat_history WHERE user_id = ? AND repo_id = ?")) {
            ps.setInt(1, userId);
            ps.setInt(2, repoId);
            ps.executeUpdate();
        }
    }

    private ChatMessage mapRow(ResultSet rs) throws SQLException {
        ChatMessage m = new ChatMessage();
        m.setId(rs.getInt("id"));
        m.setUserId(rs.getInt("user_id"));
        m.setRepoId(rs.getInt("repo_id"));
        m.setQuestion(rs.getString("question"));
        m.setAnswer(rs.getString("answer"));
        String ts = rs.getString("timestamp");
        m.setTimestamp(ts == null ? null : LocalDateTime.parse(ts.replace(' ', 'T')));
        return m;
    }
}
