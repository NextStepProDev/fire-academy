package pl.fireacademy.domain.training;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PersonalTrainingRepository extends JpaRepository<PersonalTraining, UUID> {

    /**
     * One calendar page. Ordering matches how the day column renders: untimed first (the default
     * case, "do this today"), then chronologically, then by insertion for ties. The same rule lives
     * in {@code sortTiles()} on the frontend — change one, change both.
     */
    @Query("""
        SELECT pt FROM PersonalTraining pt
        WHERE pt.athlete.id = :athleteId
          AND pt.date BETWEEN :from AND :to
        ORDER BY pt.date ASC, pt.startTime ASC NULLS FIRST, pt.createdAt ASC
        """)
    List<PersonalTraining> findRange(@Param("athleteId") UUID athleteId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);

    /**
     * Trainings the other side touched since this viewer last looked.
     * <p>
     * Keyed on {@code updatedAt}, never {@code completedAt}: undoing a completion clears that column,
     * and the coach still needs to hear about it. {@code lastModifiedByAdmin} is what separates
     * "they changed it" from "I did" — {@code @PreUpdate} bumps {@code updatedAt} either way.
     */
    @Query("""
        SELECT COUNT(pt) FROM PersonalTraining pt
        WHERE pt.athlete.id = :athleteId
          AND pt.lastModifiedByAdmin = :byAdmin
          AND pt.updatedAt > :since
        """)
    long countTouchedSince(@Param("athleteId") UUID athleteId,
                           @Param("byAdmin") boolean byAdmin,
                           @Param("since") Instant since);

    /**
     * Next training from today onwards — powers the "what's next" line on the account tile. Tasks are
     * excluded on purpose: a calorie ceiling is not what "next training" means.
     */
    @Query("""
        SELECT MIN(pt.date) FROM PersonalTraining pt
        WHERE pt.athlete.id = :athleteId AND pt.date >= :from AND pt.completedAt IS NULL
          AND pt.kind = pl.fireacademy.domain.training.TrainingKind.TRAINING
        """)
    @Nullable
    LocalDate findNextTrainingDate(@Param("athleteId") UUID athleteId, @Param("from") LocalDate from);
}
