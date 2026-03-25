package com.baitap.gate;

import com.baitap.gate.coordinator.CoordinatorClient;
import com.baitap.gate.db.GateDatabase;
import com.baitap.gate.model.JobPayload;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

// 3 thư viện mới thêm vào để tạo Web Server
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

public class GateMain {

    public static void main(String[] args) throws Exception {
        Properties props = loadProperties();
        String coordinatorUrl = firstNonBlank(
                System.getenv("COORDINATOR_BASE_URL"),
                props.getProperty("coordinator.base.url")
        );
        if (coordinatorUrl == null || coordinatorUrl.isBlank()) {
            System.err.println("Set coordinator.base.url or COORDINATOR_BASE_URL");
            System.exit(1);
        }
        String gateId = firstNonBlank(System.getenv("GATE_ID"), props.getProperty("gate.id"));
        if (gateId == null || gateId.isBlank()) {
            gateId = "1";
        }
        int processSeconds = parseInt(
                firstNonBlank(System.getenv("PROCESS_SECONDS"), props.getProperty("process.seconds")),
                3
        );

        String jdbcUrl = firstNonBlank(System.getenv("DB_URL"), System.getenv("JDBC_URL"), props.getProperty("db.url"));
        String dbUser = firstNonBlank(System.getenv("DB_USER"), props.getProperty("db.user"));
        String dbPassword = firstNonBlank(System.getenv("DB_PASSWORD"), props.getProperty("db.password"));
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            System.err.println("Set db.url or DB_URL / JDBC_URL");
            System.exit(1);
        }

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(jdbcUrl.trim());
        if (dbUser != null) {
            hc.setUsername(dbUser);
        }
        if (dbPassword != null) {
            hc.setPassword(dbPassword);
        }
        hc.setMaximumPoolSize(4);
        HikariDataSource ds = new HikariDataSource(hc);
        GateDatabase db = new GateDatabase(ds);
        db.runSchema();

        CoordinatorClient coordinator = new CoordinatorClient(coordinatorUrl);
        System.out.println("Cổng " + gateId + " đang hoạt động; thời gian xử lý là " + processSeconds + " giây");

        Runtime.getRuntime().addShutdownHook(new Thread(ds::close));

        // --- ĐOẠN CODE LÁCH LUẬT RENDER (TẠO WEB SERVER GIẢ) ---
        // Lấy port do Render cấp tự động, nếu chạy ở máy tính thì mặc định là 8080
        int port = parseInt(System.getenv("PORT"), 8080);
        HttpServer dummyServer = HttpServer.create(new InetSocketAddress(port), 0);
        // Biến gateId bên trong lambda (hàm ẩn danh) phải là final hoặc effectively final
        final String currentGateId = gateId; 
        dummyServer.createContext("/", exchange -> {
            String response = "Gate " + currentGateId + " dang hoat dong ngon lanh!";
            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        });
        dummyServer.start();
        System.out.println("Đã khởi động Web Server giả tại port " + port + " để đánh lừa Render!");
        // --- KẾT THÚC ĐOẠN CODE LÁCH LUẬT ---

        long idleSleepMs = 500;
        while (true) {
            try {
                Optional<JobPayload> job = coordinator.pollNext(gateId);
                if (job.isEmpty()) {
                    Thread.sleep(idleSleepMs);
                    continue;
                }
                JobPayload j = job.get();
                System.out.println("Processing job " + j.id + " plate=" + j.plate + " type=" + j.type);
                Thread.sleep(processSeconds * 1000L);
                coordinator.complete(j.id);
                db.insertProcessed(j.id, j.plate, j.type);
                System.out.println("Done job " + j.id);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Gate loop error: " + e.getMessage());
                Thread.sleep(idleSleepMs);
            }
        }
    }

    private static Properties loadProperties() throws IOException {
        Properties props = new Properties();
        try (InputStream in = GateMain.class.getClassLoader().getResourceAsStream("application.properties")) {
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

    private static int parseInt(String s, int defaultValue) {
        if (s == null || s.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}