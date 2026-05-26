package com.richie.component.mqtt.enums;


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
    MEMORY("mqttMemoryStoreHandler"),

    /**
     * Redis 缓存
     */
    REDIS("mqttRedisStoreHandler");

    /**
     * 处理器名称
     */
    private final String handlerName;

}
