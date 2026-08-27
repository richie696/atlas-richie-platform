/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.storage.core;

import cn.richie696.component.storage.bean.DirectDownloadPolicy;
import cn.richie696.component.storage.bean.ObjectConfig;
import cn.richie696.component.storage.enums.AclTypeEnum;
import cn.richie696.component.storage.util.ObjectStorageKeys;

import java.time.OffsetDateTime;

/**
 * 统一对象存储运行时代理接口。
 *
 * <p>手工模式下 {@code objectStorageEngine} 是可热切换的 JDK 代理。把服务端
 * 存储能力和直传/直读能力合并到同一个代理类型，避免代理只声明
 * {@link StorageEngine} 时导致 {@link DirectStorageEngine} 注入或类型判断失败。</p>
 */
public interface ObjectStorageEngine extends StorageEngine, DirectStorageEngine {

    /** 返回当前运行时对象存储配置（包含 ACL、endpoint、bucket 和 basePath）。 */
    ObjectConfig objectConfig();

    /** 生成当前对象存储配置下的公开对象地址。 */
    String publicObjectUrl(String key);

    /**
     * 按当前 ACL 策略生成对象读地址。
     *
     * <p>公开读 ACL 返回稳定的公开地址；其他 ACL 一律走 DirectStorageEngine
     * 的预签名能力。该策略属于 Storage 组件，不应由业务服务或渠道 Provider 重复实现。</p>
     */
    default DirectDownloadPolicy resolveDownloadPolicy(String key, int expireSeconds) {
        if (key == null || key.isBlank()) {
            return DirectDownloadPolicy.builder()
                    .success(false)
                    .errorMessage("对象键不能为空")
                    .downloadUrl("")
                    .key(key)
                    .expireAt(OffsetDateTime.now())
                    .fallback(false)
                    .build();
        }
        AclTypeEnum acl = objectConfig().getAcl();
        int safeExpire = Math.max(expireSeconds, 60);
        if (acl == AclTypeEnum.PUBLIC_READ || acl == AclTypeEnum.PUBLIC_READ_WRITE) {
            String realKey = ObjectStorageKeys.realPath(objectConfig().getBasePath(), key);
            return DirectDownloadPolicy.builder()
                    .success(true)
                    .downloadUrl(publicObjectUrl(key))
                    .bucketName(objectConfig().getBucketName())
                    .key(realKey)
                    .expireAt(OffsetDateTime.now().plusSeconds(safeExpire))
                    .fallback(false)
                    .build();
        }
        return issueDirectDownloadPolicy(key, safeExpire);
    }
}
