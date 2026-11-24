package com.devpilot.model;

/**
 * One row of the "users" table, as a Java object.
 * A model class holds data only: no SQL, no HTTP, no UI.
 */
public class User {
    private int id;                 // local DB primary key (0 = not saved yet)
    private long githubId;          // GitHub's own numeric user id
    private String githubUsername;
    private String displayName;
    private String avatarUrl;
    private String accessToken;
    private String tokenScope;

    public User() { }

    public User(long githubId, String githubUsername, String displayName,
                String avatarUrl, String accessToken, String tokenScope) {
        this.githubId = githubId;
        this.githubUsername = githubUsername;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.accessToken = accessToken;
        this.tokenScope = tokenScope;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public long getGithubId() { return githubId; }
    public void setGithubId(long githubId) { this.githubId = githubId; }
    public String getGithubUsername() { return githubUsername; }
    public void setGithubUsername(String githubUsername) { this.githubUsername = githubUsername; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getTokenScope() { return tokenScope; }
    public void setTokenScope(String tokenScope) { this.tokenScope = tokenScope; }

    @Override
    public String toString() {
        // Never print the token itself
        return "User{id=" + id + ", githubId=" + githubId + ", username='" + githubUsername
                + "', displayName='" + displayName + "', hasToken=" + (accessToken != null) + "}";
    }
}
