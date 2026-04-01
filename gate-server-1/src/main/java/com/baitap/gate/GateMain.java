package com.baitap.gate;

import com.baitap.gate.db.GateDatabase;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

// Các thư viện dựng Web Server và gọi HTTP nội tại của Java
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

// Các thư viện tiện ích
import java.util.Properties;
import java.util.UUID;

public class GateMain {

    // Cuốn sổ lưu lịch sử chung của toàn hệ thống (Chống đụng độ đa luồng)
    public static final java.util.List<String> lichSuChung = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void main(String[] args) throws Exception {
        Properties props = loadProperties();
        
        // 1. LẤY ID CỦA CỔNG
        String gateId = firstNonBlank(System.getenv("GATE_ID"), props.getProperty("gate.id"));
        if (gateId == null || gateId.isBlank()) {
            gateId = "1";
        }
        final String currentGateId = gateId;

        // 2. KẾT NỐI DATABASE AIVEN (Giữ nguyên của bạn)
        String jdbcUrl = firstNonBlank(System.getenv("DB_URL"), System.getenv("JDBC_URL"), props.getProperty("db.url"));
        String dbUser = firstNonBlank(System.getenv("DB_USER"), props.getProperty("db.user"));
        String dbPassword = firstNonBlank(System.getenv("DB_PASSWORD"), props.getProperty("db.password"));
        
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            System.err.println("Set db.url or DB_URL / JDBC_URL");
            System.exit(1);
        }

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(jdbcUrl.trim());
        if (dbUser != null) hc.setUsername(dbUser);
        if (dbPassword != null) hc.setPassword(dbPassword);
        hc.setMaximumPoolSize(4);
        
        HikariDataSource ds = new HikariDataSource(hc);
        GateDatabase db = new GateDatabase(ds);
        db.runSchema();

        System.out.println("Cổng " + currentGateId + " đang khởi động chế độ P2P...");
        Runtime.getRuntime().addShutdownHook(new Thread(ds::close));

        // 3. KHỞI TẠO WEB SERVER (Thay thế hoàn toàn Coordinator)
        int port = parseInt(System.getenv("PORT"), 8080);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // --- API 1: HIỂN THỊ GIAO DIỆN WEB CHO NGƯỜI DÙNG ---
        server.createContext("/", exchange -> {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                // Đọc file index.html từ thư mục resources
                try (InputStream is = GateMain.class.getClassLoader().getResourceAsStream("index.html")) {
                    if (is != null) {
                        // Nếu tìm thấy file HTML -> Ép sang dạng Byte và gửi cho trình duyệt
                        byte[] htmlBytes = is.readAllBytes();
                        // Báo cho trình duyệt biết đây là file HTML để nó hiển thị giao diện
                        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
                        exchange.sendResponseHeaders(200, htmlBytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(htmlBytes);
                        os.close();
                    } else {
                        // Nếu quên chưa tạo file index.html thì hiện cảnh báo
                        String response = "<h1>Cổng " + currentGateId + " đang chạy P2P</h1><p>Lỗi: Chưa tìm thấy file index.html trong resources!</p>";
                        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
                        exchange.sendResponseHeaders(200, response.getBytes().length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(response.getBytes());
                        os.close();
                    }
                } catch (Exception e) {
                    System.out.println("Lỗi load giao diện: " + e.getMessage());
                }
            }
        });

        // --- API 2: Nhận xe từ Web Frontend gửi vào ---
        server.createContext("/api/nhan-xe", exchange -> {
            // Hỗ trợ CORS để Frontend gọi không bị lỗi
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStream is = exchange.getRequestBody();
                String bienSo = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();

                if (!bienSo.isEmpty()) {
                    // Tạo ID ngẫu nhiên cho lượt vào bãi
                    String jobId = UUID.randomUUID().toString();
                    
                    // Lưu vào Database của cổng này (Có bọc try-catch chống sập)
                    try {
                        db.insertProcessed(jobId, bienSo, "Vao bai");
                        System.out.println("[DB] Đã lưu xe " + bienSo + " vào Database của Cổng " + currentGateId);
                    } catch (java.sql.SQLException e) {
                        System.out.println("[DB ERROR] Lỗi khi lưu vào Aiven: " + e.getMessage());
                    }
                    
                    // 1. Ghi vào sổ chung của cổng này
                    String thongBao = "Xe " + bienSo + " vừa vào bãi qua Cổng " + currentGateId;
                    lichSuChung.add(0, thongBao); 
                    if(lichSuChung.size() > 20) lichSuChung.remove(20); // Chỉ giữ 20 dòng gần nhất
                    
                    // 2. PHÁT LOA CHO CÁC CỔNG KHÁC (Broadcast)
                    phatLoaChoAnhEm(bienSo, currentGateId);
                }

                // Trả về báo cáo cho Web
                exchange.sendResponseHeaders(200, "OK".getBytes().length);
                exchange.getResponseBody().write("OK".getBytes());
                exchange.getResponseBody().close();
            }
        });

        // --- API 3: Đôi tai lắng nghe anh em báo cáo ---
        server.createContext("/api/dong-bo", exchange -> {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStream is = exchange.getRequestBody();
                String thongBao = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                System.out.println(">>> [P2P SYNC] Nhận tin báo: " + thongBao);
                
                // Ghi thẳng tin nhắn của hàng xóm vào sổ chung
                lichSuChung.add(0, "[P2P] " + thongBao); 
                if(lichSuChung.size() > 20) lichSuChung.remove(20);

                exchange.sendResponseHeaders(200, "OK".getBytes().length);
                exchange.getResponseBody().write("OK".getBytes());
                exchange.getResponseBody().close();
            }
        });

        // --- API 4 (MỚI): Cho giao diện Web hỏi thăm lịch sử ---
        server.createContext("/api/lich-su", exchange -> {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                // Ghép cuốn sổ thành 1 chuỗi dài ngăn cách bởi dấu phẩy
                String data = String.join(",", lichSuChung);
                byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
                
                exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
                exchange.sendResponseHeaders(200, bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
            }
        });

        // Chạy Server
        server.start();
        System.out.println("Đã mở các luồng API P2P tại port " + port);
    }

    // ==========================================================
    // CÁI MIỆNG: HÀM GỬI THÔNG BÁO CHO CÁC SERVER KHÁC
    // ==========================================================
    public static void phatLoaChoAnhEm(String bienSoXe, String congHienTai) {
        String peerServers = System.getenv("PEER_SERVERS");
        if (peerServers == null || peerServers.isBlank()) {
            return; // Không có anh em thì tự kỷ 1 mình
        }

        String[] peers = peerServers.split(",");
        HttpClient client = HttpClient.newHttpClient();
        String dataGuiDi = "Xe " + bienSoXe + " vua qua Cong " + congHienTai;

        for (String peerUrl : peers) {
            try {
                String targetUrl = peerUrl.trim() + "/api/dong-bo";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(targetUrl))
                        .POST(HttpRequest.BodyPublishers.ofString(dataGuiDi))
                        .build();

                // Gửi tin nhắn ngầm (Async) để không làm chậm luồng xử lý chính
                client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                      .thenAccept(response -> System.out.println("Đã báo cáo tình hình cho: " + targetUrl));
            } catch (Exception e) {
                System.out.println("Cảnh báo: Cổng " + peerUrl + " đang sập hoặc ngủ gật!");
            }
        }
    }

    // ==========================================================
    // CÁC HÀM TIỆN ÍCH (Giữ nguyên của bạn)
    // ==========================================================
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
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static int parseInt(String s, int defaultValue) {
        if (s == null || s.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}