# T14-SUB – Admin plans, transactions & revenue (subscription-service)

Branch `feat/T14-sub-admin-plans` (based on `40abe0c`).

## What changed
- **Schema**: `plan.is_active BOOLEAN NOT NULL DEFAULT true`, `plan.updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP`
  (+ index `idx_payment_created ON payment_transaction(created_at DESC)` for the admin list/stats) in `init-db.sql` and in the
  idempotent `scripts/migrations/003_plan_active.sql`. README → "Operations notes" has the apply command
  (`docker exec -i parroto-postgres psql -U parroto -d subscription_db -v ON_ERROR_STOP=1 < scripts/migrations/003_plan_active.sql`,
  then `docker-compose restart subscription-service`). **Apply it before starting the new jar** (`ddl-auto: validate`).
- **Entity** `Plan`: `boolean isActive` (Lombok: `isActive()` / `setActive()` / builder `isActive()`), `updatedAt` (`@UpdateTimestamp`).
- **Public** `GET /subscriptions/plans`: only active plans, same shape (`{plans:[{id,name,durationDays,price,features,priceDisplay}]}`),
  now ordered by `durationDays, price`.
- **Disabled plan** → 400 `"Gói này hiện không còn bán"` for `create-payment`, `activate-code` (code is left unused) and,
  additionally, admin code generation `POST /subscriptions/admin/activation-codes`. A VNPay IPN for a payment created
  *before* the plan was disabled still activates (the user has paid).
- **AdminGuard** 403 message is now `"Bạn không có quyền quản trị"` (also for the existing activation-code endpoints).
- **New endpoints** (all: Bearer + `ADMIN_EMAILS`; no token → 401, non-admin → 403):
  - `GET /subscriptions/admin/plans` → `AdminPlanResponse[]` incl. disabled plans:
    `id, name, durationDays, price, features (string[]), isActive, activeSubscribers, createdAt, updatedAt`.
    `activeSubscribers` = distinct users with an `active` subscription whose `end_at > now` on that plan.
  - `POST /subscriptions/admin/plans` body `{id, name, durationDays, price, features, isActive}` → 201 `AdminPlanResponse`;
    duplicate id → 409 `"Mã gói đã tồn tại"`. `isActive` omitted → `true`. Features are trimmed, blank entries dropped.
  - `PUT /subscriptions/admin/plans/{id}` same body (body `id` ignored) → 200; unknown → 404 `"Không tìm thấy gói"`;
    `isActive` omitted → unchanged. No DELETE (disable instead).
  - Validation (Bean Validation → **422** per architecture §5.1, messages joined with `; `): `id` `^[a-z0-9-]{3,50}$`
    (`"Mã gói chỉ gồm a-z, 0-9, dấu '-' và dài 3–50 ký tự"`), `name` 1–255 (`"Tên gói là bắt buộc"` / `"Tên gói tối đa 255 ký tự"`),
    `durationDays` 1–3650 (`"Thời hạn phải từ 1 đến 3650 ngày"`), `price` ≥ 1000 (`"Giá tối thiểu là 1.000 ₫"`),
    `features` ≤ 20 items × ≤ 255 chars. Missing `id` on create → 400 `"Mã gói là bắt buộc"`.
  - `GET /subscriptions/admin/transactions?status=all|pending|success|failed&method=&q=&from=&to=&page=1&limit=20`
    → `PageResponse<AdminTransactionResponse>` = `{data:[…], pagination:{page, limit, total, totalPages}}` (this service's
    existing PageResponse shape). Row: `id, userId, planId, planName, amount, method, status, transactionId, description, createdAt`.
    `method` exact (case-insensitive; `all`/blank = any); `q` = case-insensitive substring of `transactionId`, or exact `userId`
    when `q` is a UUID; `from`/`to` inclusive `YYYY-MM-DD` in Asia/Ho_Chi_Minh. Sorted `createdAt DESC`; `limit` clamped 1–100.
    Bad status → 400 `"Trạng thái không hợp lệ (all|pending|success|failed)"`; bad date → 400 `"Ngày không hợp lệ (định dạng YYYY-MM-DD)"`;
    `from > to` → 400 `"Ngày bắt đầu phải trước hoặc bằng ngày kết thúc"`.
  - `GET /subscriptions/admin/stats` → `{activeSubscribers, revenueTotal, revenueLast30Days, successfulTransactions,
    pendingTransactions, revenueByDay:[{date:"YYYY-MM-DD", amount}]}`. Revenue = `SUM(amount)` of `status='success'` and
    `method <> 'gift_code'` (NULL method counts). `revenueByDay` = exactly 30 VN days, oldest first, last = today (VN), zero-filled;
    `revenueLast30Days` = sum of those 30 days. `successfulTransactions` uses the same paid rule (gift codes excluded);
    `activeSubscribers` = distinct users with a live active subscription.
  - `POST /subscriptions/admin/user-status` body `{userIds: UUID[]}` (≤ 100, else 422 `"Tối đa 100 userIds mỗi lần"`;
    missing → 422 `"userIds là bắt buộc"`; malformed UUID → 400) → `[{userId, premium, planId, endAt}]`, one per distinct id in
    request order; non-premium → `planId`/`endAt` null; several active subs → latest `endAt`.
- **Cache**: nothing to evict. The public plan list is not cached (read from DB each time), and plan edits / admin reads don't
  change anyone's premium status (`premium:{userId}` depends only on subscriptions, which these endpoints never write).
  Existing eviction after activation (`PremiumCacheEvictor`) is unchanged.

## Verification
- `mvn -B -q clean test` (Docker `maven:3.9-eclipse-temurin-21`): **50 tests, 0 failures** (new: `AdminPlanServiceTest` 10,
  `AdminReportServiceTest` 10, `InactivePlanTest` 6; `ActivationCodeTest` stubs updated).
- Migration: throwaway `postgres:16-alpine` initialised from the **old** `init-db.sql` (`40abe0c`) + `initdb/00-roles.sh`;
  `003_plan_active.sql` applied twice with the README command shape (`psql -v ON_ERROR_STOP=1 < …`): run 1 `ALTER/ALTER/CREATE INDEX`,
  run 2 only "already exists, skipping" notices, exit 0 both times; existing plans got `is_active=t`.
- Real run: jar on that DB + `redis:7-alpine` (private network) → started, `ddl-auto: validate` OK. curl with minted HS256 tokens:
  public plans 200; admin endpoints as user 403 / no token 401; admin plans 200 (incl. `activeSubscribers`); create 201, dup 409,
  invalid 422; disable 200 → plan gone from public list, `create-payment` 400 and `activate-code` 400 with the contract message;
  re-enable → same code redeems 200; PUT unknown 404; transactions filters (status+method+q, userId q, from/to today, bad
  status/date 400); stats `revenueByDay` length 30, sum = `revenueLast30Days` = 248000 (40-day-old and gift_code rows excluded);
  user-status premium/non-premium (expired sub → false). All throwaway containers/network removed; `parroto-*` untouched.

## Notes for T14-FE
- JSON field is `isActive` (not `active`) in both request and response.
- Pagination lives under `pagination` (`data`, `pagination.page/limit/total/totalPages`), same as `/subscriptions/transactions`
  and the activation-code list.
- Plan validation errors are 422 (not 400) with a `; `-joined Vietnamese message in `error`.
- `files/init-db.sql` (stale copy, not used by compose) was not touched.
