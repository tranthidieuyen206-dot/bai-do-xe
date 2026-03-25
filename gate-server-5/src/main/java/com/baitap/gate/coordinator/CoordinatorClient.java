package com.baitap.gate.coordinator;

import com.baitap.gate.model.JobPayload;
import com.google.gson.Gson;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

public class CoordinatorClient {

    private static final Gson GSON = new Gson();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String baseUrl;

    public CoordinatorClient(String baseUrl) {
        String b = baseUrl.trim();
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        this.baseUrl = b;
    }

    public Optional<JobPayload> pollNext(String gateId) throws IOException, InterruptedException {
        String q = URLEncoder.encode(gateId, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/jobs/next?gateId=" + q))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() == 204) {
            return Optional.empty();
        }
        if (res.statusCode() != 200) {
            throw new IOException("pollNext failed: HTTP " + res.statusCode() + " " + res.body());
        }
        return Optional.of(GSON.fromJson(res.body(), JobPayload.class));
    }

    public void complete(String jobId) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/jobs/" + jobId + "/complete"))
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) {
            throw new IOException("complete failed: HTTP " + res.statusCode() + " " + res.body());
        }
    }
}
