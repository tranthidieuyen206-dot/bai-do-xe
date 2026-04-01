package com.baitap.gate.model;

public class SyncPayload {
    private String bienSo;
    private String gateId;
    private String trangThai; // "Vao bai" hoặc "Ra bai"

    // Tạo Constructor rỗng (Bắt buộc cho Spring Boot)
    public SyncPayload() {}

    // Tạo Constructor có tham số cho nhanh
    public SyncPayload(String bienSo, String gateId, String trangThai) {
        this.bienSo = bienSo;
        this.gateId = gateId;
        this.trangThai = trangThai;
    }

    // Nhớ Generate toàn bộ Getter và Setter ở dưới này nhé!
    public String getBienSo() { return bienSo; }
    public void setBienSo(String bienSo) { this.bienSo = bienSo; }
    public String getGateId() { return gateId; }
    public void setGateId(String gateId) { this.gateId = gateId; }
    public String getTrangThai() { return trangThai; }
    public void setTrangThai(String trangThai) { this.trangThai = trangThai; }
}