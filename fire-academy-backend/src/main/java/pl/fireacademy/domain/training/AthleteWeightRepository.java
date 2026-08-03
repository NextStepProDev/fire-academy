package pl.fireacademy.domain.training;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AthleteWeightRepository extends JpaRepository<AthleteWeight, UUID> {

    Optional<AthleteWeight> findByAthleteIdAndMeasuredOn(UUID athleteId, LocalDate measuredOn);

    /**
     * Records one morning's weight, atomically.
     * <p>
     * Read-then-write loses a double-tapped save: both requests find no row for today, both insert,
     * and {@code uq_athlete_weights_day} rejects the second — a server error for someone who simply
     * pressed the button twice. Letting the database resolve the tie means the second write is a
     * correction, which is what a second weigh-in has always meant here.
     * <p>
     * {@code clearAutomatically} matters as much as the upsert itself: the trend is read back in the
     * same transaction, and without it Hibernate would keep serving the pre-save row from its
     * identity map and close a weight goal against yesterday's number.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO athlete_weights (athlete_id, measured_on, weight_kg)
        VALUES (:athleteId, :measuredOn, :weightKg)
        ON CONFLICT (athlete_id, measured_on)
        DO UPDATE SET weight_kg = :weightKg, updated_at = now()
        """, nativeQuery = true)
    void upsertReading(@Param("athleteId") UUID athleteId,
                       @Param("measuredOn") LocalDate measuredOn,
                       @Param("weightKg") BigDecimal weightKg);

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
