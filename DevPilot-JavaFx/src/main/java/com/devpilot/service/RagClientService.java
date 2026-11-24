package com.devpilot.service;

import com.devpilot.model.Repository;
import com.devpilot.model.User;
import com.devpilot.util.AppConfig;
import com.devpilot.util.HttpClientWrapper;
import com.devpilot.util.JsonUtil;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/** Sends questions to the Python RAG service. */
public class RagClientService {
    // The JSON contract; must match models/schemas.py in DevPilot-RAGService
    private record AskRequest(String repo, String branch, String question) { }
    private record AskResponse(String answer) { }

    // The first question about a repo waits while Python indexes it
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

    public String ask(User user, Repository repo, String question) throws IOException, InterruptedException {
        String body = JsonUtil.toJson(new AskRequest(repo.getFullName(), repo.getDefaultBranch(), question));
        Map<String, String> headers = Map.of("Authorization", "Bearer " + user.getAccessToken());
        AskResponse response = JsonUtil.fromJson(
                http.post(url + "/ask", body, headers, TIMEOUT), AskResponse.class);
        if (response == null || response.answer() == null) {
            throw new IOException("The RAG service's response has no answer");
        }
        return response.answer();
    }
}
