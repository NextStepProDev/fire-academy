package pl.fireacademy.api.dev;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.domain.user.UserRepository;
import pl.fireacademy.domain.user.UserRole;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/dev")
@Profile("dev")
public class DevAuthController {
    private final UserRepository userRepository;

    public DevAuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(HttpServletRequest request,
                                                      @RequestParam(defaultValue = "false") boolean asAdmin) {
        String email = asAdmin ? "admin@dev.local" : "user@dev.local";
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User u = new User(email, asAdmin ? "Admin" : "User", "Dev", null);
            u.setRole(asAdmin ? UserRole.ADMIN : UserRole.USER);
            u.markEmailVerified();
            return userRepository.save(u);
        });
        HttpSession session = request.getSession(true);
        session.setAttribute("DEV_USER_ID", user.getId());
        return ResponseEntity.ok(Map.of("userId", user.getId(), "email", user.getEmail(), "role", user.getRole().name()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/session")
    public ResponseEntity<Map<String, Object>> session(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return ResponseEntity.ok(Map.of("authenticated", false));
        Object userId = session.getAttribute("DEV_USER_ID");
        if (userId == null) return ResponseEntity.ok(Map.of("authenticated", false));
        return userRepository.findById((UUID) userId)
            .map(u -> ResponseEntity.ok(Map.of("authenticated", (Object) true, "userId", u.getId(), "email", u.getEmail(), "role", u.getRole().name())))
            .orElse(ResponseEntity.ok(Map.of("authenticated", false)));
    }

    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> users() {
        List<Map<String, Object>> list = userRepository.findAll().stream()
            .map(u -> Map.<String, Object>of("id", u.getId(), "email", u.getEmail(), "role", u.getRole().name(), "verified", u.isEmailVerified()))
            .toList();
        return ResponseEntity.ok(list);
    }
}
