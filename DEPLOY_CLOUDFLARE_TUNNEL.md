# Đưa Parroto lên Internet bằng Cloudflare Named Tunnel

> Tài liệu này cấu hình Cloudflare Tunnel làm cổng truy cập công khai tới ứng dụng đang chạy trong Docker trên máy cá nhân. Ứng dụng vẫn chạy trên máy của bạn; tunnel không chuyển backend sang máy chủ Cloudflare.

## Cần chuẩn bị

1. Một domain đã được thêm vào Cloudflare DNS.
2. Một Cloudflare Named Tunnel đã tạo trong Cloudflare Dashboard.
3. Token của tunnel. Chỉ lưu token trong `.env` trên máy chủ; không commit, gửi qua chat hoặc đưa vào frontend.
4. Máy chủ phải bật Docker, có Internet và không ở trạng thái sleep khi cần truy cập web.

Named Tunnel cho phép gắn hostname của domain vào một service cục bộ. `cloudflared` tạo kết nối đi ra Cloudflare nên không cần mở cổng inbound trên router. Xem [Cloudflare Tunnel setup](https://developers.cloudflare.com/tunnel/get-started/) và [chạy cloudflared như service](https://developers.cloudflare.com/tunnel/features/locally-managed-tunnels/as-a-service/).

## Cấu hình hostname

Trong Cloudflare Dashboard, vào **Networking → Tunnels**, tạo hoặc chọn tunnel, rồi thêm hai Published application routes:

| Public hostname mẫu | Service URL (trong Docker network) |
|---|---|
| `app.example.com` | `http://frontend:3000` |
| `api.example.com` | `http://api-gateway:8000` |

Thay `example.com` bằng domain của bạn. Không tạo route cho PostgreSQL, Redis hoặc Adminer.

## Cấu hình dự án

1. Điền token của tunnel vào file `.env` ở thư mục gốc:

   ```dotenv
   CLOUDFLARE_TUNNEL_TOKEN=<token-cua-named-tunnel>
   FRONTEND_URL=https://app.example.com
   NEXT_PUBLIC_API_URL=https://api.example.com
   ```

2. Đảm bảo `FRONTEND_URL` là origin frontend chính xác để CORS cho phép trình duyệt gọi API.
3. `NEXT_PUBLIC_API_URL` được nhúng vào frontend khi build, vì vậy phải build lại frontend sau khi đổi hostname.
4. Khởi động stack và tunnel:

   ```bash
   docker-compose up -d --build
   docker-compose --profile cloudflare up -d cloudflared
   docker-compose ps
   docker-compose logs -f cloudflared
   ```

   Có thể dùng `docker compose` thay cho `docker-compose` nếu máy đã cài Compose plugin mới.

5. Mở `https://app.example.com`. API của trình duyệt sẽ gọi `https://api.example.com`.

## Đóng terminal và giữ địa chỉ ổn định

Các lệnh trên chạy container ở chế độ nền (`-d`); đóng terminal không dừng container. Container `cloudflared` có `restart: unless-stopped`, nên Docker sẽ khởi động lại nó khi Docker daemon chạy lại. Named Tunnel dùng hostname đã cấu hình trong Cloudflare, vì vậy khởi động lại tunnel không làm hostname đổi.

Nếu tắt máy, Docker dừng, mất mạng hoặc tunnel không kết nối được, hostname vẫn giữ nguyên nhưng trang sẽ tạm thời không truy cập được. Máy chủ cần hoạt động để phục vụ ứng dụng.

## Xử lý sự cố

- **Hostname không mở được:** kiểm tra tunnel đang Healthy/Connected trong Dashboard và `docker-compose logs cloudflared`.
- **Cloudflare báo 502:** xác nhận origin route đang dùng đúng Docker service name và cổng (`frontend:3000`, `api-gateway:8000`), đồng thời các container đó đang chạy.
- **Frontend báo lỗi CORS:** đặt `FRONTEND_URL` bằng origin frontend, không kèm path, rồi recreate các service backend/Gateway.
- **Frontend gọi nhầm API cũ:** cập nhật `NEXT_PUBLIC_API_URL` rồi chạy lại `docker-compose up -d --build frontend`.
- **Tên miền chưa có trong Cloudflare:** cần đăng ký domain và thiết lập DNS trên Cloudflare trước khi tạo hostname ổn định cho Named Tunnel. Quick Tunnel dùng `trycloudflare.com` là tạm thời và hostname đổi khi tạo phiên mới; không phù hợp nếu cần link cố định.

## Bảo mật

- Không public Adminer (`localhost:8080`), PostgreSQL (`5432`), Redis (`6379`) hoặc các cổng service Java (`8001`–`8005`) qua tunnel.
- Giữ `.env` và tunnel token ngoài Git; nếu token bị lộ, rotate token trong Cloudflare Dashboard.
- Thay secret demo trong `.env`, bảo vệ tài khoản admin và bật kiểm soát truy cập phù hợp trước khi chia sẻ công khai.
- Dùng một route frontend và một route API; CORS chỉ cho phép hostname frontend đã cấu hình.
