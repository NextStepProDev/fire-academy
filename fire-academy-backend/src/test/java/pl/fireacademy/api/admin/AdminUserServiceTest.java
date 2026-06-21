package pl.fireacademy.api.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import pl.fireacademy.api.NotFoundException;
import pl.fireacademy.api.admin.AdminUserDtos.*;
import pl.fireacademy.config.AdminEmailConfig;
import pl.fireacademy.domain.auth.AuthTokenRepository;
import pl.fireacademy.domain.enrollment.Enrollment;
import pl.fireacademy.domain.enrollment.EnrollmentRepository;
import pl.fireacademy.domain.event.Event;
import pl.fireacademy.domain.event.EventCategory;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.domain.user.UserRepository;
import pl.fireacademy.domain.user.UserRole;
import pl.fireacademy.infrastructure.i18n.MessageService;
import pl.fireacademy.infrastructure.mail.AdminUserMailService;
import pl.fireacademy.infrastructure.security.JwtAuthenticationFilter;
import pl.fireacademy.infrastructure.storage.FileStorageService;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminUserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AuthTokenRepository authTokenRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private pl.fireacademy.domain.enrollment.EnrollmentErasureService enrollmentErasureService;
    @Mock private AdminEmailConfig adminEmailConfig;
    @Mock private AdminUserMailService adminUserMailService;
    @Mock private FileStorageService fileStorageService;
    @Mock private JwtAuthenticationFilter jwtAuthenticationFilter;
    @Mock private MessageService msg;

    @InjectMocks private AdminUserService service;

    private User regular;
    private UUID regularId;

    @BeforeEach
    void setUp() throws Exception {
        when(msg.get(anyString())).thenReturn("Komunikat");
        when(adminEmailConfig.isAdminEmail(anyString())).thenReturn(false);

        regularId = UUID.randomUUID();
        regular = user(regularId, "jan@test.com", "Jan", "Kowalski", UserRole.USER);
    }

    // --- list / search ---

    @Test
    void shouldListAllUsersWhenNoSearch() {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(regular)));

        PagedUsersResponse result = service.list(null, 0, 30, "created", "desc");

        assertEquals(1, result.content().size());
        assertEquals("jan@test.com", result.content().getFirst().email());
        verify(userRepository).findAll(any(Pageable.class));
        verify(userRepository, never()).searchByPhrase(any(), any());
    }

    private static final java.util.Set<String> ALLOWED_SORT_PROPERTIES =
            java.util.Set.of("lastName", "firstName", "email", "role", "marketingConsentAt", "createdAt");

    @ParameterizedTest(name = "sort=\"{0}\" dir={1} -> {3} {2}")
    @CsvSource({
            "name,       asc,  lastName,           ASC",
            "name,       desc, lastName,           DESC",
            "email,      asc,  email,              ASC",
            "role,       desc, role,               DESC",
            "marketing,  asc,  marketingConsentAt, ASC",
            "created,    desc, createdAt,          DESC",
            "phone,      asc,  createdAt,          ASC",   // phone is not sortable -> default
            "garbage,    desc, createdAt,          DESC",  // value outside the whitelist -> default
            "'',         asc,  createdAt,          ASC"    // empty -> default
    })
    void shouldResolveSortFromWhitelistOnly(String sort, String direction,
                                            String expectedProperty, Sort.Direction expectedDir) {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(regular)));

        service.list(null, 0, 30, sort, direction);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(captor.capture());
        Sort.Order order = captor.getValue().getSort().getOrderFor(expectedProperty);
        assertNotNull(order, "Brak sortowania po " + expectedProperty);
        assertEquals(expectedDir, order.getDirection());
        // The whitelist must not let an arbitrary property through (protection against injection into Sort).
        captor.getValue().getSort().forEach(o ->
                assertTrue(ALLOWED_SORT_PROPERTIES.contains(o.getProperty()),
                        "Niedozwolona właściwość sortowania: " + o.getProperty()));
    }

    @Test
    void shouldSortByLastNameThenFirstNameForNameKey() {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(regular)));

        service.list(null, 0, 30, "name", "asc");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(captor.capture());
        assertEquals(List.of("lastName", "firstName"),
                captor.getValue().getSort().toList().stream().map(Sort.Order::getProperty).toList());
    }

    @Test
    void shouldSearchUsersByPhraseTrimmed() {
        when(userRepository.searchByPhrase(eq("kowal"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(regular)));

        PagedUsersResponse result = service.list("  kowal  ", 0, 30, "created", "desc");

        assertEquals(1, result.content().size());
        verify(userRepository).searchByPhrase(eq("kowal"), any(Pageable.class));
    }

    @Test
    void shouldExcludeHiddenEmailsWhenListingWithoutSearch() {
        when(adminEmailConfig.getHiddenEmails()).thenReturn(java.util.Set.of("dev@test.com"));
        when(userRepository.findAllExcludingEmails(anyCollection(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(regular)));

        PagedUsersResponse result = service.list(null, 0, 30, "created", "desc");

        assertEquals(1, result.content().size());
        verify(userRepository).findAllExcludingEmails(eq(java.util.Set.of("dev@test.com")), any(Pageable.class));
        verify(userRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void shouldExcludeHiddenEmailsWhenSearching() {
        when(adminEmailConfig.getHiddenEmails()).thenReturn(java.util.Set.of("dev@test.com"));
        when(userRepository.searchByPhraseExcludingEmails(eq("kowal"), anyCollection(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(regular)));

        PagedUsersResponse result = service.list("  kowal  ", 0, 30, "created", "desc");

        assertEquals(1, result.content().size());
        verify(userRepository).searchByPhraseExcludingEmails(eq("kowal"), anyCollection(), any(Pageable.class));
        verify(userRepository, never()).searchByPhrase(any(), any());
    }

    @Test
    void shouldMarkSuperAdminInResponse() {
        when(adminEmailConfig.isAdminEmail("jan@test.com")).thenReturn(true);
        when(userRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(regular)));

        AdminUserResponse response = service.list(null, 0, 30, "created", "desc").content().getFirst();

        assertTrue(response.superAdmin());
    }

    @Test
    void shouldReturnPaginationMetadata() {
        // 70 total, page 1, size 30 → 3 pages
        when(userRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(regular), PageRequest.of(1, 30), 70));

        PagedUsersResponse result = service.list(null, 1, 30, "created", "desc");

        assertEquals(1, result.page());
        assertEquals(30, result.size());
        assertEquals(70, result.totalElements());
        assertEquals(3, result.totalPages());
    }

    @Test
    void shouldClampPageSizeToMaximum() {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(regular)));

        service.list(null, 0, 5000, "created", "desc");

        // page size clamped to MAX_PAGE_SIZE (100)
        verify(userRepository).findAll(argThat((Pageable p) -> p.getPageSize() == 100));
    }

    @Test
    void shouldSortByNameAscending() {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(regular)));

        service.list(null, 0, 30, "name", "asc");

        // „name" → sort by lastName (then firstName), direction ASC
        verify(userRepository).findAll(argThat((Pageable p) -> {
            var order = p.getSort().getOrderFor("lastName");
            return order != null && order.isAscending();
        }));
    }

    @Test
    void shouldDefaultToCreatedDescForUnknownSortField() {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(regular)));

        service.list(null, 0, 30, "phone", "asc");

        // field outside the whitelist (e.g. phone) → defaults to createdAt
        verify(userRepository).findAll(argThat((Pageable p) ->
                p.getSort().getOrderFor("createdAt") != null
                        && p.getSort().getOrderFor("lastName") == null));
    }

    // --- detail ---

    @Test
    void shouldReturnUserDetailWithCurrentAndPastEnrollments() {
        when(userRepository.findById(regularId)).thenReturn(Optional.of(regular));
        Event future = new Event(EventCategory.TRAINING, "Nadchodzący", LocalDate.now().plusDays(5));
        Event pastEv = new Event(EventCategory.CAMP, "Miniony", LocalDate.now().minusDays(10));
        Enrollment f = new Enrollment(future, "Jan", "Kowalski", "jan@test.com", "123456789", null, false);
        Enrollment p = new Enrollment(pastEv, "Jan", "Kowalski", "jan@test.com", "123456789", null, false);
        when(enrollmentRepository.findByUserIdOrderByCreatedAtDesc(regularId)).thenReturn(List.of(f, p));

        AdminUserDetailResponse detail = service.getDetail(regularId);

        assertEquals("jan@test.com", detail.email());
        assertEquals(1, detail.currentEnrollments().size());
        assertEquals(1, detail.pastEnrollments().size());
        assertFalse(detail.currentEnrollments().getFirst().past());
        assertTrue(detail.pastEnrollments().getFirst().past());
    }

    @Test
    void shouldThrowWhenUserDetailNotFound() {
        UUID missing = UUID.randomUUID();
        when(userRepository.findById(missing)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.getDetail(missing));
    }

    // --- send email ---

    @Test
    void shouldSendEmailToAllUsers() {
        User other = user(UUID.randomUUID(), "anna@test.com", "Anna", "Nowak", UserRole.USER);
        when(userRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(regular, other));

        SendEmailResponse result = service.sendEmail(
                new SendEmailRequest("Temat", "Treść", "ALL", null));

        assertEquals(2, result.recipientCount());
        verify(adminUserMailService).sendCustomMessage("jan@test.com", "Jan", "Temat", "Treść", null);
        verify(adminUserMailService).sendCustomMessage("anna@test.com", "Anna", "Temat", "Treść", null);
    }

    @Test
    void shouldSkipHiddenEmailRecipientsWhenEmailingAll() {
        User dev = user(UUID.randomUUID(), "dev@test.com", "Dev", "Eloper", UserRole.ADMIN);
        when(userRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(regular, dev));
        when(adminEmailConfig.isHiddenEmail("dev@test.com")).thenReturn(true);

        SendEmailResponse result = service.sendEmail(
                new SendEmailRequest("Temat", "Treść", "ALL", null));

        assertEquals(1, result.recipientCount());
        verify(adminUserMailService).sendCustomMessage("jan@test.com", "Jan", "Temat", "Treść", null);
        verify(adminUserMailService, never()).sendCustomMessage(eq("dev@test.com"), any(), any(), any(), any());
    }

    @Test
    void shouldSendEmailToSelectedUsers() {
        when(userRepository.findAllById(List.of(regularId))).thenReturn(List.of(regular));

        SendEmailResponse result = service.sendEmail(
                new SendEmailRequest("Temat", "Treść", "SELECTED", List.of(regularId)));

        assertEquals(1, result.recipientCount());
        verify(userRepository, never()).findAllByOrderByCreatedAtDesc();
        verify(adminUserMailService).sendCustomMessage("jan@test.com", "Jan", "Temat", "Treść", null);
    }

    @Test
    void shouldThrowWhenNoRecipientsSelected() {
        assertThrows(IllegalStateException.class, () -> service.sendEmail(
                new SendEmailRequest("Temat", "Treść", "SELECTED", List.of())));
        verifyNoInteractions(adminUserMailService);
    }

    @Test
    void shouldSendMarketingEmailOnlyToConsentersWithUnsubscribeLink() {
        when(userRepository.findAllByMarketingConsentAtIsNotNullOrderByCreatedAtDesc())
                .thenReturn(List.of(regular));

        SendEmailResponse result = service.sendEmail(
                new SendEmailRequest("Nowy obóz!", "Zapraszamy", "MARKETING", null));

        assertEquals(1, result.recipientCount());
        verify(userRepository).findAllByMarketingConsentAtIsNotNullOrderByCreatedAtDesc();
        verify(userRepository, never()).findAllByOrderByCreatedAtDesc();
        // A marketing mail gets an unsubscribe token (non-null); a service mail got null.
        verify(adminUserMailService).sendCustomMessage(
                eq("jan@test.com"), eq("Jan"), eq("Nowy obóz!"), eq("Zapraszamy"), notNull());
    }

    @Test
    void shouldThrowWhenAudienceInvalid() {
        assertThrows(IllegalArgumentException.class, () -> service.sendEmail(
                new SendEmailRequest("Temat", "Treść", "WHATEVER", null)));
        verifyNoInteractions(adminUserMailService);
    }

    // --- delete ---

    @Test
    void shouldDeleteUserDelegatingEnrollmentErasure() {
        UUID adminId = UUID.randomUUID();
        regular.setAvatarFilename("avatar.jpg");

        when(userRepository.findById(regularId)).thenReturn(Optional.of(regular));
        when(enrollmentErasureService.eraseForUser(regularId))
                .thenReturn(new pl.fireacademy.domain.enrollment.EnrollmentErasureService.ErasureResult(1, 1, java.util.List.of()));

        DeleteUserResponse result = service.delete(adminId, regularId, true);

        assertEquals(1, result.freedEnrollments());
        assertEquals(1, result.anonymizedEnrollments());
        // Erasure must run BEFORE account deletion (after delete the FK nulls user_id).
        var order = inOrder(enrollmentErasureService, userRepository);
        order.verify(enrollmentErasureService).eraseForUser(regularId);
        order.verify(userRepository).delete(regular);
        verify(fileStorageService).delete("avatars", "avatar.jpg");
        verify(authTokenRepository).deleteAllByUserId(regularId);
        verify(jwtAuthenticationFilter).evictUser(regularId);
    }

    @Test
    void shouldNotifyUserWhenDeletingWithNotify() {
        when(userRepository.findById(regularId)).thenReturn(Optional.of(regular));
        when(enrollmentErasureService.eraseForUser(regularId))
                .thenReturn(new pl.fireacademy.domain.enrollment.EnrollmentErasureService.ErasureResult(0, 0, java.util.List.of()));

        service.delete(UUID.randomUUID(), regularId, true);

        verify(adminUserMailService).sendAccountDeletedNotification(
                eq(regular.getEmail()), eq(regular.getFirstName()), anyList());
    }

    @Test
    void shouldNotNotifyUserWhenNotifyFalse() {
        when(userRepository.findById(regularId)).thenReturn(Optional.of(regular));
        when(enrollmentErasureService.eraseForUser(regularId))
                .thenReturn(new pl.fireacademy.domain.enrollment.EnrollmentErasureService.ErasureResult(0, 0, java.util.List.of()));

        service.delete(UUID.randomUUID(), regularId, false);

        verify(adminUserMailService, never()).sendAccountDeletedNotification(any(), any(), any());
    }

    @Test
    void shouldThrowWhenDeletingSelf() {
        when(userRepository.findById(regularId)).thenReturn(Optional.of(regular));

        assertThrows(IllegalStateException.class, () -> service.delete(regularId, regularId, true));
        verify(userRepository, never()).delete(any());
    }

    @Test
    void shouldThrowWhenDeletingSuperAdmin() {
        User superAdmin = user(UUID.randomUUID(), "admin@fireacademy.test", "Admin", "Env", UserRole.ADMIN);
        when(adminEmailConfig.isAdminEmail("admin@fireacademy.test")).thenReturn(true);
        when(userRepository.findById(superAdmin.getId())).thenReturn(Optional.of(superAdmin));

        assertThrows(IllegalStateException.class, () -> service.delete(UUID.randomUUID(), superAdmin.getId(), true));
        verify(userRepository, never()).delete(any());
    }

    @Test
    void shouldThrowWhenDeletingNonExistentUser() {
        UUID missing = UUID.randomUUID();
        when(userRepository.findById(missing)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.delete(UUID.randomUUID(), missing, true));
    }

    // --- promote ---

    @Test
    void shouldPromoteUserToAdminWhenCallerIsSuperAdmin() {
        User superAdmin = user(UUID.randomUUID(), "admin@fireacademy.test", "Admin", "Env", UserRole.ADMIN);
        when(adminEmailConfig.isAdminEmail("admin@fireacademy.test")).thenReturn(true);
        when(userRepository.findById(superAdmin.getId())).thenReturn(Optional.of(superAdmin));
        when(userRepository.findById(regularId)).thenReturn(Optional.of(regular));

        AdminUserResponse result = service.promote(superAdmin.getId(), regularId);

        assertEquals(UserRole.ADMIN.name(), result.role());
        assertEquals(UserRole.ADMIN, regular.getRole());
        verify(userRepository).save(regular);
        verify(jwtAuthenticationFilter).evictUser(regularId);
    }

    @Test
    void shouldThrowWhenPromoteCallerIsNotSuperAdmin() {
        User plainAdmin = user(UUID.randomUUID(), "boss@test.com", "Boss", "Admin", UserRole.ADMIN);
        when(userRepository.findById(plainAdmin.getId())).thenReturn(Optional.of(plainAdmin));

        assertThrows(IllegalStateException.class,
                () -> service.promote(plainAdmin.getId(), regularId));
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenPromotingExistingAdmin() {
        User superAdmin = user(UUID.randomUUID(), "admin@fireacademy.test", "Admin", "Env", UserRole.ADMIN);
        when(adminEmailConfig.isAdminEmail("admin@fireacademy.test")).thenReturn(true);
        when(userRepository.findById(superAdmin.getId())).thenReturn(Optional.of(superAdmin));
        User admin = user(UUID.randomUUID(), "boss@test.com", "Boss", "Admin", UserRole.ADMIN);
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        assertThrows(IllegalStateException.class, () -> service.promote(superAdmin.getId(), admin.getId()));
        verify(userRepository, never()).save(any());
    }

    // --- demote ---

    @Test
    void shouldDemoteAdminWhenCallerIsSuperAdmin() {
        User superAdmin = user(UUID.randomUUID(), "admin@fireacademy.test", "Admin", "Env", UserRole.ADMIN);
        User targetAdmin = user(UUID.randomUUID(), "boss@test.com", "Boss", "Admin", UserRole.ADMIN);
        when(adminEmailConfig.isAdminEmail("admin@fireacademy.test")).thenReturn(true);
        when(userRepository.findById(superAdmin.getId())).thenReturn(Optional.of(superAdmin));
        when(userRepository.findById(targetAdmin.getId())).thenReturn(Optional.of(targetAdmin));

        AdminUserResponse result = service.demote(superAdmin.getId(), targetAdmin.getId());

        assertEquals(UserRole.USER.name(), result.role());
        assertEquals(UserRole.USER, targetAdmin.getRole());
        verify(jwtAuthenticationFilter).evictUser(targetAdmin.getId());
    }

    @Test
    void shouldThrowWhenDemoteCallerIsNotSuperAdmin() {
        User plainAdmin = user(UUID.randomUUID(), "admin2@test.com", "Zwykły", "Admin", UserRole.ADMIN);
        when(userRepository.findById(plainAdmin.getId())).thenReturn(Optional.of(plainAdmin));

        assertThrows(IllegalStateException.class,
                () -> service.demote(plainAdmin.getId(), UUID.randomUUID()));
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenDemotingSuperAdminTarget() {
        User superAdmin = user(UUID.randomUUID(), "admin@fireacademy.test", "Admin", "Env", UserRole.ADMIN);
        User envTarget = user(UUID.randomUUID(), "admin2@fireacademy.test", "Inny", "Env", UserRole.ADMIN);
        when(adminEmailConfig.isAdminEmail("admin@fireacademy.test")).thenReturn(true);
        when(adminEmailConfig.isAdminEmail("admin2@fireacademy.test")).thenReturn(true);
        when(userRepository.findById(superAdmin.getId())).thenReturn(Optional.of(superAdmin));
        when(userRepository.findById(envTarget.getId())).thenReturn(Optional.of(envTarget));

        assertThrows(IllegalStateException.class,
                () -> service.demote(superAdmin.getId(), envTarget.getId()));
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenDemotingSelf() {
        User superAdmin = user(UUID.randomUUID(), "admin@fireacademy.test", "Admin", "Env", UserRole.ADMIN);
        when(adminEmailConfig.isAdminEmail("admin@fireacademy.test")).thenReturn(true);
        when(userRepository.findById(superAdmin.getId())).thenReturn(Optional.of(superAdmin));

        assertThrows(IllegalStateException.class,
                () -> service.demote(superAdmin.getId(), superAdmin.getId()));
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenDemotingNonAdmin() {
        User superAdmin = user(UUID.randomUUID(), "admin@fireacademy.test", "Admin", "Env", UserRole.ADMIN);
        when(adminEmailConfig.isAdminEmail("admin@fireacademy.test")).thenReturn(true);
        when(userRepository.findById(superAdmin.getId())).thenReturn(Optional.of(superAdmin));
        when(userRepository.findById(regularId)).thenReturn(Optional.of(regular));

        assertThrows(IllegalStateException.class,
                () -> service.demote(superAdmin.getId(), regularId));
        verify(userRepository, never()).save(any());
    }

    private static User user(UUID id, String email, String firstName, String lastName, UserRole role) {
        User user = new User(email, firstName, lastName, null);
        user.setRole(role);
        try {
            Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return user;
    }
}
