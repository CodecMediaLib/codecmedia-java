package me.tamkungz.codecmedia.internal.convert;

import me.tamkungz.codecmedia.CodecMediaException;
import me.tamkungz.codecmedia.model.ConversionResult;

public interface MediaConverter {

    ConversionResult convert(ConversionRequest request) throws CodecMediaException;
}

