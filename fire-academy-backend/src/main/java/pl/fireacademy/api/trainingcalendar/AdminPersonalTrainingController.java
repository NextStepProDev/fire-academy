package pl.fireacademy.api.trainingcalendar;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.fireacademy.api.trainingcalendar.TrainingCalendarDtos.*;
import pl.fireacademy.config.CurrentUserId;

import java.time.LocalDate;
import java.util.UUID;

/** Coach side of the 1-on-1 calendar. Role protection comes from the {@code /api/admin/**} prefix. */
@RestController
@RequestMapping("/api/admin/personal-trainings")
public class AdminPersonalTrainingController {

    private final PersonalTrainingService service;

    public AdminPersonalTrainingController(PersonalTrainingService service) {
        this.service = service;
    }

    @GetMapping
    public CalendarRangeResponse getRange(
            @RequestParam UUID athleteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.getRange(athleteId, from, to);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PersonalTrainingResponse create(@RequestParam UUID athleteId,
                                           @Valid @RequestBody CreateTrainingRequest request) {
        return service.create(athleteId, request, true);
    }

    @PutMapping("/{id}")
    public PersonalTrainingResponse update(@CurrentUserId UUID adminId, @PathVariable UUID id,
                                           @Valid @RequestBody UpdateTrainingRequest request) {
        return service.update(id, request, adminId, true);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CurrentUserId UUID adminId, @PathVariable UUID id) {
        service.delete(id, adminId, true);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public PersonalTrainingResponse duplicate(@CurrentUserId UUID adminId, @PathVariable UUID id,
                                              @Valid @RequestBody DuplicateTrainingRequest request) {
        return service.duplicate(id, request, adminId, true);
    }

    @PostMapping("/paste")
    public PersonalTrainingResponse paste(@CurrentUserId UUID adminId,
                                          @Valid @RequestBody PasteTrainingRequest request) {
        return service.paste(request, adminId, true);
    }
}
