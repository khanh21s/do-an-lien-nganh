package com.parroto.practice.service;

import com.parroto.common.exception.ApiException;
import com.parroto.common.security.AdminGuard;
import com.parroto.common.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * P2-A1 "preview, lock practice": anyone may read a Pro lesson (content-service), but Dictation /
 * Shadowing on it (attempts + the shadowing upload URL) need an active premium subscription.
 * Admins (ADMIN_EMAILS) always pass.
 */
@Component
public class LessonAccessGuard {

    public static final String PRO_REQUIRED_MESSAGE =
            "Bài học này dành cho tài khoản Premium. Nâng cấp Premium để luyện Dictation và Shadowing.";
    public static final String PREMIUM_CHECK_FAILED_MESSAGE =
            "Không kiểm tra được gói Premium, vui lòng thử lại sau.";

    private final PremiumClient premiumClient;
    private final AdminGuard adminGuard;

    public LessonAccessGuard(PremiumClient premiumClient, AdminGuard adminGuard) {
        this.premiumClient = premiumClient;
        this.adminGuard = adminGuard;
    }

    /**
     * Free lesson / admin / premium → returns. Not premium → 403 {@link #PRO_REQUIRED_MESSAGE}.
     * subscription-service unreachable → 503 (fail closed, but without telling a paying user to upgrade).
     */
    public void requirePracticeAccess(AuthenticatedUser user, ContentServiceClient.LessonRef lesson) {
        if (!lesson.isPro() || adminGuard.isAdmin(user)) {
            return;
        }
        Boolean premium = premiumClient.lookup(user.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, PREMIUM_CHECK_FAILED_MESSAGE));
        if (!premium) {
            throw ApiException.forbidden(PRO_REQUIRED_MESSAGE);
        }
    }

    /** Non-throwing variant for listings (review queue): unknown premium status counts as locked. */
    public boolean canPractice(AuthenticatedUser user, boolean lessonIsPro) {
        return !lessonIsPro || adminGuard.isAdmin(user) || premiumClient.lookup(user.userId()).orElse(false);
    }
}
