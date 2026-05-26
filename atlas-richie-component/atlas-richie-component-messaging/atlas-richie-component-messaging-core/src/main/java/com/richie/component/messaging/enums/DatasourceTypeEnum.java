package com.richie.component.messaging.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 数据源类型枚举
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-16 18:06:00
 */
@Getter
@RequiredArgsConstructor
public enum DatasourceTypeEnum {

    /**
     * 内存数据库
     */
    MEMORY("memoryStoreHandler"),

    /**
     * Redis 缓存
     */
    REDIS("redisStoreHandler");

    private final String serviceName;

}
