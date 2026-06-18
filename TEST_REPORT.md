# Fire Academy — Raport Testów

**Data wygenerowania:** 2026-05-30
**Status:** Wszystkie testy przechodzą (317/317)
**Pokrycie kodu (backend):** 84% instrukcji, 69% gałęzi

---

## Podsumowanie

| Warstwa | Plików testowych | Testów | Framework | Status |
|---------|-----------------|--------|-----------|--------|
| Backend — jednostkowe | 21 | 218 | JUnit 5, Mockito | PASS |
| Backend — integracyjne | 6 | 57 | Testcontainers, MockMvc, PostgreSQL | PASS |
| Frontend — komponenty/utility | 5 | 34 | Vitest, React Testing Library | PASS |
| Frontend — E2E | 1 | 8 | Playwright (Chromium) | PASS* |
| **RAZEM** | **33** | **317** | | |

*E2E wymaga uruchomionego dev servera

---

## Pokrycie kodu (JaCoCo — Backend)

| Pakiet | Instrukcje | Gałęzie | Uwagi |
|--------|-----------|---------|-------|
| infrastructure.mail | 100% | 100% | AuthMailService, EnrollmentMailService |
| domain.enrollment | 100% | 75% | Enrollment entity, anonymize |
| api.file | 100% | 100% | FileController |
| infrastructure.scheduler | 100% | 100% | Token cleanup |
| infrastructure.i18n | 100% | 100% | MessageService |
| api.auth | 98% | 95% | AuthController + AuthService |
| domain.instructor | 96% | n/a | Instructor entity |
| infrastructure.security | 92% | 85% | JwtService, JwtAuthenticationFilter |
| domain.event | 92% | 100% | Event, EventType, EventTypePhoto |
| domain.user | 92% | 100% | User entity + lockout |
| api.admin | 89% | 73% | CRUD kontrolery + serwisy |
| api.user | 89% | 87% | UserController + UserService |
| api.pub | 84% | 68% | PublicController, OgController |
| api (exception handler) | 77% | n/a | GlobalExceptionHandler |
| infrastructure.storage | 76% | 90% | LocalFileStorageService |
| domain.auth | 70% | 50% | AuthToken entity |
| config | 58% | 39% | SecurityConfig, OAuth2, Flyway |
| api.dev | 0% | 0% | DevController (profil dev-only) |
| **TOTAL** | **84%** | **69%** | |

**Próg JaCoCo:** minimum 60% (enforced w `build.gradle`)

---

## Backend — Testy jednostkowe (220 testów)

### JwtServiceTest (15 testów)
Plik: `infrastructure/security/JwtServiceTest.java`
Testowany: `JwtService` — generowanie, walidacja i parsowanie tokenów JWT

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | shouldGenerateValidAccessToken | Generowanie access tokenu |
| 2 | shouldGenerateValidRefreshToken | Generowanie refresh tokenu |
| 3 | shouldExtractUserIdFromToken | Ekstrakcja userId z JWT |
| 4 | shouldExtractEmailFromToken | Ekstrakcja emaila z JWT |
| 5 | shouldRejectInvalidToken | Odrzucenie nieprawidłowego tokenu |
| 6 | shouldRejectTokenWithWrongSecret | Odrzucenie tokenu z innym kluczem |
| 7 | shouldRejectExpiredToken | Odrzucenie wygasłego tokenu |
| 8 | shouldReturnFalseForIsAccessTokenWhenInvalidToken | isAccessToken na złym tokenie |
| 9 | shouldReturnFalseForIsRefreshTokenWhenInvalidToken | isRefreshToken na złym tokenie |
| 10 | shouldGenerateUniqueSecureTokens | Unikalność secure tokenów |
| 11 | shouldHashTokenConsistently | Determinizm hashowania SHA-256 |
| 12 | shouldProduceDifferentHashesForDifferentTokens | Różne hashe dla różnych tokenów |
| 13 | shouldReturnCorrectExpirationSeconds | Poprawny czas wygaśnięcia |
| 14 | shouldReturnCorrectRefreshExpirationMs | Czas refresh tokenu |
| 15 | shouldGenerateTokenForAdminUser | Token dla roli ADMIN |

### JwtConfigTest (6 testów)
Plik: `infrastructure/security/JwtConfigTest.java`
Testowany: `JwtConfig` — walidacja konfiguracji JWT

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | shouldAcceptValidSecret | Prawidłowy secret (32+ znaków) |
| 2 | shouldRejectNullSecret | Null secret → wyjątek |
| 3 | shouldRejectBlankSecret | Pusty secret → wyjątek |
| 4 | shouldRejectShortSecret | Krótki secret → wyjątek |
| 5 | shouldAcceptExactly32CharSecret | Dokładnie 32 znaki — minimum |
| 6 | shouldStoreExpirationValues | Ustawianie wartości expiration |

### AuthServiceTest (32 testy)
Plik: `api/auth/AuthServiceTest.java`
Testowany: `AuthService` — rejestracja, logowanie, weryfikacja, reset hasła, refresh, logout

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | shouldRegisterNewUser | Rejestracja nowego użytkownika |
| 2 | shouldThrowWhenEmailAlreadyExists | Duplikat emaila → błąd |
| 3 | shouldAutoPromoteAdminOnRegister | Auto-awans admina przy rejestracji |
| 4 | shouldDefaultLanguageToPlWhenNull | Domyślny język → pl |
| 5 | shouldDefaultLanguageToPlWhenUnsupported | Nieobsługiwany język → pl |
| 6 | shouldLoginSuccessfully | Poprawne logowanie |
| 7 | shouldThrowWhenUserNotFound | Nieistniejący user → błąd |
| 8 | shouldThrowWhenOAuthUserTriesToLoginWithPassword | OAuth user + hasło → błąd |
| 9 | shouldThrowWhenAccountLocked | Zablokowane konto → błąd |
| 10 | shouldIncrementFailedAttemptsOnWrongPassword | Inkrementacja prób przy złym haśle |
| 11 | shouldLockAccountAfterFiveFailedAttempts | 5 prób → blokada 15 min |
| 12 | shouldThrowWhenEmailNotVerified | Niezweryfikowany email → błąd |
| 13 | shouldResetFailedAttemptsOnSuccessfulLogin | Reset prób po udanym logowaniu |
| 14 | shouldAutoPromoteAdminOnLogin | Auto-awans admina przy logowaniu |
| 15 | shouldVerifyEmailSuccessfully | Weryfikacja emaila tokenem |
| 16 | shouldThrowWhenVerificationTokenInvalid | Nieprawidłowy token → błąd |
| 17 | shouldResendVerificationEmail | Ponowne wysłanie weryfikacji |
| 18 | shouldReturnSuccessForNonExistentEmailOnResend | Bezpieczeństwo: brak ujawniania emaili |
| 19 | shouldReturnAlreadyVerifiedMessage | Już zweryfikowany email |
| 20 | shouldThrowWhenResendCooldownActive | Cooldown 1 min → błąd |
| 21 | shouldSendPasswordResetEmail | Wysłanie emaila resetu hasła |
| 22 | shouldReturnSuccessForNonExistentEmailOnForgotPassword | Bezpieczeństwo: forgot password |
| 23 | shouldReturnSuccessForOAuthUserOnForgotPassword | OAuth user → brak resetu |
| 24 | shouldThrowWhenForgotPasswordCooldownActive | Cooldown resetu → błąd |
| 25 | shouldResetPasswordSuccessfully | Reset hasła + unieważnienie refresh tokenów |
| 26 | shouldThrowWhenResetTokenInvalid | Nieprawidłowy token resetu |
| 27 | shouldRefreshTokensSuccessfully | Odświeżenie tokenów (rotacja) |
| 28 | shouldThrowWhenRefreshTokenInvalid | Nieprawidłowy refresh token |
| 29 | shouldThrowWhenRefreshTokenIsNotRefreshType | Access token jako refresh → błąd |
| 30 | shouldThrowWhenRefreshTokenRevoked | Unieważniony refresh token |
| 31 | shouldLogoutSuccessfully | Logout — unieważnienie refresh |
| 32 | shouldHandleLogoutWithNonExistentToken | Logout z nieznanym tokenem |

### PublicServiceTest (15 testów)
Plik: `api/pub/PublicServiceTest.java`
Testowany: `PublicService` — publiczne dane + zapis na wydarzenie

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | shouldReturnActiveInstructors | Lista aktywnych instruktorów z URL zdjęć |
| 2 | shouldReturnNullPhotoUrlWhenNoPhoto | Brak zdjęcia → null URL |
| 3 | shouldReturnActiveEventTypes | Lista aktywnych rodzajów wydarzeń |
| 4 | shouldReturnUpcomingEventsWithAvailableSpots | Kalkulacja wolnych miejsc |
| 5 | shouldReturnMinusOneAvailableSpotsWhenNoMaxParticipants | Brak limitu → -1 |
| 6 | shouldReturnZeroAvailableSpotsWhenFull | Pełne wydarzenie → 0 |
| 7 | shouldReturnInstructorById | Pobranie instruktora po ID |
| 8 | shouldThrowWhenInstructorNotActive | Nieaktywny instruktor → błąd |
| 9 | shouldEnrollSuccessfully | Poprawny zapis + maile |
| 10 | shouldThrowWhenEventNotFound | Nieistniejące wydarzenie → błąd |
| 11 | shouldThrowWhenEventInactive | Nieaktywne wydarzenie → błąd |
| 12 | shouldThrowWhenEnrollingLessThan24HoursBeforeEvent | Zapis <24h przed → błąd |
| 13 | shouldThrowWhenDuplicateEnrollment | Duplikat zapisu → błąd |
| 14 | shouldThrowWhenEventFull | Pełne wydarzenie → błąd |
| 15 | shouldAllowEnrollmentWhenNoMaxParticipants | Brak limitu → OK |

### AdminInstructorServiceTest (14 testów)
Plik: `api/admin/AdminInstructorServiceTest.java`
Testowany: `AdminInstructorService` — CRUD kadry

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | shouldReturnAllInstructors | Lista wszystkich instruktorów |
| 2 | shouldCreateInstructorWithNextDisplayOrder | Auto-increment kolejności |
| 3 | shouldCreateFirstInstructorWithOrderZero | Pierwszy instruktor → order 0 |
| 4 | shouldUpdateInstructor | Aktualizacja danych instruktora |
| 5 | shouldThrowWhenUpdatingNonExistentInstructor | Nieistniejący → błąd |
| 6 | shouldDeleteInstructorAndPhoto | Usunięcie + kasowanie pliku zdjęcia |
| 7 | shouldDeleteInstructorWithoutPhoto | Usunięcie bez zdjęcia |
| 8 | shouldUploadPhotoAndDeleteOld | Upload nowego zdjęcia + kasowanie starego |
| 9 | shouldUploadFirstPhoto | Pierwsze zdjęcie — brak starego do kasowania |
| 10 | shouldToggleActiveStatus | Przełączanie active/inactive |
| 11 | shouldReorderUp | Przesunięcie w górę |
| 12 | shouldReorderDown | Przesunięcie w dół |
| 13 | shouldNotReorderWhenAlreadyFirst | Już pierwszy → brak zmiany |
| 14 | shouldNotReorderWhenAlreadyLast | Już ostatni → brak zmiany |

### AdminEventServiceTest (11 testów)
Plik: `api/admin/AdminEventServiceTest.java`
Testowany: `AdminEventService` — CRUD wydarzeń

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | shouldGetAllEventsByCategory | Lista wydarzeń z enrollment count |
| 2 | shouldCreateEventWithEventType | Tworzenie z powiązanym rodzajem |
| 3 | shouldCreateEventWithCustomName | Tworzenie z niestandardową nazwą |
| 4 | shouldThrowWhenCreatingEventInThePast | Data w przeszłości → błąd |
| 5 | shouldThrowWhenNoEventTypeIdAndNoCustomName | Brak nazwy → błąd |
| 6 | shouldThrowWhenCustomNameIsBlank | Pusta nazwa → błąd |
| 7 | shouldUpdateEventAndSendNotifications | Aktualizacja + notyfikacje do zapisanych |
| 8 | shouldNotSendNotificationsWhenNoChanges | Brak zmian → brak maili |
| 9 | shouldToggleEventActive | Przełączanie aktywności |
| 10 | shouldDeleteEventWithoutEnrollments | Usunięcie bez zapisów |
| 11 | shouldThrowWhenDeletingEventWithEnrollments | Usunięcie z zapisami → błąd |

### AdminEventTypeServiceTest (12 testów)
Plik: `api/admin/AdminEventTypeServiceTest.java`
Testowany: `AdminEventTypeService` — CRUD rodzajów wydarzeń

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | shouldGetAllByCategory | Lista rodzajów per kategoria |
| 2 | shouldCreateEventType | Tworzenie nowego rodzaju |
| 3 | shouldSetNextDisplayOrderOnCreate | Auto-increment kolejności |
| 4 | shouldUpdateEventType | Aktualizacja nazwy/opisu |
| 5 | shouldDeleteEventTypeAndConvertLinkedEvents | Cascade: linked events → customName |
| 6 | shouldUploadThumbnailAndDeleteOld | Upload miniaturki |
| 7 | shouldAddPhoto | Dodanie zdjęcia do galerii |
| 8 | shouldDeletePhoto | Usunięcie zdjęcia z galerii |
| 9 | shouldThrowWhenPhotoNotFound | Nieistniejące zdjęcie → błąd |
| 10 | shouldToggleActive | Przełączanie aktywności |
| 11 | shouldReorderEventTypes | Zmiana kolejności rodzajów |
| 12 | shouldReorderPhotos | Zmiana kolejności zdjęć w galerii |

### AdminEnrollmentServiceTest (10 testów)
Plik: `api/admin/AdminEnrollmentServiceTest.java`
Testowany: `AdminEnrollmentService` — zarządzanie zapisami

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | shouldGetEnrollmentsByEvent | Lista zapisów per wydarzenie |
| 2 | shouldGetEnrollmentsByCategory | Lista zapisów per kategoria |
| 3 | shouldAdminEnroll | Zapis przez admina + maile |
| 4 | shouldThrowWhenAdminEnrollEventNotFound | Nieistniejące wydarzenie → błąd |
| 5 | shouldDeleteEnrollmentAndSendNotifications | Usunięcie + notyfikacja user + admin |
| 6 | shouldThrowWhenDeletingNonExistentEnrollment | Nieistniejący zapis → błąd |
| 7 | shouldSearchByEmail | Wyszukiwanie po emailu |
| 8 | shouldTrimEmailOnSearch | Trimowanie spacji w emailu |
| 9 | shouldAnonymizeByEmail | RODO: anonimizacja danych |
| 10 | shouldReturnZeroWhenNoEnrollmentsToAnonymize | Brak danych do anonimizacji |

### UserServiceTest (12 testów)
Plik: `api/user/UserServiceTest.java`
Testowany: `UserService` — profil użytkownika

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | shouldGetCurrentUser | Pobranie profilu |
| 2 | shouldThrowWhenUserNotFound | Nieistniejący user → błąd |
| 3 | shouldUpdateUserProfile | Aktualizacja imienia/nazwiska/telefonu |
| 4 | shouldChangePassword | Zmiana hasła |
| 5 | shouldThrowWhenChangingPasswordForOAuthUser | OAuth user → brak hasła → błąd |
| 6 | shouldThrowWhenCurrentPasswordInvalid | Złe aktualne hasło → błąd |
| 7 | shouldDeleteAccountWithPasswordVerification | Usunięcie konta z potwierdzeniem hasła |
| 8 | shouldDeleteOAuthAccountWithoutPassword | Usunięcie konta OAuth (bez hasła) |
| 9 | shouldThrowWhenDeletePasswordInvalid | Złe hasło przy usuwaniu → błąd |
| 10 | shouldThrowWhenDeleteWithNullPasswordForPasswordUser | Null hasło → błąd |
| 11 | shouldUpdateNotifications | Aktualizacja ustawień powiadomień |
| 12 | shouldReturnAdminFlagForAdminUser | Flaga isAdmin dla ADMIN |

### RateLimitFilterTest (7 testów)
Plik: `config/RateLimitFilterTest.java`
Testowany: `RateLimitFilter` — rate limiting per IP/endpoint

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | shouldAllowRequestBelowLimit | Żądanie poniżej limitu → OK |
| 2 | shouldBlockAuthRequestsAfterLimit | 16. żądanie auth → 429 |
| 3 | shouldPassThroughPublicEndpoints | Publiczne endpointy → brak limitu |
| 4 | shouldTrackDifferentBucketsSeparately | auth/user/admin → osobne liczniki |
| 5 | shouldUseXForwardedForHeader | IP z X-Forwarded-For |
| 6 | shouldAllowHigherLimitForAdminEndpoints | Admin: 60 req/min |
| 7 | shouldReturnJsonErrorResponse | Format JSON odpowiedzi 429 |

### AdminEmailConfigTest (10 testów)
Plik: `config/AdminEmailConfigTest.java`
Testowany: `AdminEmailConfig` — parsowanie emaili adminów

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | shouldParseMultipleAdminEmails | CSV: "a@x.com, b@x.com" |
| 2 | shouldNormalizeTolowercase | Case-insensitive |
| 3 | shouldTrimWhitespace | Trimowanie spacji |
| 4 | shouldHandleEmptyConfig | Pusty config |
| 5 | shouldHandleNullConfig | Null config |
| 6 | shouldHandleBlankConfig | Whitespace-only config |
| 7 | shouldReturnFalseForNullEmail | isAdminEmail(null) → false |
| 8 | shouldReturnFalseForNonAdminEmail | Nie-admin email → false |
| 9 | shouldHandleSingleEmail | Jeden email |
| 10 | shouldFilterEmptyEntriesInList | "a@x.com,,, ,b@x.com" |

### LocalFileStorageServiceTest (14 testów)
Plik: `infrastructure/storage/LocalFileStorageServiceTest.java`
Testowany: `LocalFileStorageService` — zapis/odczyt plików

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | shouldStoreFileSuccessfully | Zapis JPEG |
| 2 | shouldStorePngFile | Zapis PNG |
| 3 | shouldStoreWebpFile | Zapis WebP |
| 4 | shouldRejectDisallowedContentType | PDF → błąd |
| 5 | shouldRejectNullContentType | Null content-type → błąd |
| 6 | shouldRejectDisallowedExtension | .gif → błąd |
| 7 | shouldRejectFileWithoutExtension | Brak rozszerzenia → błąd |
| 8 | shouldDeleteFile | Kasowanie pliku |
| 9 | shouldNotThrowWhenDeletingNonExistentFile | Kasowanie nieistniejącego → OK |
| 10 | shouldReturnFileInputStream | Odczyt strumienia |
| 11 | shouldReturnFileSize | Rozmiar pliku |
| 12 | shouldReturnFalseForNonExistentFile | exists() → false |
| 13 | shouldCreateDirectoriesAutomatically | Auto-tworzenie folderów |
| 14 | shouldGenerateUniqueFilenames | UUID w nazwie pliku |

### AuthMailServiceTest (5 testów)
Plik: `infrastructure/mail/AuthMailServiceTest.java`
Testowany: `AuthMailService` — maile auth

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | shouldSendVerificationEmail | Mail weryfikacyjny |
| 2 | shouldSendWelcomeEmail | Mail powitalny |
| 3 | shouldSendPasswordResetEmail | Mail resetu hasła |
| 4 | shouldSendPasswordChangedNotification | Notyfikacja o zmianie hasła |
| 5 | shouldHandleMailException | SMTP error → brak wyjątku |

### EnrollmentMailServiceTest (11 testów)
Plik: `infrastructure/mail/EnrollmentMailServiceTest.java`
Testowany: `EnrollmentMailService` — maile zapisy

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | shouldSendEnrollmentConfirmation | Potwierdzenie zapisu (z lokalizacją) |
| 2 | shouldSendEnrollmentConfirmationWithoutLocation | Potwierdzenie bez lokalizacji |
| 3 | shouldSendEnrollmentNotification | Notyfikacja do admina |
| 4 | shouldSendEventModificationNotification | Mail o zmianie terminu |
| 5 | shouldSendEventModificationAdminNotification | Zmiana terminu → wielu adminów |
| 6 | shouldSendEnrollmentDeletionNotification | Usunięcie zapisu → mail |
| 7 | shouldSendEnrollmentDeletionAdminNotification | Usunięcie → admin mail |
| 8 | shouldSendAdminEnrollmentConfirmation | Zapis przez admina → mail |
| 9 | shouldSendAdminEnrollmentNotification | Zapis admin → notyfikacja |
| 10 | shouldHandleMailExceptionGracefully | SMTP error → brak wyjątku |
| 11 | shouldNotSendNotificationWhenNoAdminEmails | Brak adminów → brak maili |

### MessageServiceTest (5 testów)
Plik: `infrastructure/i18n/MessageServiceTest.java`
Testowany: `MessageService` — internacjonalizacja

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | shouldGetMessageWithDefaultLocale | Wiadomość z domyślnym locale |
| 2 | shouldGetMessageWithArgs | Wiadomość z parametrami |
| 3 | shouldGetMessageWithSpecificLocale | Wiadomość z konkretnym locale |
| 4 | shouldGetMessageForLanguage | getForLang("pl") |
| 5 | shouldDefaultToPolishWhenLanguageIsNull | null → pl |

### TokenCleanupSchedulerTest (2 testy)
Plik: `infrastructure/scheduler/TokenCleanupSchedulerTest.java`
Testowany: `TokenCleanupScheduler` — czyszczenie wygasłych tokenów

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | shouldDeleteExpiredTokens | Usunięcie wygasłych tokenów |
| 2 | shouldHandleNoExpiredTokens | Brak do usunięcia → OK |

### UserTest (15 testów)
Plik: `domain/user/UserTest.java`
Testowany: `User` — encja użytkownika

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | shouldReturnFullName | "Jan" + "Kowalski" → "Jan Kowalski" |
| 2 | shouldNotBeLocked | Nowy user → nie zablokowany |
| 3 | shouldIncrementFailedAttempts | 1 próba → failedAttempts=1 |
| 4 | shouldLockAccountAfterFiveFailedAttempts | 5 prób → locked 15 min |
| 5 | shouldNotLockAfterFourFailedAttempts | 4 próby → nie zablokowany |
| 6 | shouldResetFailedAttempts | Reset → 0 prób, null lockedUntil |
| 7 | shouldNotBeLockedWhenLockExpired | lockedUntil w przeszłości → false |
| 8 | shouldReturnRemainingLockoutMinutes | Minuty do odblokowania |
| 9 | shouldReturnZeroMinutesWhenNotLocked | Nie zablokowany → 0 |
| 10 | shouldReturnZeroMinutesWhenLockExpired | Wygasła blokada → 0 |
| 11 | shouldMarkEmailVerified | markEmailVerified() → true + timestamp |
| 12 | shouldDetectPasswordPresence | hasPassword() |
| 13 | shouldDetectAdmin | isAdmin() |
| 14 | shouldDefaultToPolish | preferredLanguage → "pl" |
| 15 | shouldDefaultToEmailNotificationsEnabled | emailNotifications → true |

### EnrollmentTest (4 testy)
Plik: `domain/enrollment/EnrollmentTest.java`
Testowany: `Enrollment` — encja zapisu + RODO

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | shouldNotBeAnonymizedInitially | Nowy zapis → nie zanonimizowany |
| 2 | shouldAnonymizeData | anonymize() → "Dane usunięte", @usuniety.rodo |
| 3 | shouldPreserveAdminFlag | addedByAdmin zachowany |
| 4 | shouldReturnBasicProperties | Gettery |

### EventTest (6 testów)
Plik: `domain/event/EventTest.java`
Testowany: `Event` — encja wydarzenia

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | shouldReturnEventTypeNameAsDisplayName | eventType.getName() jako displayName |
| 2 | shouldReturnCustomNameAsDisplayName | customName jako displayName |
| 3 | shouldConvertToCustomName | convertToCustomName() — kasuje eventType |
| 4 | shouldSetEventType | setEventType() — kasuje customName |
| 5 | shouldDefaultToActive | Nowe wydarzenie → active=true |
| 6 | shouldToggleActive | setActive(false) |

---

## Backend — Testy integracyjne (57 testów)

Infrastruktura: `BaseIntegrationTest` z singleton PostgreSQL Testcontainer, `@Sql("/cleanup.sql")` przed każdym testem.

### SecurityIntegrationTest (10 testów)
Plik: `api/SecurityIntegrationTest.java`
Testowane: reguły dostępu, role, JSON error responses

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | shouldAllowPublicEndpointsWithoutAuth | /api/public → 200 bez tokenu |
| 2 | shouldAllowAuthEndpointsWithoutAuth | /api/auth → dostępne bez tokenu |
| 3 | shouldReject401ForUserEndpointWithoutToken | /api/user → 401 |
| 4 | shouldReject401ForAdminEndpointWithoutToken | /api/admin → 401 |
| 5 | shouldReject403ForAdminEndpointWithUserToken | USER → /api/admin → 403 |
| 6 | shouldAllowAdminEndpointWithAdminToken | ADMIN → /api/admin → 200 |
| 7 | shouldAllowUserEndpointWithUserToken | USER → /api/user → 200 |
| 8 | shouldRejectInvalidJwtToken | Złe JWT → 401 |
| 9 | shouldAllowAdminToAccessUserEndpoints | ADMIN → /api/user → 200 |
| 10 | shouldReturnJsonErrorForUnauthorized | JSON z code/message/timestamp |

### AuthControllerIntegrationTest (12 testów)
Plik: `api/auth/AuthControllerIntegrationTest.java`
Testowane: pełny auth flow HTTP

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | shouldRegisterNewUser | POST /register → 200, user w bazie |
| 2 | shouldRejectDuplicateEmail | Duplikat → 400 |
| 3 | shouldRejectInvalidRegistration | Walidacja DTO → 400 VALIDATION_ERROR |
| 4 | shouldLoginWithVerifiedUser | POST /login → JWT tokens |
| 5 | shouldRejectLoginWithWrongPassword | Złe hasło → 400 |
| 6 | shouldRejectLoginWithUnverifiedEmail | Niezweryfikowany → 409 |
| 7 | shouldVerifyEmail | POST /verify-email?token= → 200 |
| 8 | shouldRejectInvalidVerificationToken | Zły token → 400 |
| 9 | shouldRefreshTokens | POST /refresh → nowe tokeny |
| 10 | shouldLogout | POST /logout → 204 |
| 11 | shouldAutoPromoteAdminEmailOnRegister | admin@fireacademy.test → ADMIN |
| 12 | shouldHandleForgotPasswordForNonExistentEmail | Nieistniejący email → 200 (security) |

### PublicControllerIntegrationTest (8 testów)
Plik: `api/pub/PublicControllerIntegrationTest.java`
Testowane: publiczne endpointy + enrollment

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | shouldGetActiveInstructors | GET /instructors?category=TRAINING |
| 2 | shouldGetActiveEventTypes | GET /event-types?category=CAMP |
| 3 | shouldGetUpcomingEvents | GET /events?category=TRAINING |
| 4 | shouldGetInstructorById | GET /instructors/{id} |
| 5 | shouldReturn400ForInactiveInstructor | Nieaktywny → 400 |
| 6 | shouldEnrollSuccessfully | POST /events/{id}/enroll → 201 |
| 7 | shouldRejectDuplicateEnrollment | Duplikat → 409 |
| 8 | shouldRejectEnrollmentWithInvalidData | Walidacja → 400 |

### AdminControllerIntegrationTest (12 testów)
Plik: `api/admin/AdminControllerIntegrationTest.java`
Testowane: CRUD admin z autoryzacją

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | shouldCreateInstructor | POST /admin/instructors → 201 |
| 2 | shouldUpdateInstructor | PUT /admin/instructors/{id} |
| 3 | shouldDeleteInstructor | DELETE → 204 |
| 4 | shouldToggleInstructorActive | PATCH /toggle-active |
| 5 | shouldCreateEventWithCustomName | POST /admin/events |
| 6 | shouldRejectEventInThePast | Data przeszła → 400 |
| 7 | shouldDeleteEventWithoutEnrollments | DELETE → 204 |
| 8 | shouldRejectDeleteEventWithEnrollments | Ma zapisy → 409 |
| 9 | shouldCreateEventType | POST /admin/event-types → 201 |
| 10 | shouldToggleEventTypeActive | PATCH /toggle-active |
| 11 | shouldAdminEnroll | POST /admin/enrollments → 201 |
| 12 | shouldGetEnrollmentsByCategory | GET /by-category |

### UserControllerIntegrationTest (8 testów)
Plik: `api/user/UserControllerIntegrationTest.java`
Testowane: profil użytkownika z autoryzacją

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | shouldGetCurrentUserProfile | GET /user/me → profil |
| 2 | shouldUpdateUserProfile | PUT /user/me |
| 3 | shouldRejectUpdateWithInvalidData | Walidacja → 400 |
| 4 | shouldChangePassword | PUT /user/me/password |
| 5 | shouldRejectWrongCurrentPassword | Złe hasło → 400 |
| 6 | shouldDeleteAccount | DELETE /user/me → 204 |
| 7 | shouldUpdateNotificationSettings | PUT /user/me/notifications |
| 8 | shouldReturnAdminProfileForAdminUser | ADMIN profil |

### OgControllerIntegrationTest (7 testów)
Plik: `api/pub/OgControllerIntegrationTest.java`
Testowane: Open Graph meta tagi

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | shouldReturnOgHtmlForHome | GET /og/ → HTML z og:title |
| 2 | shouldReturnOgForEventType | og:title z nazwą rodzaju |
| 3 | shouldReturnOgForEvent | og:description z datą i lokalizacją |
| 4 | shouldReturnOgForInstructor | og:title z imieniem trenera |
| 5 | shouldReturn404ForInvalidCategorySlug | Nieprawidłowy slug → 404 |
| 6 | shouldEscapeHtmlInOgOutput | XSS: `<script>` → `&lt;script&gt;` |
| 7 | shouldIncludeMetaRefreshRedirect | meta http-equiv="refresh" |

### FileControllerIntegrationTest (11 testów)
Plik: `api/file/FileControllerIntegrationTest.java`
Testowane: serwowanie plików + walidacja

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | shouldServeExistingFile | GET /api/files/instructors/{uuid}.jpg → 200 |
| 2 | shouldReturn404ForNonExistentFile | Nieistniejący → 404 |
| 3 | shouldRejectInvalidFolderName | UPPERCASE → 400 |
| 4 | shouldRejectFolderWithNumbers | folder123 → 400 |
| 5 | shouldRejectFolderWithSpecialChars | fold-er → 400 |
| 6 | shouldRejectInvalidFilename | not-a-uuid.jpg → 400 |
| 7 | shouldRejectUnsupportedExtension | .gif → 400 |
| 8 | shouldRejectPathTraversal | ../../../etc/passwd → 400 |
| 9 | shouldSetCacheControlHeader | max-age=604800 (7 dni) |
| 10 | shouldDetectPngContentType | .png → image/png |
| 11 | shouldDetectWebpContentType | .webp → image/webp |

---

## Frontend — Testy komponentów i utility (34 testy)

Framework: Vitest + React Testing Library + jsdom

### categorySlug.test.ts (7 testów)
Plik: `utils/categorySlug.test.ts`
Testowane: mapowanie slug ↔ kategoria

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | should map treningi to TRAINING | slugToCategory |
| 2 | should map obozy to CAMP | slugToCategory |
| 3 | should map szkolenia to COURSE | slugToCategory |
| 4 | should return undefined for unknown slug | Nieznany slug |
| 5 | should map TRAINING to treningi | categoryToSlug |
| 6 | should map CAMP to obozy | categoryToSlug |
| 7 | should map COURSE to szkolenia | categoryToSlug |

### dates.test.ts (5 testów)
Plik: `utils/dates.test.ts`
Testowane: formatowanie dat po polsku

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | should format ISO date to Polish format | "2026-05-30" → "30 maj 2026" |
| 2 | should format another date | "2026-01-15" → "15 sty 2026" |
| 3 | should format single date when no end date | Bez daty końcowej |
| 4 | should format same month range | "10 – 15 lip 2026" |
| 5 | should format cross-month range | "28 cze – 5 lip 2026" |

### tokenStorage.test.ts (7 testów)
Plik: `utils/tokenStorage.test.ts`
Testowane: zarządzanie tokenami w localStorage

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | should save and retrieve tokens | saveTokens → getAccessToken |
| 2 | should return null when no tokens saved | Brak tokenów → null |
| 3 | should detect non-expired token | isAccessTokenExpired → false |
| 4 | should detect expired token | expiresIn=0 → expired |
| 5 | should return expired when no expiry saved | Brak danych → expired |
| 6 | should clear all tokens | clearTokens() |
| 7 | should detect hasTokens correctly | hasTokens() |

### ShareButton.test.tsx (6 testów)
Plik: `components/ui/ShareButton.test.tsx`
Testowany: `ShareButton` — udostępnianie (Facebook/WhatsApp/kopiuj)

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | should render share button | Przycisk z aria-label |
| 2 | should open dropdown on click | Klik → Facebook/WhatsApp/Kopiuj |
| 3 | should close dropdown on second click | Drugi klik → zamknięcie |
| 4 | should open Facebook share in new window | window.open z facebook.com |
| 5 | should open WhatsApp share | window.open z wa.me |
| 6 | should show copy link option in dropdown | "Kopiuj link" widoczny |

### EnrollmentModal.test.tsx (9 testów)
Plik: `components/events/EnrollmentModal.test.tsx`
Testowany: `EnrollmentModal` — formularz zapisu na wydarzenie

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | should render form when open | Formularz: imię, nazwisko, email, telefon |
| 2 | should not render when closed | isOpen=false → brak renderowania |
| 3 | should show validation errors on empty submit | Puste pola → 5 błędów walidacji |
| 4 | should show error for short name on blur | "Ab" → "Minimum 3 znaki" |
| 5 | should show error for invalid email on blur | "not-an-email" → błąd |
| 6 | should show error for invalid phone on blur | "abc" → "Nieprawidłowy numer" |
| 7 | should submit form with valid data | Poprawne dane → publicApi.enroll() |
| 8 | should show success after enrollment | Po zapisie → "Zapisano!" |
| 9 | should show server error on failure | API error → wyświetlenie komunikatu |

---

## Frontend — Testy E2E (8 testów)

Framework: Playwright (Chromium headless)
Plik: `e2e/golden-path.spec.ts`
Uruchamianie: `npm run test:e2e`

| # | Test | Co weryfikuje |
|---|------|--------------|
| 1 | should navigate from home to category page | Strona główna → /treningi |
| 2 | should load trainings page | /treningi → OK |
| 3 | should load camps page | /obozy → OK |
| 4 | should load courses page | /szkolenia → OK |
| 5 | should navigate via navbar | Navbar: min. 3 linki |
| 6 | should show footer with links | Footer widoczny |
| 7 | should show privacy policy page | /polityka-prywatnosci → nagłówek |
| 8 | should redirect /admin to login | /admin → /admin/login |

---

## Komendy uruchamiania

```bash
# Backend — wszystkie testy (unit + integracyjne)
cd fire-academy-backend && ./gradlew test

# Backend — z weryfikacją pokrycia (min. 60%)
cd fire-academy-backend && ./gradlew check

# Backend — konkretna klasa
cd fire-academy-backend && ./gradlew test --tests "AuthServiceTest"

# Frontend — testy Vitest (unit + komponenty)
cd fire-academy-frontend && npm test

# Frontend — tryb watch
cd fire-academy-frontend && npm run test:watch

# Frontend — E2E (wymaga uruchomionego backendu)
cd fire-academy-frontend && npm run test:e2e
```

---

## Nietestowane obszary

| Obszar | Powód |
|--------|-------|
| `api.dev` (DevAuthController, DevDataSeeder) | Tooling dev-only, profil `dev` |
| `SecurityConfig` — gałęzie OAuth2 | Opcjonalny profil `oauth2` |
| `FlywayConfig` | Konfiguracja Springa, pokryta pośrednio przez testy integracyjne |
| Frontend: strony (HomePage, AdminPage, itp.) | Wymagają rozbudowanego mockowania routera + API |
| Frontend: AuthContext, ThemeContext | Stan globalny — do pokrycia w przyszłości |
