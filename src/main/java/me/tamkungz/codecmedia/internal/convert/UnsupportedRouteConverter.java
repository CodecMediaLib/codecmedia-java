package me.tamkungz.codecmedia.internal.convert;

import me.tamkungz.codecmedia.CodecMediaException;
import me.tamkungz.codecmedia.model.ConversionResult;

public final class UnsupportedRouteConverter implements MediaConverter {

    private final String message;

    public UnsupportedRouteConverter(String message) {
        this.message = message;
    }

    @Override
    public ConversionResult convert(ConversionRequest request) throws CodecMediaException {
        throw new CodecMediaException(message);
    }
}

