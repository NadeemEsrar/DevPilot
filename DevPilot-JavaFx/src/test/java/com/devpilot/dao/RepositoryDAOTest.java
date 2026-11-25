package com.devpilot.dao;

import com.devpilot.model.ChatMessage;
import com.devpilot.model.Repository;
import com.devpilot.model.User;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryDAOTest extends DaoTest {

    @Test
    void savesAndReadsEveryField() throws SQLException {
        User ana = newUser(1, "ana");
        Repository saved = repoDAO.save(new Repository(ana.getId(), 9_000_000_000L, "owner", "one", true,
                "dev", "Python", "https://example.test/one", "About one"));
        assertTrue(saved.getId() > 0);

        Repository found = repoDAO.findById(saved.getId()).orElseThrow();
        assertEquals(ana.getId(), found.getUserId());
        assertEquals(9_000_000_000L, found.getGhRepoId());
        assertEquals("owner", found.getOwner());
        assertEquals("one", found.getName());
        assertEquals("owner/one", found.getFullName());
        assertTrue(found.isPrivate());
        assertEquals("dev", found.getDefaultBranch());
        assertEquals("Python", found.getLanguage());
        assertEquals("https://example.test/one", found.getHtmlUrl());
        assertEquals("About one", found.getDescription());
    }

    @Test
    void savingAgainUpdatesEveryFieldAndKeepsTheId() throws SQLException {
        User ana = newUser(1, "ana");
        int id = repoDAO.save(repo(ana, 10, "one")).getId();
        Repository again = repoDAO.save(new Repository(ana.getId(), 10, "new-owner", "renamed", true,
                "dev", null, "https://example.test/renamed", null));
        assertEquals(id, again.getId());

        Repository found = repoDAO.findById(id).orElseThrow();
        assertEquals("new-owner/renamed", found.getFullName());
        assertEquals("new-owner", found.getOwner());
        assertEquals("renamed", found.getName());
        assertTrue(found.isPrivate());
        assertEquals("dev", found.getDefaultBranch());
        assertNull(found.getLanguage());
        assertEquals("https://example.test/renamed", found.getHtmlUrl());
        assertNull(found.getDescription());
        assertEquals(1, repoDAO.findByUserId(ana.getId()).size());
    }

    @Test
    void listsOnlyTheUsersRepositoriesInOrder() throws SQLException {
        User ana = newUser(1, "ana");
        User ben = newUser(2, "ben");
        repoDAO.saveAll(List.of(repo(ana, 10, "b"), repo(ana, 11, "a"), repo(ben, 10, "c")));
        assertEquals(List.of("a", "b"),
                repoDAO.findByUserId(ana.getId()).stream().map(Repository::getName).toList());
        assertEquals(1, repoDAO.findByUserId(ben.getId()).size());
    }

    @Test
    void returnsEmptyWhenNotFound() throws SQLException {
        User ana = newUser(1, "ana");
        assertTrue(repoDAO.findById(999).isEmpty());
        assertTrue(repoDAO.findByGhRepoId(ana.getId(), 999).isEmpty());
        assertTrue(repoDAO.findByUserId(ana.getId()).isEmpty());
    }

    @Test
    void rejectsARepositoryWithoutAUser() {
        User missing = new User();
        missing.setId(999);
        assertThrows(SQLException.class, () -> repoDAO.save(repo(missing, 10, "one")));
    }

    @Test
    void saveAllSavesNothingIfOneFails() throws SQLException {
        User ana = newUser(1, "ana");
        assertThrows(SQLException.class,
                () -> repoDAO.saveAll(List.of(repo(ana, 10, "one"), repo(ana, 11, null))));
        assertTrue(repoDAO.findByUserId(ana.getId()).isEmpty());
    }

    @Test
    void syncAddsUpdatesAndDeletes() throws SQLException {
        User ana = newUser(1, "ana");
        repoDAO.saveAll(List.of(repo(ana, 10, "keep"), repo(ana, 11, "gone")));
        int keptId = repoDAO.findByGhRepoId(ana.getId(), 10).orElseThrow().getId();

        repoDAO.sync(ana.getId(), List.of(repo(ana, 10, "kept"), repo(ana, 12, "new")));

        assertEquals(List.of("kept", "new"),
                repoDAO.findByUserId(ana.getId()).stream().map(Repository::getName).toList());
        assertEquals(keptId, repoDAO.findByGhRepoId(ana.getId(), 10).orElseThrow().getId());
    }

    @Test
    void syncDeletesChatsOnlyOfRemovedRepositories() throws SQLException {
        User ana = newUser(1, "ana");
        Repository keep = repoDAO.save(repo(ana, 10, "keep"));
        Repository gone = repoDAO.save(repo(ana, 11, "gone"));
        chatDAO.save(new ChatMessage(ana.getId(), keep.getId(), "q", "a"));
        chatDAO.save(new ChatMessage(ana.getId(), gone.getId(), "q", "a"));

        repoDAO.sync(ana.getId(), List.of(repo(ana, 10, "keep")));

        assertEquals(1, chatDAO.findByUserAndRepo(ana.getId(), keep.getId()).size());
        assertTrue(chatDAO.findByUserAndRepo(ana.getId(), gone.getId()).isEmpty());
    }

    @Test
    void failedSyncChangesNothing() throws SQLException {
        User ana = newUser(1, "ana");
        repoDAO.saveAll(List.of(repo(ana, 10, "one"), repo(ana, 11, "two")));
        assertThrows(SQLException.class, () -> repoDAO.sync(ana.getId(),
                List.of(repo(ana, 10, "changed"), repo(ana, 12, null))));
        assertEquals(List.of("one", "two"),
                repoDAO.findByUserId(ana.getId()).stream().map(Repository::getName).toList());
    }

    @Test
    void syncLeavesOtherUsersAlone() throws SQLException {
        User ana = newUser(1, "ana");
        User ben = newUser(2, "ben");
        repoDAO.saveAll(List.of(repo(ana, 10, "one"), repo(ben, 11, "two")));
        repoDAO.sync(ana.getId(), List.of());
        assertTrue(repoDAO.findByUserId(ana.getId()).isEmpty());
        assertEquals(1, repoDAO.findByUserId(ben.getId()).size());
    }

    @Test
    void deletesAllRepositoriesOfAUser() throws SQLException {
        User ana = newUser(1, "ana");
        User ben = newUser(2, "ben");
        repoDAO.saveAll(List.of(repo(ana, 10, "one"), repo(ben, 11, "two")));
        repoDAO.deleteByUserId(ana.getId());
        assertTrue(repoDAO.findByUserId(ana.getId()).isEmpty());
        assertEquals(1, repoDAO.findByUserId(ben.getId()).size());
    }
}
