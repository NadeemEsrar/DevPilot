package com.devpilot.model;

/** One row of the "repositories" table. */
public class Repository {
    private int id;
    private int userId;             // foreign key -> users.id
    private long ghRepoId;
    private String owner;
    private String name;
    private String fullName;        // "owner/name"
    private boolean isPrivate;
    private String defaultBranch;
    private String language;
    private String htmlUrl;
    private String description;

    public Repository() { }

    public Repository(int userId, long ghRepoId, String owner, String name, boolean isPrivate,
                      String defaultBranch, String language, String htmlUrl, String description) {
        this.userId = userId;
        this.ghRepoId = ghRepoId;
        this.owner = owner;
        this.name = name;
        this.fullName = owner + "/" + name;
        this.isPrivate = isPrivate;
        this.defaultBranch = defaultBranch;
        this.language = language;
        this.htmlUrl = htmlUrl;
        this.description = description;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public long getGhRepoId() { return ghRepoId; }
    public void setGhRepoId(long ghRepoId) { this.ghRepoId = ghRepoId; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public boolean isPrivate() { return isPrivate; }
    public void setPrivate(boolean isPrivate) { this.isPrivate = isPrivate; }
    public String getDefaultBranch() { return defaultBranch; }
    public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getHtmlUrl() { return htmlUrl; }
    public void setHtmlUrl(String htmlUrl) { this.htmlUrl = htmlUrl; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public String toString() {
        return "Repository{id=" + id + ", fullName='" + fullName + "', private=" + isPrivate
                + ", language='" + language + "'}";
    }
}
