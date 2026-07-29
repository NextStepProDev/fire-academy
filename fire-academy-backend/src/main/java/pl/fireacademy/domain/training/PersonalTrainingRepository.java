package pl.fireacademy.domain.training;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
