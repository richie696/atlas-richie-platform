package com.richie.component.dao.tenant;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;

/**
 * 多租户数据源相关常量（表字段名、数据源前缀、主库名等）。
 *
 * @author yuyue
 * @version 1.0
 * @since 2023-09-13
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TenantConstant {

    /**
     * 多数据源前缀
     */
    public static final String DATASOURCE_PREFIX = "ds";

    /**
     * 主库的名字
     */
    public static final String MASTER_DS_NAME = "master";

    /**
     * 字段：主键
     */
    public static final String ID_COLUMN = "id";

    /**
     * 字段：服务名，必须与spring.application.name一致
     */
    public static final String SERVICE_NAME_COLUMN = "service_name";

    /**
     * 字段：多数据源名，主数据库的多数据源名为必须为master，其他的可以自定义，但是要和@DS注解的值一致
     */
    public static final String DS_NAME_COLUMN = "ds_name";

    /**
     * 字段：租户id，以逗号分隔
     */
    public static final String TENANT_CODES_COLUMN = "tenant_codes";

    /**
     * 数据库用户名
     */
    public static final String DB_USERNAME_COLUMN = "db_username";

    /**
     * 数据库密码，加密后的
     */
    public static final String DB_PASSWORD_COLUMN = "db_password";

    /**
     * 数据库参数字段名，如 127.0.0.1:3306/db_name
     */
    public static final String DB_PARAM_COLUMN = "db_param";


}
