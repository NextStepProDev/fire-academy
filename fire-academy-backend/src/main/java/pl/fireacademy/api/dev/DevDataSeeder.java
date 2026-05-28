package pl.fireacademy.api.dev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import pl.fireacademy.config.AdminEmailConfig;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.domain.user.UserRepository;
import pl.fireacademy.domain.user.UserRole;

@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final String DEV_ADMIN_PASSWORD = "admin";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminEmailConfig adminEmailConfig;

    public DevDataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder, AdminEmailConfig adminEmailConfig) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmailConfig = adminEmailConfig;
    }

    @Override
    public void run(String... args) {
        for (String email : adminEmailConfig.getAdminEmails()) {
            if (userRepository.existsByEmail(email)) {
                log.info("DEV-SEEDER: Admin account already exists: {}", email);
                continue;
            }

            User admin = new User(email, "Admin", "Dev", null);
            admin.setPasswordHash(passwordEncoder.encode(DEV_ADMIN_PASSWORD));
            admin.setRole(UserRole.ADMIN);
            admin.markEmailVerified();
            userRepository.save(admin);

            log.info("DEV-SEEDER: Created admin account: {} (password: {})", email, DEV_ADMIN_PASSWORD);
        }
    }
}
