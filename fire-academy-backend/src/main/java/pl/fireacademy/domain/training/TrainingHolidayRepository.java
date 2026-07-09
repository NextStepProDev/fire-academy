package pl.fireacademy.domain.training;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TrainingHolidayRepository extends JpaRepository<TrainingHoliday, UUID> {

    boolean existsByHolidayDate(LocalDate holidayDate);

    List<TrainingHoliday> findByHolidayDateBetweenOrderByHolidayDateAsc(LocalDate from, LocalDate to);

    @Transactional
    void deleteByHolidayDate(LocalDate holidayDate);
}
