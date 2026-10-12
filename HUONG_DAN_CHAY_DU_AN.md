# Hướng dẫn chạy dự án Parroto

Tài liệu này hướng dẫn từng bước để chạy toàn bộ hệ thống Parroto (5 microservice backend + API Gateway + frontend) trên máy local bằng Docker.

## 1. Yêu cầu hệ thống

- **Docker Desktop** (đã bật Docker Compose v2) — https://www.docker.com/products/docker-desktop
- Tối thiểu **4GB RAM** dành cho Docker (khuyến nghị 6-8GB vì có 5 service Java + Postgres + Redis + Node)
- Cổng trống trên máy: `3000`, `5432`, `6379`, `8000`, `8001`-`8005`
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

Mặc định `.env` đã chạy được ngay (JWT, database, admin email đều có giá trị sẵn). Các tích hợp ngoài sau đây **không bắt buộc** để chạy thử — nếu để trống, tính năng liên quan sẽ báo lỗi rõ ràng (503/502) khi được gọi, còn lại toàn bộ hệ thống vẫn hoạt động bình thường:

| Biến | Tính năng liên quan | Nếu để trống |
|---|---|---|
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Đăng nhập Google | Nút "Đăng nhập Google" tự ẩn trên frontend |
| `APPLE_CLIENT_ID` / `APPLE_CLIENT_SECRET` | Đăng nhập Apple | Bỏ qua |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` + phần S3 | Upload/phát audio bài học, ghi âm Shadowing | Lỗi khi tải/phát audio |
| `AZURE_SPEECH_KEY` / `AZURE_SPEECH_REGION` | Chấm điểm phát âm Shadowing | Lỗi khi nộp bài Shadowing |
| `VNPAY_API_KEY` / `VNPAY_SECRET` / `VNPAY_MERCHANT_ID` | Thanh toán VNPay | Không tạo được link thanh toán thật (vẫn dùng được "Kích hoạt bằng mã") |
| `MAIL_HOST` / `MAIL_USERNAME` / `MAIL_PASSWORD` | Gửi email | Bỏ qua, chỉ ghi log |

Nếu chỉ muốn chạy thử các luồng chính (đăng ký, học Dictation, ôn từ vựng, xem gói Premium), **không cần chỉnh gì thêm** trong `.env`.

> ⚠️ Biến `VNPAY_RETURN_URL` phải trỏ về `http://localhost:8000/subscriptions/payment-callback` (đúng là API Gateway, **không phải** một trang frontend) — giá trị này đã được cấu hình sẵn, không sửa nếu không chắc chắn.

## 4. Khởi động toàn bộ hệ thống

```bash
docker-compose up -d
```

Lần đầu chạy sẽ build 6 image (5 service Java + frontend) và tải 2 image có sẵn (Postgres, Redis) — mất khoảng **5-10 phút** tuỳ máy. Các lần sau sẽ nhanh hơn nhiều nhờ cache.

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
```

Kết quả mong đợi: `{"status":"OK","timestamp":"..."}`

## 6. Truy cập ứng dụng

Mở trình duyệt tại:

**http://localhost:3000**

Từ đây có thể:
1. Bấm **Đăng ký** → tạo tài khoản mới (email bất kỳ, mật khẩu tối thiểu 8 ký tự)
2. Vào **Bài học** để xem 5 bài học mẫu có sẵn (A1-B2, đủ câu + IPA + dịch tiếng Việt — dữ liệu mẫu nằm trong `init-db.sql`, phần "SEED DATA")
3. Thử **Dictation** trên một câu bất kỳ — chấm điểm hoạt động đầy đủ ngay cả khi chưa cấu hình audio thật
4. Vào **Bộ từ vựng** để xem 3 bộ từ mẫu (giao tiếp, du lịch, công việc) → thêm từ vào sổ tay → **Ôn tập từ vựng** để thử thuật toán SM-2
5. Vào **Gói Premium** để xem danh sách gói (thanh toán VNPay cần cấu hình thật ở bước 3 mới hoạt động đầy đủ)

### Tài khoản Admin

Để thử các trang quản trị (`/admin/lessons`, `/admin/vocabulary`), đăng ký một tài khoản bằng đúng email đã khai trong `ADMIN_EMAILS` của `.env` (mặc định là `admin@parroto.com`). Tài khoản này sẽ tự động có quyền tạo/sửa/xoá bài học và bộ từ vựng — không cần cấu hình gì thêm.

### Những phần cần API key thật mới hoạt động đầy đủ

Với `.env` mặc định (chưa điền API key thật), các phần sau **sẽ báo lỗi khi thử — đây là điều bình thường, không phải lỗi hệ thống**:

| Tính năng | Vì sao chưa chạy được | Cần cấu hình |
|---|---|---|
| Nghe audio bài học/câu | Audio được lưu trữ và stream qua AWS S3 thật | `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, bucket S3 thật |
| Shadowing (ghi âm + chấm phát âm) | Cần upload bản ghi lên S3 rồi gọi Azure Speech để chấm điểm | Cả AWS S3 lẫn `AZURE_SPEECH_KEY` |
| Đăng nhập Google/Apple | Cần Client ID thật từ Google/Apple Console | `GOOGLE_CLIENT_ID`, `APPLE_CLIENT_ID` (nút đăng nhập Google sẽ tự ẩn nếu chưa cấu hình) |
| Thanh toán VNPay (nút "Thanh toán qua VNPay") | Cần tài khoản merchant VNPay sandbox thật | `VNPAY_MERCHANT_ID`, `VNPAY_SECRET` |
| Gửi email (quên mật khẩu, thông báo) | Cần SMTP thật | `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD` |

Các tính năng **không** phụ thuộc API ngoài, chạy đầy đủ ngay từ đầu: đăng ký/đăng nhập email-password, Dictation (chấm điểm text), toàn bộ luồng Từ vựng SRS, quản trị bài học/từ vựng (Admin), xem gói Premium, kích hoạt Premium bằng mã (`activate-code`, không qua VNPay).

## 7. Các lệnh thường dùng

```bash
# Xem log của một service cụ thể
docker-compose logs -f auth-service
docker-compose logs -f frontend

# Build lại và khởi động lại một service sau khi sửa code
docker-compose up -d --build vocabulary-service

# Dừng toàn bộ hệ thống (giữ lại dữ liệu database)
docker-compose down

# Dừng và xoá luôn dữ liệu database/redis (reset sạch)
docker-compose down -v

# Truy cập trực tiếp Postgres
docker-compose exec postgres psql -U parroto -d auth_db
# (các database khác: content_db, practice_db, vocabulary_db, subscription_db)

# Truy cập Redis CLI
docker-compose exec redis redis-cli
```

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

**Container báo `unhealthy` mãi không lên:**
```bash
docker-compose logs <tên-service>
```
Kiểm tra dòng lỗi cuối cùng — thường do Postgres chưa sẵn sàng khi service khởi động (đợi thêm) hoặc sai mật khẩu database trong `.env`.

**Lỗi `port is already allocated`:**
Một chương trình khác đang dùng cổng đó. Tắt chương trình đó, hoặc đổi mapping cổng trong `docker-compose.yml` (ví dụ `"3001:3000"` cho frontend).

**Muốn reset toàn bộ dữ liệu về ban đầu:**
```bash
docker-compose down -v
docker-compose up -d
```
Lệnh `down -v` xoá cả volume Postgres/Redis, lần `up -d` tiếp theo sẽ chạy lại `init-db.sql` (kèm dữ liệu mẫu) từ đầu.

**Danh sách bài học/bộ từ vựng trống hoặc thiếu dữ liệu mẫu:**
Chỉ xảy ra nếu bạn đã chạy `docker-compose up` từ **trước** khi dữ liệu mẫu được thêm vào `init-db.sql` — vì Postgres chỉ chạy file khởi tạo này **một lần duy nhất** (lúc volume `postgres_data` còn trống). Reset lại bằng lệnh ở mục trên (`down -v` rồi `up -d`) để dữ liệu mẫu được nạp lại.

**Sửa code Java nhưng chạy `docker-compose up -d` không thấy thay đổi:**
Phải thêm cờ `--build` để Docker build lại image:
```bash
docker-compose up -d --build <tên-service>
```

**Trang frontend load được nhưng gọi API bị lỗi CORS/401 liên tục:**
Kiểm tra `NEXT_PUBLIC_API_URL` trỏ đúng `http://localhost:8000` (không phải `http://api-gateway:8000` — hostname đó chỉ dùng được giữa các container trong Docker, không dùng được từ trình duyệt).

## 10. Tổng quan cổng dịch vụ

| Service | Cổng | Ghi chú |
|---|---|---|
| Frontend | 3000 | Next.js, giao diện chính |
| API Gateway | 8000 | Điểm vào duy nhất cho frontend |
| auth-service | 8001 | Có thể gọi thẳng để debug |
| content-service | 8002 | |
| practice-service | 8003 | |
| vocabulary-service | 8004 | |
| subscription-service | 8005 | |
| PostgreSQL | 5432 | |
| Redis | 6379 | |

Xem thêm kiến trúc tổng thể và danh sách tính năng đã hoàn thiện tại [README.md](README.md).
