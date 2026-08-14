package me.tamkungz.codecmedia.internal.convert;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import me.tamkungz.codecmedia.CodecMediaException;
import me.tamkungz.codecmedia.internal.image.bmp.BmpCodec;
import me.tamkungz.codecmedia.internal.image.heif.HeifCodec;
import me.tamkungz.codecmedia.internal.image.jpeg.JpegCodec;
import me.tamkungz.codecmedia.internal.image.png.PngCodec;
import me.tamkungz.codecmedia.internal.image.tiff.TiffCodec;
import me.tamkungz.codecmedia.internal.image.webp.WebpCodec;
import me.tamkungz.codecmedia.model.ConversionResult;

public final class ImageTranscodeConverter implements MediaConverter {

    @Override
    public ConversionResult convert(ConversionRequest request) throws CodecMediaException {
        String source = normalize(request.sourceExtension());
        String target = normalize(request.targetExtension());
        if (!isSupportedImage(source) || !isSupportedImage(target)) {
            throw new CodecMediaException("image->image transcoding currently supports png/jpg/jpeg/webp/bmp/tiff/heif/heic/avif");
        }

        Path output = request.output();
        try {
            Path parent = output.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (java.io.IOException e) {
            throw new CodecMediaException("Failed to prepare output path: " + output, e);
        }
        if (Files.exists(output) && !request.options().overwrite()) {
            throw new CodecMediaException("Output already exists and overwrite is disabled: " + output);
        }

        BufferedImage inputImage = decodeByExtension(source, request.input());
        encodeByExtension(target, inputImage, output);

        return new ConversionResult(output, target, true);
    }

    private static boolean isSupportedImage(String ext) {
        return "png".equals(ext)
                || "jpg".equals(ext)
                || "jpeg".equals(ext)
                || "webp".equals(ext)
                || "bmp".equals(ext)
                || "tif".equals(ext)
                || "tiff".equals(ext)
                || "heif".equals(ext)
                || "heic".equals(ext)
                || "avif".equals(ext);
    }

    private static BufferedImage decodeByExtension(String extension, Path input) throws CodecMediaException {
        return switch (extension) {
            case "png" -> PngCodec.decode(input);
            case "jpg", "jpeg" -> JpegCodec.decode(input);
            case "webp" -> WebpCodec.decode(input);
            case "bmp" -> BmpCodec.decode(input);
            case "tif", "tiff" -> TiffCodec.decode(input);
            case "heif", "heic", "avif" -> HeifCodec.decode(input);
            default -> throw new CodecMediaException("Unsupported source image extension: " + extension);
        };
    }

    private static void encodeByExtension(String extension, BufferedImage inputImage, Path output) throws CodecMediaException {
        switch (extension) {
            case "png" -> PngCodec.encode(inputImage, output);
            case "jpg", "jpeg" -> JpegCodec.encode(inputImage, output);
            case "webp" -> WebpCodec.encode(inputImage, output);
            case "bmp" -> BmpCodec.encode(inputImage, output);
            case "tif", "tiff" -> TiffCodec.encode(inputImage, output);
            case "heif", "heic", "avif" -> HeifCodec.encode(inputImage, output, extension);
            default -> throw new CodecMediaException("Unsupported target image extension: " + extension);
        }
    }

    private static String normalize(String ext) {
        if (ext == null) {
            return "";
        }
        String value = ext.trim().toLowerCase(java.util.Locale.ROOT);
        return value.startsWith(".") ? value.substring(1) : value;
    }
}

