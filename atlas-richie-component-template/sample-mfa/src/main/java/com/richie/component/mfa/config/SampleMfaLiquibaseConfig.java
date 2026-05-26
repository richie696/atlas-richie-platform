package com.richie.component.mfa.config;

import com.richie.component.liquibase.migration.ChangeLogRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * sample-mfa 自己的 Liquibase 变更集注册
 * <p>
 * 用于创建 user_info 示例表，后续可与 mfa_user_info 表关联使用。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SampleMfaLiquibaseConfig {

    private final ChangeLogRegistry changeLogRegistry;

    @PostConstruct
    public void registerChangeLog() {
        log.info("Registering sample-mfa user_info Liquibase changelog...");
        changeLogRegistry.add("db/changelog/sample-mfa/db.changelog-master.yaml");
    }
}

