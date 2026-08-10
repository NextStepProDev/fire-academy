package pl.fireacademy.infrastructure.storage;

import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

@Component
public class ImageOptimizer {
    private static final Logger log = LoggerFactory.getLogger(ImageOptimizer.class);

    /**
     * Ceiling on the decoded picture, checked BEFORE any pixels are read.
     * <p>
     * Byte size says nothing about decode cost: a heavily compressed 1 MB JPEG can describe a
     * 12000x12000 image, and {@code ImageIO.read} would then allocate roughly 4 bytes per pixel —
     * ~576 MB inside a container capped at 384 MB. One request would be enough to get the backend
     * OOM-killed. The header carries the dimensions, so the size can be settled without decoding.
     * 40 MPx leaves every real camera and phone screenshot comfortably inside.
     */
    private static final long MAX_PIXELS = 40_000_000L;

    /**
     * How an image is re-encoded on the way in.
     *
     * @param maxDimension  longest side after resizing
     * @param quality       encoder quality, 0..1
     * @param sizeThreshold above this many bytes the image is recompressed even when it fits
     * @param forceReencode always run the encoder, even for a small, correctly sized image. Set for
     *                      uploads where the stored bytes must be the server's own output rather
     *                      than whatever the client sent.
     * @param outputFormat  {@code null} keeps the source format, otherwise forces this one
     */
    public record Profile(int maxDimension, double quality, long sizeThreshold,
                          boolean forceReencode, String outputFormat) {

        /** Catalog artwork: photos to look at, so quality wins over bytes. */
        public static final Profile DEFAULT = new Profile(1920, 0.85, 2 * 1024 * 1024, false, "");

        /**
         * A screenshot from a sports watch. Smaller and harder-compressed than catalog artwork
         * because it is read once and then deleted after 30 days — but not so hard that the numbers
         * on the dial stop being legible, which is the entire point of sending it.
         * <p>
         * {@code forceReencode} is what makes the stored file the server's own JPEG: metadata (EXIF,
         * and the GPS tag inside it) does not survive the encoder, and the dimensions become facts
         * rather than claims.
         */
        public static final Profile TRAINING_PHOTO = new Profile(1280, 0.75, 0, true, "jpg");
    }

    public OptimizedImage optimize(InputStream inputStream, String extension) throws IOException {
        return optimize(inputStream, extension, Profile.DEFAULT);
    }

    public OptimizedImage optimize(InputStream inputStream, String extension, Profile profile) throws IOException {
        byte[] originalBytes = inputStream.readAllBytes();

        // Dimensions first, from the header alone. Decoding a bomb to find out how big it is would
        // be the very allocation this check exists to prevent.
        long pixels = readPixelCount(originalBytes);
        if (pixels > MAX_PIXELS) {
            log.debug("Rejected an upload declaring {} pixels (max {})", pixels, MAX_PIXELS);
            throw new IllegalArgumentException("Obraz ma zbyt duże wymiary");
        }

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(originalBytes));

        if (image == null) {
            // Either WebP (the JDK ships no reader, so it passes through untouched) or a damaged file.
            // Which of the two it is, is settled by the signature check in LocalFileStorageService —
            // this class only reports that nothing could be decoded.
            return new OptimizedImage(new ByteArrayInputStream(originalBytes), extension, false, 0, 0);
        }

        boolean needsResize = image.getWidth() > profile.maxDimension() || image.getHeight() > profile.maxDimension();
        boolean needsCompression = originalBytes.length > profile.sizeThreshold();

        if (!needsResize && !needsCompression && !profile.forceReencode()) {
            log.debug("Image already optimized ({}×{}, {} KB) — skipping", image.getWidth(), image.getHeight(), originalBytes.length / 1024);
            return new OptimizedImage(new ByteArrayInputStream(originalBytes), extension, true,
                    image.getWidth(), image.getHeight());
        }

        String outputFormat = profile.outputFormat().isEmpty() ? outputFormat(extension) : profile.outputFormat();
        var baos = new ByteArrayOutputStream();

        var builder = Thumbnails.of(image);
        if (needsResize) {
            builder.size(profile.maxDimension(), profile.maxDimension());
        } else {
            builder.scale(1.0);
        }
        builder.outputQuality(profile.quality())
                .outputFormat(outputFormat)
                .toOutputStream(baos);

        byte[] encoded = baos.toByteArray();
        // Thumbnailator preserves the aspect ratio, so re-reading the header is the only way to learn
        // the exact output size — and the client needs it to reserve the box before the bytes arrive.
        int width = image.getWidth();
        int height = image.getHeight();
        if (needsResize) {
            double ratio = Math.min((double) profile.maxDimension() / width, (double) profile.maxDimension() / height);
            width = Math.max(1, (int) Math.round(width * ratio));
            height = Math.max(1, (int) Math.round(height * ratio));
        }

        log.info("Optimized image: {} KB → {} KB ({}×{} → {}×{}, format: {})",
                originalBytes.length / 1024, encoded.length / 1024,
                image.getWidth(), image.getHeight(), width, height, outputFormat);

        String newExtension = "." + outputFormat;
        return new OptimizedImage(new ByteArrayInputStream(encoded), newExtension, true, width, height);
    }

    /**
     * Width × height straight from the file header, without decoding a single pixel.
     *
     * @return 0 when no reader recognises the bytes — WebP takes this path, and so does a damaged
     *         file; both are settled later by the signature check and the decode attempt.
     */
    private static long readPixelCount(byte[] bytes) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (in == null) {
                return 0;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return 0;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                return (long) reader.getWidth(0) * reader.getHeight(0);
            } catch (IOException | RuntimeException e) {
                // A header too broken to state its own size. Not our call to make here — the decode
                // attempt below turns it into the same 400 as every other damaged upload.
                log.debug("Could not read image dimensions from header: {}", e.getMessage());
                return 0;
            } finally {
                reader.dispose();
            }
        }
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
     * @param width   stored dimensions, both 0 when {@code decoded} is false
     */
    public record OptimizedImage(InputStream inputStream, String extension, boolean decoded,
                                 int width, int height) {}
}
