package com.richie.component.storage.support;

import com.richie.component.storage.util.ObjectStorageKeys;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 启动时对「桶 + basePath 前缀」做读写探测所用的临时对象键与内容。
 */
public final class ObjectStorageStartupProbe {

    private static final byte[] CONTENT = "ok".getBytes(StandardCharsets.UTF_8);

    private ObjectStorageStartupProbe() {
    }

    public static String newProbeObjectKey(String basePath) {
        return ObjectStorageKeys.realPath(basePath, ".richie-storage-probe/" + UUID.randomUUID());
    }

    public static byte[] content() {
        return CONTENT;
    }
}
