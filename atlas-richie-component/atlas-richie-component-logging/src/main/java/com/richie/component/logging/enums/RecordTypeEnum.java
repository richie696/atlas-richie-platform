package com.richie.component.logging.enums;

/**
 * 记录类型枚举
 *
 * @author richie696
 * @version 1.0
 * @since 2022-12-30 09:49:42
 */
public enum RecordTypeEnum {

    /**
     * 日志文件
     */
    FILE,

    /**
     * Redis缓存（记录到缓存需要在项目中配置Redis，否则运行时会出错导致无法正确记录）
     */
    REDIS,

    /**
     * 消息队列（记录到消息队列需要在项目中配置Messaging组件的配置信息，否则运行时会出错导致无法正确记录）
     */
    MQ

}
