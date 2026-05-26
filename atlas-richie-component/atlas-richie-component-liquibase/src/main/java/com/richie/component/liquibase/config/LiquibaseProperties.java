package com.richie.component.liquibase.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 通用 Liquibase 配置
 *
 * <p>统一通过前缀 {@code platform.component.liquibase.*} 控制是否执行迁移以及 changelog 路径。
 */
@Data
@ConfigurationProperties(prefix = "platform.component.liquibase")
public class LiquibaseProperties {

    /**
     * 默认构造函数（供配置绑定使用）。
     */
    public LiquibaseProperties() {
    }

    /**
     * 是否启用 Liquibase 迁移
     */
    private boolean enable = true;

    /**
     * 支持多 changelog（列表或逗号分隔），可使用 classpath*: 通配扫描
     * 例如：classpath*:db/changelog/**\/db.changelog-master.yaml
     */
    private List<String> changeLogs = List.of("classpath*:db/changelog/**/db.changelog-master.yaml");

    /**
     * 是否启用 changelog 扫描（changeLogs / changeLog），默认 false。
     * 关闭后，仅执行注册表（组件按需注册）的 changelog。
     */
    private boolean enableScan = false;

    /**
     * 是否仅输出 SQL（dry-run），默认 false
     */
    private boolean dryRun = false;
}
