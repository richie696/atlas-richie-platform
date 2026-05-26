package com.richie.component.logging.enums;

/**
 * 数据库实例类型枚举（用于日志/数据源等配置区分）。
 *
 * @author richie696
 * @since 2022-10-09
 */
public enum DbInstanceTypeEnum {

    /** 引用（共享）数据源 */
    REF,

    /** 独立数据源 */
    STANDALONE

}
