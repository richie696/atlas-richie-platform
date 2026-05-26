package com.richie.component.dao.tenant;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

/**
 * 多租户数据源配置（租户表名、忽略表、租户列、DB URL 模板、Topic 等）。
 *
 * @author yuyue
 * @version 1.0
 * @since 2023-09-13
 */
@Data
@ConfigurationProperties(prefix = "spring.datasource.dynamic.tenant")
public class TenantProperties implements Serializable {

    /**
     * 忽略租户隔离的表名
     */
    private Set<String> ignoreTenantTables = Collections.emptySet();

    /**
     * 表名：租户数据源
     */
    private String tenantTableName = "tenant_datasource";

    /**
     * 数据库连接url模板
     */
    private String dbUrlTemplate = "jdbc:mysql://%s?useUnicode=true&characterEncoding=utf8&useSSL=false&zeroDateTimeBehavior=convertToNull&allowMultiQueries=true&autoReconnect=true&useLegacyDatetimeCode=false&serverTimezone=UTC";

    /**
     * 租户的字段名
     */
    private String tenantIdColumn = "tenant_code";

    /**
     * 新增租户的topic
     */
    private String addTenantTopic = "add_tenant_topic";

    /**
     * 配置datasource master如果找不到就使用随机的
     */
    private boolean useRandomMaster = true;
}
