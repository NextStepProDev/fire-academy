package pl.fireacademy.api.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import pl.fireacademy.BaseIntegrationTest;
import pl.fireacademy.api.trainingcalendar.TrainingPhotoService;
import pl.fireacademy.domain.event.EventCategory;
import pl.fireacademy.domain.event.EventType;
import pl.fireacademy.domain.event.EventTypeRepository;
import pl.fireacademy.domain.training.TrainingSlot;
import pl.fireacademy.domain.training.TrainingSlotRepository;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.domain.user.UserRole;
import pl.fireacademy.infrastructure.storage.FileStorageService;
import pl.fireacademy.infrastructure.storage.StorePolicy;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The GDPR export: everything about the person goes in, nothing about anybody else does, and the
 * columns that are ours rather than theirs (password hash, unsubscribe token) stay home.
 */
class AdminUserDataExportIntegrationTest extends BaseIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private EventTypeRepository eventTypeRepository;
    @Autowired private TrainingSlotRepository trainingSlotRepository;
    @Autowired private FileStorageService storage;
    @Autowired private JdbcTemplate jdbc;

    private UUID idOf(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    private String athlete(String email, String firstName) {
        String token = createUserAndGetToken(email, firstName, "Testowy", UserRole.USER);
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setAthlete(true);
        user.grantTrainingConsent();
        userRepository.saveAndFlush(user);
        return token;
    }

    private String createTraining(String admin, UUID athleteId, String title) throws Exception {
        String created = mockMvc.perform(post("/api/admin/personal-trainings?athleteId=" + athleteId)
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"date\":\"" + LocalDate.now() + "\",\"title\":\"" + title + "\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.id");
    }

    private void weight(String token, String kg) throws Exception {
        mockMvc.perform(put("/api/user/my-training/weights")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{\"date\":\"" + LocalDate.now() + "\",\"weightKg\":" + kg + "}"))
            .andExpect(status().is2xxSuccessful());
    }

    private void note(String path, String token, String body) throws Exception {
        mockMvc.perform(put(path).header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON).content("{\"body\":\"" + body + "\"}"))
            .andExpect(status().isNoContent());
    }

    private String storedPhoto() throws Exception {
        BufferedImage image = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", bytes);
        var file = new MockMultipartFile("file", "p.jpg", "image/jpeg", bytes.toByteArray());
        return storage.storeImage(TrainingPhotoService.FOLDER, file, StorePolicy.TRAINING_PHOTO).filename();
    }

    private Map<String, byte[]> export(String admin, UUID userId) throws Exception {
        byte[] zip = mockMvc.perform(get("/api/admin/users/" + userId + "/export")
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/zip"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
            .andReturn().getResponse().getContentAsByteArray();

        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                entries.put(entry.getName(), in.readAllBytes());
            }
        }
        return entries;
    }

    @Test
    void shouldExportEverythingAboutThePersonAndNothingAboutAnybodyElse() throws Exception {
        String admin = adminToken();
        String marek = athlete("marek@fireacademy.test", "Marek");
        String ola = athlete("ola@fireacademy.test", "Ola");
        UUID marekId = idOf("marek@fireacademy.test");
        UUID olaId = idOf("ola@fireacademy.test");

        EventType type = eventTypeRepository.saveAndFlush(new EventType(EventCategory.TRAINING, "Boks"));
        TrainingSlot slot = new TrainingSlot(type, LocalDate.now().getDayOfWeek().getValue(), LocalTime.of(18, 0), 8);
        slot.setActive(true);
        trainingSlotRepository.saveAndFlush(slot);
        mockMvc.perform(post("/api/user/training-slots/" + slot.getId() + "/enroll")
                .header("Authorization", "Bearer " + marek)
                .contentType(APPLICATION_JSON)
                .content("{\"startMonth\":\"" + YearMonth.now() + "\"}"))
            .andExpect(status().is2xxSuccessful());

        // Money nests under its subscription by id; a type mismatch there would empty it silently.
        UUID subscription = jdbc.queryForObject(
            "SELECT id FROM training_enrollments WHERE user_id = ?", UUID.class, marekId);
        jdbc.update("INSERT INTO training_payments (enrollment_id, year_month, amount) VALUES (?, '2026-01', 199.00)",
            subscription);
        jdbc.update("INSERT INTO training_refunds (enrollment_id, session_date, year_month, amount, type)"
            + " VALUES (?, DATE '2026-01-14', '2026-01', 49.75, 'SESSION')", subscription);

        String marekTraining = createTraining(admin, marekId, "Sparing Marka");
        String olaTraining = createTraining(admin, olaId, "Trening Oli");
        weight(marek, "80.5");
        weight(ola, "61.0");

        String photo = storedPhoto();
        jdbc.update("INSERT INTO training_comments (training_id, author_id, author_is_admin, body, photo_filename)"
            + " VALUES (?::uuid, ?, false, 'boli kolano', ?)", marekTraining, marekId, photo);
        // A photo whose file the expiry sweep already removed: the comment travels, the file cannot.
        String gone = UUID.randomUUID() + ".jpg";
        jdbc.update("INSERT INTO training_comments (training_id, author_id, author_is_admin, body, photo_filename)"
            + " VALUES (?::uuid, ?, false, 'stare zdjecie', ?)", marekTraining, marekId, gone);

        note("/api/admin/notes/training/" + marekTraining, admin, "notatka o treningu Marka");
        note("/api/admin/notes/session/" + slot.getId() + "?athleteId=" + marekId + "&date=" + LocalDate.now(),
            admin, "notatka o zajeciach Marka");
        note("/api/admin/notes/slot/" + slot.getId(), admin, "notatka o grupie");
        note("/api/admin/notes/training/" + olaTraining, admin, "notatka o Oli");

        Map<String, byte[]> zip = export(admin, marekId);

        assertTrue(zip.containsKey("CZYTAJ_MNIE.txt"));
        assertTrue(zip.containsKey("zdjecia/treningi/" + photo));
        assertFalse(zip.containsKey("zdjecia/treningi/" + gone));

        String raw = new String(zip.get("dane.json"), StandardCharsets.UTF_8);
        JsonNode data = JSON.readTree(raw);
        assertEquals("marek@fireacademy.test", data.at("/profil/email").asText());
        assertFalse(data.at("/zgody/zgoda_na_dane_zdrowotne").isNull());
        assertEquals(1, data.at("/treningi_cykliczne").size());
        assertEquals("Boks", data.at("/treningi_cykliczne/0/zajecia").asText());
        assertEquals(199.0, data.at("/treningi_cykliczne/0/wplaty/0/kwota").asDouble());
        assertEquals(49.75, data.at("/treningi_cykliczne/0/zwroty/0/kwota").asDouble());

        JsonNode trainings = data.at("/plan_1na1/treningi_i_zadania");
        assertEquals(1, trainings.size());
        assertEquals("Sparing Marka", trainings.at("/0/tytul").asText());
        assertEquals(2, trainings.at("/0/komentarze").size());
        assertEquals(1, data.at("/plan_1na1/pomiary_wagi").size());
        assertEquals(80.5, data.at("/plan_1na1/pomiary_wagi/0/waga_kg").asDouble());

        // Both notes about Marek, from the training and from the group session; never the slot's.
        assertEquals(2, data.at("/notatki_trenera").size());
        assertTrue(raw.contains("notatka o treningu Marka"));
        assertTrue(raw.contains("notatka o zajeciach Marka"));
        assertFalse(raw.contains("notatka o grupie"));

        // Nothing of Ola's, and none of our own bookkeeping.
        assertFalse(raw.contains("Oli"));
        assertFalse(raw.contains("61.0"));
        assertFalse(raw.contains("password"));
        assertFalse(raw.contains("unsubscribe"));
        String token = jdbc.queryForObject(
            "SELECT marketing_unsubscribe_token::text FROM users WHERE id = ?", String.class, marekId);
        assertFalse(raw.contains(token));
        assertFalse(raw.contains(marekId.toString()));
    }

    @Test
    void shouldIncludeDeletedTrainingSnapshotsAndSurviveTwoCommentsSharingOnePhoto() throws Exception {
        String admin = adminToken();
        athlete("marek@fireacademy.test", "Marek");
        UUID marekId = idOf("marek@fireacademy.test");
        String training = createTraining(admin, marekId, "Trening");

        // Two rows naming the same file: a ZIP refuses a duplicate entry, and by then the headers
        // are out, so one shared photo used to cut the download off half-written.
        String photo = storedPhoto();
        for (String body : new String[] {"pierwszy", "drugi"}) {
            jdbc.update("INSERT INTO training_comments (training_id, author_id, author_is_admin, body, photo_filename)"
                + " VALUES (?::uuid, ?, false, ?, ?)", training, marekId, body, photo);
        }
        // The banner snapshot of a deleted training is the person's data too: title, day and hour.
        jdbc.update("INSERT INTO training_deletions (athlete_id, training_date, title, deleted_by_admin)"
            + " VALUES (?, ?, 'Skasowany sparing', true)", marekId, LocalDate.now().plusDays(3));

        Map<String, byte[]> zip = export(admin, marekId);

        assertTrue(zip.containsKey("zdjecia/treningi/" + photo));
        String raw = new String(zip.get("dane.json"), StandardCharsets.UTF_8);
        assertEquals(1, JSON.readTree(raw).at("/plan_1na1/usuniete_treningi").size());
        assertTrue(raw.contains("Skasowany sparing"));
    }

    @Test
    void shouldAnswer404ForAnUnknownUser() throws Exception {
        mockMvc.perform(get("/api/admin/users/" + UUID.randomUUID() + "/export")
                .header("Authorization", "Bearer " + adminToken()))
            .andExpect(status().isNotFound());
    }

    @Test
    void shouldRefuseANonAdmin() throws Exception {
        String user = userToken();
        mockMvc.perform(get("/api/admin/users/" + regularUserId() + "/export")
                .header("Authorization", "Bearer " + user))
            .andExpect(status().isForbidden());
    }
}
