package com.devpilot;

import com.devpilot.dao.RepositoryDAO;
import com.devpilot.dao.UserDAO;
import com.devpilot.model.Repository;
import com.devpilot.model.User;
import com.devpilot.service.GitHubRepoService;
import com.devpilot.util.DBConnection;
import com.devpilot.util.HttpClientWrapper.HttpStatusException;

import java.util.List;
import java.util.Optional;

/** Phase 4 console test: real repos from GitHub, saved via JDBC. */
public class Phase4Demo {
    public static void main(String[] args) throws Exception {
        DBConnection.initSchema();
        UserDAO userDAO = new UserDAO();
        RepositoryDAO repoDAO = new RepositoryDAO();

        Optional<User> loggedIn = userDAO.findLoggedIn();
        if (loggedIn.isEmpty()) {
            System.out.println("Not logged in. Run Phase3Demo first.");
            return;
        }
        User user = loggedIn.get();

        try {
            List<Repository> fetched = new GitHubRepoService().fetchAll(user);
            repoDAO.saveAll(fetched);
            System.out.println("Fetched from GitHub: " + fetched.size());
        } catch (HttpStatusException e) {
            if (e.getStatusCode() != 401) throw e;
            userDAO.clearToken(user.getId());
            System.out.println("Token no longer valid. Run Phase3Demo to log in again.");
            return;
        }

        List<Repository> saved = repoDAO.findByUserId(user.getId());
        System.out.println("In database: " + saved.size());
        saved.forEach(r -> System.out.println("  " + r));
    }
}
