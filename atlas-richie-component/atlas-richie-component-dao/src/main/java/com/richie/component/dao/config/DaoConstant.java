package com.richie.component.dao.config;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * DAO 组件常量（配置前缀、多租户开关 key 等）。
 *
 * @author yuy
 * @version 1.0
 * @since 2023-10-13
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DaoConstant {

    public static final String DAO_PREFIX = "platform.component.dao";

    public static final String DAO_ENABLE_TENANT_PREFIX = "enable-tenant";

}
