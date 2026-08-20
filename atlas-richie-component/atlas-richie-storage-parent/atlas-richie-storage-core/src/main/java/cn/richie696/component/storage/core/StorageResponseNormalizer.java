/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package cn.richie696.component.storage.core;

import cn.richie696.component.storage.bean.DirectDownloadPolicy;
import cn.richie696.component.storage.bean.DirectUploadPolicy;
import cn.richie696.component.storage.bean.ObjectStatResponse;
import cn.richie696.component.storage.bean.UploadResponse;

import java.util.Map;

/**
 * Provider 返回值的统一化契约。
 *
 * <p>Provider 可以在这里对本厂商 SDK 的差异做最后一层加工；代理只把本契约定义的
 * Storage DTO 暴露给业务代码，不会把 COS/TOS/OBS 等 SDK 类型向上泄漏。</p>
 */
@FunctionalInterface
public interface StorageResponseNormalizer {

    /** 将 Provider 返回的统一 DTO 规范化后交给业务代理。 */
    Object normalize(Object response);

    /** 默认规范化器：补齐可选集合字段，保持失败结果可安全解析。 */
    static StorageResponseNormalizer standard() {
        return response -> {
            if (response instanceof DirectUploadPolicy policy) {
                return policy.toBuilder()
                        .headers(emptyIfNull(policy.getHeaders()))
                        .formFields(emptyIfNull(policy.getFormFields()))
                        .build();
            }
            if (response instanceof DirectDownloadPolicy policy) {
                return policy;
            }
            if (response instanceof ObjectStatResponse stat) {
                return stat.toBuilder()
                        .checksums(emptyIfNull(stat.getChecksums()))
                        .userMetadata(emptyIfNull(stat.getUserMetadata()))
                        .build();
            }
            if (response instanceof UploadResponse upload) {
                return upload.toBuilder().checksums(emptyIfNull(upload.getChecksums())).build();
            }
            return response;
        };
    }

    private static <K, V> Map<K, V> emptyIfNull(Map<K, V> value) {
        return value == null ? Map.of() : value;
    }
}
