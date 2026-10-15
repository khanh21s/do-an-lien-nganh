package com.parroto.subscription.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parroto.subscription.dto.AdminPlanRequest;
import com.parroto.subscription.dto.AdminPlanResponse;
import com.parroto.subscription.entity.Plan;
import com.parroto.common.exception.ApiException;
import com.parroto.subscription.repository.PlanRepository;
import com.parroto.subscription.repository.SubscriptionRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** T14-SUB: admin plan create/update rules. */
class AdminPlanServiceTest {

    private static final jakarta.validation.ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    private PlanRepository planRepository;
    private SubscriptionRepository subscriptionRepository;
    private AdminPlanService service;

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    @BeforeEach
    void setUp() {
        planRepository = mock(PlanRepository.class);
        subscriptionRepository = mock(SubscriptionRepository.class);
        when(planRepository.saveAndFlush(any(Plan.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new AdminPlanService(planRepository, subscriptionRepository);
    }

    private static AdminPlanRequest req(String id, String name, Integer days, Long price, List<String> features,
                                        Boolean active) {
        return new AdminPlanRequest(id, name, days, price, features, active);
    }

    private static Set<ConstraintViolation<AdminPlanRequest>> violations(AdminPlanRequest r) {
        return VALIDATOR.validate(r);
    }

    // ---- bean validation ----

    @Test
    void validRequestHasNoViolations() {
        assertThat(violations(req("plan-6month", "6 tháng", 180, 299000L, List.of("A"), true))).isEmpty();
        assertThat(violations(req(null, "x", 1, 1000L, null, null))).isEmpty(); // PUT: id optional
        assertThat(violations(req("abc", "x".repeat(255), 3650, 1000L, null, false))).isEmpty();
    }

    @Test
    void rejectsBadIdNamePriceAndDuration() {
        assertThat(violations(req("AB", "x", 30, 1000L, null, true))).hasSize(1);          // too short + upper case
        assertThat(violations(req("Plan_1", "x", 30, 1000L, null, true))).hasSize(1);      // bad chars
        assertThat(violations(req("a".repeat(51), "x", 30, 1000L, null, true))).hasSize(1);
        assertThat(violations(req("abc", " ", 30, 1000L, null, true))).hasSize(1);         // blank name
        assertThat(violations(req("abc", "x".repeat(256), 30, 1000L, null, true))).hasSize(1);
        assertThat(violations(req("abc", "x", 0, 1000L, null, true))).hasSize(1);
        assertThat(violations(req("abc", "x", 3651, 1000L, null, true))).hasSize(1);
        assertThat(violations(req("abc", "x", null, 1000L, null, true))).hasSize(1);
        assertThat(violations(req("abc", "x", 30, 999L, null, true))).hasSize(1);
        assertThat(violations(req("abc", "x", 30, null, null, true))).hasSize(1);
        assertThat(violations(req("abc", "x", 30, 1000L, java.util.Collections.nCopies(21, "f"), true))).hasSize(1);
    }

    @Test
    void isActiveIsTheJsonFieldName() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        AdminPlanRequest parsed = mapper.readValue(
                "{\"id\":\"abc\",\"name\":\"n\",\"durationDays\":1,\"price\":1000,\"features\":[],\"isActive\":false}",
                AdminPlanRequest.class);
        assertThat(parsed.isActive()).isFalse();

        String json = mapper.writeValueAsString(AdminPlanResponse.from(
                Plan.builder().id("abc").name("n").durationDays(1).price(1000L).isActive(false).build(), 3));
        assertThat(json).contains("\"isActive\":false").contains("\"activeSubscribers\":3")
                .contains("\"features\":[]").doesNotContain("\"active\"");
    }

    // ---- create ----

    @Test
    void createTrimsFeaturesAndDefaultsToActive() {
        when(planRepository.existsById("plan-6month")).thenReturn(false);

        AdminPlanResponse r = service.createPlan(req("plan-6month", "  6 tháng  ", 180, 299000L,
                Arrays.asList(" Không quảng cáo ", "", null, "Pro"), null));

        assertThat(r.id()).isEqualTo("plan-6month");
        assertThat(r.name()).isEqualTo("6 tháng");
        assertThat(r.features()).containsExactly("Không quảng cáo", "Pro");
        assertThat(r.isActive()).isTrue();
        assertThat(r.activeSubscribers()).isZero();
        verify(planRepository).saveAndFlush(any(Plan.class));
    }

    @Test
    void createDuplicateIdIs409() {
        when(planRepository.existsById("plan-1month")).thenReturn(true);

        assertThatThrownBy(() -> service.createPlan(req("plan-1month", "x", 30, 1000L, null, true)))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo("Mã gói đã tồn tại");
                });
        verify(planRepository, never()).saveAndFlush(any());
    }

    @Test
    void createWithoutIdOrBadIdIs400() {
        assertThatThrownBy(() -> service.createPlan(req(null, "x", 30, 1000L, null, true)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.createPlan(req("Bad Id", "x", 30, 1000L, null, true)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ---- update ----

    @Test
    void updateUnknownPlanIs404() {
        when(planRepository.findById(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePlan("nope", req(null, "x", 30, 1000L, null, true)))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo("Không tìm thấy gói");
                });
    }

    @Test
    void updateCanDisableAndIgnoresBodyId() {
        Plan plan = Plan.builder().id("plan-1month").name("1 tháng").durationDays(30).price(79000L).build();
        when(planRepository.findById("plan-1month")).thenReturn(Optional.of(plan));
        when(subscriptionRepository.countActiveSubscribersOfPlan(eq("plan-1month"), any())).thenReturn(7L);

        AdminPlanResponse r = service.updatePlan("plan-1month",
                req("something-else", "1 tháng Premium", 31, 89000L, List.of("A"), false));

        assertThat(r.id()).isEqualTo("plan-1month");
        assertThat(r.isActive()).isFalse();
        assertThat(r.price()).isEqualTo(89000L);
        assertThat(r.durationDays()).isEqualTo(31);
        assertThat(r.activeSubscribers()).isEqualTo(7L);
        assertThat(plan.isActive()).isFalse();
    }

    @Test
    void updateWithoutIsActiveKeepsCurrentFlag() {
        Plan plan = Plan.builder().id("plan-old").name("x").durationDays(30).price(5000L).isActive(false).build();
        when(planRepository.findById("plan-old")).thenReturn(Optional.of(plan));

        service.updatePlan("plan-old", req(null, "y", 30, 5000L, null, null));

        assertThat(plan.isActive()).isFalse();
        assertThat(plan.getName()).isEqualTo("y");
    }

    @Test
    void listIncludesDisabledPlansWithSubscriberCounts() {
        Plan a = Plan.builder().id("plan-1month").name("a").durationDays(30).price(79000L).build();
        Plan b = Plan.builder().id("plan-old").name("b").durationDays(60).price(9000L).isActive(false).build();
        when(planRepository.findAllByOrderByDurationDaysAscPriceAsc()).thenReturn(List.of(a, b));
        when(subscriptionRepository.countActiveSubscribersByPlan(any()))
                .thenReturn(List.<Object[]>of(new Object[]{"plan-1month", 4L}));

        List<AdminPlanResponse> list = service.listPlans();

        assertThat(list).extracting(AdminPlanResponse::id).containsExactly("plan-1month", "plan-old");
        assertThat(list).extracting(AdminPlanResponse::activeSubscribers).containsExactly(4L, 0L);
        assertThat(list).extracting(AdminPlanResponse::isActive).containsExactly(true, false);
    }
}
