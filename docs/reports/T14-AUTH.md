# T14-AUTH – Admin user management (auth-service)

Branch `feat/T14-auth-admin-users` (base `40abe0c`). Only `services/auth-service` changed.

## Endpoints (all: Bearer token + email in `ADMIN_EMAILS`, otherwise 401 / 403 `"Bạn không có quyền quản trị"`)

| Method / path | Result |
|---|---|
| `GET /auth/admin/users?q=&status=all\|active\|locked&page=1&limit=20` | `PageResponse<AdminUserResponse>` = `{ data: [...], pagination: { page, limit, total, totalPages } }` (same shape/field names as the other services). `q`: case-insensitive "contains" on email (`LOWER(email) LIKE`, `%`/`_`/`\` matched literally). Sort `created_at DESC, id ASC`. `page` < 1 → 1, `limit` clamped to 1–100. Unknown `status` → 400 `"Trạng thái lọc không hợp lệ (all\|active\|locked)"`. |
| `GET /auth/admin/users/{id}` | `AdminUserResponse`; unknown → 404 `"Không tìm thấy người dùng"`; non-UUID id → 400 `"Dữ liệu yêu cầu không hợp lệ"`. |
| `PATCH /auth/admin/users/{id}` body `{ "isActive": boolean }` | `AdminUserResponse`. Locking yourself or any `ADMIN_EMAILS` account → 400 `"Không thể khoá tài khoản quản trị"` (unlocking is always allowed). Missing `isActive` → 422 (existing validation convention of auth-service). |
| `GET /auth/admin/stats` | `{ totalUsers, activeUsers, lockedUsers, newUsersLast7Days, newUsersLast30Days }`, `ADMIN_EMAILS` accounts excluded. |

`AdminUserResponse`: `id`, `email`, `authProvider`, `cefrLevel`, `isActive`, `isAdmin`, `createdAt`, `lastLoginAt` (JSON names verified by a unit test and by curl).

## Locked accounts
- `is_active = false` (or NULL) = locked. Password login, Google login, Apple login and `POST /auth/refresh-token` return **403 `"Tài khoản đã bị khoá"`** (login previously returned 401 "Account has been deactivated"; Google/Apple had no check). The password is checked first, so a wrong password on a locked account still gets the generic 401.
- A refresh token presented while locked is not rotated/consumed; after unlocking it works again (until it expires).
- **Access tokens issued before the lock stay valid until they expire naturally (≤ 1 hour, `JWT_EXPIRY_SECONDS`)** – JWT validation is stateless and is not re-checked against the DB. During that window, auth-service's own `/auth/profile` already answers 404 for the locked user (existing `findActiveUser`).
- Self-deactivation (`DELETE /auth/account`) uses the same flag, so such accounts now also get 403 "Tài khoản đã bị khoá" and show up as `locked` in the admin list.

## Implementation
- `security/AdminGuard` (same pattern as other services, Vietnamese 403 message), `controller/AdminUserController`, `service/AdminUserService`, DTOs `AdminUserResponse`, `AdminUpdateUserRequest`, `AdminUserStatsResponse`, `PageResponse` (copied from the other services).
- `UserRepository`: `searchForAdmin` (JPQL, no nullable binds – PostgreSQL cannot always type a NULL parameter), `countExcluding`, `countActiveExcluding`, `countCreatedSinceExcluding` (`LOWER(email) NOT IN :admins`, sentinel `""` when `ADMIN_EMAILS` is empty).
- `GlobalExceptionHandler`: `MethodArgumentTypeMismatchException` → 400 instead of 500.
- No schema change, no gateway change (`/auth/**` already proxied).

## Verification
- `mvn -B -q clean test` (Docker `maven:3.9-eclipse-temurin-21`): **51/51** green (new: `AdminGuardTest` 4, `AdminUserServiceTest` 12, `LockedAccountTest` 5).
- Runtime smoke: jar against throwaway `postgres:16-alpine` (initdb/00-roles.sh + init-db.sql) + `redis:7-alpine` on a private network (removed afterwards): no token 401; user token 403; list/paging/`q` case-insensitive/`q=_` literal; bad status 400; bad UUID 400; unknown id 404; lock other admin 400; lock self 400; lock user 200 → login 403 + refresh 403 "Tài khoản đã bị khoá"; `status=locked`/`active` filters; stats exclude admins (3 users, 2 active, 1 locked); unlock → refresh 200.
