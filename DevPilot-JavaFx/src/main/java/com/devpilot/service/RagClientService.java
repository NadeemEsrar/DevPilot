package com.devpilot.service;

import com.devpilot.model.Repository;
import com.devpilot.model.User;
import com.devpilot.util.AppConfig;
import com.devpilot.util.HttpClientWrapper;
import com.devpilot.util.JsonUtil;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** Talks to the Python RAG service. */
public class RagClientService {
    // The JSON contract; must match models/schemas.py in DevPilot-RAGService
    private record AskRequest(String repo, String branch, String question) { }
    private record AskResponse(String answer) { }
    private record RepoRequest(String repo, String branch) { }

    /** Describes a repo's embeddings. createdAt and commit are null if there are none. */
    public record IndexStatus(boolean exists, boolean building,
                              @JsonProperty("created_at") String createdAt, String commit) { }

    // Creating embeddings for a big repo takes a while
    private static final Duration TIMEOUT = Duration.ofMinutes(10);

    private final HttpClientWrapper http = new HttpClientWrapper();
    private final String url;

    public RagClientService() {
        this(AppConfig.get("rag.url"));
    }

    /** Lets tests point at another address. */
    RagClientService(String url) {
        this.url = url;
    }

    public IndexStatus indexStatus(User user, Repository repo) throws IOException, InterruptedException {
        String query = "?repo=" + encode(repo.getFullName());
        if (repo.getDefaultBranch() != null) query += "&branch=" + encode(repo.getDefaultBranch());
        return readStatus(http.get(url + "/index" + query, headers(user)));
    }

    /** Creates or re-creates the embeddings from the newest commit. Returns when done. */
    public IndexStatus createIndex(User user, Repository repo) throws IOException, InterruptedException {
        String body = JsonUtil.toJson(new RepoRequest(repo.getFullName(), repo.getDefaultBranch()));
        return readStatus(http.post(url + "/index", body, headers(user), TIMEOUT));
    }

    public String ask(User user, Repository repo, String question) throws IOException, InterruptedException {
        String body = JsonUtil.toJson(new AskRequest(repo.getFullName(), repo.getDefaultBranch(), question));
        AskResponse response = JsonUtil.fromJson(
                http.post(url + "/ask", body, headers(user), TIMEOUT), AskResponse.class);
        if (response == null || response.answer() == null) {
            throw new IOException("The RAG service's response has no answer");
        }
        return response.answer();
    }

    private static IndexStatus readStatus(String json) throws IOException {
        IndexStatus status = JsonUtil.fromJson(json, IndexStatus.class);
        if (status == null || status.exists() && (status.createdAt() == null || status.commit() == null)) {
            throw new IOException("The RAG service's response is incomplete");
        }
        return status;
    }

    private static Map<String, String> headers(User user) {
        return Map.of("Authorization", "Bearer " + user.getAccessToken());
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }
}
