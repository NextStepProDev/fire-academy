package pl.fireacademy.api.pub;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/marketing")
public class MarketingController {

    private final MarketingService service;

    public MarketingController(MarketingService service) {
        this.service = service;
    }

    public record UnsubscribeRequest(@NotBlank String token) {}

    // Unsubscribe from marketing e-mails straight from the link in the e-mail (without logging in).
    // Idempotent and enumeration-safe — always 204, regardless of the account's existence/state.
    @PostMapping("/unsubscribe")
    public ResponseEntity<Void> unsubscribe(@RequestBody UnsubscribeRequest request) {
        service.unsubscribe(request.token());
        return ResponseEntity.noContent().build();
    }
}
