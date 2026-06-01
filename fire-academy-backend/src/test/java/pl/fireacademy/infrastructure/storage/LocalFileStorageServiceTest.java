package pl.fireacademy.infrastructure.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalFileStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalFileStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new LocalFileStorageService(tempDir.toString(), new ImageOptimizer());
    }

    @Test
    void shouldStoreFileSuccessfully() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getOriginalFilename()).thenReturn("photo.jpg");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream("image data".getBytes()));

        String filename = storageService.store("instructors", file);

        assertNotNull(filename);
        assertTrue(filename.endsWith(".jpg"));
        assertTrue(storageService.exists("instructors", filename));
    }

    @Test
    void shouldStorePngFile() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn("image/png");
        when(file.getOriginalFilename()).thenReturn("image.png");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream("png data".getBytes()));

        String filename = storageService.store("eventtypes", file);

        assertTrue(filename.endsWith(".png"));
        assertTrue(storageService.exists("eventtypes", filename));
    }

    @Test
    void shouldStoreWebpFile() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn("image/webp");
        when(file.getOriginalFilename()).thenReturn("image.webp");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream("webp data".getBytes()));

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
        MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getOriginalFilename()).thenReturn("photo.jpg");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream("data".getBytes()));

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
        MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getOriginalFilename()).thenReturn("photo.jpg");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream("test content".getBytes()));

        String filename = storageService.store("instructors", file);

        try (InputStream is = storageService.getInputStream("instructors", filename)) {
            assertNotNull(is);
            String content = new String(is.readAllBytes());
            assertEquals("test content", content);
        }
    }

    @Test
    void shouldReturnFileSize() throws Exception {
        byte[] data = "file content here".getBytes();
        MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn("image/png");
        when(file.getOriginalFilename()).thenReturn("image.png");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(data));

        String filename = storageService.store("instructors", file);

        assertEquals(data.length, storageService.getFileSize("instructors", filename));
    }

    @Test
    void shouldReturnFalseForNonExistentFile() {
        assertFalse(storageService.exists("instructors", "does-not-exist.jpg"));
    }

    @Test
    void shouldCreateDirectoriesAutomatically() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getOriginalFilename()).thenReturn("photo.jpg");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream("data".getBytes()));

        String filename = storageService.store("new-folder", file);

        assertTrue(Files.exists(tempDir.resolve("new-folder")));
        assertTrue(storageService.exists("new-folder", filename));
    }

    @Test
    void shouldGenerateUniqueFilenames() throws Exception {
        MultipartFile file1 = mock(MultipartFile.class);
        when(file1.getContentType()).thenReturn("image/jpeg");
        when(file1.getOriginalFilename()).thenReturn("same.jpg");
        when(file1.getInputStream()).thenReturn(new ByteArrayInputStream("data1".getBytes()));

        MultipartFile file2 = mock(MultipartFile.class);
        when(file2.getContentType()).thenReturn("image/jpeg");
        when(file2.getOriginalFilename()).thenReturn("same.jpg");
        when(file2.getInputStream()).thenReturn(new ByteArrayInputStream("data2".getBytes()));

        String name1 = storageService.store("instructors", file1);
        String name2 = storageService.store("instructors", file2);

        assertNotEquals(name1, name2);
    }
}
