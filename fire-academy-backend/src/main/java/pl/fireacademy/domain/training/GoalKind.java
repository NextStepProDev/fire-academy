package pl.fireacademy.domain.training;

/**
 * What a goal is measured against.
 * <p>
 * The distinction is not cosmetic: a {@link #WEIGHT} goal closes itself from the weight log, while a
 * {@link #GENERAL} one needs a human to say it happened.
 */
public enum GoalKind {
    /** Free text — "10 pull-ups", "spar three times a week". Only the coach can call it achieved. */
    GENERAL,
    /** A target weight. Closes automatically once the 7-day trend reaches it. */
    WEIGHT
}
