/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.component.storage.util;

import cn.richie696.component.storage.core.impl.AbstractObjectStorageEngine;
import org.apache.commons.lang3.StringUtils;

/**
 * 对象存储键拼接（与 {@link AbstractObjectStorageEngine#getRealPath} 保持一致）。
 */
public final class ObjectStorageKeys {

    private ObjectStorageKeys() {
    }

    public static String realPath(String basePath, String key) {
        if (StringUtils.isBlank(basePath)) {
            return key;
        }
        String normalizedBasePath = StringUtils.strip(basePath, "/");
        String normalizedKey = StringUtils.stripStart(key, "/");
        // 直传策略会把真实对象键回传给调用方。后续读取、删除或签发下载地址时
        // 必须允许该键直接回传，避免把 basePath 重复拼接为 basePath/basePath/...
        if (normalizedKey.equals(normalizedBasePath) || normalizedKey.startsWith(normalizedBasePath + "/")) {
            return normalizedKey;
        }
        return normalizedBasePath + "/" + normalizedKey;
    }
}
