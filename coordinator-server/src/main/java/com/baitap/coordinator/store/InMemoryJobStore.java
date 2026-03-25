package com.baitap.coordinator.store;

import com.baitap.coordinator.model.JobResponse;
import com.baitap.coordinator.model.JobType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * FIFO job queue and metadata in JVM memory. No Redis — suitable for local demo;
 * queue is lost on restart; use a single coordinator instance.
 */
public class InMemoryJobStore {

    private static final int LIST_MAX = 100;

    private final Object mutex = new Object();
    private final Queue<String> pending = new ConcurrentLinkedQueue<>();
    private final Map<String, Map<String, String>> jobs = new ConcurrentHashMap<>();
    private final Deque<String> recentNewestFirst = new ArrayDeque<>();
    /** Biển số đã xử lý xong lượt vào, chưa xử lý xong lượt ra (chuẩn hoá để so khớp). */
    private final Set<String> vehiclesInLot = new HashSet<>();

    public static String normalizePlate(String plate) {
        return plate.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "");
    }

    private boolean hasActiveJobForPlate(String plateKey, JobType type) {
        for (Map<String, String> j : jobs.values()) {
            if (!type.name().equals(j.get("type"))) {
                continue;
            }
            if (!plateKey.equals(normalizePlate(j.getOrDefault("plate", "")))) {
                continue;
            }
            String st = j.get("status");
            if ("PENDING".equals(st) || "PROCESSING".equals(st)) {
                return true;
            }
        }
        return false;
    }

    public String enqueue(String plate, JobType type) {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        String displayPlate = plate.trim();
        String key = normalizePlate(displayPlate);
        Map<String, String> map = new HashMap<>();
        map.put("plate", displayPlate);
        map.put("type", type.name());
        map.put("status", "PENDING");
        map.put("createdAt", String.valueOf(now));
        synchronized (mutex) {
            if (type == JobType.EXIT) {
                if (!vehiclesInLot.contains(key)) {
                    throw new IllegalArgumentException(
                            "Không thể cho xe ra: biển số này hiện không có trong bãi "
                                    + "(chưa có lượt vào đã xử lý xong, hoặc đã ra rồi).");
                }
                if (hasActiveJobForPlate(key, JobType.EXIT)) {
                    throw new IllegalArgumentException(
                            "Đã có yêu cầu ra bãi cho biển số này đang chờ hoặc đang xử lý.");
                }
            } else if (type == JobType.ENTER) {
                if (vehiclesInLot.contains(key)) {
                    throw new IllegalArgumentException(
                            "Xe biển số này đang trong bãi. Vui lòng cho xe ra trước khi đăng ký vào lại.");
                }
                if (hasActiveJobForPlate(key, JobType.ENTER)) {
                    throw new IllegalArgumentException(
                            "Đã có yêu cầu vào bãi cho biển số này đang chờ hoặc đang xử lý.");
                }
            }
            jobs.put(id, map);
            pending.offer(id);
            recentNewestFirst.addFirst(id);
            while (recentNewestFirst.size() > LIST_MAX) {
                recentNewestFirst.removeLast();
            }
        }
        return id;
    }

    public Optional<JobResponse> dequeueForGate(String gateId) {
        synchronized (mutex) {
            String id = pending.poll();
            if (id == null) {
                return Optional.empty();
            }
            Map<String, String> map = jobs.get(id);
            if (map == null) {
                return Optional.empty();
            }
            long now = System.currentTimeMillis();
            map.put("status", "PROCESSING");
            map.put("assignedGateId", gateId);
            map.put("startedAt", String.valueOf(now));
            return Optional.of(JobResponse.fromFieldMap(id, map));
        }
    }

    public boolean markCompleted(String jobId) {
        synchronized (mutex) {
            Map<String, String> map = jobs.get(jobId);
            if (map == null) {
                return false;
            }
            map.put("status", "DONE");
            map.put("completedAt", String.valueOf(System.currentTimeMillis()));
            String plateKey = normalizePlate(map.getOrDefault("plate", ""));
            String typeStr = map.get("type");
            if (!plateKey.isEmpty() && typeStr != null) {
                if ("ENTER".equals(typeStr)) {
                    vehiclesInLot.add(plateKey);
                } else if ("EXIT".equals(typeStr)) {
                    vehiclesInLot.remove(plateKey);
                }
            }
            return true;
        }
    }

    public List<JobResponse> listRecentJobs() {
        List<String> ids;
        synchronized (mutex) {
            ids = new ArrayList<>(recentNewestFirst);
        }
        List<JobResponse> out = new ArrayList<>();
        for (String id : ids) {
            Map<String, String> map = jobs.get(id);
            if (map != null) {
                out.add(JobResponse.fromFieldMap(id, map));
            }
        }
        return out;
    }
}
