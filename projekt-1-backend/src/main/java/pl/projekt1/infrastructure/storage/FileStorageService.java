package pl.projekt1.infrastructure.storage;

import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;

public interface FileStorageService {
    String store(String folder, MultipartFile file);
    void delete(String folder, String filename);
    boolean exists(String folder, String filename);
    InputStream getInputStream(String folder, String filename);
    long getFileSize(String folder, String filename);
}
