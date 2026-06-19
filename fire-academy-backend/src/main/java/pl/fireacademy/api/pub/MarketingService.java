package pl.fireacademy.api.pub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.domain.user.UserRepository;
import pl.fireacademy.infrastructure.security.JwtAuthenticationFilter;

import java.util.UUID;

@Service
public class MarketingService {

    private static final Logger log = LoggerFactory.getLogger(MarketingService.class);

    private final UserRepository userRepository;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public MarketingService(UserRepository userRepository, JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.userRepository = userRepository;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Transactional
    public void unsubscribe(String token) {
        UUID parsed;
        try {
            parsed = UUID.fromString(token.trim());
        } catch (IllegalArgumentException e) {
            // Token nie jest poprawnym UUID — nie ujawniamy tego, traktujemy jak brak konta.
            return;
        }
        userRepository.findByMarketingUnsubscribeToken(parsed).ifPresent(user -> {
            if (user.hasMarketingConsent()) {
                user.setMarketingConsentAt(null);
                userRepository.save(user);
                jwtAuthenticationFilter.evictUser(user.getId());
                log.info("Marketing unsubscribe via email link: {}", user.getId());
            }
        });
    }
}
