package com.parroto.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.parroto.auth.dto.AdminUserResponse;
import com.parroto.auth.dto.AdminUserStatsResponse;
import com.parroto.auth.dto.PageResponse;
import com.parroto.auth.entity.User;
import com.parroto.auth.exception.ApiException;
import com.parroto.auth.repository.UserRepository;
import com.parroto.auth.security.AdminGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserServiceTest {

    private UserRepository repository;
    private AdminUserService service;
    private final UUID adminId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(UserRepository.class);
        service = new AdminUserService(repository, new AdminGuard("admin@parroto.com,boss@parroto.com"));
        when(repository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private User user(String email, Boolean active) {
        User u = User.builder().id(UUID.randomUUID()).email(email).authProvider("email").cefrLevel("B1")
                .isActive(active).build();
        u.setCreatedAt(OffsetDateTime.parse("2026-10-01T00:00:00Z"));
        when(repository.findById(u.getId())).thenReturn(Optional.of(u));
        return u;
    }

    // --- search / filter ---------------------------------------------------------------------

    @Test
    void listPassesEscapedLowercasePatternStatusAndCreatedAtDescPaging() {
        User a = user("Admin@Parroto.com", true);
        User b = user("learner@x.io", false);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(repository.searchForAdmin(eq("%a\\_b\\%c%"), eq("locked"), pageable.capture()))
                .thenAnswer(inv -> new PageImpl<>(List.of(a, b), inv.getArgument(2), 42));

        PageResponse<AdminUserResponse> page = service.listUsers("  A_b%C ", "LOCKED", 3, 10);

        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(page.pagination().page()).isEqualTo(3);
        assertThat(page.pagination().limit()).isEqualTo(10);
        assertThat(page.pagination().total()).isEqualTo(42);
        assertThat(page.pagination().totalPages()).isEqualTo(5);
        assertThat(page.data()).extracting(AdminUserResponse::email).containsExactly("Admin@Parroto.com", "learner@x.io");
        assertThat(page.data()).extracting(AdminUserResponse::isAdmin).containsExactly(true, false);
        assertThat(page.data()).extracting(AdminUserResponse::isActive).containsExactly(true, false);
    }

    @Test
    void blankQueryAndStatusMatchEveryoneAndPagingIsClamped() {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(repository.searchForAdmin(eq("%"), eq("all"), pageable.capture()))
                .thenAnswer(inv -> Page.empty(inv.getArgument(2)));

        PageResponse<AdminUserResponse> page = service.listUsers(" ", null, 0, 1000);

        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(AdminUserService.MAX_LIMIT);
        assertThat(page.pagination().page()).isEqualTo(1);
        assertThat(page.data()).isEmpty();
    }

    @Test
    void unknownStatusIs400() {
        assertThatThrownBy(() -> service.listUsers(null, "deleted", 1, 20))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(repository, never()).searchForAdmin(anyString(), anyString(), any());
    }

    @Test
    void likePatternEscapesWildcards() {
        assertThat(AdminUserService.likePattern(null)).isEqualTo("%");
        assertThat(AdminUserService.likePattern("Gmail")).isEqualTo("%gmail%");
        assertThat(AdminUserService.likePattern("a\\b")).isEqualTo("%a\\\\b%");
    }

    // --- detail -------------------------------------------------------------------------------

    @Test
    void unknownUserIs404() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getUser(id)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(e.getMessage()).isEqualTo("Không tìm thấy người dùng");
        });
        assertThatThrownBy(() -> service.setActive(id, false, adminId)).isInstanceOf(ApiException.class);
    }

    // --- lock / unlock ------------------------------------------------------------------------

    @Test
    void lockAndUnlockRegularUser() {
        User u = user("learner@x.io", true);

        AdminUserResponse locked = service.setActive(u.getId(), false, adminId);
        assertThat(locked.isActive()).isFalse();
        assertThat(u.getIsActive()).isFalse();

        AdminUserResponse unlocked = service.setActive(u.getId(), true, adminId);
        assertThat(unlocked.isActive()).isTrue();
        assertThat(u.getIsActive()).isTrue();
        verify(repository, org.mockito.Mockito.times(2)).save(u);
    }

    @Test
    void cannotLockAnotherAdmin() {
        User other = user("BOSS@parroto.com", true);
        assertThatThrownBy(() -> service.setActive(other.getId(), false, adminId))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo("Không thể khoá tài khoản quản trị");
                });
        assertThat(other.getIsActive()).isTrue();
        verify(repository, never()).save(any());
    }

    @Test
    void cannotLockSelfEvenIfNoLongerInAllowList() {
        User self = user("former-admin@x.io", true);
        assertThatThrownBy(() -> service.setActive(self.getId(), false, self.getId()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getMessage()).isEqualTo("Không thể khoá tài khoản quản trị"));
        verify(repository, never()).save(any());
    }

    @Test
    void unlockingAnAdminIsAllowed() {
        User admin = user("admin@parroto.com", false);
        assertThat(service.setActive(admin.getId(), true, adminId).isActive()).isTrue();
    }

    // --- stats --------------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void statsExcludeAdminEmails() {
        ArgumentCaptor<Collection<String>> excluded = ArgumentCaptor.forClass(Collection.class);
        when(repository.countExcluding(excluded.capture())).thenReturn(10L);
        when(repository.countActiveExcluding(anyCollection())).thenReturn(7L);
        ArgumentCaptor<OffsetDateTime> since = ArgumentCaptor.forClass(OffsetDateTime.class);
        when(repository.countCreatedSinceExcluding(anyCollection(), since.capture())).thenReturn(2L, 5L);

        AdminUserStatsResponse stats = service.stats();

        assertThat(excluded.getValue()).containsExactlyInAnyOrder("admin@parroto.com", "boss@parroto.com");
        assertThat(stats).isEqualTo(new AdminUserStatsResponse(10, 7, 3, 2, 5));
        assertThat(since.getAllValues().get(0)).isAfter(since.getAllValues().get(1));
    }

    @Test
    @SuppressWarnings("unchecked")
    void statsWithoutAdminConfigUseNonEmptySentinel() {
        AdminUserService noAdmins = new AdminUserService(repository, new AdminGuard(""));
        ArgumentCaptor<Collection<String>> excluded = ArgumentCaptor.forClass(Collection.class);
        when(repository.countExcluding(excluded.capture())).thenReturn(0L);

        noAdmins.stats();

        assertThat(excluded.getValue()).containsExactly("");
    }

    // --- JSON contract ------------------------------------------------------------------------

    @Test
    void responseJsonUsesContractFieldNames() throws Exception {
        User u = user("learner@x.io", true);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        JsonNode json = mapper.valueToTree(AdminUserResponse.from(u, false));
        assertThat(json.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "id", "email", "authProvider", "cefrLevel", "isActive", "isAdmin", "createdAt", "lastLoginAt");
        JsonNode body = mapper.readTree("{\"isActive\":false}");
        assertThat(mapper.treeToValue(body, com.parroto.auth.dto.AdminUpdateUserRequest.class).isActive()).isFalse();
    }
}
