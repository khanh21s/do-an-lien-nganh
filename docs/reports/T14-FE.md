# T14-FE report: separate admin console (frontend)

Branch `feat/T14-fe-admin-console` (base `40abe0c`).

## What was done
- **Separate route group**: the existing admin pages moved with `git mv` from `app/(app)/admin/**` to `app/(admin)/admin/**`, and they behave as before.
  - `app/(admin)/layout.tsx` wraps the group in `AuthGuard`.
  - `admin/layout.tsx` keeps the ADM-01 guard: it waits for `qk.profile`, sends a non-admin to `/dashboard` with a toast, and only then renders `AdminShell`.
- **`components/admin/AdminShell.tsx`** contains:
  - A sidebar (≥ md) with these entries: Tổng quan, Người dùng, Bài học, Từ vựng, the group "Gói & giao dịch" (Gói Premium, Giao dịch), Mã kích hoạt and Tài khoản.
  - A topbar with the admin email and "Đăng xuất" (it reuses `useLogout`).
  - A left drawer on mobile (`role="dialog" aria-modal`).
  - None of the learner widgets: no streak, review chip, upgrade button or ReviewReminder.
  - The nav model lives in `components/admin/adminNav.ts` and reuses `findActiveHref`.
- **Learner nav**: `ADMIN_NAV` is removed from `navItems.ts`. Sidebar, MobileTabBar and MoreDrawer no longer read `isAdmin`, and `navItems.test.ts` is updated.
- **Role routing**, built on the pure helpers in `components/layout/roleRouting.ts`:
  - `usePostAuthRedirect` (login and register, including Google) loads the profile once a token exists, then sends an admin to `/admin` and a user to `next` or `/dashboard`.
    - An admin whose `next` points inside `/admin/**` keeps it.
    - A user whose `next` is `/admin/**` goes to `/dashboard`.
    - The query cache is cleared on each new login, so a previous account's role is never reused.
    - A spinner shows while redirecting.
  - `LearnerOnly` wraps the `(app)` group and the signed-in branch of `AuthAwareShell` (`/lessons`, `/pricing`). It shows a spinner until the profile is known, so there is no flash, then replaces an admin to `/admin`. If the profile fails to load, it renders the learner UI.
- **New pages**:
  - `/admin`: stat cards from `/auth/admin/stats` and `/subscriptions/admin/stats`, plus lesson and deck totals (`pagination.total` with `limit=1`). A 30-day revenue bar chart is drawn in plain SVG (`RevenueChart`). Each widget has its own error and retry.
  - `/admin/users`:
    - Debounced email search, status chips and pagination.
    - A Premium column from `POST /subscriptions/admin/user-status` with the ids of the current page.
    - Lock/unlock through a confirmation modal. Locking an active admin or your own account is disabled; unlocking is always allowed.
    - Shown as a table at lg and up, as cards below that.
  - `/admin/plans`:
    - A table (cards on mobile) sorted by duration. Prices are shown as `79.000đ`.
    - One create/edit modal: the id can only be set when creating. Features are entered one per line.
    - An on/off switch. Switching a plan off asks for confirmation first, then sends a full PUT. Plans are never deleted.
  - `/admin/transactions`:
    - Filters for status, method (vnpay/momo/gift_code/activation_code), from/to date and search (transaction id or user id, debounced). An invalid date range shows an inline error and the request is not sent.
    - Status badges and pagination.
  - `/admin/account`: account info plus change password (`PUT /auth/change-password`, with a confirm field). Accounts that are not local get an explanation instead of the form.
  - **Edit deck** (`DeckFormModal`: name, description, level or none, isProOnly) is available in `/admin/vocabulary` (per deck, and it is also used for create) and in `/admin/vocabulary/[deckId]` ("Sửa deck").
    - It sends `PUT /vocabs/decks/{id}`, with a blank description and no level sent as `null`.
    - It invalidates the vocabulary caches.
  - `ConfirmModal` gets an optional `confirmVariant` so the unlock button is not red.
- **Contract code** is in the `// --- T14 additions ---` blocks of `types.ts`, `endpoints/{auth,subscription,vocabulary}.ts` and `queryKeys.ts`. The new keys are `admin-users`, `admin-user-status`, `admin-auth-stats`, `admin-subscription-stats`, `admin-plans` and `admin-transactions`. The counts live under the existing `admin-lessons` and `admin-decks` prefixes.
- **Tests**:
  - `roleRouting.test.ts`
  - `components/admin/console.test.ts`, which covers nav, filters → params, date range, lock rules, `niceCeil`/revenue chart scaling (including all-zero and empty series), compact VND labels, and plan and deck validation/mapping.

## Contract alignment (confirmed by coordinator from T14-AUTH/SUB/VOC)
- Paged admin responses use the nested `{ data, pagination: { page, limit, total, totalPages } }`, which is the existing `PageResponse<T>`. A temporary flat-shape fallback was removed.
- Lock rules: locking an admin or yourself returns 400 "Không thể khoá tài khoản quản trị", so the FE disables that button. Unlocking an admin is allowed and the FE allows it.
- A locked account's login or refresh returns 403 "Tài khoản đã bị khoá". The login page already shows the backend `error` text through `apiErrorMessage`.
- 400 and 422 validation errors (plan, user-status) show the backend `error` text as returned. A duplicate plan id gives 409 "Mã gói đã tồn tại".
- Activation codes: the plan select uses the public `GET /subscriptions/plans`, which now lists only active plans. A 400 "Gói này hiện không còn bán" is shown as returned. Plan edits invalidate `qk.plans`.
- `revenueByDay` dates are `YYYY-MM-DD` strings. The FE shows them as `dd/mm` without parsing them as a `Date`, so there is no timezone shift.

## Verification
Docker `node:20-alpine`: `npm ci && npx tsc --noEmit && npx next lint && npx vitest run && npx next build`.
- tsc: 0 errors.
- lint: no warnings or errors.
- vitest: 173/173 tests pass.
- build: OK, and every `/admin/*` route is generated.

## Notes / risks
- Not checked in a browser against a running stack; layout at 360px was judged from the Tailwind classes. Wide tables only render at `lg` and up; below that they become cards.
- A locked user's current access token stays valid for up to 1 hour (BE behaviour, see T14-AUTH). The confirm dialog says so.
- `/subscription/success` and `/subscription/failed` sit outside `(app)` and are not role-gated. Admins never reach them in normal use.
- The landing `PublicHeader` "Vào học" link still points to `/dashboard`. For an admin, `LearnerOnly` forwards that to `/admin`.
