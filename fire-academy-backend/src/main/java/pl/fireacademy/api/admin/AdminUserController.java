package pl.fireacademy.api.admin;

import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.fireacademy.api.admin.AdminUserDtos.*;
import pl.fireacademy.config.CurrentUserId;
import pl.fireacademy.domain.user.UserRepository;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService service;
    private final UserRepository userRepository;
    private final AdminTrainingPlanErasureService planErasure;

    public AdminUserController(AdminUserService service, UserRepository userRepository,
                               AdminTrainingPlanErasureService planErasure) {
        this.service = service;
        this.userRepository = userRepository;
        this.planErasure = planErasure;
    }

    @GetMapping
    public PagedUsersResponse list(@RequestParam(required = false) @Nullable String search,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "50") int size,
                                   @RequestParam(defaultValue = "created") String sort,
                                   @RequestParam(defaultValue = "desc") String direction) {
        return service.list(search, page, size, sort, direction);
    }

    public record UserSummary(UUID id, String firstName, String lastName, String email) {}

    /** Search box for registered users (for manually adding them to trainings). */
    @GetMapping("/search")
    public List<UserSummary> search(@RequestParam String query) {
        var q = query.trim();
        if (q.length() < 2) {
            return List.of();
        }
        return userRepository.searchByNameOrEmail(q, PageRequest.of(0, 10)).stream()
                .map(u -> new UserSummary(u.getId(), u.getFirstName(), u.getLastName(), u.getEmail()))
                .toList();
    }

    @GetMapping("/{id}")
    public AdminUserDetailResponse getDetail(@PathVariable UUID id) {
        return service.getDetail(id);
    }

    @PostMapping("/email")
    public SendEmailResponse sendEmail(@Valid @RequestBody SendEmailRequest request) {
        return service.sendEmail(request);
    }

    @DeleteMapping("/{id}")
    public DeleteUserResponse delete(@CurrentUserId UUID adminId, @PathVariable UUID id,
                                     @RequestParam(defaultValue = "true") boolean notify) {
        return service.delete(adminId, id, notify);
    }

    @PostMapping("/{id}/logout-all")
    public ResponseEntity<Void> forceLogout(@PathVariable UUID id) {
        service.forceLogout(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/promote")
    public AdminUserResponse promote(@CurrentUserId UUID adminId, @PathVariable UUID id) {
        return service.promote(adminId, id);
    }

    @PostMapping("/{id}/demote")
    public AdminUserResponse demote(@CurrentUserId UUID adminId, @PathVariable UUID id) {
        return service.demote(adminId, id);
    }

    /** Marks the user as a 1-on-1 coaching client — unlocks the personal training calendar. */
    @PostMapping("/{id}/athlete")
    public AdminUserResponse enableAthlete(@PathVariable UUID id) {
        return service.setAthlete(id, true);
    }

    /** Hides the personal training calendar. Non-destructive — the plan and its history survive. */
    @DeleteMapping("/{id}/athlete")
    public AdminUserResponse disableAthlete(@PathVariable UUID id) {
        return service.setAthlete(id, false);
    }

    /**
     * Erases the 1-on-1 plan for good: trainings, weights, goals, comments, photos and the coach's
     * notes about this person. The opposite of the call above, which hides and keeps.
     * <p>
     * Group subscriptions, payments, the account and its camp sign-ups are untouched — a client can
     * finish their personal coaching and carry on attending Wednesday's class. Works even when the
     * athlete flag is already gone; that account is the one whose data would otherwise be stranded.
     */
    @DeleteMapping("/{id}/training-plan")
    public AdminTrainingPlanErasureService.ErasedPlan eraseTrainingPlan(@PathVariable UUID id) {
        return planErasure.erase(id);
    }
}
