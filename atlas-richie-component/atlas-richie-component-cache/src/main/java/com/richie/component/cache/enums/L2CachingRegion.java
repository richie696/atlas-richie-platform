package com.richie.component.cache.enums;

import com.richie.component.cache.local.manage.CacheName;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 全局缓存的二级缓存区域名称枚举类
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-17 17:11:39
 */
@Getter
@RequiredArgsConstructor
public enum L2CachingRegion implements CacheName {

    /** 全局缓存区域（通用 KV 等） */
    GLOBAL_CACHE("global_cache"),

    /** 访问日志专用缓存区域 */
    ACCESS_LOG("access_log");

    /** 区域名称（对应 JSR-107 Cache 名称） */
    private final String cache;

}
