package me.tamkungz.codecmedia.internal.image.png;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import me.tamkungz.codecmedia.CodecMediaException;

public final class PngCodec {

    private PngCodec() {
    }

    public static BufferedImage decode(Path input) throws CodecMediaException {
        try {
            BufferedImage image = ImageIO.read(input.toFile());
            if (image == null) {
                throw new CodecMediaException("Unable to decode PNG: " + input);
            }
            validateDecodedImage(image, input);
            return image;
        } catch (IOException e) {
            throw new CodecMediaException("Failed to decode PNG: " + input, e);
        }
    }

    public static PngProbeInfo probe(Path input) throws CodecMediaException {
        try {
            byte[] bytes = Files.readAllBytes(input);
            return PngParser.parse(bytes);
        } catch (IOException e) {
            throw new CodecMediaException("Failed to probe PNG: " + input, e);
        }
    }

    public static void encode(BufferedImage image, Path output) throws CodecMediaException {
        try {
            boolean written = ImageIO.write(image, "png", output.toFile());
            if (!written) {
                throw new CodecMediaException("No PNG writer available in ImageIO runtime");
            }
        } catch (IOException e) {
            throw new CodecMediaException("Failed to encode PNG: " + output, e);
        }
    }

    private static void validateDecodedImage(BufferedImage image, Path input) throws CodecMediaException {
        if (image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new CodecMediaException("Decoded PNG has invalid dimensions: " + input);
        }
        if (image.getColorModel() == null || image.getColorModel().getPixelSize() <= 0) {
            throw new CodecMediaException("Decoded PNG has invalid bit depth: " + input);
        }
        if (image.getRaster() == null || image.getRaster().getNumBands() <= 0) {
            throw new CodecMediaException("Decoded PNG has invalid pixel channels: " + input);
        }
    }
}

