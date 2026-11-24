package com.devpilot.service;

import com.devpilot.dao.RepositoryDAO;
import com.devpilot.dao.UserDAO;
import com.devpilot.model.Repository;
import com.devpilot.model.User;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/** Makes the local database match GitHub for one user. */
public class RepoSyncService {
    private final GitHubAuthService auth;
    private final GitHubRepoService github;
    private final RepositoryDAO repoDAO = new RepositoryDAO();
    private final UserDAO userDAO = new UserDAO();

    public record Result(User user, List<Repository> repos) { }

    public RepoSyncService(GitHubAuthService auth, GitHubRepoService github) {
        this.auth = auth;
        this.github = github;
    }

    /** What we already have locally; no network. */
    public List<Repository> cached(User user) throws SQLException {
        return repoDAO.findByUserId(user.getId());
    }

    /** Network first, database last: if any fetch fails, nothing is changed or deleted. */
    public Result sync(User user) throws IOException, InterruptedException, SQLException {
        User profile = auth.fetchUser(new GitHubAuthService.Token(user.getAccessToken(), user.getTokenScope()));
        profile.setId(user.getId());
        List<Repository> fetched = github.fetchAll(user);
        userDAO.updateProfile(profile);
        repoDAO.sync(user.getId(), fetched);
        return new Result(profile, cached(user));
    }
}
