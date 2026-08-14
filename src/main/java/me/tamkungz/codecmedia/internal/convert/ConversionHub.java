package me.tamkungz.codecmedia.internal.convert;

import me.tamkungz.codecmedia.CodecMediaException;
import me.tamkungz.codecmedia.model.ConversionResult;

public interface ConversionHub {

    ConversionResult convert(ConversionRequest request) throws CodecMediaException;
}

