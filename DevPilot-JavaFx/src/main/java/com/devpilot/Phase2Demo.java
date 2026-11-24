package com.devpilot;

import com.devpilot.model.User;
import com.devpilot.util.HttpClientWrapper;
import com.devpilot.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.Map;

/** Phase 2 console test: HTTP + JSON against GitHub's public API (no login). */
public class Phase2Demo {
    private static final Map<String, String> HEADERS =
            Map.of("Accept", "application/vnd.github+json");

    public static void main(String[] args) throws Exception {
        HttpClientWrapper http = new HttpClientWrapper();

        // 1. GET and map by hand: GitHub's "id" is our githubId, not our local id
        JsonNode node = JsonUtil.parse(http.get("https://api.github.com/users/octocat", HEADERS));
        User user = new User(node.path("id").asLong(), node.path("login").asText(),
                node.path("name").asText(null), node.path("avatar_url").asText(null), null, null);
        System.out.println("Mapped: " + user);

        // 2. Java -> JSON -> Java (data binding)
        String json = JsonUtil.toJson(Map.of("question", "Where is main?"));
        System.out.println("JSON:   " + json);
        System.out.println("Back:   " + JsonUtil.fromJson(json, Map.class));

        // 3. The server answers with an error status
        try {
            http.get("https://api.github.com/not-a-real-endpoint", HEADERS);
        } catch (HttpClientWrapper.HttpStatusException e) {
            System.out.println("Status: " + e.getMessage());
        }

        // 4. No server answers at all
        try {
            http.get("http://localhost:1/", Map.of());
        } catch (IOException e) {
            System.out.println("Network: " + e.getClass().getSimpleName());
        }

        // 5. The response is not valid JSON
        try {
            JsonUtil.parse("{ broken");
        } catch (JsonProcessingException e) {
            System.out.println("Bad JSON: " + e.getClass().getSimpleName());
        }
    }
}
