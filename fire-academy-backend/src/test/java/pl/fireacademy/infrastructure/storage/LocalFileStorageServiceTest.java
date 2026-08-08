package pl.fireacademy.infrastructure.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalFileStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalFileStorageService storageService;

    /**
     * Real encoded images, because uploads are now accepted on their signature. These tests used to post
     * strings like "image data" and pass — which was the hole, not a convenience.
     */
    private static byte[] image(String format) throws IOException {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }

    /** A minimal RIFF/WEBP container — the JDK has no WebP encoder, and none is needed for a signature. */
    private static byte[] webp() {
        byte[] bytes = new byte[64];
        System.arraycopy("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, bytes, 0, 4);
        System.arraycopy("WEBP".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, bytes, 8, 4);
        return bytes;
    }

    private static MultipartFile upload(String filename, String contentType, byte[] content) {
        return new MockMultipartFile("file", filename, contentType, content);
    }

    @BeforeEach
    void setUp() {
        storageService = new LocalFileStorageService(tempDir.toString(), new ImageOptimizer());
    }

    @Test
    void shouldStoreFileSuccessfully() throws Exception {
        MultipartFile file = upload("photo.jpg", "image/jpeg", image("jpg"));

        String filename = storageService.store("instructors", file);

        assertNotNull(filename);
        assertTrue(filename.endsWith(".jpg"));
        assertTrue(storageService.exists("instructors", filename));
    }

    @Test
    void shouldStorePngFile() throws Exception {
        MultipartFile file = upload("image.png", "image/png", image("png"));

        String filename = storageService.store("eventtypes", file);

        assertTrue(filename.endsWith(".png"));
        assertTrue(storageService.exists("eventtypes", filename));
    }

    @Test
    void shouldStoreWebpFile() throws Exception {
        MultipartFile file = upload("image.webp", "image/webp", webp());

        String filename = storageService.store("instructors", file);

        assertTrue(filename.endsWith(".webp"));
    }

    @Test
    void shouldRejectDisallowedContentType() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn("application/pdf");

        assertThrows(IllegalArgumentException.class, () -> storageService.store("instructors", file));
    }

    @Test
    void shouldRejectNullContentType() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> storageService.store("instructors", file));
    }

    @Test
    void shouldRejectDisallowedExtension() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getOriginalFilename()).thenReturn("file.gif");

        assertThrows(IllegalArgumentException.class, () -> storageService.store("instructors", file));
    }

    @Test
    void shouldRejectFileWithoutExtension() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getOriginalFilename()).thenReturn("filename");

        assertThrows(IllegalArgumentException.class, () -> storageService.store("instructors", file));
    }

    @Test
    void shouldDeleteFile() throws Exception {
        MultipartFile file = upload("photo.jpg", "image/jpeg", image("jpg"));

        String filename = storageService.store("instructors", file);
        assertTrue(storageService.exists("instructors", filename));

        storageService.delete("instructors", filename);
        assertFalse(storageService.exists("instructors", filename));
    }

    @Test
    void shouldNotThrowWhenDeletingNonExistentFile() {
        assertDoesNotThrow(() -> storageService.delete("instructors", "nonexistent.jpg"));
    }

    @Test
    void shouldReturnFileInputStream() throws Exception {
        byte[] stored = image("jpg");
        MultipartFile file = upload("photo.jpg", "image/jpeg", stored);

        String filename = storageService.store("instructors", file);

        try (InputStream is = storageService.getInputStream("instructors", filename)) {
            assertNotNull(is);
            assertArrayEquals(stored, is.readAllBytes());
        }
    }

    @Test
    void shouldReturnFileSize() throws Exception {
        byte[] data = image("png");
        MultipartFile file = upload("image.png", "image/png", data);

        String filename = storageService.store("instructors", file);

        assertEquals(data.length, storageService.getFileSize("instructors", filename));
    }

    @Test
    void shouldReturnFalseForNonExistentFile() {
        assertFalse(storageService.exists("instructors", "does-not-exist.jpg"));
    }

    @Test
    void shouldCreateDirectoriesAutomatically() throws Exception {
        MultipartFile file = upload("photo.jpg", "image/jpeg", image("jpg"));

        String filename = storageService.store("new-folder", file);

        assertTrue(Files.exists(tempDir.resolve("new-folder")));
        assertTrue(storageService.exists("new-folder", filename));
    }

    @Test
    void shouldGenerateUniqueFilenames() throws Exception {
        MultipartFile file1 = upload("same.jpg", "image/jpeg", image("jpg"));
        MultipartFile file2 = upload("same.jpg", "image/jpeg", image("jpg"));

        String name1 = storageService.store("instructors", file1);
        String name2 = storageService.store("instructors", file2);

        assertNotEquals(name1, name2);
    }
}
