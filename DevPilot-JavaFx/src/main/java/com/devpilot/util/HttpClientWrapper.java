package com.devpilot.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Sends HTTP requests and turns non-2xx statuses into exceptions. */
public class HttpClientWrapper {

    /** The server answered, but with an error status. */
    public static class HttpStatusException extends IOException {
        private final int statusCode;
        private final String body;

        public HttpStatusException(int statusCode, String body) {
            super("Server returned HTTP " + statusCode);
            this.statusCode = statusCode;
            this.body = body;
        }

        public int getStatusCode() { return statusCode; }
        public String getBody() { return body; }
    }

    // Thread-safe and reuses connections, so one is enough for the whole app
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public String get(String url, Map<String, String> headers)
            throws IOException, InterruptedException {
        return send(newRequest(url, headers).GET().build());
    }

    /** Sends the body as JSON. */
    public String post(String url, String jsonBody, Map<String, String> headers)
            throws IOException, InterruptedException {
        return send(newRequest(url, headers)
                .setHeader("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build());
    }

    private HttpRequest.Builder newRequest(String url, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30));
        headers.forEach(builder::setHeader);
        return builder;
    }

    private String send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new HttpStatusException(status, response.body());
        }
        return response.body();
    }
}
