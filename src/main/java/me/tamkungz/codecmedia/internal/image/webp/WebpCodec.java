package me.tamkungz.codecmedia.internal.image.webp;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import me.tamkungz.codecmedia.CodecMediaException;

/**
 * WebP image decode/encode bridge backed by {@link ImageIO}.
 * <p>
 * Note: standard JDK runtimes do not always include a WebP reader/writer SPI.
 * A compatible plugin must be present in the runtime for decode/encode to work.
 */
public final class WebpCodec {

    private WebpCodec() {
    }

    public static BufferedImage decode(Path input) throws CodecMediaException {
        try {
            BufferedImage image = ImageIO.read(input.toFile());
            if (image == null) {
                throw new CodecMediaException("No WEBP reader available in ImageIO runtime");
            }
            validateDecodedImage(image, input);
            return image;
        } catch (IOException e) {
            throw new CodecMediaException("Failed to decode WebP: " + input, e);
        }
    }

    public static void encode(BufferedImage image, Path output) throws CodecMediaException {
        try {
            boolean written = ImageIO.write(image, "webp", output.toFile());
            if (!written) {
                throw new CodecMediaException("No WEBP writer available in ImageIO runtime");
            }
        } catch (IOException e) {
            throw new CodecMediaException("Failed to encode WebP: " + output, e);
        }
    }

    private static void validateDecodedImage(BufferedImage image, Path input) throws CodecMediaException {
        if (image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new CodecMediaException("Decoded WebP has invalid dimensions: " + input);
        }
        if (image.getColorModel() == null || image.getColorModel().getPixelSize() <= 0) {
            throw new CodecMediaException("Decoded WebP has invalid bit depth: " + input);
        }
        if (image.getRaster() == null || image.getRaster().getNumBands() <= 0) {
            throw new CodecMediaException("Decoded WebP has invalid pixel channels: " + input);
        }
    }
}

