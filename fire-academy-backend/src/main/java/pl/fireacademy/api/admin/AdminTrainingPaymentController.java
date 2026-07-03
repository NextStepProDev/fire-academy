package pl.fireacademy.api.admin;

import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.fireacademy.api.RequestParams;
import pl.fireacademy.api.admin.TrainingSlotDtos.PayUserMonthRequest;
import pl.fireacademy.api.admin.TrainingSlotDtos.UserMonthlyPayment;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.util.List;
import java.util.UUID;

/** Monthly payments grouped by person — a subscriber pays for the month, not per training. */
@RestController
@RequestMapping("/api/admin/training-payments")
public class AdminTrainingPaymentController {

    private final AdminTrainingEnrollmentService service;
    private final MessageService msg;

    public AdminTrainingPaymentController(AdminTrainingEnrollmentService service, MessageService msg) {
        this.service = service;
        this.msg = msg;
    }

    @GetMapping
    public List<UserMonthlyPayment> list(@RequestParam(required = false) @Nullable String month) {
        return service.listMonthlyByUser(RequestParams.parseMonth(month, msg));
    }

    /** Mark (or revert) a whole month's payment for one subscriber across all their trainings at once. */
    @PostMapping("/pay-user/{userId}")
    public ResponseEntity<Void> payUser(@PathVariable UUID userId, @Valid @RequestBody PayUserMonthRequest request) {
        service.payUserMonth(userId, request.month(), request.paid());
        return ResponseEntity.noContent().build();
    }
}
