/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.storage.core;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.richie.component.storage.config.StorageEngineAutoConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StorageEngineAutoConfiguration 启动绑定审计日志测试
 * <p>
 * 验证自动模式下默认引擎绑定到 Proxy 时，审计日志包含 actor=auto-init / reason=startup，
 * 便于运维侧审计启动期的引擎配置。
 */
class StorageEngineAutoConfigurationAuditTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(StorageEngineAutoConfiguration.class));

    private Logger storageLogger;
    private ListAppender<ILoggingEvent> listAppender;
    private Level previousLevel;

    @BeforeEach
    void setUpAppender() {
        storageLogger = (Logger) LoggerFactory.getLogger(StorageEngineAutoConfiguration.class);
        previousLevel = storageLogger.getLevel();
        storageLogger.setLevel(Level.INFO);
        listAppender = new ListAppender<>();
        listAppender.start();
        storageLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDownAppender() {
        storageLogger.detachAppender(listAppender);
        storageLogger.setLevel(previousLevel);
        listAppender.stop();
    }

    @Test
    void autoInitRunner_shouldEmitAuditLogWithActorAndReason() {
        runner.withUserConfiguration(AuditPropertiesConfig.class)
                .withPropertyValues("platform.component.storage.object.engine=minio")
                .run(ctx -> {
                    ApplicationRunner autoInitRunner = ctx.getBean(ApplicationRunner.class);
                    autoInitRunner.run(null);

                    assertThat(listAppender.list)
                            .anyMatch(event -> event.getFormattedMessage().contains("actor=auto-init")
                                    && event.getFormattedMessage().contains("reason=startup")
                                    && event.getLevel() == Level.INFO);
                });
    }

    @Test
    void autoInitRunner_shouldLogDefaultEngineClassName() {
        runner.withUserConfiguration(AuditPropertiesConfig.class)
                .withPropertyValues("platform.component.storage.object.engine=minio")
                .run(ctx -> {
                    ApplicationRunner autoInitRunner = ctx.getBean(ApplicationRunner.class);
                    autoInitRunner.run(null);

                    assertThat(listAppender.list)
                            .anyMatch(event -> event.getFormattedMessage().contains("MinimalStorageEngine")
                                    && event.getFormattedMessage().contains("actor=auto-init"));
                });
    }

    @Test
    void autoInitRunner_whenNoEngines_shouldEmitWarnAudit() {
        // auto-init=true 但无任何引擎 Bean 注册
        runner.withUserConfiguration(AuditEmptyConfig.class).run(ctx -> {
            ApplicationRunner autoInitRunner = ctx.getBean(ApplicationRunner.class);
            autoInitRunner.run(null);

            assertThat(listAppender.list)
                    .anyMatch(event -> event.getLevel() == Level.WARN
                            && event.getFormattedMessage().contains("actor=auto-init")
                            && event.getFormattedMessage().contains("reason=startup"));
        });
    }

    @Test
    void autoInitRunner_shouldLogAllNonDefaultEngines() {
        runner.withUserConfiguration(AuditAllEnginesConfig.class)
                .withPropertyValues("platform.component.storage.object.engine=minio")
                .run(ctx -> {
                    ApplicationRunner autoInitRunner = ctx.getBean(ApplicationRunner.class);
                    autoInitRunner.run(null);

                    // MINIO 是默认引擎（非默认走 "对象引擎" 分支），其余 4 个走 registerIfPresent
                    long autoInitLogs = listAppender.list.stream()
                            .filter(e -> e.getFormattedMessage().contains("actor=auto-init"))
                            .count();
                    assertThat(autoInitLogs).isGreaterThanOrEqualTo(5L);
                });
    }

    @Configuration
    @EnableConfigurationProperties(com.richie.component.storage.config.StorageProperties.class)
    static class AuditPropertiesConfig {

        @Bean("objectStorageEngine")
        StorageEngine objectStorageEngine() {
            return new StorageEngineProviderTest.MinimalStorageEngine();
        }
    }

    @Configuration
    @EnableConfigurationProperties(com.richie.component.storage.config.StorageProperties.class)
    static class AuditAllEnginesConfig {

        @Bean("objectStorageEngine")
        StorageEngine objectStorageEngine() {
            return new StorageEngineProviderTest.MinimalStorageEngine();
        }

        @Bean("ftpStorageEngine")
        StorageEngine ftpStorageEngine() {
            return new StorageEngineProviderTest.MinimalStorageEngine();
        }

        @Bean("sftpStorageEngine")
        StorageEngine sftpStorageEngine() {
            return new StorageEngineProviderTest.MinimalStorageEngine();
        }

        @Bean("smbStorageEngine")
        StorageEngine smbStorageEngine() {
            return new StorageEngineProviderTest.MinimalStorageEngine();
        }

        @Bean("localStorageEngine")
        StorageEngine localStorageEngine() {
            return new StorageEngineProviderTest.MinimalStorageEngine();
        }
    }

    @Configuration
    @EnableConfigurationProperties(com.richie.component.storage.config.StorageProperties.class)
    static class AuditEmptyConfig {
    }
}