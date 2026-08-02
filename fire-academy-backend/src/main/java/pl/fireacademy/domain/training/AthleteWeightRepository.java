package pl.fireacademy.domain.training;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AthleteWeightRepository extends JpaRepository<AthleteWeight, UUID> {

    Optional<AthleteWeight> findByAthleteIdAndMeasuredOn(UUID athleteId, LocalDate measuredOn);

    /** Oldest first — the chart reads left to right and the trend walks forward. */
    @Query("""
        SELECT w FROM AthleteWeight w
        WHERE w.athlete.id = :athleteId AND w.measuredOn BETWEEN :from AND :to
        ORDER BY w.measuredOn ASC
        """)
    List<AthleteWeight> findRange(@Param("athleteId") UUID athleteId,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to);

    void deleteByAthleteIdAndMeasuredOn(UUID athleteId, LocalDate measuredOn);
}
