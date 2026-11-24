package com.devpilot.service;

import com.devpilot.model.Repository;
import com.devpilot.util.AppConfig;
import com.devpilot.util.HttpClientWrapper;
import com.devpilot.util.JsonUtil;

import java.io.IOException;
import java.util.Map;

/** Sends questions to the Python RAG service. */
public class RagClientService {
    // The JSON contract; must match models/schemas.py in DevPilot-RAGService
    private record AskRequest(String repo, String branch, String question) { }
    private record AskResponse(String answer) { }

    private final HttpClientWrapper http = new HttpClientWrapper();
    private final String url;

    public RagClientService() {
        this(AppConfig.get("rag.url"));
    }

    /** Lets tests point at another address. */
    RagClientService(String url) {
        this.url = url;
    }

    public String ask(Repository repo, String question) throws IOException, InterruptedException {
        String body = JsonUtil.toJson(new AskRequest(repo.getFullName(), repo.getDefaultBranch(), question));
        AskResponse response = JsonUtil.fromJson(http.post(url + "/ask", body, Map.of()), AskResponse.class);
        if (response == null || response.answer() == null) {
            throw new IOException("The RAG service's response has no answer");
        }
        return response.answer();
    }
}
