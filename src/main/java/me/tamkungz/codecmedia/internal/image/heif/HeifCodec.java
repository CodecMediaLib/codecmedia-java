package me.tamkungz.codecmedia.internal.image.heif;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import me.tamkungz.codecmedia.CodecMediaException;

/**
 * HEIF/HEIC/AVIF decode/encode bridge backed by {@link ImageIO}.
 * <p>
 * Note: standard JDK runtimes may not include HEIF/AVIF reader/writer SPI by default.
 * A compatible ImageIO plugin may be required at runtime.
 */
public final class HeifCodec {

    private HeifCodec() {
    }

    public static BufferedImage decode(Path input) throws CodecMediaException {
        try {
            BufferedImage image = ImageIO.read(input.toFile());
            if (image == null) {
                throw new CodecMediaException("Unable to decode HEIF/HEIC: " + input);
            }
            validateDecodedImage(image, input);
            return image;
        } catch (IOException e) {
            throw new CodecMediaException("Failed to decode HEIF/HEIC: " + input, e);
        }
    }

    public static HeifProbeInfo probe(Path input) throws CodecMediaException {
        try {
            byte[] bytes = Files.readAllBytes(input);
            return HeifParser.parse(bytes);
        } catch (IOException e) {
            throw new CodecMediaException("Failed to probe HEIF: " + input, e);
        }
    }

    public static void encode(BufferedImage image, Path output, String targetExtension) throws CodecMediaException {
        String formatName = normalizeTargetExtension(targetExtension);
        try {
            boolean written = ImageIO.write(image, formatName, output.toFile());
            if (!written) {
                throw new CodecMediaException("No HEIF writer available in ImageIO runtime"
                        + " (target format: " + formatName.toUpperCase(java.util.Locale.ROOT) + ")");
            }
        } catch (IOException e) {
            throw new CodecMediaException("Failed to encode HEIF/HEIC: " + output, e);
        }
    }

    static String normalizeTargetExtension(String extension) throws CodecMediaException {
        if (extension == null) {
            throw new CodecMediaException("HEIF/HEIC target extension is required");
        }
        String value = extension.trim().toLowerCase(java.util.Locale.ROOT);
        if (value.startsWith(".")) {
            value = value.substring(1);
        }
        if ("heif".equals(value)) {
            return "heif";
        }
        if ("heic".equals(value)) {
            return "heic";
        }
        if ("avif".equals(value)) {
            return "avif";
        }
        throw new CodecMediaException("Unsupported HEIF target extension: " + extension);
    }

    private static void validateDecodedImage(BufferedImage image, Path input) throws CodecMediaException {
        if (image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new CodecMediaException("Decoded HEIF/HEIC has invalid dimensions: " + input);
        }
        if (image.getColorModel() == null || image.getColorModel().getPixelSize() <= 0) {
            throw new CodecMediaException("Decoded HEIF/HEIC has invalid bit depth: " + input);
        }
        if (image.getRaster() == null || image.getRaster().getNumBands() <= 0) {
            throw new CodecMediaException("Decoded HEIF/HEIC has invalid pixel channels: " + input);
        }
    }
}

