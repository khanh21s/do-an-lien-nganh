package com.parroto.auth.service;

import com.parroto.auth.dto.AdminUserResponse;
import com.parroto.auth.dto.AdminUserStatsResponse;
import com.parroto.auth.dto.PageResponse;
import com.parroto.auth.entity.User;
import com.parroto.auth.exception.ApiException;
import com.parroto.auth.repository.UserRepository;
import com.parroto.auth.security.AdminGuard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** T14-AUTH: admin user management (list/detail/lock/unlock) and user stats. Callers must check AdminGuard first. */
@Service
public class AdminUserService {

    public static final String USER_NOT_FOUND = "Không tìm thấy người dùng";
    public static final String CANNOT_LOCK_ADMIN = "Không thể khoá tài khoản quản trị";
    public static final String INVALID_STATUS = "Trạng thái lọc không hợp lệ (all|active|locked)";
    static final int MAX_LIMIT = 100;
    private static final Set<String> STATUSES = Set.of("all", "active", "locked");

    private final UserRepository userRepository;
    private final AdminGuard adminGuard;

    public AdminUserService(UserRepository userRepository, AdminGuard adminGuard) {
        this.userRepository = userRepository;
        this.adminGuard = adminGuard;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> listUsers(String q, String status, int page, int limit) {
        String normalizedStatus = status == null || status.isBlank() ? "all" : status.trim().toLowerCase(Locale.ROOT);
        if (!STATUSES.contains(normalizedStatus)) {
            throw ApiException.badRequest(INVALID_STATUS);
        }
        int safePage = Math.max(page, 1);
        int safeLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        PageRequest pageable = PageRequest.of(safePage - 1, safeLimit,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        Page<AdminUserResponse> result = userRepository
                .searchForAdmin(likePattern(q), normalizedStatus, pageable)
                .map(this::toResponse);
        return PageResponse.of(result, safePage, safeLimit);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUser(UUID id) {
        return toResponse(findUser(id));
    }

    /**
     * Locks/unlocks an account. Admin accounts (including the caller's own) can never be locked;
     * unlocking is always allowed. Login and refresh-token reject locked accounts with 403; an access
     * token issued before the lock stays valid until it expires (at most 1 hour).
     */
    @Transactional
    public AdminUserResponse setActive(UUID id, boolean active, UUID actingAdminId) {
        User user = findUser(id);
        if (!active && (id.equals(actingAdminId) || adminGuard.isAdminEmail(user.getEmail()))) {
            throw ApiException.badRequest(CANNOT_LOCK_ADMIN);
        }
        if (!Boolean.valueOf(active).equals(user.getIsActive())) {
            user.setIsActive(active);
            user = userRepository.save(user);
        }
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public AdminUserStatsResponse stats() {
        Collection<String> excluded = adminGuard.adminEmails().isEmpty() ? Set.of("") : adminGuard.adminEmails();
        OffsetDateTime now = OffsetDateTime.now();
        long total = userRepository.countExcluding(excluded);
        long active = userRepository.countActiveExcluding(excluded);
        return new AdminUserStatsResponse(total, active, total - active,
                userRepository.countCreatedSinceExcluding(excluded, now.minusDays(7)),
                userRepository.countCreatedSinceExcluding(excluded, now.minusDays(30)));
    }

    /** Lower-cased "contains" LIKE pattern; %, _ and \ in the query are matched literally. */
    static String likePattern(String q) {
        if (q == null || q.isBlank()) {
            return "%";
        }
        String escaped = q.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private User findUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> ApiException.notFound(USER_NOT_FOUND));
    }

    private AdminUserResponse toResponse(User user) {
        return AdminUserResponse.from(user, adminGuard.isAdminEmail(user.getEmail()));
    }
}
