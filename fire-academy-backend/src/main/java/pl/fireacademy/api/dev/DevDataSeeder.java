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
import pl.fireacademy.domain.training.TrainingSlot;
import pl.fireacademy.domain.training.TrainingSlotRepository;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.domain.user.UserRepository;
import pl.fireacademy.domain.user.UserRole;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Fills the dev database with sample data (`dev` profile).
 * <p>
 * <b>Idempotent</b> — each section checks whether the data already exists, so restarting the application
 * deletes NOTHING and creates no duplicates. You get a fresh set by resetting the database (on dev this is done
 * by FlywayConfig clean+migrate).
 * <p>
 * <b>Scope:</b> camps, courses, instructors (CAMP/COURSE), users, enrollments, and the cyclical trainings
 * feature — training event types (TRAINING), TRAINING instructors and <b>27 weekly slots (Mon–Fri)</b>.
 * <p>
 * All accounts have the password <b>{@value #DEV_PASSWORD}</b>. Administrators are the e-mails from {@code ADMIN_EMAIL}.
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
    private static final int TRAINING_SLOTS = 27;

    // Enrollment participants' e-mails — each corresponds to an account from seedUsers() (enrollment requires an account).
    // Personal data in an enrollment is a snapshot copied from the account by Enrollment.forUser().
    private static final List<String> PEOPLE = List.of(
            "jan@dev.pl",
            "anna@dev.pl",
            "piotr@dev.pl",   // no phone — tests the account-enrollment block
            "kasia@dev.pl",
            "marek@dev.pl"
    );

    private final UserRepository userRepository;
    private final InstructorRepository instructorRepository;
    private final EventTypeRepository eventTypeRepository;
    private final EventRepository eventRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final TrainingSlotRepository trainingSlotRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminEmailConfig adminEmailConfig;

    public DevDataSeeder(UserRepository userRepository, InstructorRepository instructorRepository,
                         EventTypeRepository eventTypeRepository, EventRepository eventRepository,
                         EnrollmentRepository enrollmentRepository, TrainingSlotRepository trainingSlotRepository,
                         PasswordEncoder passwordEncoder, AdminEmailConfig adminEmailConfig) {
        this.userRepository = userRepository;
        this.instructorRepository = instructorRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.eventRepository = eventRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.trainingSlotRepository = trainingSlotRepository;
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
        seedTrainingSlots();
        log.info("DEV-SEEDER: done. Password for all accounts: '{}'.", DEV_PASSWORD);
    }

    // --- Administrators (from ADMIN_EMAIL) ---

    private void seedAdmins() {
        for (String email : adminEmailConfig.getAdminEmails()) {
            if (userRepository.existsByEmail(email)) continue;
            User admin = new User(email, "Admin", "Dev", null);
            admin.setPasswordHash(passwordEncoder.encode(DEV_PASSWORD));
            admin.setRole(UserRole.ADMIN);
            admin.markEmailVerified();
            userRepository.save(admin);
            log.info("DEV-SEEDER: admin {} (password: {})", email, DEV_PASSWORD);
        }
    }

    // --- Regular users (simple @dev.pl e-mails) ---

    private void seedUsers() {
        // Piotr without a phone (tests forcing profile completion); a mix of marketing consents for testing the MARKETING audience.
        createUser("jan@dev.pl", "Jan", "Kowalski", "500100100", true);
        createUser("anna@dev.pl", "Anna", "Nowak", "500100200", true);
        createUser("piotr@dev.pl", "Piotr", "Wiśniewski", null, false);
        createUser("kasia@dev.pl", "Katarzyna", "Lewandowska", "500100400", false);
        createUser("marek@dev.pl", "Marek", "Zieliński", "500100500", true);
    }

    private void createUser(String email, String firstName, String lastName,
                            @Nullable String phone, boolean marketingConsent) {
        if (userRepository.existsByEmail(email)) return;
        User u = new User(email, firstName, lastName, phone);
        u.setPasswordHash(passwordEncoder.encode(DEV_PASSWORD));
        u.markEmailVerified();
        if (marketingConsent) {
            u.setMarketingConsentAt(Instant.now());
        }
        userRepository.save(u);
        log.info("DEV-SEEDER: user {} (password: {})", email, DEV_PASSWORD);
    }

    // --- Instructors (only CAMP/COURSE categories — no TRAINING) ---

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
        log.info("DEV-SEEDER: 4 instructors");
    }

    private Instructor instructor(String firstName, String lastName, String bio, int order, Set<EventCategory> cats) {
        Instructor i = new Instructor(firstName, lastName);
        i.setBio(bio);
        i.setDisplayOrder(order);
        i.setCategories(cats);
        i.setActive(true);
        return i;
    }

    // --- Event types (catalog) — only CAMP/COURSE ---

    private void seedEventTypes() {
        if (eventTypeRepository.count() > 0) return;
        eventType(EventCategory.CAMP, "Obóz sportowy", "Kilkudniowy obóz treningowy.", 0);
        eventType(EventCategory.CAMP, "Obóz przygotowawczy", "Intensywne przygotowanie do sezonu.", 1);
        eventType(EventCategory.COURSE, "Szkolenie samoobrony", "Weekendowe szkolenie praktyczne.", 0);
        eventType(EventCategory.COURSE, "Szkolenie instruktorskie", "Kurs dla przyszłych instruktorów.", 1);
        log.info("DEV-SEEDER: 4 event types");
    }

    private void eventType(EventCategory category, String name, String description, int order) {
        EventType et = new EventType(category, name);
        et.setDescription(description);
        et.setDisplayOrder(order);
        et.setActive(true);
        eventTypeRepository.save(et);
    }

    // --- Events (camps + courses, future + archived) + enrollments ---

    private void seedEventsAndEnrollments() {
        if (eventRepository.count() > 0) return;
        LocalDate today = LocalDate.now();

        List<Event> camps = seedCamps(today);      // [0..FUTURE_CAMPS) future, the rest archived
        List<Event> courses = seedCourses(today);

        // --- ENROLLMENTS ---
        // Current (for rosters and the "current" section in the profile)
        enrollPeople(camps.get(0), 1, 3);                            // Anna, Kasia
        enrollPeople(camps.get(1), 0, 4, 1);                         // Jan, Marek, Anna
        enroll(courses.get(0), PEOPLE.get(2), "Bez numeru telefonu");// Piotr (account without a phone)
        enrollPeople(courses.get(1), 1);                             // Anna

        // Archived — MOST with enrollments, some with a few people (a few stay empty for realism)
        fillArchivalEnrollments(camps.subList(FUTURE_CAMPS, camps.size()));
        fillArchivalEnrollments(courses.subList(FUTURE_COURSES, courses.size()));

        log.info("DEV-SEEDER: events (camps/courses) + enrollments");
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
        log.info("DEV-SEEDER: {} camps", result.size());
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
        log.info("DEV-SEEDER: {} courses", result.size());
        return result;
    }

    // Most archived events get 1..4 participants (different people), every 6th stays empty.
    private void fillArchivalEnrollments(List<Event> pastEvents) {
        for (int j = 0; j < pastEvents.size(); j++) {
            if (j % 6 == 4) continue; // some left empty
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
        // Description → the page has content, so a "Details" button appears in the list (hasDetails()).
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

    // Enrollment = a user account. Enrollment.forUser() copies the personal-data snapshot from the account.
    private void enroll(Event event, String email, @Nullable String note) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("DEV-SEEDER: brak konta dla zapisu " + email));
        enrollmentRepository.save(Enrollment.forUser(event, user, note, true));
    }

    // --- Treningi cykliczne: rodzaje (TRAINING), kadra TRAINING i 27 slotów pon–pt ---

    // 27 cyklicznych slotów rozłożonych pon–pt (dzień ISO 1..5), z rotacją godzin/rodzajów/trenerów.
    private void seedTrainingSlots() {
        if (trainingSlotRepository.count() > 0) return;

        List<EventType> types = seedTrainingTypes();
        List<Instructor> instructors = seedTrainingInstructors();

        LocalTime[][] hours = {
                { LocalTime.of(7, 0), LocalTime.of(8, 0) },
                { LocalTime.of(10, 0), LocalTime.of(11, 0) },
                { LocalTime.of(16, 0), LocalTime.of(17, 0) },
                { LocalTime.of(17, 30), LocalTime.of(19, 0) },
                { LocalTime.of(19, 0), LocalTime.of(20, 30) },
                { LocalTime.of(20, 30), LocalTime.of(22, 0) },
        };

        for (int i = 0; i < TRAINING_SLOTS; i++) {
            int dayOfWeek = (i % 5) + 1;            // 1=pon … 5=pt
            LocalTime[] slot = hours[(i / 5) % hours.length];
            boolean personal = i % 6 == 0;
            int max = personal ? 1 : 6;

            TrainingSlot ts = new TrainingSlot(types.get(i % types.size()), dayOfWeek, slot[0], max);
            ts.setEndTime(slot[1]);
            ts.setPrice(BigDecimal.valueOf(personal ? 120 : 60));
            ts.setInstructor(i % 3 == 0 ? null : instructors.get(i % instructors.size()));
            ts.setDisplayOrder(i);
            ts.setActive(true);
            trainingSlotRepository.save(ts);
        }
        log.info("DEV-SEEDER: {} slotów treningowych (pon–pt)", TRAINING_SLOTS);
    }

    private List<EventType> seedTrainingTypes() {
        return List.of(
                saveTrainingType("Trening personalny MMA", "Indywidualny trening 1:1.", 0),
                saveTrainingType("Kickboxing — mała grupa", "Zajęcia w grupie 4–6 osób.", 1),
                saveTrainingType("Boks — technika", "Praca nad techniką uderzeń.", 2),
                saveTrainingType("Grappling", "Zapasy i walka w parterze.", 3)
        );
    }

    private EventType saveTrainingType(String name, String description, int order) {
        EventType et = new EventType(EventCategory.TRAINING, name);
        et.setDescription(description);
        et.setDisplayOrder(order);
        et.setActive(true);
        return eventTypeRepository.save(et);
    }

    private List<Instructor> seedTrainingInstructors() {
        return List.of(
                saveTrainingInstructor("Mateusz", "Adamczyk", "Trener MMA z wieloletnim stażem.", 4),
                saveTrainingInstructor("Paweł", "Górski", "Bokser, instruktor kickboxingu.", 5),
                saveTrainingInstructor("Agnieszka", "Sikora", "Przygotowanie motoryczne.", 6)
        );
    }

    private Instructor saveTrainingInstructor(String firstName, String lastName, String bio, int order) {
        Instructor i = new Instructor(firstName, lastName);
        i.setBio(bio);
        i.setDisplayOrder(order);
        i.setCategories(Set.of(EventCategory.TRAINING));
        i.setActive(true);
        return instructorRepository.save(i);
    }
}
