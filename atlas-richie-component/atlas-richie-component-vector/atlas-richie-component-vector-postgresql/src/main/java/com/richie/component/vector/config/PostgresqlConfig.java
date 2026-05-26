package com.richie.component.vector.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 向量数据库配置属性
 * 用于配置不同向量数据库的连接参数和索引设置
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
@Data
@ConfigurationProperties(prefix = "platform.component.vector.postgresql")
public class PostgresqlConfig {

    /**
     * JDBC连接URL
     */
    private String jdbcUrl = "jdbc:postgresql://localhost:5432/yourdb";

    /**
     * 用户名
     */
    private String username = "youruser";

    /**
     * 密码
     */
    private String password = "yourpassword";

    /**
     * 最大连接数，建议根据服务器和业务量调整
     */
    private Integer maximumPoolSize = 30;

    /**
     * 最小空闲连接数
     */
    private Integer minimumIdle = 10;

    /**
     * 空闲连接最大存活时间（毫秒），10分钟
     */
    private Long idleTimeout = 600_000L;

    /**
     * 连接最大存活时间（毫秒），30分钟
     */
    private Long maxLifetime = 1_800_000L;

    /**
     * 获取连接的最大等待时间（毫秒），30秒
     */
    private Long connectionTimeout = 30_000L;

    /**
     * 校验连接有效性的超时时间（毫秒），5秒
     */
    private Long validationTimeout = 5_000L;

    /**
     * 连接池名称
     */
    private String poolName = "HikariCP";

    /**
     * 自动提交
     */
    private Boolean autoCommit = true;

    /**
     * 连接测试SQL
     */
    private String connectionTestQuery = "SELECT 1";

}
