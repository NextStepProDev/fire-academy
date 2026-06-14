package pl.fireacademy.api.admin;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.NotFoundException;
import pl.fireacademy.api.admin.TrainingSlotDtos.*;
import pl.fireacademy.domain.event.EventCategory;
import pl.fireacademy.domain.event.EventType;
import pl.fireacademy.domain.event.EventTypeRepository;
import pl.fireacademy.domain.instructor.Instructor;
import pl.fireacademy.domain.instructor.InstructorRepository;
import pl.fireacademy.domain.training.TrainingEnrollmentRepository;
import pl.fireacademy.domain.training.TrainingSlot;
import pl.fireacademy.domain.training.TrainingSlotRepository;
import pl.fireacademy.infrastructure.i18n.MessageService;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AdminTrainingSlotService {

    private final TrainingSlotRepository slotRepository;
    private final TrainingEnrollmentRepository enrollmentRepository;
    private final EventTypeRepository eventTypeRepository;
    private final InstructorRepository instructorRepository;
    private final MessageService msg;

    public AdminTrainingSlotService(TrainingSlotRepository slotRepository,
                                    TrainingEnrollmentRepository enrollmentRepository,
                                    EventTypeRepository eventTypeRepository,
                                    InstructorRepository instructorRepository,
                                    MessageService msg) {
        this.slotRepository = slotRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.instructorRepository = instructorRepository;
        this.msg = msg;
    }

    @Transactional(readOnly = true)
    public List<TrainingSlotResponse> getAll(YearMonth month) {
        String m = month.toString();
        return slotRepository.findAllByOrderByDayOfWeekAscStartTimeAscDisplayOrderAsc().stream()
                .map(s -> toResponse(s, m))
                .toList();
    }

    @Transactional
    public TrainingSlotResponse create(CreateTrainingSlotRequest request) {
        var eventType = resolveTrainingType(request.eventTypeId());
        var slot = new TrainingSlot(eventType, request.dayOfWeek(), request.startTime(), request.maxParticipants());
        applyCommon(slot, request.instructorId(), request.endTime(), request.price());
        return toResponse(slotRepository.save(slot), YearMonth.now().toString());
    }

    @Transactional
    public List<TrainingSlotResponse> createBatch(BatchCreateTrainingSlotRequest request) {
        var eventType = resolveTrainingType(request.eventTypeId());
        var instructor = resolveInstructor(request.instructorId());
        String month = YearMonth.now().toString();
        List<TrainingSlotResponse> created = new ArrayList<>();
        for (var row : request.slots()) {
            var slot = new TrainingSlot(eventType, row.dayOfWeek(), row.startTime(), row.maxParticipants());
            slot.setInstructor(instructor);
            slot.setEndTime(row.endTime());
            slot.setPrice(row.price());
            created.add(toResponse(slotRepository.save(slot), month));
        }
        return created;
    }

    @Transactional
    public TrainingSlotResponse update(UUID id, UpdateTrainingSlotRequest request) {
        var slot = findOrThrow(id);
        slot.setEventType(resolveTrainingType(request.eventTypeId()));
        slot.setDayOfWeek(request.dayOfWeek());
        slot.setStartTime(request.startTime());
        slot.setMaxParticipants(request.maxParticipants());
        applyCommon(slot, request.instructorId(), request.endTime(), request.price());
        return toResponse(slotRepository.save(slot), YearMonth.now().toString());
    }

    @Transactional
    public TrainingSlotResponse toggleActive(UUID id) {
        var slot = findOrThrow(id);
        slot.setActive(!slot.isActive());
        return toResponse(slotRepository.save(slot), YearMonth.now().toString());
    }

    @Transactional
    public void delete(UUID id) {
        var slot = findOrThrow(id);
        if (enrollmentRepository.countBySlotId(id) > 0) {
            throw new IllegalStateException(msg.get("trainingslot.has.enrollments"));
        }
        slotRepository.delete(slot);
    }

    private void applyCommon(TrainingSlot slot, @Nullable UUID instructorId,
                             @Nullable LocalTime endTime, @Nullable BigDecimal price) {
        slot.setInstructor(resolveInstructor(instructorId));
        slot.setEndTime(endTime);
        slot.setPrice(price);
    }

    private EventType resolveTrainingType(UUID eventTypeId) {
        var eventType = eventTypeRepository.findById(eventTypeId)
                .orElseThrow(() -> new NotFoundException(msg.get("eventtype.not.found")));
        if (eventType.getCategory() != EventCategory.TRAINING) {
            throw new IllegalArgumentException(msg.get("trainingslot.type.not.training"));
        }
        return eventType;
    }

    @Nullable
    private Instructor resolveInstructor(@Nullable UUID instructorId) {
        if (instructorId == null) {
            return null;
        }
        return instructorRepository.findById(instructorId)
                .orElseThrow(() -> new NotFoundException(msg.get("instructor.not.found")));
    }

    private TrainingSlot findOrThrow(UUID id) {
        return slotRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(msg.get("trainingslot.not.found")));
    }

    private TrainingSlotResponse toResponse(TrainingSlot s, String month) {
        var et = s.getEventType();
        var instr = s.getInstructor();
        return new TrainingSlotResponse(
                s.getId(),
                et.getId(), et.getName(),
                instr != null ? instr.getId() : null,
                instr != null ? instr.getFirstName() + " " + instr.getLastName() : null,
                s.getDayOfWeek(), s.getStartTime(), s.getEndTime(), s.getPrice(),
                s.getMaxParticipants(),
                s.getDisplayOrder(),
                enrollmentRepository.countCovering(s.getId(), month),
                s.isActive(), s.getCreatedAt()
        );
    }
}
