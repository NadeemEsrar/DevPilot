package com.devpilot.service;

import com.devpilot.model.User;
import com.devpilot.util.AppConfig;
import com.devpilot.util.HttpClientWrapper;
import com.devpilot.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** Logs a user in with GitHub's OAuth Device Flow. Knows nothing about SQL or UI. */
public class GitHubAuthService {
    private static final String DEVICE_CODE_URL = "https://github.com/login/device/code";
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_URL = "https://api.github.com/user";
    // Without this header GitHub answers in form encoding instead of JSON
    private static final Map<String, String> JSON = Map.of("Accept", "application/json");

    private final HttpClientWrapper http = new HttpClientWrapper();

    /** Step 1's answer. deviceCode stays private to the app; userCode is shown to the user. */
    public record DeviceCode(String deviceCode, String userCode, String verificationUri,
                             int intervalSeconds, Instant expiresAt) { }

    public record Token(String accessToken, String scope) {
        // A record's default toString would print the token
        @Override
        public String toString() { return "Token[hidden, scope=" + scope + "]"; }
    }

    /** The login could not be completed (cancelled, expired, bad setup...). */
    public static class AuthException extends IOException {
        public AuthException(String message) { super(message); }
    }

    /** Step 1: ask GitHub for a pair of codes. */
    public DeviceCode requestDeviceCode() throws IOException, InterruptedException {
        JsonNode json = JsonUtil.parse(http.postForm(DEVICE_CODE_URL, Map.of(
                "client_id", AppConfig.get("github.client-id"),
                "scope", AppConfig.get("github.scope")), JSON));
        if (json.has("error")) throw authError(json);
        return new DeviceCode(
                required(json, "device_code"), required(json, "user_code"),
                required(json, "verification_uri"), json.path("interval").asInt(5),
                Instant.now().plusSeconds(json.path("expires_in").asLong(900)));
    }

    /** Step 3: poll until the user approves, cancels, or the code expires. BLOCKS the thread. */
    public Token waitForToken(DeviceCode code) throws IOException, InterruptedException {
        int interval = code.intervalSeconds();
        while (Instant.now().isBefore(code.expiresAt())) {
            Thread.sleep(Duration.ofSeconds(interval));
            JsonNode json = JsonUtil.parse(http.postForm(TOKEN_URL, Map.of(
                    "client_id", AppConfig.get("github.client-id"),
                    "device_code", code.deviceCode(),
                    "grant_type", "urn:ietf:params:oauth:grant-type:device_code"), JSON));

            // GitHub reports these errors with status 200, so look inside the JSON
            switch (json.path("error").asText("")) {
                case "" -> {
                    return new Token(required(json, "access_token"), json.path("scope").asText(""));
                }
                case "authorization_pending" -> { /* not approved yet: poll again */ }
                case "slow_down" -> interval += 5;
                case "expired_token" -> throw new AuthException("The code expired. Please log in again.");
                case "access_denied" -> throw new AuthException("Login was cancelled.");
                default -> throw authError(json);
            }
        }
        throw new AuthException("The code expired. Please log in again.");
    }

    /** Step 4: use the token to find out who logged in. */
    public User fetchUser(Token token) throws IOException, InterruptedException {
        JsonNode json = JsonUtil.parse(http.get(USER_URL, Map.of(
                "Accept", "application/vnd.github+json",
                "Authorization", "Bearer " + token.accessToken())));
        return new User(json.required("id").asLong(), required(json, "login"),
                json.path("name").asText(null), json.path("avatar_url").asText(null),
                token.accessToken(), token.scope());
    }

    private static String required(JsonNode json, String field) throws AuthException {
        String value = json.path(field).asText("");
        if (value.isEmpty()) throw new AuthException("GitHub's response is missing '" + field + "'");
        return value;
    }

    private static AuthException authError(JsonNode json) {
        String error = json.path("error").asText();
        return new AuthException("GitHub login failed: " + json.path("error_description").asText(error));
    }
}
