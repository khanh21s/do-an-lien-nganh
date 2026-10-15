# P2 foundation: shared Java module + Flyway

Branch `feat/phase2-foundation`. Covers two things: (1) the infrastructure classes that were copied into each of the 5 Spring services now live once in `services/common`, and (2) every service runs Flyway with a V1 baseline. Behaviour is meant to stay byte-for-byte the same. The evidence for that is in the Verification section below.

## 1. Shared module `services/common`

Maven: `com.parroto:parroto-common:1.0.0`. It is a plain jar, not a Boot app, and its parent is `spring-boot-starter-parent` 3.2.1 (the same parent as the services). Its dependencies are starter-web, starter-security, starter-data-redis and jjwt 0.12.3. All services already had these, so their dependency trees don't change.

`services/pom.xml` is an **aggregator only**. It lists `common` plus the 5 services, and each module keeps its own Boot parent. To build a service together with its dependencies, run this from `services/`: `mvn -pl <svc> -am test`.

### What moved (package → classes)

| Package | Class | Was in | Notes |
|---|---|---|---|
| `com.parroto.common.security` | `JwtValidator` | content, practice, subscription, vocabulary | All copies identical. auth-service now uses it too, to validate Bearer tokens. |
| | `JwtAuthFilter` | all 5 | 4 copies identical. auth's copy used `JwtService.parseAccessToken` + `TokenBlacklistService.isBlacklisted`, which run the same code: same key, same `type=access` check, key `token_blacklist:{jti}`, same fail-open WARN text. So auth uses the shared filter now. |
| | `AuthenticatedUser` | all 5 | Identical apart from a javadoc comment. |
| | `AdminGuard` | auth, content, subscription, vocabulary | Based on auth's version, which has the most methods (`adminEmails()`, `isAdminEmail()`, `isAdmin()`, `requireAdmin()`). The 403 text comes from property `admin.forbidden-message` (default `Bạn không có quyền quản trị`). content-service sets it to `Admin privileges required` in its `application.yml`, so its old message is kept. Small differences are listed below. |
| | `InternalTokenFilter` | auth, practice, subscription | Identical. |
| `com.parroto.common.web` | `RequestIdFilter`, `RestAuthEntryPoint` | all 5 (`config/`) | Identical. |
| `com.parroto.common.exception` | `ApiException` | all 5 | Identical. |
| `com.parroto.common.dto` | `ErrorResponse`, `PageResponse`, `MessageResponse` | all 5 | Identical (auth's `PageResponse` only had an extra comment). |
| `com.parroto.common.cache` | `CacheService`, `RedisConfig` (JSON `RedisTemplate<String,Object>`, bean name `redisTemplate`) | content, practice, subscription, vocabulary | Identical. Not imported by auth (it never had them). |
| `com.parroto.common.config` | `ParrotoWebConfig` (imports RequestIdFilter, RestAuthEntryPoint, JwtValidator, JwtAuthFilter), `ParrotoCacheConfig` (imports RedisConfig, CacheService) | new | Groups of classes for `@Import`. |

### How the services wire it

Each service has one new class, `<svc>/config/CommonConfig.java`. It is a `@Configuration` with an explicit `@Import`, because `com.parroto.common` is outside the services' component-scan base package:

| Service | `@Import` |
|---|---|
| auth | `ParrotoWebConfig`, `AdminGuard`, `InternalTokenFilter` |
| content | `ParrotoWebConfig`, `ParrotoCacheConfig`, `AdminGuard` |
| practice | `ParrotoWebConfig`, `ParrotoCacheConfig`, `InternalTokenFilter` |
| subscription | `ParrotoWebConfig`, `ParrotoCacheConfig`, `AdminGuard`, `InternalTokenFilter` |
| vocabulary | `ParrotoWebConfig`, `ParrotoCacheConfig`, `AdminGuard` |

I chose explicit imports over a Boot auto-configuration for two reasons:

- The set of beans differs per service. content and vocabulary must not get `InternalTokenFilter`, which fails at startup without `INTERNAL_TOKEN`. auth must not get the JSON `redisTemplate`.
- The imports sit in a scanned `@Configuration` class rather than on the `@SpringBootApplication` class. That keeps future `@WebMvcTest` slices free of Redis beans.

`SecurityConfig` in each service is unchanged except for its imports. The Spring Security filter chain logged at startup is the same class-by-class as in the running stack, for all 5 services (see Verification).

### Deliberately left per-service

| Class | Why |
|---|---|
| `GlobalExceptionHandler` (all 5) | The copies really differ. Different handlers: multipart and 50MB limit in content, `NoResourceFoundException`/405 in vocabulary and content, a Spring `ErrorResponse` passthrough. Different messages: `Malformed request body`, `Tham số không hợp lệ`, `Dữ liệu yêu cầu không hợp lệ`. Merging them would change responses. They now use the shared `ApiException`/`ErrorResponse`. |
| `SecurityConfig` (all 5) | Each has its own route rules and filter set, and auth has the `PasswordEncoder`. The CORS block is copied 5 times. It could become a shared helper later, but that wasn't needed here. |
| `RestTemplateConfig` (auth, practice, subscription, vocabulary) | Different timeouts: none / 5s+15s / 3s+5s. Two lines each. |
| auth `JwtService`, `TokenBlacklistService` | Token issuing and blacklist writes stay in auth-service, as the task required. `JwtService.parse` is still used for refresh tokens. |
| auth `ProfileService` admin-email parsing | It is a separate `isAdmin` flag for the profile response and uses `toLowerCase()` without a trim. I didn't touch it to keep the change small. |

### AdminGuard differences that were unified

These change nothing in practice, but you should know them. The shared guard is auth's version. Compared with the old content, subscription and vocabulary guards:

1. The principal's e-mail is now `trim()`med before the lookup. JWT `email` claims come from users that passed `@Email` validation, which rejects surrounding whitespace, so this never fires.
2. Lowercasing uses `Locale.ROOT` instead of the JVM default locale. This only differs under a Turkish or Azerbaijani default locale; the images use the default `en`/POSIX.
3. A null CSV is tolerated. `@Value("${admin.emails:}")` never passes null anyway.

### Tests

- Moved to common: `AdminGuardTest` and `InternalTokenFilterTest` (from auth). Only the package and imports changed.
- New in common: `AdminGuardTest.forbiddenMessageCanBeOverriddenPerService`, plus `JwtAuthFilterTest` with 3 tests: valid token gives the principal; blacklisted, refresh, missing-jti or garbage tokens stay anonymous; Redis down fails open.
- No test was deleted or weakened. The source has 262 `@Test` methods at the base commit and 266 now (+4 new). Surefire ran 280 test executions.

### Build wiring

- **Dockerfiles** (`services/<svc>/Dockerfile`): the build context is now `./services`. The builder runs `mvn -f common/pom.xml dependency:go-offline`, then copies `common/src` and runs `mvn -f common/pom.xml install -DskipTests`, then runs `go-offline` and `package` for `<svc>/pom.xml`. The jar is copied from `/app/<svc>/target/*.jar`. The runtime stage is unchanged.
- **Caching trade-off:** a change in `common/src` invalidates the service's `go-offline` layer, so those dependencies are downloaded again. Most of them are already cached in the common go-offline layer. `go-offline -DexcludeArtifactIds=parroto-common` does not work (it still fails to resolve the module).
- `services/.dockerignore` excludes `**/target` and IDE files.
- **docker-compose.yml**: `build: { context: ./services, dockerfile: <svc>-service/Dockerfile }` for the 5 services.
- **scripts/verify-java.sh**: mounts `services/` and runs `mvn -pl <svc,...> -am test`. With no arguments it runs `mvn test` on the whole reactor; `common` is accepted as an argument.
- **CI** (`.github/workflows/ci.yml`, java matrix): working-directory is `services`, and the job runs `mvn -B -pl ${{ matrix.service }} -am test`. The cache key includes the aggregator, common and service poms.

## 2. Flyway

- `flyway-core` is added to each service with no version; Spring Boot 3.2.1 manages it as **Flyway 9.22.3**. On PostgreSQL 16 it logs this WARN once at startup: `Flyway upgrade recommended: PostgreSQL 16.15 is newer than this version of Flyway and support has not been tested. The latest supported version of PostgreSQL is 15.` It works fine (proven below), so I did not override the version. Flyway 10 would also need the separate `flyway-database-postgresql` artifact.
- Added to each `application.yml`:
  ```yaml
  spring:
    flyway:
      enabled: true
      locations: classpath:db/migration
      baseline-on-migrate: true
      baseline-version: 1
    jpa.hibernate.ddl-auto: validate   # unchanged
  ```
- Each service has `src/main/resources/db/migration/V1__baseline.sql`. It is `pg_dump -U parroto --schema-only --no-owner --no-privileges <db>` of the **running** database, with the `\restrict`, `SET` and `set_config` lines and the dump banner removed, and an explanatory header added. It contains no seed data and no extensions (`gen_random_uuid()` is built in).
- **How it behaves:**
  - When the `public` schema already has tables (any existing volume, or a fresh volume where `init-db.sql` ran first), Flyway creates `flyway_schema_history` with one `BASELINE` row for version 1 and **does not run V1**.
  - When the database is completely empty, V1 runs and builds the identical schema.
  - The service roles own their databases (`initdb/00-roles.sh`: `CREATE DATABASE … OWNER <svc>_user`), so they are allowed to create the history table.
- **Drift check:** the live schema and a fresh `init-db.sql` volume differ only in column *order* in three tables. These are `vocab_deck.owner_id`, the `game_session` timestamps and `plan.created_at`, all added by `ALTER TABLE ADD COLUMN` in migrations 001/004/006/003. V1 follows the live order. Hibernate doesn't care about column order.
- `init-db.sql` and `scripts/migrations/` are unchanged (frozen history). The README "Operations notes" now explain the rule and list the reserved version numbers.

### How to add a migration

1. Create `services/<svc>/src/main/resources/db/migration/V<n>__<snake_case_description>.sql`. `<n>` is the next free number for that service (table below). Two underscores after the version.
2. Write forward-only PostgreSQL. Flyway applies each file once, in a transaction, before Hibernate validates. Keep entities and the migration in the same commit.
3. Never edit an applied migration or `V1__baseline.sql`; their checksums are validated. Fix a mistake with a new version.
4. Deploy with `docker-compose up -d --build <svc>`. The log shows `Migrating schema "public" to version "<n> - …"`. Check with `docker exec parroto-postgres psql -U parroto -d <db> -c 'select version,description,success from flyway_schema_history'`.
5. Don't add files to `scripts/migrations/` or edit `init-db.sql` for new changes. A fresh volume gets `init-db.sql` (the baseline) plus all `V2+` on first start.

### Reserved versions

| Service | Version | Feature |
|---|---|---|
| content-service | V2 | premium lessons |
| practice-service | V2 | sentence review SRS |
| vocabulary-service | V2 | gamification |
| vocabulary-service | V3 | vocabulary groups / import / dictionary |

Next free numbers: auth V2, subscription V2, content V3, practice V3, vocabulary V4.

## Verification evidence

All of this ran on 2026-10-15 on this machine. The running `parroto-*` stack was not stopped, rebuilt or written to. I only ran `pg_dump` against it and sent a few GET/DELETE requests that are rejected before any handler code runs (401/403/400).

1. **Unit tests.** `scripts/verify-java.sh` (common + all 5) returned `verify-java: OK (common auth-service content-service practice-service vocabulary-service subscription-service)`. Surefire results:

   | Module | Tests | Errors / failures |
   |---|---|---|
   | auth | 42 | 0 |
   | common | 13 | 0 |
   | content | 31 | 0 |
   | practice | 47 | 0 |
   | subscription | 63 | 0 |
   | vocabulary | 84 | 0 |

   `scripts/verify-java.sh content vocabulary` returned `OK (content vocabulary)`.
2. **Images.** `docker-compose --env-file .env.example -p phase2fnd -f docker-compose.yml build` built all 7 images (5 services, api-gateway, frontend) as `phase2fnd-*`, exit 0. No `up`.
3. **Compose config.** `docker-compose --env-file .env.example config -q` and `… -f docker-compose.yml -f docker-compose.override.yml.example config -q` both passed. `bash -n` passed on `scripts/*.sh initdb/*.sh`.
4. **Flyway on a copy of the real data.** I started a separate `postgres:16-alpine` (`p2fly-copy`) on its own network `p2fly-net`, with roles and databases from `initdb/00-roles.sh`. I loaded the data with `pg_dump --no-owner --no-privileges <db> | psql -U <svc>_user` from `parroto-postgres` for all 5 databases (row counts match, e.g. vocab_card 1008/1008). Then I ran each new `phase2fnd-<svc>-service` image against it, with the compose environment plus a throwaway Redis. For every service:
   - The log showed `Creating Schema History table "public"."flyway_schema_history" with baseline ...`, then `Successfully baselined schema with version: 1`, then `Schema "public" is up to date. No migration necessary.`, then `Started …Application`.
   - `flyway_schema_history` had a single row: `1 | << Flyway Baseline >> | BASELINE | success=t`.
   - Hibernate `validate` passed (the service started) and `/actuator/health` returned `{"status":"UP"}`.
5. **Behaviour is identical (old code vs new).** I sent the same 15 requests to the live old-code containers and to the new containers: 401s, 403s from AdminGuard in auth/content/vocabulary/subscription, the 400 handlers, and `/internal/**` without or with a wrong token. After normalising timestamps, the response bodies and status codes were **identical**. Examples:
   - content: `{"error":"Admin privileges required","status":403,…}`
   - vocabulary and subscription: `{"error":"Bạn không có quyền quản trị","status":403,…}`
   - `{"error":"Invalid or missing internal token","status":401,…}`
   - a refresh token used as Bearer: 401 `Authentication required`
   - `X-Request-Id` was echoed back

   On the new code only:
   - a valid admin token gave 200 on `/auth/admin/stats` and `/subscriptions/admin/stats`
   - a blacklisted jti gave 401
   - with Redis stopped, a valid token gave 200 and the WARN `Redis unavailable, skipping token blacklist check (fail-open)` from `c.parroto.common.security.JwtAuthFilter`

   The Security filter chains matched the live services class by class.
6. **Fresh-volume path.** I started an empty `postgres:16-alpine` (`p2fly-fresh`) with the same initdb mounts as compose (`00-roles.sh`, `init-db.sql` as `01-init.sql`, `02`/`03` seed scripts with their seed SQL). All 5 services **baselined** (type `BASELINE`, V1 not executed) and started.
7. **V1 matches the schema exactly.** I created empty databases `v1empty_<svc>` owned by each role. Flyway ran `V1__baseline.sql` (`Migrating schema "public" to version "1 - baseline"`, success) and every service started with validate. A `pg_dump --schema-only -T flyway_schema_history` of each was **identical** to the live dump.
8. **V2 applies and restarts are idempotent.** On the baselined copy of vocabulary_db I ran with an extra location (`filesystem:/extra`) containing a probe `V2__probe.sql`. The first start logged `Migrating schema "public" to version "2 - probe"`. The second start logged `Current version … 2 … up to date`. The history table had `1 BASELINE` and `2 SQL V2__probe.sql`.
9. **Cleanup.** I removed the containers `p2fly-copy`, `p2fly-fresh`, `p2fly-redis` and `p2fly-run-*`, the network `p2fly-net`, and their 3 anonymous volumes. The live databases have no `flyway_schema_history` (checked with `to_regclass`). The `phase2fnd-*` images are still there; remove them with `docker rmi $(docker images -q 'phase2fnd-*')`.

## Risks / follow-ups

- **First deploy on the live volume:** each service creates `flyway_schema_history` with a baseline row on its first start with this code. This is proven on a copy of the live data, but it is still the first write Flyway makes to production data.
  - If a live database has drifted from the 001–007 state (for example a migration was never applied), the baseline still records it as V1, and Hibernate `validate` catches the missing columns as before.
  - From then on, V2+ migrations must assume the V1 schema.
- **Parallel feature branches** can collide on version numbers. Use the reserved table above, and renumber before merging if needed. Flyway refuses out-of-order versions by default: `outOfOrder=false`, so a lower version added after a higher one has run fails validation, which is the safe failure.
- **Flyway 9.22.3 on PG 16** is officially "untested" (WARN only). If a later Boot upgrade brings Flyway 10, also add `org.flywaydb:flyway-database-postgresql`.
- **Docker layer caching** is slightly worse when only `common/src` changes (see Build wiring).
- **Build context is `./services`:** the Dockerfiles can no longer be built from inside a service directory. Use `docker build -f services/<svc>/Dockerfile services`.
- **`parroto-common` is versioned 1.0.0 and always built from source** (reactor or Dockerfile). Nothing is published. A service built with `-f <svc>/pom.xml` alone needs `mvn -f common/pom.xml install` first.
- `files/docker-compose.yml` and `files/*.md` (archived planning docs) still show the old per-service build contexts. They are not used at runtime.
