package com.baitap.coordinator;

import com.baitap.coordinator.http.ApiHandler;
import com.baitap.coordinator.http.StaticHandler;
import com.baitap.coordinator.store.InMemoryJobStore;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Properties;

public class CoordinatorMain {

    public static void main(String[] args) throws IOException {
        Properties props = loadProperties();
        int port = parsePort(props);

        InMemoryJobStore store = new InMemoryJobStore();

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/api", new ApiHandler(store));
        server.createContext("/health", new ApiHandler(store));
        server.createContext("/", new StaticHandler());
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8));
        server.start();

        System.out.println("Coordinator đang lắng nghe ở 0.0.0.0:" + port + " ");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(1)));
    }

    private static int parsePort(Properties props) {
        String p = firstNonBlank(System.getenv("PORT"), props.getProperty("server.port"));
        if (p != null && !p.isBlank()) {
            return Integer.parseInt(p.trim());
        }
        return 8080;
    }

    private static Properties loadProperties() throws IOException {
        Properties props = new Properties();
        try (InputStream in = CoordinatorMain.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) {
                props.load(in);
            }
        }
        return props;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
