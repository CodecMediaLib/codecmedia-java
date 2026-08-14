package me.tamkungz.codecmedia.internal.image.tiff;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import me.tamkungz.codecmedia.CodecMediaException;

/**
 * TIFF decode/encode bridge backed by {@link ImageIO}.
 * <p>
 * Note: standard JDK runtimes may not include TIFF reader/writer SPI by default.
 * A compatible ImageIO plugin may be required at runtime.
 */
public final class TiffCodec {

    private TiffCodec() {
    }

    public static BufferedImage decode(Path input) throws CodecMediaException {
        try {
            BufferedImage image = ImageIO.read(input.toFile());
            if (image == null) {
                throw new CodecMediaException("Unable to decode TIFF: " + input);
            }
            validateDecodedImage(image, input);
            return image;
        } catch (IOException e) {
            throw new CodecMediaException("Failed to decode TIFF: " + input, e);
        }
    }

    public static TiffProbeInfo probe(Path input) throws CodecMediaException {
        try {
            byte[] bytes = Files.readAllBytes(input);
            return TiffParser.parse(bytes);
        } catch (IOException e) {
            throw new CodecMediaException("Failed to probe TIFF: " + input, e);
        }
    }

    public static void encode(BufferedImage image, Path output) throws CodecMediaException {
        try {
            boolean written = ImageIO.write(image, "tiff", output.toFile());
            if (!written) {
                throw new CodecMediaException("No TIFF writer available in ImageIO runtime");
            }
        } catch (IOException e) {
            throw new CodecMediaException("Failed to encode TIFF: " + output, e);
        }
    }

    private static void validateDecodedImage(BufferedImage image, Path input) throws CodecMediaException {
        if (image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new CodecMediaException("Decoded TIFF has invalid dimensions: " + input);
        }
        if (image.getColorModel() == null || image.getColorModel().getPixelSize() <= 0) {
            throw new CodecMediaException("Decoded TIFF has invalid bit depth: " + input);
        }
        if (image.getRaster() == null || image.getRaster().getNumBands() <= 0) {
            throw new CodecMediaException("Decoded TIFF has invalid pixel channels: " + input);
        }
    }
}

