package com.richie.component.storage.bean.image;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ImageFormat {

    JPG("jpg"),
    PNG("png"),
    BMP("bmp"),
    WEBP("webp"),
    TIFF("tiff"),
    GIF("gif");

    private final String format;

}
