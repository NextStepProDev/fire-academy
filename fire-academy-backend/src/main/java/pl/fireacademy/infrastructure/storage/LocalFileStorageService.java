package pl.fireacademy.infrastructure.storage;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
public class LocalFileStorageService implements FileStorageService {
    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private final Path rootLocation;
    private final ImageOptimizer imageOptimizer;

    public LocalFileStorageService(@Value("${app.storage.root:./uploads}") String storageRoot,
                                   ImageOptimizer imageOptimizer) {
        this.rootLocation = Path.of(storageRoot);
        this.imageOptimizer = imageOptimizer;
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory", e);
        }
    }

    @Override
    public String store(String folder, MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Niedozwolony typ pliku. Dozwolone: JPG, PNG, WebP");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
            ? originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase()
            : "";
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Niedozwolone rozszerzenie pliku. Dozwolone: .jpg, .jpeg, .png, .webp");
        }

        // Everything checked so far — the content type and the extension — was written by the client.
        // The bytes are the only part of the upload it cannot lie about.
        ImageFormat format = detectFormat(file);
        if (format == null) {
            throw new IllegalArgumentException("Plik nie jest obrazem JPG, PNG ani WebP");
        }
        if (!format.matchesExtension(extension)) {
            // Extension and content disagree. The served content type comes from the extension, so
            // storing this would hand the file back out described as something it is not.
            throw new IllegalArgumentException("Zawartość pliku nie zgadza się z jego rozszerzeniem");
        }

        // A real JPEG/PNG always decodes. One carrying the right signature that then fails to is truncated
        // or damaged; the decoder signals that either by returning nothing or by throwing. Both are the
        // uploader's problem, not the server's, so both become a 400 — this used to surface as a 500.
        ImageOptimizer.OptimizedImage optimized;
        try {
            optimized = imageOptimizer.optimize(file.getInputStream(), extension);
        } catch (IOException e) {
            log.debug("Rejected an undecodable {} upload: {}", extension, e.getMessage());
            throw new IllegalArgumentException("Plik obrazu jest uszkodzony");
        }
        if (format.isDecodable() && !optimized.decoded()) {
            throw new IllegalArgumentException("Plik obrazu jest uszkodzony");
        }

        try {
            String finalExtension = optimized.extension();
            String filename = UUID.randomUUID() + finalExtension;
            Path dir = rootLocation.resolve(folder);
            Files.createDirectories(dir);
            Files.copy(optimized.inputStream(), dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            log.info("Stored file: {}/{}", folder, filename);
            return filename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }
    }

    /** Reads just the signature. Multipart streams can be reopened, so the optimizer still sees the whole file. */
    @Nullable
    private static ImageFormat detectFormat(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return ImageFormat.sniff(in.readNBytes(ImageFormat.HEADER_BYTES));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file", e);
        }
    }

    @Override
    public void delete(String folder, String filename) {
        try {
            Path path = rootLocation.resolve(folder).resolve(filename);
            Files.deleteIfExists(path);
            log.info("Deleted file: {}/{}", folder, filename);
        } catch (IOException e) {
            log.error("Failed to delete file: {}/{}", folder, filename, e);
        }
    }

    @Override
    public boolean exists(String folder, String filename) {
        return Files.exists(rootLocation.resolve(folder).resolve(filename));
    }

    @Override
    public InputStream getInputStream(String folder, String filename) {
        try {
            return Files.newInputStream(rootLocation.resolve(folder).resolve(filename));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file", e);
        }
    }

    @Override
    public long getFileSize(String folder, String filename) {
        try {
            return Files.size(rootLocation.resolve(folder).resolve(filename));
        } catch (IOException e) {
            throw new RuntimeException("Failed to get file size", e);
        }
    }
}
