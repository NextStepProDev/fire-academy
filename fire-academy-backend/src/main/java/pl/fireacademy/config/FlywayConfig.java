package pl.fireacademy.config;

import java.util.Arrays;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class FlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

    @Bean
    public Flyway flyway(DataSource dataSource, Environment environment) {
        boolean devProfile = Arrays.asList(environment.getActiveProfiles()).contains("dev");
        // Tylko dev: baza odbudowywana od zera przy każdym starcie (clean + migrate), żeby przełączanie
        // gałęzi o rozjechanych migracjach nigdy nie kolidowało. Dane pochodzą z DevDataSeeder.
        // Wyłączalne: app.dev.reset-on-start=false (wtedy zwykły migrate, dane trwają między restartami).
        // Prod/test: clean ZABLOKOWANY (cleanDisabled=true) — bezpiecznik przed skasowaniem danych.
        boolean resetOnStart = devProfile
                && environment.getProperty("app.dev.reset-on-start", Boolean.class, true);

        FluentConfiguration config = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .cleanDisabled(!resetOnStart);

        Flyway flyway = config.load();

        if (resetOnStart) {
            log.warn("DEV reset-on-start: czyszczę bazę (clean) i migruję od zera. Dane odbuduje seeder.");
            flyway.clean();
        }

        log.info("Running Flyway migrations...");
        flyway.migrate();
        log.info("Flyway migrations completed.");
        return flyway;
    }
}
