package pl.fireacademy.api.admin;

import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.fireacademy.api.RequestParams;
import pl.fireacademy.api.admin.TrainingSlotDtos.CreateHolidayRequest;
import pl.fireacademy.api.admin.TrainingSlotDtos.TrainingHolidayResponse;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/training-holidays")
public class AdminTrainingHolidayController {

    private final AdminTrainingHolidayService service;
    private final MessageService msg;

    public AdminTrainingHolidayController(AdminTrainingHolidayService service, MessageService msg) {
        this.service = service;
        this.msg = msg;
    }

    @GetMapping
    public List<TrainingHolidayResponse> getForMonth(@RequestParam(required = false) @Nullable String month) {
        return service.getForMonth(RequestParams.parseMonth(month, msg));
    }

    @PostMapping
    public ResponseEntity<TrainingHolidayResponse> add(@Valid @RequestBody CreateHolidayRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.add(request.date(), request.label()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable UUID id) {
        service.remove(id);
        return ResponseEntity.noContent().build();
    }
}
