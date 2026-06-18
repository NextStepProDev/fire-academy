package pl.fireacademy.domain.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    // Pełna lista (np. mail „do wszystkich") — nie stronicowana.
    List<User> findAllByOrderByCreatedAtDesc();

    // Wyszukiwanie po fragmencie imienia, nazwiska lub e-maila (bez rozróżniania wielkości liter), stronicowane.
    @Query("""
        SELECT u FROM User u
        WHERE LOWER(u.firstName) LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(u.lastName)  LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(u.email)     LIKE LOWER(CONCAT('%', :q, '%'))
        """)
    Page<User> searchByPhrase(@Param("q") String q, Pageable pageable);

    // Warianty z wykluczeniem kont ukrytych (techniczne/deweloperskie) — używane, gdy lista wykluczonych
    // e-maili jest niepusta; trzymają liczniki i paginację spójne (filtr w SQL, nie po stronie Javy).
    // :excluded musi zawierać e-maile zapisane małymi literami.
    @Query("""
        SELECT u FROM User u
        WHERE LOWER(u.email) NOT IN :excluded
        """)
    Page<User> findAllExcludingEmails(@Param("excluded") Collection<String> excluded, Pageable pageable);

    @Query("""
        SELECT u FROM User u
        WHERE (LOWER(u.firstName) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(u.lastName)  LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(u.email)     LIKE LOWER(CONCAT('%', :q, '%')))
          AND LOWER(u.email) NOT IN :excluded
        """)
    Page<User> searchByPhraseExcludingEmails(@Param("q") String q,
                                             @Param("excluded") Collection<String> excluded,
                                             Pageable pageable);

    // E-maile traktujemy bez rozróżniania wielkości liter (jak wszyscy dostawcy poczty),
    // żeby uniknąć duplikatów kont i nieudanych logowań przy innej wielkości liter.
    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByOauthProviderAndOauthId(String oauthProvider, String oauthId);

    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);
}
