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
package com.richie.component.desensitize.core.config;

import com.richie.component.desensitize.core.permission.DefaultMaskPermissionEvaluator;
import com.richie.component.desensitize.core.permission.MaskPermissionEvaluator;
import com.richie.component.desensitize.core.registry.MaskRuleRegistry;
import com.richie.component.desensitize.core.registry.SensitiveKeyRegistry;
import com.richie.component.desensitize.core.serializer.SafeLogSerializer;
import com.richie.component.desensitize.core.service.DefaultMaskingService;
import com.richie.component.desensitize.core.service.DefaultObjectMaskingService;
import com.richie.component.desensitize.core.service.MaskingService;
import com.richie.component.desensitize.core.service.ObjectMaskingService;
import com.richie.component.desensitize.core.strategy.MaskingStrategy;
import com.richie.component.desensitize.core.strategy.MaskingStrategyRegistry;
import com.richie.component.desensitize.core.util.DesensitizeUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import java.util.List;

/**
 * 脱敏 Core 自动配置。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
@AutoConfiguration
@EnableConfigurationProperties(DesensitizeProperties.class)
@ComponentScan(basePackages = "com.richie.component.desensitize.core.strategy")
public class DesensitizeAutoConfiguration {

    /**
     * 注册脱敏策略注册表。
     *
     * @param strategies Spring 容器中的策略列表
     * @return 策略注册表
     */
    @Bean
    @ConditionalOnMissingBean
    public MaskingStrategyRegistry maskingStrategyRegistry(ObjectProvider<List<MaskingStrategy>> strategies) {
        return new MaskingStrategyRegistry(strategies.getIfAvailable(List::of));
    }

    /**
     * 注册敏感键规则注册表。
     *
     * @param properties 脱敏配置
     * @return 敏感键注册表
     */
    @Bean
    @ConditionalOnMissingBean
    public SensitiveKeyRegistry sensitiveKeyRegistry(DesensitizeProperties properties) {
        return new SensitiveKeyRegistry(properties);
    }

    /**
     * 注册字段级脱敏规则注册表。
     *
     * @param properties 脱敏配置
     * @return 规则注册表
     */
    @Bean
    @ConditionalOnMissingBean
    public MaskRuleRegistry maskRuleRegistry(DesensitizeProperties properties) {
        return new MaskRuleRegistry(properties);
    }

    /**
     * 注册默认脱敏权限评估器。
     *
     * @param properties 脱敏配置
     * @return 权限评估器
     */
    @Bean
    @ConditionalOnMissingBean
    public MaskPermissionEvaluator maskPermissionEvaluator(DesensitizeProperties properties) {
        return new DefaultMaskPermissionEvaluator(properties);
    }

    /**
     * 注册统一脱敏服务。
     *
     * @param properties 脱敏配置
     * @param strategyRegistry 策略注册表
     * @param ruleRegistry 字段规则注册表
     * @param sensitiveKeyRegistry 敏感键注册表
     * @param permissionEvaluator 权限评估器
     * @return 脱敏服务实现
     */
    @Bean
    @ConditionalOnMissingBean
    public MaskingService maskingService(
            DesensitizeProperties properties,
            MaskingStrategyRegistry strategyRegistry,
            MaskRuleRegistry ruleRegistry,
            SensitiveKeyRegistry sensitiveKeyRegistry,
            MaskPermissionEvaluator permissionEvaluator) {
        return new DefaultMaskingService(properties, strategyRegistry, ruleRegistry, sensitiveKeyRegistry, permissionEvaluator);
    }

    /**
     * 注册日志安全序列化器。
     *
     * @param maskingService 脱敏服务
     * @param ruleRegistry 字段规则注册表
     * @return 安全序列化器
     */
    @Bean
    @ConditionalOnMissingBean
    public SafeLogSerializer safeLogSerializer(MaskingService maskingService, MaskRuleRegistry ruleRegistry) {
        return new SafeLogSerializer(maskingService, ruleRegistry);
    }

    /**
     * 注册对象脱敏服务。
     *
     * @param safeLogSerializer 日志安全序列化器
     * @return 对象脱敏服务
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMaskingService objectMaskingService(SafeLogSerializer safeLogSerializer) {
        return new DefaultObjectMaskingService(safeLogSerializer);
    }

    /**
     * 注册静态工具初始化器。
     *
     * @param maskingService 脱敏服务
     * @param objectMaskingService 对象脱敏服务
     * @return 初始化器
     */
    @Bean
    public DesensitizeUtilsInitializer desensitizeUtilsInitializer(
            MaskingService maskingService,
            ObjectMaskingService objectMaskingService) {
        return new DesensitizeUtilsInitializer(maskingService, objectMaskingService);
    }

    /**
     * 初始化 {@link DesensitizeUtils} 静态门面。
     *
     * @author @richie696
     * @since 1.0.0
     * @version 1.0
     */
    public static class DesensitizeUtilsInitializer {

        /**
         * 构造时绑定静态工具委托。
         *
         * @param maskingService 脱敏服务
         * @param objectMaskingService 对象脱敏服务
         */
        public DesensitizeUtilsInitializer(MaskingService maskingService, ObjectMaskingService objectMaskingService) {
            DesensitizeUtils.bind(maskingService, objectMaskingService);
        }

        @PostConstruct
        void noop() {
            // bean 存在即保证 bind 已执行
        }
    }
}
