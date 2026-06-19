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

    // Rezygnacja z maili marketingowych prosto z linku w mailu (bez logowania).
    // Idempotentne i bezpieczne dla enumeracji — zawsze 204, niezależnie od istnienia/stanu konta.
    @PostMapping("/unsubscribe")
    public ResponseEntity<Void> unsubscribe(@RequestBody UnsubscribeRequest request) {
        service.unsubscribe(request.token());
        return ResponseEntity.noContent().build();
    }
}
