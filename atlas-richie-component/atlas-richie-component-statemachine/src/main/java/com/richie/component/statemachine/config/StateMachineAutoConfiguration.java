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
package com.richie.component.statemachine.config;

import com.richie.component.liquibase.migration.ChangeLogRegistry;
import com.richie.component.statemachine.config.properties.DbPersistenceMode;
import com.richie.component.statemachine.config.properties.RedisStreamConfig;
import com.richie.component.statemachine.config.properties.RulesEngineConfig;
import com.richie.component.statemachine.config.properties.ThreadPoolConfig;
import com.richie.component.statemachine.model.StateMachineModel;
import com.richie.component.statemachine.registry.StateMachineRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 状态机自动配置
 * <p>
 * Spring Boot 自动配置类，负责在应用启动时加载状态机配置文件并注册状态机模型。
 * 支持从 classpath 或文件系统加载 YAML 和 JSON 格式的状态机配置文件。
 *
 *
 * @author richie696
 * @since 1.0.0
 */
@Configuration
@EnableConfigurationProperties({
        StateMachineProperties.class,
        RulesEngineConfig.class,
        RedisStreamConfig.class,
        ThreadPoolConfig.class,
        RedisStreamConfig.RedisStreamDbReplicationConfig.class,
        RedisStreamConfig.StorageCleanupConfig.class
})
@EnableScheduling
@ComponentScan(basePackages = "com.richie.component.statemachine")
@ConditionalOnProperty(prefix = "platform.component.statemachine", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class StateMachineAutoConfiguration {

    /**
     * 状态机配置属性
     */
    private final StateMachineProperties properties;

    /**
     * 状态机配置加载器
     */
    private final StateMachineConfigLoader configLoader;

    /**
     * 状态机注册表
     */
    private final StateMachineRegistry registry;

    /**
     * 状态机定义注册表
     */
    private final StateMachineDefinitionRegistry definitionRegistry;

    /** Liquibase 变更日志注册表，用于注册状态机表结构脚本 */
    private final ChangeLogRegistry changeLogRegistry;

    /**
     * 注册状态机模块的数据库变更日志路径。
     */
    @PostConstruct
    public void init() {
        log.info("Do StateMachineAutoConfiguration init.");
        changeLogRegistry.add("db/changelog/statemachine/db.changelog-master.yaml");
    }

    /**
     * 状态机启动引导运行器
     * <p>
     * 在应用启动时执行，负责：
     * 1. 从配置路径加载所有状态机配置文件（YAML/JSON）
     * 2. 将定义注册到定义注册表
     * 3. 转换为状态机模型并注册到状态机注册表
     *
     *
     * @return ApplicationRunner Bean
     */
    @Bean
    public ApplicationRunner stateMachineBootstrapRunner() {
        return args -> {
            String basePath = properties.getConfigPath();
            if (basePath == null || basePath.isBlank()) {
                basePath = "classpath:statemachine/";
            }
            String yamlPattern1 = basePath.endsWith("/") ? basePath + "**/*.yml" : basePath + "/**/*.yml";
            String yamlPattern2 = basePath.endsWith("/") ? basePath + "**/*.yaml" : basePath + "/**/*.yaml";
            String jsonPattern = basePath.endsWith("/") ? basePath + "**/*.json" : basePath + "/**/*.json";

            var definitions = new java.util.ArrayList<StateMachineDefinition>();
            definitions.addAll(configLoader.loadFromYaml(yamlPattern1));
            definitions.addAll(configLoader.loadFromYaml(yamlPattern2));
            definitions.addAll(configLoader.loadFromJson(jsonPattern));

            int count = 0;
            for (StateMachineDefinition definition : definitions) {
                try {
                    validatePersistenceModeConfig(definition);
                    // 缓存定义
                    definitionRegistry.register(definition);
                    // 转换为模型并注册
                    StateMachineModel model = configLoader.convertToStateMachine(definition);
                    registry.register(model);
                    count++;
                } catch (Exception e) {
                    log.error("注册状态机失败: {}", definition.getName(), e);
                }
            }
            log.info("状态机装载完成, 成功注册 {} 个状态机", count);
        };
    }

    private void validatePersistenceModeConfig(StateMachineDefinition definition) {
        DbPersistenceMode machineMode = StatePersistenceModeResolver.resolveMachineMode(definition, properties.getDbPersistenceMode());
        if (machineMode != DbPersistenceMode.SYNC || definition.getStates() == null) {
            return;
        }
        for (StateMachineDefinition.StateDefinition stateDefinition : definition.getStates()) {
            DbPersistenceMode stateMode = stateDefinition.getStatePersistenceMode();
            if (stateMode == DbPersistenceMode.ASYNC) {
                String msg = "检测到非法持久化配置（SYNC 降级为 ASYNC）: stateMachine=%s, state=%s"
                        .formatted(definition.getName(), stateDefinition.getName());
                if (properties.isStrictPersistenceMode()) {
                    throw new IllegalStateException(msg);
                }
                log.warn("{}，将按 SYNC 继续运行", msg);
            }
        }
    }
}
