package pl.fireacademy.api.dev;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import pl.fireacademy.config.AdminEmailConfig;
import pl.fireacademy.domain.enrollment.Enrollment;
import pl.fireacademy.domain.enrollment.EnrollmentRepository;
import pl.fireacademy.domain.event.Event;
import pl.fireacademy.domain.event.EventCategory;
import pl.fireacademy.domain.event.EventRepository;
import pl.fireacademy.domain.event.EventType;
import pl.fireacademy.domain.event.EventTypeRepository;
import pl.fireacademy.domain.instructor.Instructor;
import pl.fireacademy.domain.instructor.InstructorRepository;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.domain.user.UserRepository;
import pl.fireacademy.domain.user.UserRole;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Wypełnia bazę dev przykładowymi danymi (profil `dev`).
 * <p>
 * <b>Idempotentny</b> — każda sekcja sprawdza, czy dane już są, więc restart aplikacji NIC nie kasuje
 * ani nie duplikuje. Świeży zestaw dostajesz przez reset bazy (na dev robi to FlywayConfig clean+migrate).
 * <p>
 * <b>Zakres:</b> obozy, szkolenia, kadra, użytkownicy, zapisy. <b>NIE</b> sieje zawartości zakładki
 * „Treningi" (terminy/rodzaje TRAINING) — docelowo zastąpi je funkcja treningów cyklicznych
 * (encja {@code TrainingSlot} z osobnej gałęzi), więc tam mieszka jej własny seeder.
 * <p>
 * Wszystkie konta mają hasło <b>{@value #DEV_PASSWORD}</b>. Administratorami są e-maile z {@code ADMIN_EMAIL}.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final String DEV_PASSWORD = "dev";
    private static final String LOC_CENTRUM = "Katowice — Centrum";
    private static final String LOC_LIGOTA = "Katowice — Ligota";
    private static final int FUTURE_CAMPS = 6;
    private static final int PAST_CAMPS = 8;
    private static final int FUTURE_COURSES = 6;
    private static final int PAST_COURSES = 8;

    // Dane uczestników do zapisów (denormalizowane w enrollments). Pokrywają się z kontami z seedUsers().
    private record Participant(String firstName, String lastName, String email, @Nullable String phone) {}

    private static final List<Participant> PEOPLE = List.of(
            new Participant("Jan", "Kowalski", "jan@dev.pl", "500100100"),
            new Participant("Anna", "Nowak", "anna@dev.pl", "500100200"),
            new Participant("Piotr", "Wiśniewski", "piotr@dev.pl", null),
            new Participant("Katarzyna", "Lewandowska", "kasia@dev.pl", "500100400"),
            new Participant("Marek", "Zieliński", "marek@dev.pl", "500100500")
    );

    private final UserRepository userRepository;
    private final InstructorRepository instructorRepository;
    private final EventTypeRepository eventTypeRepository;
    private final EventRepository eventRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminEmailConfig adminEmailConfig;

    public DevDataSeeder(UserRepository userRepository, InstructorRepository instructorRepository,
                         EventTypeRepository eventTypeRepository, EventRepository eventRepository,
                         EnrollmentRepository enrollmentRepository, PasswordEncoder passwordEncoder,
                         AdminEmailConfig adminEmailConfig) {
        this.userRepository = userRepository;
        this.instructorRepository = instructorRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.eventRepository = eventRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmailConfig = adminEmailConfig;
    }

    @Override
    public void run(String... args) {
        seedAdmins();
        seedUsers();
        seedInstructors();
        seedEventTypes();
        seedEventsAndEnrollments();
        log.info("DEV-SEEDER: gotowe. Hasło do wszystkich kont: '{}'.", DEV_PASSWORD);
    }

    // --- Administratorzy (z ADMIN_EMAIL) ---

    private void seedAdmins() {
        for (String email : adminEmailConfig.getAdminEmails()) {
            if (userRepository.existsByEmail(email)) continue;
            User admin = new User(email, "Admin", "Dev", null);
            admin.setPasswordHash(passwordEncoder.encode(DEV_PASSWORD));
            admin.setRole(UserRole.ADMIN);
            admin.markEmailVerified();
            userRepository.save(admin);
            log.info("DEV-SEEDER: admin {} (hasło: {})", email, DEV_PASSWORD);
        }
    }

    // --- Zwykli użytkownicy (proste maile @dev.pl) ---

    private void seedUsers() {
        // jeden bez telefonu (test RODO), jeden z wyłączonymi powiadomieniami
        createUser("jan@dev.pl", "Jan", "Kowalski", "500100100", true);
        createUser("anna@dev.pl", "Anna", "Nowak", "500100200", true);
        createUser("piotr@dev.pl", "Piotr", "Wiśniewski", null, true);
        createUser("kasia@dev.pl", "Katarzyna", "Lewandowska", "500100400", false);
        createUser("marek@dev.pl", "Marek", "Zieliński", "500100500", true);
    }

    private void createUser(String email, String firstName, String lastName,
                            @Nullable String phone, boolean notifications) {
        if (userRepository.existsByEmail(email)) return;
        User u = new User(email, firstName, lastName, phone);
        u.setPasswordHash(passwordEncoder.encode(DEV_PASSWORD));
        u.markEmailVerified();
        u.setEmailNotificationsEnabled(notifications);
        userRepository.save(u);
        log.info("DEV-SEEDER: user {} (hasło: {})", email, DEV_PASSWORD);
    }

    // --- Kadra (tylko kategorie CAMP/COURSE — bez TRAINING) ---

    private void seedInstructors() {
        if (instructorRepository.count() > 0) return;
        instructorRepository.save(instructor("Tomasz", "Mazur",
                "Instruktor szkoleń, wieloletnie doświadczenie zawodnicze.", 0,
                Set.of(EventCategory.COURSE)));
        instructorRepository.save(instructor("Robert", "Król",
                "Prowadzi obozy i szkolenia specjalistyczne.", 1,
                Set.of(EventCategory.CAMP, EventCategory.COURSE)));
        instructorRepository.save(instructor("Ewa", "Lewandowska",
                "Przygotowanie motoryczne i obozy sportowe.", 2,
                Set.of(EventCategory.CAMP)));
        instructorRepository.save(instructor("Damian", "Wilk",
                "Instruktor samoobrony, szkolenia praktyczne.", 3,
                Set.of(EventCategory.COURSE)));
        log.info("DEV-SEEDER: 4 instruktorów");
    }

    private Instructor instructor(String firstName, String lastName, String bio, int order, Set<EventCategory> cats) {
        Instructor i = new Instructor(firstName, lastName);
        i.setBio(bio);
        i.setDisplayOrder(order);
        i.setCategories(cats);
        i.setActive(true);
        return i;
    }

    // --- Rodzaje (katalog) — tylko CAMP/COURSE ---

    private void seedEventTypes() {
        if (eventTypeRepository.count() > 0) return;
        eventType(EventCategory.CAMP, "Obóz sportowy", "Kilkudniowy obóz treningowy.", 0);
        eventType(EventCategory.CAMP, "Obóz przygotowawczy", "Intensywne przygotowanie do sezonu.", 1);
        eventType(EventCategory.COURSE, "Szkolenie samoobrony", "Weekendowe szkolenie praktyczne.", 0);
        eventType(EventCategory.COURSE, "Szkolenie instruktorskie", "Kurs dla przyszłych instruktorów.", 1);
        log.info("DEV-SEEDER: 4 rodzaje");
    }

    private void eventType(EventCategory category, String name, String description, int order) {
        EventType et = new EventType(category, name);
        et.setDescription(description);
        et.setDisplayOrder(order);
        et.setActive(true);
        eventTypeRepository.save(et);
    }

    // --- Terminy (obozy + szkolenia, przyszłe + archiwalne) + zapisy ---

    private void seedEventsAndEnrollments() {
        if (eventRepository.count() > 0) return;
        LocalDate today = LocalDate.now();

        List<Event> camps = seedCamps(today);      // [0..FUTURE_CAMPS) przyszłe, reszta archiwalne
        List<Event> courses = seedCourses(today);

        // --- ZAPISY ---
        // Bieżące (dla rosterów i sekcji „aktualne" w profilu)
        enrollPeople(camps.get(0), 1, 3);                            // Anna, Kasia
        enrollPeople(camps.get(1), 0, 4, 1);                         // Jan, Marek, Anna
        enroll(courses.get(0), PEOPLE.get(2), "Bez numeru telefonu");// Piotr (bez telefonu)
        enrollPeople(courses.get(1), 1);                             // Anna

        // Archiwalne — WIĘKSZOŚĆ z zapisami, część z kilkoma osobami (kilka zostaje pustych dla realizmu)
        fillArchivalEnrollments(camps.subList(FUTURE_CAMPS, camps.size()));
        fillArchivalEnrollments(courses.subList(FUTURE_COURSES, courses.size()));

        log.info("DEV-SEEDER: terminy (obozy/szkolenia) + zapisy");
    }

    private List<Event> seedCamps(LocalDate today) {
        String[] names = { "Obóz sportowy — Beskidy", "Obóz przygotowawczy", "Obóz letni",
                "Obóz kondycyjny", "Obóz dla początkujących", "Obóz zaawansowany" };
        String[] places = { "Szczyrk", "Wisła", "Zakopane", "Karpacz", "Ustroń" };

        List<Event> result = new ArrayList<>();
        for (int i = 0; i < FUTURE_CAMPS; i++) {
            LocalDate start = today.plusDays(20L + i * 14L);
            result.add(save(eventRange(EventCategory.CAMP, names[i % names.length], start, start.plusDays(5),
                    LocalTime.of(9, 0), LocalTime.of(16, 0), places[i % places.length], 1000 + i * 100, 16 + i)));
        }
        for (int i = 0; i < PAST_CAMPS; i++) {
            LocalDate start = today.minusDays(25L + i * 18L);
            result.add(save(eventRange(EventCategory.CAMP, names[i % names.length], start, start.plusDays(5),
                    LocalTime.of(9, 0), LocalTime.of(16, 0), places[i % places.length], 1000 + i * 100, 16 + i)));
        }
        log.info("DEV-SEEDER: {} obozów", result.size());
        return result;
    }

    private List<Event> seedCourses(LocalDate today) {
        String[] names = { "Szkolenie samoobrony", "Szkolenie instruktorskie", "Szkolenie pierwszej pomocy",
                "Szkolenie taktyczne", "Szkolenie z technik obronnych", "Szkolenie weekendowe" };
        String[] locations = { LOC_CENTRUM, LOC_LIGOTA };

        List<Event> result = new ArrayList<>();
        for (int i = 0; i < FUTURE_COURSES; i++) {
            result.add(save(event(EventCategory.COURSE, names[i % names.length], today.plusDays(10L + i * 9L),
                    LocalTime.of(10, 0), LocalTime.of(16, 0), locations[i % locations.length], 250 + i * 50, 12 + i)));
        }
        for (int i = 0; i < PAST_COURSES; i++) {
            result.add(save(event(EventCategory.COURSE, names[i % names.length], today.minusDays(15L + i * 16L),
                    LocalTime.of(10, 0), LocalTime.of(16, 0), locations[i % locations.length], 250 + i * 50, 12 + i)));
        }
        log.info("DEV-SEEDER: {} szkoleń", result.size());
        return result;
    }

    // Większość archiwalnych terminów dostaje 1..4 uczestników (różne osoby), co 6. zostaje pusty.
    private void fillArchivalEnrollments(List<Event> pastEvents) {
        for (int j = 0; j < pastEvents.size(); j++) {
            if (j % 6 == 4) continue; // część pusta
            Event ev = pastEvents.get(j);
            int max = ev.getMaxParticipants() != null ? ev.getMaxParticipants() : Integer.MAX_VALUE;
            int count = Math.min(1 + (j % 4), Math.min(max, PEOPLE.size()));
            int[] idx = new int[count];
            for (int k = 0; k < count; k++) idx[k] = (j + k) % PEOPLE.size();
            enrollPeople(ev, idx);
        }
    }

    private Event event(EventCategory category, String name, LocalDate date,
                        LocalTime start, LocalTime end, String location, double price, int max) {
        Event e = new Event(category, name, date);
        e.setStartTime(start);
        e.setEndTime(end);
        e.setLocation(location);
        // Opis → strona ma treść, więc na liście pojawia się button „Szczegóły" (hasDetails()).
        e.setDescription("Termin: " + name + " (" + location
                + "). Plan zajęć, poziom zaawansowania i informacje organizacyjne. Zapraszamy!");
        e.setPrice(BigDecimal.valueOf(price));
        e.setMaxParticipants(max);
        e.setActive(true);
        return e;
    }

    private Event eventRange(EventCategory category, String name, LocalDate start, LocalDate end,
                             LocalTime startTime, LocalTime endTime, String location, double price, int max) {
        Event e = event(category, name, start, startTime, endTime, location, price, max);
        e.setEndDate(end);
        return e;
    }

    private Event save(Event e) {
        return eventRepository.save(e);
    }

    private void enrollPeople(Event event, int... peopleIndices) {
        for (int idx : peopleIndices) {
            enroll(event, PEOPLE.get(idx % PEOPLE.size()), null);
        }
    }

    private void enroll(Event event, Participant p, @Nullable String note) {
        enroll(event, p.firstName(), p.lastName(), p.email(), p.phone(), note);
    }

    private void enroll(Event event, String firstName, String lastName,
                        String email, @Nullable String phone, @Nullable String note) {
        enrollmentRepository.save(new Enrollment(event, firstName, lastName, email, phone, note, true));
    }
}
