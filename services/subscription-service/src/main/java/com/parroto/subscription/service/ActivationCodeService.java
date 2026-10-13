package com.parroto.subscription.service;

import com.parroto.subscription.dto.ActivationCodeResponse;
import com.parroto.subscription.dto.CreateActivationCodesRequest;
import com.parroto.subscription.dto.CreateActivationCodesResponse;
import com.parroto.subscription.dto.PageResponse;
import com.parroto.subscription.entity.ActivationCode;
import com.parroto.subscription.entity.Plan;
import com.parroto.subscription.exception.ApiException;
import com.parroto.subscription.repository.ActivationCodeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.random.RandomGenerator;

/** ADM-06: batch generation and listing of activation codes for admins. */
@Service
public class ActivationCodeService {

    /** A–Z and 2–9 without the easily confused 0/O/1/I (architecture §4.2). */
    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static final int MAX_QUANTITY = 500;
    static final int DEFAULT_VALID_DAYS = 365;
    private static final int MAX_GENERATION_ROUNDS = 10;
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ActivationCodeRepository activationCodeRepository;
    private final PlanService planService;
    private final AuthServiceClient authServiceClient;
    private final RandomGenerator random;

    @org.springframework.beans.factory.annotation.Autowired
    public ActivationCodeService(ActivationCodeRepository activationCodeRepository, PlanService planService,
                                  AuthServiceClient authServiceClient) {
        this(activationCodeRepository, planService, authServiceClient, new SecureRandom());
    }

    ActivationCodeService(ActivationCodeRepository activationCodeRepository, PlanService planService,
                          AuthServiceClient authServiceClient, RandomGenerator random) {
        this.activationCodeRepository = activationCodeRepository;
        this.planService = planService;
        this.authServiceClient = authServiceClient;
        this.random = random;
    }

    @Transactional
    public CreateActivationCodesResponse createCodes(CreateActivationCodesRequest request) {
        int quantity = request.quantity() == null ? 0 : request.quantity();
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw ApiException.badRequest("Số lượng mã phải từ 1 đến " + MAX_QUANTITY);
        }
        Plan plan = planService.getActivePlanOrThrow(request.planId());
        OffsetDateTime expiresAt = parseExpiresAt(request.expiresAt());

        Set<String> codes = generateUniqueCodes(quantity);
        List<ActivationCode> entities = codes.stream()
                .map(code -> ActivationCode.builder().code(code).plan(plan).expiresAt(expiresAt).build())
                .toList();
        List<ActivationCode> saved = activationCodeRepository.saveAllAndFlush(entities);
        return new CreateActivationCodesResponse(
                saved.stream().map(c -> ActivationCodeResponse.from(c, null)).toList());
    }

    /**
     * @param used null = all, true = only redeemed codes, false = only unused codes
     */
    @Transactional(readOnly = true)
    public PageResponse<ActivationCodeResponse> listCodes(Boolean used, int page, int limit) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safeLimit);
        Page<ActivationCode> codes;
        if (used == null) {
            codes = activationCodeRepository.findAllByOrderByCreatedAtDesc(pageable);
        } else if (used) {
            codes = activationCodeRepository.findByUsedByUserIdIsNotNullOrderByCreatedAtDesc(pageable);
        } else {
            codes = activationCodeRepository.findByUsedByUserIdIsNullOrderByCreatedAtDesc(pageable);
        }

        // One auth-service lookup per distinct user on this page; failures resolve to null.
        Map<UUID, String> emails = new HashMap<>();
        for (ActivationCode c : codes.getContent()) {
            if (c.getUsedByUserId() != null && !emails.containsKey(c.getUsedByUserId())) {
                emails.put(c.getUsedByUserId(), authServiceClient.findEmail(c.getUsedByUserId()));
            }
        }
        Page<ActivationCodeResponse> mapped = codes.map(c -> ActivationCodeResponse.from(c,
                c.getUsedByUserId() == null ? null : emails.get(c.getUsedByUserId())));
        return PageResponse.of(mapped, safePage, safeLimit);
    }

    /** Generates {@code quantity} codes unique within the batch and against the DB, retrying collisions. */
    Set<String> generateUniqueCodes(int quantity) {
        Set<String> result = new LinkedHashSet<>();
        for (int round = 0; round < MAX_GENERATION_ROUNDS && result.size() < quantity; round++) {
            Set<String> candidates = new LinkedHashSet<>();
            while (candidates.size() < quantity - result.size()) {
                String candidate = randomCode();
                if (!result.contains(candidate)) {
                    candidates.add(candidate);
                }
            }
            candidates.removeAll(activationCodeRepository.findExistingCodes(candidates));
            result.addAll(candidates);
        }
        if (result.size() < quantity) {
            throw new IllegalStateException("Could not generate " + quantity + " unique activation codes");
        }
        return result;
    }

    /** {@code PROMO-XXXXX-XXXXX}. */
    String randomCode() {
        StringBuilder sb = new StringBuilder("PROMO-");
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                sb.append('-');
            }
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    static OffsetDateTime parseExpiresAt(String value) {
        OffsetDateTime now = OffsetDateTime.now();
        if (value == null || value.isBlank()) {
            return now.plusDays(DEFAULT_VALID_DAYS);
        }
        OffsetDateTime parsed;
        try {
            parsed = OffsetDateTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            try {
                parsed = LocalDate.parse(value.trim()).atTime(LocalTime.MAX).atZone(APP_ZONE).toOffsetDateTime();
            } catch (DateTimeParseException e2) {
                throw ApiException.badRequest("expiresAt không hợp lệ (cần ISO-8601, vd 2027-12-31)");
            }
        }
        if (!parsed.isAfter(now)) {
            throw ApiException.badRequest("Ngày hết hạn phải ở tương lai");
        }
        return parsed;
    }
}
