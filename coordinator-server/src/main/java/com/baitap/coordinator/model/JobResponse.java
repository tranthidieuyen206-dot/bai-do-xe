package com.baitap.coordinator.model;

import java.util.Map;

public class JobResponse {
    public String id;
    public String plate;
    public String type;
    public String status;
    public String assignedGateId;
    public Long createdAt;
    public Long startedAt;
    public Long completedAt;

    public static JobResponse fromFieldMap(String id, Map<String, String> map) {
        JobResponse r = new JobResponse();
        r.id = id;
        r.plate = map.get("plate");
        r.type = map.get("type");
        r.status = map.get("status");
        r.assignedGateId = map.get("assignedGateId");
        r.createdAt = parseLong(map.get("createdAt"));
        r.startedAt = parseLong(map.get("startedAt"));
        r.completedAt = parseLong(map.get("completedAt"));
        return r;
    }

    private static Long parseLong(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
