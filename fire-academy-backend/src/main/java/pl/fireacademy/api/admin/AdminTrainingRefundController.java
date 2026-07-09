package pl.fireacademy.api.admin;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.fireacademy.api.admin.TrainingSlotDtos.RefundEntry;
import pl.fireacademy.api.admin.TrainingSlotDtos.SettleRefundRequest;
import pl.fireacademy.api.admin.TrainingSlotDtos.UnconsumedCreditEntry;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/training-refunds")
public class AdminTrainingRefundController {

    private final AdminTrainingRefundService service;

    public AdminTrainingRefundController(AdminTrainingRefundService service) {
        this.service = service;
    }

    /** Refunds still owed (default) or the settled history when {@code settled=true}. */
    @GetMapping
    public List<RefundEntry> list(@RequestParam(defaultValue = "false") boolean settled) {
        return service.list(settled);
    }

    /** Ended subscriptions still sitting on unconsumed CREDITED surplus — needs a manual cash refund. */
    @GetMapping("/unconsumed-credit")
    public List<UnconsumedCreditEntry> unconsumedCredit() {
        return service.listUnconsumedCredit();
    }

    /** Resolve a refund as REFUNDED (money back) or CREDITED (counted toward this/next month). */
    @PostMapping("/{id}/settle")
    public ResponseEntity<Void> settle(@PathVariable UUID id, @Valid @RequestBody SettleRefundRequest request) {
        service.settle(id, request.settlementType());
        return ResponseEntity.noContent().build();
    }

    /** Resolve every pending refund of one subscriber the same way (bulk action from the grouped view). */
    @PostMapping("/settle-user/{userId}")
    public ResponseEntity<Void> settleUser(@PathVariable UUID userId, @Valid @RequestBody SettleRefundRequest request) {
        service.settleAllForUser(userId, request.settlementType());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/unsettle")
    public ResponseEntity<Void> unsettle(@PathVariable UUID id) {
        service.unsettle(id);
        return ResponseEntity.noContent().build();
    }
}
