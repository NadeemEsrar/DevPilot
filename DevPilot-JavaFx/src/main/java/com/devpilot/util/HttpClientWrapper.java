package com.devpilot.util;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

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
    public String post(String url, String jsonBody, Map<String, String> headers, Duration timeout)
            throws IOException, InterruptedException {
        return send(newRequest(url, headers)
                .timeout(timeout)
                .setHeader("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build());
    }

    /** Sends the fields as an HTML form would: key=value&key=value. */
    public String postForm(String url, Map<String, String> fields, Map<String, String> headers)
            throws IOException, InterruptedException {
        String body = fields.entrySet().stream()
                .map(f -> encode(f.getKey()) + "=" + encode(f.getValue()))
                .collect(Collectors.joining("&"));
        return send(newRequest(url, headers)
                .setHeader("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    private HttpRequest.Builder newRequest(String url, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30));
        if (url.startsWith("http://")) {
            // uvicorn can't do the HTTP/2 upgrade Java attempts on plain HTTP
            builder.version(HttpClient.Version.HTTP_1_1);
        }
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
