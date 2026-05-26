package com.richie.component.storage.util;

import com.richie.component.storage.core.impl.AbstractObjectStorageEngine;
import org.apache.commons.lang3.StringUtils;

/**
 * 对象存储键拼接（与 {@link AbstractObjectStorageEngine#getRealPath} 保持一致）。
 */
public final class ObjectStorageKeys {

    private ObjectStorageKeys() {
    }

    public static String realPath(String basePath, String key) {
        return StringUtils.isNotBlank(basePath) ? basePath + "/" + key : key;
    }
}
