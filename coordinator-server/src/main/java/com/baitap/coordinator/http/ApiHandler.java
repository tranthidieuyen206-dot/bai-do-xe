package com.baitap.coordinator.http;

import com.baitap.coordinator.model.CreateJobRequest;
import com.baitap.coordinator.model.JobResponse;
import com.baitap.coordinator.model.JobType;
import com.baitap.coordinator.store.InMemoryJobStore;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

public class ApiHandler implements HttpHandler {

    private final Gson gson = new Gson();
    private final InMemoryJobStore store;

    public ApiHandler(InMemoryJobStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            sendEmpty(exchange, 204);
            return;
        }

        String path = exchange.getRequestURI().getPath();
        try {
            if (path.equals("/api/jobs") && "POST".equalsIgnoreCase(method)) {
                handleCreate(exchange);
            } else if (path.equals("/api/jobs") && "GET".equalsIgnoreCase(method)) {
                handleList(exchange);
            } else if (path.equals("/api/jobs/next") && "GET".equalsIgnoreCase(method)) {
                handleNext(exchange);
            } else if (path.startsWith("/api/jobs/") && path.endsWith("/complete") && "POST".equalsIgnoreCase(method)) {
                handleComplete(exchange, path);
            } else if (path.equals("/health") && "GET".equalsIgnoreCase(method)) {
                sendJson(exchange, 200, "{\"status\":\"ok\"}");
            } else {
                sendJson(exchange, 404, "{\"error\":\"not_found\"}");
            }
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, gson.toJson(new ErrorBody(e.getMessage())));
        } catch (Exception e) {
            sendJson(exchange, 500, gson.toJson(new ErrorBody("internal_error")));
        }
    }

    private void handleCreate(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        CreateJobRequest req;
        try {
            req = gson.fromJson(body, CreateJobRequest.class);
        } catch (Exception e) {
            sendJson(exchange, 400, gson.toJson(new ErrorBody("invalid_json")));
            return;
        }
        if (req == null || req.plate == null || req.plate.isBlank()) {
            sendJson(exchange, 400, gson.toJson(new ErrorBody("plate_required")));
            return;
        }
        JobType type;
        try {
            type = JobType.valueOf(req.type.trim().toUpperCase());
        } catch (Exception e) {
            sendJson(exchange, 400, gson.toJson(new ErrorBody("type_must_be_ENTER_or_EXIT")));
            return;
        }
        String id = store.enqueue(req.plate, type);
        sendJson(exchange, 201, gson.toJson(new CreatedBody(id)));
    }

    private void handleList(HttpExchange exchange) throws IOException {
        List<JobResponse> jobs = store.listRecentJobs();
        sendJson(exchange, 200, gson.toJson(jobs));
    }

    private void handleNext(HttpExchange exchange) throws IOException {
        String raw = exchange.getRequestURI().getQuery();
        String gateId = queryParam(raw, "gateId");
        if (gateId == null || gateId.isBlank()) {
            sendJson(exchange, 400, gson.toJson(new ErrorBody("gateId_required")));
            return;
        }
        Optional<JobResponse> job = store.dequeueForGate(gateId.trim());
        if (job.isEmpty()) {
            sendEmpty(exchange, 204);
            return;
        }
        sendJson(exchange, 200, gson.toJson(job.get()));
    }

    private void handleComplete(HttpExchange exchange, String path) throws IOException {
        // /api/jobs/{id}/complete
        String prefix = "/api/jobs/";
        String mid = path.substring(prefix.length(), path.length() - "/complete".length());
        if (mid.isEmpty()) {
            sendJson(exchange, 400, gson.toJson(new ErrorBody("job_id_required")));
            return;
        }
        boolean ok = store.markCompleted(mid);
        if (!ok) {
            sendJson(exchange, 404, gson.toJson(new ErrorBody("job_not_found")));
            return;
        }
        sendJson(exchange, 200, gson.toJson(new OkBody(true)));
    }

    private static String queryParam(String query, String name) {
        if (query == null || query.isEmpty()) {
            return null;
        }
        for (String part : query.split("&")) {
            int i = part.indexOf('=');
            if (i > 0) {
                String k = decode(part.substring(0, i));
                if (name.equals(k)) {
                    return decode(part.substring(i + 1));
                }
            } else if (name.equals(part)) {
                return "";
            }
        }
        return null;
    }

    private static String decode(String s) {
        try {
            return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        addCors(exchange);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private void sendEmpty(HttpExchange exchange, int status) throws IOException {
        addCors(exchange);
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    private static void addCors(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private record ErrorBody(String error) {}

    private record CreatedBody(String id) {}

    private record OkBody(boolean ok) {}
}
