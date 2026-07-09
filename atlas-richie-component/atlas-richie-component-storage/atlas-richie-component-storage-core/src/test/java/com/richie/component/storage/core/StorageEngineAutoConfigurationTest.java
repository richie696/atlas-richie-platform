/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.storage.core;

import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.enums.StorageEngineEnum;
import com.richie.context.common.api.SpringContextHolder;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StorageEngineAutoConfiguration 单元测试
 * <p>
 * 覆盖：手动模式 5 个 qualifier-named Bean、自动模式 ApplicationRunner 引擎绑定。
 */
class StorageEngineAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(StorageEngineAutoConfiguration.class));

    @Nested
    class ManualModeTest {

        private final ApplicationContextRunner manualRunner = runner
                .withUserConfiguration(ManualPropertiesConfig.class)
                .withPropertyValues("platform.component.storage.auto-init=false");

        @Test
        void shouldRegisterStorageEngineProxyAndRegistry() {
            manualRunner.run(ctx -> {
                assertThat(ctx).hasSingleBean(StorageEngineProxyFactoryBean.class);
                assertThat(ctx).hasSingleBean(StorageEngineRegistry.class);
            });
        }

        @Test
        void shouldRegisterFiveQualifierNamedBeans() {
            manualRunner.run(ctx -> {
                assertThat(ctx).hasBean("objectStorageEngine");
                assertThat(ctx).hasBean("ftpStorageEngine");
                assertThat(ctx).hasBean("sftpStorageEngine");
                assertThat(ctx).hasBean("smbStorageEngine");
                assertThat(ctx).hasBean("localStorageEngine");
            });
        }

        @Test
        void qualifierBeans_shouldReturnRegistryProxies() {
            manualRunner.run(ctx -> {
                StorageEngineRegistry registry = ctx.getBean(StorageEngineRegistry.class);
                assertThat(ctx.getBean("objectStorageEngine", StorageEngine.class))
                        .isSameAs(registry.getObjectProxy());
                assertThat(ctx.getBean("ftpStorageEngine", StorageEngine.class))
                        .isSameAs(registry.getProxy(StorageEngineEnum.FTP));
                assertThat(ctx.getBean("sftpStorageEngine", StorageEngine.class))
                        .isSameAs(registry.getProxy(StorageEngineEnum.SFTP));
                assertThat(ctx.getBean("smbStorageEngine", StorageEngine.class))
                        .isSameAs(registry.getProxy(StorageEngineEnum.SMB));
                assertThat(ctx.getBean("localStorageEngine", StorageEngine.class))
                        .isSameAs(registry.getProxy(StorageEngineEnum.LOCAL));
            });
        }

        @Test
        void shouldNotRegisterApplicationRunnerInManualMode() {
            manualRunner.run(ctx -> assertThat(ctx).doesNotHaveBean(ApplicationRunner.class));
        }

        @Test
        void proxy_isPrimary() {
            manualRunner.run(ctx -> {
                StorageEngine primary = ctx.getBean(StorageEngine.class);
                assertThat(primary).isNotNull();
            });
        }
    }

    @Nested
    class AutoModeTest {

        private final ApplicationContextRunner autoRunner = runner
                .withUserConfiguration(AutoPropertiesConfig.class)
                .withPropertyValues("platform.component.storage.object.engine=minio");

        @Test
        void shouldRegisterProxyAndRegistryInAutoMode() {
            autoRunner.run(ctx -> {
                assertThat(ctx).hasSingleBean(StorageEngineProxyFactoryBean.class);
                assertThat(ctx).hasSingleBean(StorageEngineRegistry.class);
            });
        }

        @Test
        void shouldNotRegisterDefaultStorageEngineInAutoMode() {
            autoRunner.run(ctx -> assertThat(ctx).doesNotHaveBean("defaultStorageEngine"));
        }

        @Test
        void shouldRegisterApplicationRunner() {
            autoRunner.run(ctx -> assertThat(ctx).hasSingleBean(ApplicationRunner.class));
        }

        @Test
        void applicationRunner_shouldBindObjectEngineAsDefault() {
            autoRunner.run(ctx -> {
                ApplicationRunner runner = ctx.getBean(ApplicationRunner.class);
                runner.run(null);

                StorageEngineRegistry registry = ctx.getBean(StorageEngineRegistry.class);
                assertThat(registry.isInitialized()).isTrue();
                assertThat(registry.getCurrentEngineType()).isEqualTo(StorageEngineEnum.MINIO.name());
                assertThat(registry.getEngine(StorageEngineEnum.MINIO)).isNotNull();
            });
        }

        @Test
        void applicationRunner_shouldBindProxyDelegateToDefaultEngine() {
            autoRunner.run(ctx -> {
                ApplicationRunner runner = ctx.getBean(ApplicationRunner.class);
                runner.run(null);

                StorageEngineProxyFactoryBean proxyFactory = ctx.getBean(StorageEngineProxyFactoryBean.class);
                assertThat(proxyFactory.isAvailable()).isTrue();
            });
        }

        @Test
        void applicationRunner_shouldRegisterAllPresentEngines() {
            autoRunner.run(ctx -> {
                ApplicationRunner runner = ctx.getBean(ApplicationRunner.class);
                runner.run(null);

                StorageEngineRegistry registry = ctx.getBean(StorageEngineRegistry.class);
                assertThat(registry.getRegisteredTypes())
                        .contains(StorageEngineEnum.MINIO,
                                StorageEngineEnum.FTP,
                                StorageEngineEnum.SFTP,
                                StorageEngineEnum.SMB,
                                StorageEngineEnum.LOCAL);
            });
        }

        @Test
        void applicationRunner_fallbackToProvider_whenObjectEngineTypeUnresolved() {
            runner.withUserConfiguration(ProviderBasedConfig.class)
                    .run(ctx -> {
                        ApplicationRunner runner = ctx.getBean(ApplicationRunner.class);
                        runner.run(null);

                        StorageEngineRegistry registry = ctx.getBean(StorageEngineRegistry.class);
                        assertThat(registry.getCurrentEngineType()).isEqualTo(StorageEngineEnum.MINIO.name());
                    });
        }

        @Test
        void healthIndicatorDisabledViaManagementProperty_shouldNotRegisterHealthIndicator() {
            runner.withUserConfiguration(ManualPropertiesConfig.class)
                    .withPropertyValues("management.health.storage.enabled=false")
                    .run(ctx -> assertThat(ctx).doesNotHaveBean(
                            "com.richie.component.storage.observability.StorageHealthIndicator"));
        }

        @Test
        void metricsBinderDisabledViaManagementProperty_shouldNotRegisterMetricsBinder() {
            runner.withUserConfiguration(ManualPropertiesConfig.class)
                    .withPropertyValues("management.metrics.enable.storage=NONE")
                    .run(ctx -> assertThat(ctx).doesNotHaveBean(
                            "com.richie.component.storage.observability.StorageMetricsBinder"));
        }
    }

    @Configuration
    @EnableConfigurationProperties(StorageProperties.class)
    static class ManualPropertiesConfig {
    }

    @Configuration
    @EnableConfigurationProperties(StorageProperties.class)
    static class AutoPropertiesConfig {

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
    @EnableConfigurationProperties(StorageProperties.class)
    @Import(SpringContextHolder.class)
    static class ProviderBasedConfig {

        @Bean("objectStorageEngine")
        StorageEngine objectStorageEngine() {
            return new MinimalStorageEngineSubclass();
        }

        @Bean
        StorageEngineProvider minioProvider() {
            return new MinimalProvider();
        }
    }

    static class MinimalStorageEngineSubclass extends StorageEngineProviderTest.MinimalStorageEngine {
    }

    static class MinimalProvider implements StorageEngineProvider {
        @Override
        public StorageEngineEnum supportedEngineType() {
            return StorageEngineEnum.MINIO;
        }

        @Override
        public StorageEngine create(StorageProperties properties) {
            return new StorageEngineProviderTest.MinimalStorageEngine();
        }

        @Override
        public boolean supports(Class<? extends StorageEngine> engineClass) {
            return MinimalStorageEngineSubclass.class.isAssignableFrom(engineClass);
        }
    }
}
