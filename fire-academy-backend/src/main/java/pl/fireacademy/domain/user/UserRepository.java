package pl.fireacademy.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    // E-maile traktujemy bez rozróżniania wielkości liter (jak wszyscy dostawcy poczty),
    // żeby uniknąć duplikatów kont i nieudanych logowań przy innej wielkości liter.
    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByOauthProviderAndOauthId(String oauthProvider, String oauthId);

    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);
}
