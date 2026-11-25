package com.devpilot.service;

import com.devpilot.model.Repository;
import com.devpilot.model.User;
import com.devpilot.util.HttpClientWrapper;
import com.devpilot.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Reads a repository's file list and file contents from GitHub. */
public class GitHubFileService {
    public static final long MAX_FILE_BYTES = 500 * 1024;

    /** One file. The sha identifies its exact contents. */
    public record RepoFile(String path, String sha, long size) { }

    /** truncated = GitHub shortened the list because the repository is very large. */
    public record FileList(List<RepoFile> files, boolean truncated) { }

    private final HttpClientWrapper http = new HttpClientWrapper();
    private final String apiUrl;

    public GitHubFileService() {
        this("https://api.github.com");
    }

    /** Lets tests point at another address. */
    GitHubFileService(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    /** Lists every file at a commit or branch. */
    public FileList list(User user, Repository repo, String ref) throws IOException, InterruptedException {
        JsonNode json = JsonUtil.parse(http.get(repoUrl(repo) + "/git/trees/"
                + URLEncoder.encode(ref, StandardCharsets.UTF_8) + "?recursive=1",
                headers(user, "application/vnd.github+json")));
        List<RepoFile> files = new ArrayList<>();
        for (JsonNode item : json.required("tree")) {
            if (item.path("type").asText().equals("blob")) { // folders come from the paths
                files.add(new RepoFile(item.required("path").asText(), item.required("sha").asText(),
                        item.path("size").asLong()));
            }
        }
        return new FileList(files, json.path("truncated").asBoolean());
    }

    public String read(User user, Repository repo, RepoFile file) throws IOException, InterruptedException {
        return http.get(repoUrl(repo) + "/git/blobs/" + file.sha(),
                headers(user, "application/vnd.github.raw+json")); // raw = the text itself, not JSON
    }

    private String repoUrl(Repository repo) {
        return apiUrl + "/repos/" + repo.getFullName();
    }

    private static Map<String, String> headers(User user, String accept) {
        return Map.of("Accept", accept, "Authorization", "Bearer " + user.getAccessToken());
    }
}
