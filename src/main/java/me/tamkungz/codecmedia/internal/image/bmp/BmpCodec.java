package me.tamkungz.codecmedia.internal.image.bmp;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import me.tamkungz.codecmedia.CodecMediaException;

public final class BmpCodec {

    private BmpCodec() {
    }

    public static BufferedImage decode(Path input) throws CodecMediaException {
        try {
            BufferedImage image = ImageIO.read(input.toFile());
            if (image == null) {
                throw new CodecMediaException("Unable to decode BMP: " + input);
            }
            validateDecodedImage(image, input);
            return image;
        } catch (IOException e) {
            throw new CodecMediaException("Failed to decode BMP: " + input, e);
        }
    }

    public static BmpProbeInfo probe(Path input) throws CodecMediaException {
        try {
            byte[] bytes = Files.readAllBytes(input);
            return BmpParser.parse(bytes);
        } catch (IOException e) {
            throw new CodecMediaException("Failed to probe BMP: " + input, e);
        }
    }

    public static void encode(BufferedImage image, Path output) throws CodecMediaException {
        try {
            boolean written = ImageIO.write(image, "bmp", output.toFile());
            if (!written) {
                throw new CodecMediaException("No BMP writer available in ImageIO runtime");
            }
        } catch (IOException e) {
            throw new CodecMediaException("Failed to encode BMP: " + output, e);
        }
    }

    private static void validateDecodedImage(BufferedImage image, Path input) throws CodecMediaException {
        if (image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new CodecMediaException("Decoded BMP has invalid dimensions: " + input);
        }
        if (image.getColorModel() == null || image.getColorModel().getPixelSize() <= 0) {
            throw new CodecMediaException("Decoded BMP has invalid bit depth: " + input);
        }
        if (image.getRaster() == null || image.getRaster().getNumBands() <= 0) {
            throw new CodecMediaException("Decoded BMP has invalid pixel channels: " + input);
        }
    }
}

