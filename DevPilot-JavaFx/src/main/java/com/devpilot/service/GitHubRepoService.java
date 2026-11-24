package com.devpilot.service;

import com.devpilot.model.Repository;
import com.devpilot.model.User;
import com.devpilot.util.HttpClientWrapper;
import com.devpilot.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Reads the user's repositories from GitHub. No SQL here: the caller saves them. */
public class GitHubRepoService {
    private static final int PAGE_SIZE = 100; // GitHub's maximum

    private final HttpClientWrapper http = new HttpClientWrapper();

    /** Fetches every page. An invalid token surfaces as HttpStatusException with status 401. */
    public List<Repository> fetchAll(User user) throws IOException, InterruptedException {
        Map<String, String> headers = Map.of(
                "Accept", "application/vnd.github+json",
                "Authorization", "Bearer " + user.getAccessToken());
        List<Repository> repos = new ArrayList<>();
        for (int page = 1; ; page++) {
            // A fixed sort order keeps pages consistent with each other
            JsonNode items = JsonUtil.parse(http.get("https://api.github.com/user/repos?sort=full_name&per_page="
                    + PAGE_SIZE + "&page=" + page, headers));
            for (JsonNode item : items) {
                repos.add(toRepository(item, user.getId()));
            }
            if (items.size() < PAGE_SIZE) return repos; // last page
        }
    }

    private static Repository toRepository(JsonNode json, int userId) {
        return new Repository(userId,
                json.required("id").asLong(),
                json.required("owner").required("login").asText(),
                json.required("name").asText(),
                json.path("private").asBoolean(),
                json.path("default_branch").asText(null),
                json.path("language").asText(null),
                json.path("html_url").asText(null),
                json.path("description").asText(null));
    }
}
