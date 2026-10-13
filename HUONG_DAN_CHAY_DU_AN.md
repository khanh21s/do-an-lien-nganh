# Hướng dẫn chạy dự án Parroto

Tài liệu này hướng dẫn từng bước để chạy toàn bộ hệ thống Parroto (5 microservice backend + API Gateway + frontend) trên máy local bằng Docker. Hệ thống chạy được trọn vẹn ở **chế độ demo (mock)** — không cần key AWS, Azure, Google hay VNPay.

## 1. Yêu cầu hệ thống

- **Docker** + Docker Compose (`docker-compose` hoặc `docker compose`) — https://www.docker.com/products/docker-desktop
- Tối thiểu **4GB RAM** dành cho Docker (khuyến nghị 6-8GB vì có 5 service Java + Postgres + Redis + Node)
- Cổng trống trên máy: `3000` (frontend) và `8000` (API Gateway). Các cổng khác chỉ dùng bên trong mạng Docker.
- (Chỉ cần nếu muốn chạy frontend riêng ở chế độ dev, không bắt buộc) **Node.js 20+**

Không cần cài Java hay Maven — quá trình build chạy hoàn toàn bên trong Docker.

## 2. Lấy mã nguồn

Mở terminal tại thư mục gốc dự án:

```bash
cd D:\Do-an-lien-nganh
```

## 3. Cấu hình biến môi trường

Sao chép file mẫu:

```bash
cp .env.example .env
```

**Bắt buộc** — hai biến bí mật sau không có giá trị mặc định trong `docker-compose.yml`. File mẫu có sẵn giá trị *chỉ dùng cho dev*; hãy thay bằng chuỗi ngẫu nhiên của bạn:

| Biến | Yêu cầu | Lệnh tạo |
|---|---|---|
| `JWT_SECRET` | ≥ 32 ký tự, dùng chung cho gateway + 5 service | `openssl rand -base64 48` |
| `INTERNAL_TOKEN` | ≥ 32 ký tự, xác thực gọi nội bộ giữa các service (`/internal/**`) | `openssl rand -hex 32` |

Thiếu một trong hai biến này, `docker-compose` sẽ dừng ngay với thông báo rõ ràng, ví dụ:
`required variable JWT_SECRET is missing a value: JWT_SECRET is required ...`

Các tích hợp ngoài sau đây **không bắt buộc** — để trống thì hệ thống tự chuyển sang chế độ mock hoặc tắt tính năng đó:

| Biến | Tính năng liên quan | Nếu để trống |
|---|---|---|
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | Lưu audio bài học, bản ghi Shadowing | **Chế độ mock**: lưu file local trong volume `media_data` |
| `AZURE_SPEECH_KEY` / `AZURE_SPEECH_REGION` | Chấm phát âm Shadowing | **Chế độ mock**: điểm mô phỏng (badge "Chế độ demo – điểm mô phỏng") |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Đăng nhập Google | Nút "Đăng nhập Google" tự ẩn; API trả 503 "chưa cấu hình" |
| `APPLE_CLIENT_ID` | Đăng nhập Apple | API trả 503 "chưa cấu hình" |
| `VNPAY_API_KEY` / `VNPAY_SECRET` / `VNPAY_MERCHANT_ID` | Thanh toán VNPay | Không thanh toán thật được (vẫn dùng được "Kích hoạt bằng mã") |
| `MAIL_HOST` / `MAIL_USERNAME` / `MAIL_PASSWORD` | Gửi email | Bỏ qua, chỉ ghi log |

Chọn chế độ tường minh (tuỳ chọn): `STORAGE_PROVIDER=auto|s3|local`, `SPEECH_PROVIDER=auto|azure|mock` (mặc định `auto`: có key thì dùng dịch vụ thật, không có thì mock). Ép `s3`/`azure` mà thiếu key → service dừng khi khởi động kèm log rõ ràng.

> ⚠️ Biến `VNPAY_RETURN_URL` phải trỏ về `http://localhost:8000/subscriptions/payment-callback` (đúng là API Gateway, **không phải** một trang frontend) — giá trị này đã được cấu hình sẵn, không sửa nếu không chắc chắn.

> Mật khẩu database của từng service (`AUTH_DB_PASSWORD`, `CONTENT_DB_PASSWORD`, …) được script `initdb/00-roles.sh` dùng để tạo role ở **lần khởi động đầu tiên** (volume Postgres còn trống). Muốn đổi sau đó: `docker-compose down -v` rồi chạy lại (mất dữ liệu), hoặc tự `ALTER ROLE ... PASSWORD` trong psql.

## 4. Khởi động toàn bộ hệ thống

```bash
docker-compose up -d --build
```

Lần đầu chạy sẽ build 7 image (5 service Java + gateway + frontend) và tải 2 image có sẵn (Postgres, Redis) — mất khoảng **5-10 phút** tuỳ máy. Các lần sau sẽ nhanh hơn nhiều nhờ cache.

Theo dõi tiến trình khởi động:

```bash
docker-compose ps
```

Chờ đến khi tất cả các dòng (trừ `frontend`, dòng này không cấu hình healthcheck) hiển thị **`healthy`**:

```
parroto-postgres              healthy
parroto-redis                 healthy
parroto-auth-service          healthy
parroto-content-service       healthy
parroto-practice-service      healthy
parroto-vocabulary-service    healthy
parroto-subscription-service  healthy
parroto-api-gateway           healthy
parroto-frontend              Up
```

Nếu muốn chờ tự động thay vì bấm `docker-compose ps` liên tục:

```bash
docker-compose logs -f
```
(nhấn `Ctrl+C` để thoát xem log, các container vẫn tiếp tục chạy nền)

## 5. Kiểm tra hệ thống đã chạy đúng

```bash
curl http://localhost:8000/health
curl http://localhost:8000/subscriptions/plans     # 3 gói: 79.000đ / 169.000đ / 499.000đ
```

Kết quả mong đợi cho `/health`: `{"status":"OK","timestamp":"..."}`

## 6. Truy cập ứng dụng

Mở trình duyệt tại:

**http://localhost:3000**

Từ đây có thể:
1. Bấm **Đăng ký** → tạo tài khoản mới (email bất kỳ, mật khẩu tối thiểu 8 ký tự)
2. Vào **Bài học** để xem 5 bài học mẫu (A1-B2, đủ câu + IPA + dịch tiếng Việt) — **có audio thật** (giọng đọc tổng hợp TTS, file trong `media/seed/`)
3. Thử **Dictation** và **Shadowing** trên một câu bất kỳ (Shadowing chấm bằng Azure nếu có key, nếu không thì điểm mô phỏng)
4. Vào **Bộ từ vựng** để xem 3 bộ từ mẫu (giao tiếp, du lịch, công việc – bộ thứ ba chỉ dành cho Pro) → thêm từ vào sổ tay → **Ôn tập từ vựng** để thử thuật toán SM-2
5. Vào **Gói Premium** để xem 3 gói (1 tháng 79.000đ, 3 tháng 169.000đ, 1 năm 499.000đ) và kích hoạt Premium bằng mã demo bên dưới

### Mã kích hoạt demo

Mỗi mã dùng được **một lần**, hạn đến 31/12/2027:

| Mã | Gói |
|---|---|
| `PROMO-DEMO1-MONTH` | 1 tháng Premium |
| `PROMO-DEMO3-MONTH` | 3 tháng Premium |
| `PROMO-DEMOY-EARLY` | 1 năm Premium |

Dùng hết mã thì reset dữ liệu (`docker-compose down -v && docker-compose up -d`) hoặc tạo thêm mã ở trang Admin `/admin/activation-codes`.

### Tài khoản Admin

Quyền admin được xác định bằng biến `ADMIN_EMAILS` trong `.env` (danh sách email, cách nhau bằng dấu phẩy; mặc định `admin@parroto.com`). Không có tài khoản admin seed sẵn — chỉ cần **đăng ký** tài khoản với email `admin@parroto.com` (mật khẩu tuỳ ý) tại http://localhost:3000/register. Sau khi đăng nhập, menu **Quản trị** (bài học, từ vựng, mã kích hoạt) sẽ xuất hiện.

Thêm admin khác:
```bash
# trong .env
ADMIN_EMAILS=admin@parroto.com,giaovien@example.com
# áp dụng lại cấu hình
docker-compose up -d
```

## 7. Các lệnh thường dùng

```bash
# Xem log của một service cụ thể
docker-compose logs -f auth-service
docker-compose logs -f frontend

# Build lại và khởi động lại một service sau khi sửa code
docker-compose up -d --build vocabulary-service

# Dừng toàn bộ hệ thống (giữ lại dữ liệu database)
docker-compose down

# Dừng và xoá luôn dữ liệu database/redis/media (reset sạch)
docker-compose down -v

# Truy cập trực tiếp Postgres
docker-compose exec postgres psql -U parroto -d auth_db
# (các database khác: content_db, practice_db, vocabulary_db, subscription_db)

# Truy cập Redis CLI
docker-compose exec redis redis-cli

# Kiểm tra code (chạy trong Docker, không cần Node/Maven trên máy)
scripts/verify-fe.sh                     # frontend: tsc, lint, vitest
scripts/verify-gw.sh                     # API gateway
scripts/verify-java.sh content-service   # một service Java (bỏ tham số = cả 5)
```

### Mở cổng nội bộ khi cần debug

Mặc định chỉ cổng `3000` và `8000` được mở ra máy host. Muốn gọi thẳng service (8001–8005), Postgres (5432) hay Redis (6379) từ máy mình:

```bash
cp docker-compose.override.yml.example docker-compose.override.yml   # file này bị git ignore
docker-compose up -d
```

Các cổng chỉ bind vào `127.0.0.1`. Không dùng file override này khi triển khai thật.

### Chuẩn hoá điểm cũ > 100

Từ Phase 1, điểm luyện tập luôn nằm trong 0–100. Nếu database đã có dữ liệu cũ với điểm > 100, chạy một lần (thủ công):

```bash
docker-compose exec postgres psql -U parroto -d practice_db \
  -c "UPDATE practice_attempt SET score = LEAST(score, 100) WHERE score > 100;"
```

### Thử IPN của VNPay trên máy local

VNPay không gọi được `localhost`, nên dùng script giả lập request IPN có chữ ký HMAC-SHA512 (cùng thuật toán với service, khoá `VNPAY_SECRET` trong `.env` — giá trị mẫu là đủ):

```bash
# 1. Đăng nhập, tạo giao dịch -> lấy "transactionId"
curl -s -X POST http://localhost:8000/subscriptions/create-payment \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"planId":"plan-1month"}'

# 2. Gửi IPN có chữ ký
scripts/vnpay-ipn-test.sh <transactionId> 79000                 # RspCode 00 -> kích hoạt Premium
scripts/vnpay-ipn-test.sh <transactionId> 79000                 # gọi lại -> RspCode 02 (đã xử lý)
scripts/vnpay-ipn-test.sh <transactionIdKhac> 1000              # sai số tiền -> RspCode 04, giao dịch failed
BAD_SIGNATURE=1 scripts/vnpay-ipn-test.sh <transactionId> 79000 # sai chữ ký -> RspCode 97
```

### Redis và danh sách token đã đăng xuất (fail-open)

Khi đăng xuất, token được đưa vào blacklist trên Redis và bị từ chối ở gateway + các service. Nếu Redis ngừng hoạt động, bước kiểm tra blacklist được **bỏ qua** (ghi log `WARN`) để hệ thống vẫn chạy — token đã đăng xuất có thể dùng tiếp đến khi hết hạn. Đây là đánh đổi có chủ đích cho bản demo. Các service Java dùng timeout Redis 2 giây (`REDIS_TIMEOUT`) để không bị treo khi Redis chậm.

## 8. Chạy frontend riêng ở chế độ dev (hot reload)

Cách này giúp sửa giao diện và thấy thay đổi ngay, không cần build lại Docker image mỗi lần:

```bash
# Chạy backend qua Docker như bình thường (không chạy frontend trong Docker)
docker-compose up -d postgres redis auth-service content-service practice-service vocabulary-service subscription-service api-gateway

# Chạy frontend riêng
cd frontend
npm install
cp .env.example .env.local
npm run dev
```

Frontend sẽ chạy tại `http://localhost:3000` với hot reload, vẫn gọi API qua gateway ở cổng `8000` như bình thường.

> Lưu ý: các biến `NEXT_PUBLIC_*` (như `NEXT_PUBLIC_API_URL`) được nhúng vào mã nguồn lúc **build**. Nếu chạy frontend qua Docker (`docker-compose up -d --build frontend`) và đổi các biến này, phải build lại image mới có hiệu lực — chỉ khởi động lại container (`docker-compose restart frontend`) sẽ không đủ.

## 9. Xử lý sự cố thường gặp

**`docker-compose` báo `required variable JWT_SECRET (hoặc INTERNAL_TOKEN) is missing a value`:**
Chưa có file `.env` hoặc thiếu biến — xem mục 3.

**Container báo `unhealthy` mãi không lên:**
```bash
docker-compose logs <tên-service>
```
Kiểm tra dòng lỗi cuối cùng — thường do Postgres chưa sẵn sàng khi service khởi động (đợi thêm), `JWT_SECRET`/`INTERNAL_TOKEN` ngắn hơn 32 ký tự, hoặc mật khẩu database trong `.env` đã đổi sau khi volume Postgres được tạo (xem lưu ý ở mục 3).

**Lỗi `port is already allocated`:**
Một chương trình khác đang dùng cổng `3000` hoặc `8000`. Tắt chương trình đó, hoặc đổi mapping cổng trong `docker-compose.yml` (ví dụ `"3001:3000"` cho frontend).

**Muốn reset toàn bộ dữ liệu về ban đầu:**
```bash
docker-compose down -v
docker-compose up -d
```
Lệnh `down -v` xoá cả volume Postgres/Redis/media, lần `up -d` tiếp theo sẽ chạy lại `initdb/00-roles.sh` + `init-db.sql` (kèm dữ liệu mẫu) từ đầu.

**Danh sách bài học/bộ từ vựng trống, giá gói cũ hoặc audio bài mẫu không phát:**
Postgres chỉ chạy script khởi tạo **một lần duy nhất** (lúc volume `postgres_data` còn trống). Nếu volume được tạo từ phiên bản cũ, reset lại bằng lệnh ở mục trên (`down -v` rồi `up -d`).

**Sửa code Java nhưng chạy `docker-compose up -d` không thấy thay đổi:**
Phải thêm cờ `--build` để Docker build lại image:
```bash
docker-compose up -d --build <tên-service>
```

**Trang frontend load được nhưng gọi API bị lỗi CORS/401 liên tục:**
Kiểm tra `NEXT_PUBLIC_API_URL` trỏ đúng `http://localhost:8000` (không phải `http://api-gateway:8000` — hostname đó chỉ dùng được giữa các container trong Docker, không dùng được từ trình duyệt).

## 10. Tổng quan cổng dịch vụ

| Service | Cổng | Mở ra máy host? |
|---|---|---|
| Frontend | 3000 | Có — giao diện chính |
| API Gateway | 8000 | Có — điểm vào duy nhất cho frontend |
| auth-service | 8001 | Không (chỉ khi dùng `docker-compose.override.yml`) |
| content-service | 8002 | Không (như trên) |
| practice-service | 8003 | Không (như trên) |
| vocabulary-service | 8004 | Không (như trên) |
| subscription-service | 8005 | Không (như trên) |
| PostgreSQL | 5432 | Không (như trên) |
| Redis | 6379 | Không (như trên) |

Xem thêm kiến trúc tổng thể và danh sách tính năng tại [README.md](README.md).
