package com.devpilot;

import com.devpilot.dao.ChatHistoryDAO;
import com.devpilot.dao.RepositoryDAO;
import com.devpilot.dao.UserDAO;
import com.devpilot.model.ChatMessage;
import com.devpilot.model.Repository;
import com.devpilot.model.User;
import com.devpilot.util.DBConnection;

import java.util.List;

/** Phase 1 console test: exercise every DAO with dummy data. No UI, no network. */
public class Phase1Demo {
    public static void main(String[] args) throws Exception {
        DBConnection.initSchema();

        UserDAO userDAO = new UserDAO();
        RepositoryDAO repoDAO = new RepositoryDAO();
        ChatHistoryDAO chatDAO = new ChatHistoryDAO();

        // 1. Save a user (running twice updates instead of duplicating)
        User user = userDAO.save(new User(123456L, "octocat", "The Octocat",
                "https://avatars.githubusercontent.com/u/583231", "dummy-token", "repo read:user"));
        System.out.println("Saved:   " + user);

        // 2. Save some repos for that user in one transaction
        repoDAO.saveAll(List.of(
                new Repository(user.getId(), 1001L, "octocat", "hello-world", false, "main",
                        "Java", "https://github.com/octocat/hello-world", "My first repo"),
                new Repository(user.getId(), 1002L, "octocat", "secret-project", true, "dev",
                        "Python", "https://github.com/octocat/secret-project", null)));
        List<Repository> repos = repoDAO.findByUserId(user.getId());
        System.out.println("Repos:");
        repos.forEach(r -> System.out.println("  " + r));

        // 3. Save a chat exchange about the first repo and read it back
        Repository repo = repos.get(0);
        chatDAO.save(new ChatMessage(user.getId(), repo.getId(),
                "Where is the main method?", "In src/Main.java (dummy answer)"));
        System.out.println("Chat for " + repo.getFullName() + ":");
        chatDAO.findByUserAndRepo(user.getId(), repo.getId())
                .forEach(m -> System.out.println("  " + m));

        // 4. Logout simulation
        userDAO.clearToken(user.getId());
        System.out.println("After logout: " + userDAO.findById(user.getId()).orElseThrow());
    }
}
