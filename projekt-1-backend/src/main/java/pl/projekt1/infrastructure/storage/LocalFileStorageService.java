package pl.projekt1.infrastructure.storage;

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
import java.util.UUID;

@Service
public class LocalFileStorageService implements FileStorageService {
    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);
    private final Path rootLocation;

    public LocalFileStorageService(@Value("${app.storage.root:./uploads}") String storageRoot) {
        this.rootLocation = Path.of(storageRoot);
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory", e);
        }
    }

    @Override
    public String store(String folder, MultipartFile file) {
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf('.'))
                : ".jpg";
            String filename = UUID.randomUUID() + extension;
            Path dir = rootLocation.resolve(folder);
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            log.info("Stored file: {}/{}", folder, filename);
            return filename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
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
