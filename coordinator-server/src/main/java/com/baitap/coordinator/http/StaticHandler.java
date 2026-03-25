package com.baitap.coordinator.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class StaticHandler implements HttpHandler {

    private static final String PREFIX = "/static/";

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }
        if (path.equals("/") || path.isEmpty()) {
            path = "/index.html";
        }
        String resourcePath = path.startsWith(PREFIX)
                ? "static/" + path.substring(PREFIX.length())
                : "static" + path;
        URL url = Thread.currentThread().getContextClassLoader().getResource(resourcePath);
        if (url == null) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        String contentType = guessContentType(path);
        try (InputStream in = url.openStream()) {
            byte[] data = in.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, data.length);
            exchange.getResponseBody().write(data);
        }
        exchange.close();
    }

    private static String guessContentType(String path) {
        if (path.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (path.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (path.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        return "application/octet-stream";
    }
}
