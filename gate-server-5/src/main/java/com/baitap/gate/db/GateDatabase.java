package com.baitap.gate.db;

import com.zaxxer.hikari.HikariDataSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class GateDatabase {

    private final HikariDataSource dataSource;

    public GateDatabase(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void runSchema() throws IOException, SQLException {
        String sql = readResource("schema.sql");
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            for (String part : sql.split(";")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    st.execute(trimmed);
                }
            }
        }
    }

    public void insertProcessed(String coordinatorJobId, String plate, String jobType) throws SQLException {
        String q = "INSERT IGNORE INTO gate_processed_job (coordinator_job_id, plate, job_type) VALUES (?, ?, ?)";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(q)) {
            ps.setString(1, coordinatorJobId);
            ps.setString(2, plate);
            ps.setString(3, jobType);
            ps.executeUpdate();
        }
    }

    private static String readResource(String name) throws IOException {
        try (InputStream in = GateDatabase.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IOException("Missing classpath resource: " + name);
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            return sb.toString();
        }
    }
}
