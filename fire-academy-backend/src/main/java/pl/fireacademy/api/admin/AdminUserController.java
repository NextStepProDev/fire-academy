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

    public AdminUserController(AdminUserService service, UserRepository userRepository) {
        this.service = service;
        this.userRepository = userRepository;
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

    /** Wyszukiwarka zarejestrowanych użytkowników (do ręcznego dopisywania na treningi). */
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
}
