package com.devpilot.dao;

import com.devpilot.model.ChatMessage;
import com.devpilot.model.Repository;
import com.devpilot.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatHistoryDAOTest extends DaoTest {
    private User ana;
    private Repository one;

    @BeforeEach
    void addUserAndRepository() throws SQLException { // runs after emptyTestDatabase
        ana = newUser(1, "ana");
        one = repoDAO.save(repo(ana, 10, "one"));
    }

    @Test
    void savesAndReadsEveryField() throws SQLException {
        ChatMessage saved = new ChatMessage(ana.getId(), one.getId(), "What's \"é\" 日本?", "Line 1\nLine 2");
        saved.setTimestamp(LocalDateTime.of(2026, 11, 25, 17, 30, 5, 123_000_000));
        chatDAO.save(saved);
        assertTrue(saved.getId() > 0);

        ChatMessage found = chatDAO.findByUserAndRepo(ana.getId(), one.getId()).get(0);
        assertEquals(saved.getId(), found.getId());
        assertEquals(ana.getId(), found.getUserId());
        assertEquals(one.getId(), found.getRepoId());
        assertEquals("What's \"é\" 日本?", found.getQuestion());
        assertEquals("Line 1\nLine 2", found.getAnswer());
        assertEquals(saved.getTimestamp(), found.getTimestamp());
    }

    @Test
    void fillsInAMissingTimestamp() throws SQLException {
        ChatMessage message = new ChatMessage(ana.getId(), one.getId(), "q", "a");
        message.setTimestamp(null);
        chatDAO.save(message);
        assertNotNull(chatDAO.findByUserAndRepo(ana.getId(), one.getId()).get(0).getTimestamp());
    }

    @Test
    void keepsTheOrderTheyWereSavedIn() throws SQLException {
        ChatMessage first = new ChatMessage(ana.getId(), one.getId(), "first", "a");
        ChatMessage second = new ChatMessage(ana.getId(), one.getId(), "second", "a");
        second.setTimestamp(first.getTimestamp().minusHours(1)); // the clock was set back
        chatDAO.save(first);
        chatDAO.save(second);
        assertEquals(List.of("first", "second"), chatDAO.findByUserAndRepo(ana.getId(), one.getId())
                .stream().map(ChatMessage::getQuestion).toList());
    }

    @Test
    void separatesUsersAndRepositories() throws SQLException {
        User ben = newUser(2, "ben");
        Repository two = repoDAO.save(repo(ana, 11, "two"));
        chatDAO.save(new ChatMessage(ana.getId(), one.getId(), "q", "a"));
        assertEquals(1, chatDAO.findByUserAndRepo(ana.getId(), one.getId()).size());
        assertTrue(chatDAO.findByUserAndRepo(ana.getId(), two.getId()).isEmpty());
        assertTrue(chatDAO.findByUserAndRepo(ben.getId(), one.getId()).isEmpty());
    }

    @Test
    void deletesOnlyTheChosenConversation() throws SQLException {
        Repository two = repoDAO.save(repo(ana, 11, "two"));
        chatDAO.save(new ChatMessage(ana.getId(), one.getId(), "q", "a"));
        chatDAO.save(new ChatMessage(ana.getId(), two.getId(), "q", "a"));
        chatDAO.deleteByUserAndRepo(ana.getId(), one.getId());
        assertTrue(chatDAO.findByUserAndRepo(ana.getId(), one.getId()).isEmpty());
        assertEquals(1, chatDAO.findByUserAndRepo(ana.getId(), two.getId()).size());
    }

    @Test
    void rejectsAMessageForAMissingRepository() {
        assertThrows(SQLException.class,
                () -> chatDAO.save(new ChatMessage(ana.getId(), 999, "q", "a")));
    }

    @Test
    void rejectsAMissingQuestion() {
        assertThrows(SQLException.class,
                () -> chatDAO.save(new ChatMessage(ana.getId(), one.getId(), null, "a")));
    }
}
