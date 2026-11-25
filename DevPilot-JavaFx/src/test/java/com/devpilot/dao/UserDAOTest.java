package com.devpilot.dao;

import com.devpilot.model.Repository;
import com.devpilot.model.User;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class UserDAOTest extends DaoTest {

    @Test
    void savesAndReadsEveryField() throws SQLException {
        User saved = newUser(9_000_000_000L, "ana"); // larger than an int
        assertTrue(saved.getId() > 0);

        User found = userDAO.findById(saved.getId()).orElseThrow();
        assertEquals(9_000_000_000L, found.getGithubId());
        assertEquals("ana", found.getGithubUsername());
        assertEquals("Name of ana", found.getDisplayName());
        assertEquals("https://example.test/a.png", found.getAvatarUrl());
        assertEquals("token-ana", found.getAccessToken());
        assertEquals("read:user,repo", found.getTokenScope());
    }

    @Test
    void keepsMissingFieldsAsNull() throws SQLException {
        User saved = userDAO.save(new User(1, "ana", null, null, null, null));
        User found = userDAO.findById(saved.getId()).orElseThrow();
        assertNull(found.getDisplayName());
        assertNull(found.getAvatarUrl());
        assertNull(found.getAccessToken());
    }

    @Test
    void savingTheSameGithubAccountUpdatesIt() throws SQLException {
        User first = newUser(1, "old-name");
        User second = newUser(1, "new-name");
        assertEquals(first.getId(), second.getId());
        assertEquals("new-name", userDAO.findByGithubId(1).orElseThrow().getGithubUsername());
    }

    @Test
    void returnsEmptyWhenNotFound() throws SQLException {
        assertTrue(userDAO.findById(999).isEmpty());
        assertTrue(userDAO.findByGithubId(999).isEmpty());
    }

    @Test
    void findsOnlyALoggedInUser() throws SQLException {
        assertTrue(userDAO.findLoggedIn().isEmpty());
        User ana = newUser(1, "ana");
        User ben = newUser(2, "ben");
        assertEquals(ben.getId(), userDAO.findLoggedIn().orElseThrow().getId());
        userDAO.clearToken(ben.getId());
        assertEquals(ana.getId(), userDAO.findLoggedIn().orElseThrow().getId());
        userDAO.clearToken(ana.getId());
        assertTrue(userDAO.findLoggedIn().isEmpty());
    }

    @Test
    void logoutKeepsTheUserAndTheirRepositories() throws SQLException {
        User ana = newUser(1, "ana");
        repoDAO.save(repo(ana, 10, "one"));
        userDAO.clearToken(ana.getId());

        User found = userDAO.findById(ana.getId()).orElseThrow();
        assertNull(found.getAccessToken());
        assertNull(found.getTokenScope());
        assertEquals(1, repoDAO.findByUserId(ana.getId()).size());
    }

    @Test
    void updatingTheProfileLeavesTheTokenAlone() throws SQLException {
        User ana = newUser(1, "ana");
        userDAO.clearToken(ana.getId());
        ana.setDisplayName("New Name"); // ana still holds the old token in memory
        userDAO.updateProfile(ana);

        User found = userDAO.findById(ana.getId()).orElseThrow();
        assertEquals("New Name", found.getDisplayName());
        assertNull(found.getAccessToken(), "a late refresh must not undo a logout");
    }

    @Test
    void deletingAUserDeletesTheirData() throws SQLException {
        User ana = newUser(1, "ana");
        Repository one = repoDAO.save(repo(ana, 10, "one"));
        userDAO.delete(ana.getId());
        assertTrue(userDAO.findById(ana.getId()).isEmpty());
        assertTrue(repoDAO.findById(one.getId()).isEmpty());
    }

    @Test
    void treatsSqlInInputAsText() throws SQLException {
        String nasty = "x'); DROP TABLE users; --";
        User saved = newUser(1, nasty);
        assertEquals(nasty, userDAO.findById(saved.getId()).orElseThrow().getGithubUsername());
    }
}
