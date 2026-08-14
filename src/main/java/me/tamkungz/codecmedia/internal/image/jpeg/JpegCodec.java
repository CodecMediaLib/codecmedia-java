package me.tamkungz.codecmedia.internal.image.jpeg;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import me.tamkungz.codecmedia.CodecMediaException;

public final class JpegCodec {

    private JpegCodec() {
    }

    public static BufferedImage decode(Path input) throws CodecMediaException {
        try {
            BufferedImage image = ImageIO.read(input.toFile());
            if (image == null) {
                throw new CodecMediaException("Unable to decode JPEG: " + input);
            }
            validateDecodedImage(image, input);
            return image;
        } catch (IOException e) {
            throw new CodecMediaException("Failed to decode JPEG: " + input, e);
        }
    }

    public static JpegProbeInfo probe(Path input) throws CodecMediaException {
        try {
            byte[] bytes = Files.readAllBytes(input);
            return JpegParser.parse(bytes);
        } catch (IOException e) {
            throw new CodecMediaException("Failed to probe JPEG: " + input, e);
        }
    }

    public static void encode(BufferedImage image, Path output) throws CodecMediaException {
        BufferedImage rgb = ensureRgb(image);
        try {
            boolean written = ImageIO.write(rgb, "jpg", output.toFile());
            if (!written) {
                throw new CodecMediaException("No JPEG writer available in ImageIO runtime");
            }
        } catch (IOException e) {
            throw new CodecMediaException("Failed to encode JPEG: " + output, e);
        }
    }

    private static BufferedImage ensureRgb(BufferedImage input) {
        if (input.getType() == BufferedImage.TYPE_INT_RGB) {
            return input;
        }
        BufferedImage rgb = new BufferedImage(input.getWidth(), input.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(input, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    private static void validateDecodedImage(BufferedImage image, Path input) throws CodecMediaException {
        if (image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new CodecMediaException("Decoded JPEG has invalid dimensions: " + input);
        }
        if (image.getColorModel() == null || image.getColorModel().getPixelSize() <= 0) {
            throw new CodecMediaException("Decoded JPEG has invalid bit depth: " + input);
        }
        if (image.getRaster() == null || image.getRaster().getNumBands() <= 0) {
            throw new CodecMediaException("Decoded JPEG has invalid pixel channels: " + input);
        }
    }
}

