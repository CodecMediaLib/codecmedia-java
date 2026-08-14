package me.tamkungz.codecmedia.internal.audio.ogg;

record OggPageHeader(
        int version,
        int headerType,
        long granulePosition,
        long serialNumber,
        long sequenceNumber,
        int segmentCount,
        int payloadSize,
        int totalPageSize,
        int headerSize
) {
}

