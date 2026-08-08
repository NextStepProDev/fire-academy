package pl.fireacademy.infrastructure.storage;

import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Component
public class ImageOptimizer {
    private static final Logger log = LoggerFactory.getLogger(ImageOptimizer.class);

    private static final int MAX_DIMENSION = 1920;
    private static final double OUTPUT_QUALITY = 0.85;
    private static final long SIZE_THRESHOLD_BYTES = 2 * 1024 * 1024; // 2 MB

    public OptimizedImage optimize(InputStream inputStream, String extension) throws IOException {
        byte[] originalBytes = inputStream.readAllBytes();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(originalBytes));

        if (image == null) {
            // Either WebP (the JDK ships no reader, so it passes through untouched) or a damaged file.
            // Which of the two it is, is settled by the signature check in LocalFileStorageService —
            // this class only reports that nothing could be decoded.
            return new OptimizedImage(new ByteArrayInputStream(originalBytes), extension, false);
        }

        boolean needsResize = image.getWidth() > MAX_DIMENSION || image.getHeight() > MAX_DIMENSION;
        boolean needsCompression = originalBytes.length > SIZE_THRESHOLD_BYTES;

        if (!needsResize && !needsCompression) {
            log.debug("Image already optimized ({}×{}, {} KB) — skipping", image.getWidth(), image.getHeight(), originalBytes.length / 1024);
            return new OptimizedImage(new ByteArrayInputStream(originalBytes), extension, true);
        }

        String outputFormat = outputFormat(extension);
        var baos = new ByteArrayOutputStream();

        var builder = Thumbnails.of(image);
        if (needsResize) {
            builder.size(MAX_DIMENSION, MAX_DIMENSION);
        } else {
            builder.scale(1.0);
        }
        builder.outputQuality(OUTPUT_QUALITY)
                .outputFormat(outputFormat)
                .toOutputStream(baos);

        log.info("Optimized image: {} KB → {} KB ({}×{} → max {}px, format: {})",
                originalBytes.length / 1024, baos.size() / 1024,
                image.getWidth(), image.getHeight(), MAX_DIMENSION, outputFormat);

        String newExtension = "." + outputFormat;
        return new OptimizedImage(new ByteArrayInputStream(baos.toByteArray()), newExtension, true);
    }

    private String outputFormat(String extension) {
        return switch (extension.toLowerCase()) {
            case ".png" -> "png";
            case ".webp" -> "webp";
            default -> "jpg";
        };
    }

    /**
     * @param decoded whether the image could actually be read. False means the bytes never went through a
     *                decoder, so nothing about them has been verified here.
     */
    public record OptimizedImage(InputStream inputStream, String extension, boolean decoded) {}
}
