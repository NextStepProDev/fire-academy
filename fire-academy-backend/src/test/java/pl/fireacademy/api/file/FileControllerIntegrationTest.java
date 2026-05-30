package pl.fireacademy.api.file;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.fireacademy.BaseIntegrationTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FileControllerIntegrationTest extends BaseIntegrationTest {

    private String testFilename;

    @BeforeEach
    void setUpFile() throws Exception {
        String storageRoot = System.getProperty("java.io.tmpdir") + "/fire-academy-test-uploads";
        Path dir = Path.of(storageRoot, "instructors");
        Files.createDirectories(dir);

        testFilename = UUID.randomUUID() + ".jpg";
        Files.write(dir.resolve(testFilename), "fake image data".getBytes());
    }

    @Test
    void shouldServeExistingFile() throws Exception {
        mockMvc.perform(get("/api/files/instructors/" + testFilename))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("image/jpeg"))
            .andExpect(header().exists("Cache-Control"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void shouldReturn404ForNonExistentFile() throws Exception {
        String fakeUuid = UUID.randomUUID() + ".jpg";
        mockMvc.perform(get("/api/files/instructors/" + fakeUuid))
            .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectInvalidFolderName() throws Exception {
        mockMvc.perform(get("/api/files/INVALID/" + testFilename))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectFolderWithNumbers() throws Exception {
        mockMvc.perform(get("/api/files/folder123/" + testFilename))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectFolderWithSpecialChars() throws Exception {
        mockMvc.perform(get("/api/files/fold-er/" + testFilename))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectInvalidFilename() throws Exception {
        mockMvc.perform(get("/api/files/instructors/not-a-uuid.jpg"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectUnsupportedExtension() throws Exception {
        mockMvc.perform(get("/api/files/instructors/" + UUID.randomUUID() + ".gif"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectPathTraversal() throws Exception {
        mockMvc.perform(get("/api/files/instructors/../../../etc/passwd"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldSetCacheControlHeader() throws Exception {
        mockMvc.perform(get("/api/files/instructors/" + testFilename))
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=604800")));
    }

    @Test
    void shouldDetectPngContentType() throws Exception {
        String pngFilename = UUID.randomUUID() + ".png";
        String storageRoot = System.getProperty("java.io.tmpdir") + "/fire-academy-test-uploads";
        Files.write(Path.of(storageRoot, "instructors", pngFilename), "png data".getBytes());

        mockMvc.perform(get("/api/files/instructors/" + pngFilename))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("image/png"));
    }

    @Test
    void shouldDetectWebpContentType() throws Exception {
        String webpFilename = UUID.randomUUID() + ".webp";
        String storageRoot = System.getProperty("java.io.tmpdir") + "/fire-academy-test-uploads";
        Files.write(Path.of(storageRoot, "instructors", webpFilename), "webp data".getBytes());

        mockMvc.perform(get("/api/files/instructors/" + webpFilename))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("image/webp"));
    }
}
