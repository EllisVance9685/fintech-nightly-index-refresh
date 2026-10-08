package com.example.fintech;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class InfraiClient {
    private final HttpClient http = HttpClient.newHttpClient();
    private final String key;
    private final String baseUrl;

    public InfraiClient(String key, String baseUrl) {
        this.key = key;
        this.baseUrl = baseUrl;
    }

    public String post(String path, String body) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            String envelope = response.body();
            if (!envelope.contains("\"ok\":true")) {
                throw new IOException("Infrai request rejected: " + envelope);
            }
            if (response.statusCode() != 429) return envelope;
            long delay = 250L * (1L << attempt);
            String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
            if (retryAfter != null) try { delay = Long.parseLong(retryAfter) * 1000L; } catch (NumberFormatException ignored) { }
            Thread.sleep(delay);
        }
        throw new IOException("Infrai request rejected after retries");
    }
}
