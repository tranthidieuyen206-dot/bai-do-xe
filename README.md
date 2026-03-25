# Bài đỗ xe phân tán

Hai ứng dụng Maven **độc lập**: coordinator (**hàng đợi trong RAM** + HTTP + UI tĩnh, không Redis) và gate (poll coordinator + MySQL cục bộ). Thêm cổng mới bằng cách **sao chép thư mục `gate-server`**, sửa cấu hình và build.

## Cấu trúc

- [`coordinator-server/`](coordinator-server/) — queue in-memory (FIFO), API REST, trang web đơn giản.
- **Cổng (gate)** — cùng code, khác thư mục / cấu hình:
  - [`gate-server/`](gate-server/) — `gate.id=1`, DB `gate_parking`
  - [`gate-server-2/`](gate-server-2/) … [`gate-server-5/`](gate-server-5/) — `gate.id` 2–5, DB `gate_parking_2` … `gate_parking_5`

Mỗi gate: worker poll coordinator, chờ `process.seconds`, báo hoàn thành, ghi log MySQL riêng.

**Lưu ý coordinator**: restart process là **mất hàng đợi**; chỉ nên một instance coordinator trỏ vào cùng queue. Phù hợp demo local / sinh viên, không cần Docker Redis.

**Luật nghiệp vụ (coordinator)**: chỉ cho **ra bãi** khi biển số đã **vào bãi và lượt vào đã xử lý xong** tại cổng; không cho **vào lại** khi xe vẫn đang trong bãi. Biển số so khớp theo dạng chuẩn hoá (không phân biệt hoa thường, bỏ khoảng trắng và dấu gạch ngang).

## Chạy local

1. **MySQL**: tạo database cho từng cổng bạn chạy. Ví dụ chạy đủ 5 cổng:

   ```sql
   CREATE DATABASE gate_parking;
   CREATE DATABASE gate_parking_2;
   CREATE DATABASE gate_parking_3;
   CREATE DATABASE gate_parking_4;
   CREATE DATABASE gate_parking_5;
   ```
2. **Coordinator** (không cần Redis):
   ```bash
   cd coordinator-server
   set PORT=8080
   mvn exec:java -Dexec.mainClass=com.baitap.coordinator.CoordinatorMain
   ```
   Hoặc: `mvn package` rồi `java -jar target/coordinator-server-0.0.1-SNAPSHOT.jar` (bản shade).

3. **Gate** (một hoặc nhiều terminal, đổi `GATE_ID` và DB nếu cần):
   ```bash
   cd gate-server
   set COORDINATOR_BASE_URL=http://localhost:8080
   set GATE_ID=1
   set PROCESS_SECONDS=3
   set DB_URL=jdbc:mysql://localhost:3306/gate_parking?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
   set DB_USER=root
   set DB_PASSWORD=yourpassword
   mvn exec:java -Dexec.mainClass=com.baitap.gate.GateMain
   ```

Chạy thêm cổng: mở terminal khác, `cd gate-server-2` (hoặc `-3` … `-5`) rồi `mvn exec:java` (đã cấu hình sẵn trong `application.properties`).

Mở trình duyệt: `http://localhost:8080/` — gửi xe vào/ra và xem danh sách job.

## Clone thêm cổng (gate mới)

1. Copy cả thư mục `gate-server` (ví dụ thành `gate-server-2`).
2. Trong `application.properties` (hoặc biến môi trường) đổi:
   - `gate.id`
   - `db.url` / `DB_URL` (MySQL riêng hoặc database/schema khác)
   - `coordinator.base.url` / `COORDINATOR_BASE_URL` nếu coordinator không chạy local
   - `process.seconds` nếu muốn
3. `mvn package` trong thư mục gate đó và chạy JAR.

## Deploy Render (gợi ý)

### Coordinator (Web Service)

- **Build**: `cd coordinator-server && mvn -B package`
- **Start**: `java -jar coordinator-server/target/coordinator-server-0.0.1-SNAPSHOT.jar`
- **Biến môi trường**:
  - `PORT` — Render tự gán (ứng dụng đã đọc `PORT`).
- Không cần Redis/MySQL cho coordinator.

### Gate (mỗi cổng = một Web Service hoặc một process)

- **Build**: `cd gate-server && mvn -B package`
- **Start**: `java -jar gate-server/target/gate-server-0.0.1-SNAPSHOT.jar`
- **Biến môi trường**:
  - `COORDINATOR_BASE_URL` — URL công khai HTTPS của coordinator (ví dụ `https://coordinator-xxx.onrender.com`).
  - `GATE_ID` — id cổng (chuỗi, ví dụ `1`, `2`).
  - `PROCESS_SECONDS` — thời gian mô phỏng xử lý.
  - `DB_URL` hoặc `JDBC_URL` — JDBC MySQL.
  - `DB_USER`, `DB_PASSWORD`

Render không cung cấp MySQL managed như PostgreSQL; dùng MySQL bên ngoài (PlanetScale, Aiven, Railway, …). Coordinator **không** cần MySQL.

## API (coordinator)

| Method | Path | Mô tả |
|--------|------|--------|
| POST | `/api/jobs` | Body JSON `{"plate":"...","type":"ENTER\|EXIT"}` |
| GET | `/api/jobs` | Danh sách job gần đây (trong RAM coordinator) |
| GET | `/api/jobs/next?gateId=...` | Lấy job tiếp theo (dành cho gate) |
| POST | `/api/jobs/{id}/complete` | Đánh dấu hoàn thành |
| GET | `/health` | Kiểm tra sống |

Không có đăng nhập / API key (bài học).

## Module cũ ở thư mục gốc

[`pom.xml`](pom.xml) và `src/` mặc định Maven archetype có thể bỏ qua; luồng chính dùng `coordinator-server` và `gate-server`.
