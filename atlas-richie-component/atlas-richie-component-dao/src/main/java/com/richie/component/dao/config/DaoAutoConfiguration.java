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
package com.richie.component.dao.config;

import com.richie.component.dao.interceptor.BatchUpdateLimitInterceptor;
import com.richie.component.dao.interceptor.PaginationInterceptor;
import com.richie.component.dao.snowflake.IdBuilder;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusPropertiesCustomizer;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * DAO 组件自动配置：注册 MyBatis-Plus 拦截器（分页、乐观锁、批量更新限制、防全表更新）及雪花 ID 生成器。
 *
 * @author richie696
 * @version 1.0
 * @since 2023-09-24
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ComponentScan("com.richie.component.dao")
@EnableConfigurationProperties({DaoProperties.class})
@RequiredArgsConstructor
public class DaoAutoConfiguration {

    /** DAO 组件配置 */
    private final DaoProperties properties;

    /** 雪花 ID 生成器 */
    private final IdBuilder idBuilder;

    /**
     * 注册 MyBatis-Plus 拦截器（乐观锁、批量更新限制、防全表更新、分页）
     *
     * @return MybatisPlusInterceptor 实例
     */
    @Bean
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        var interceptors = new MybatisPlusInterceptor();
        var dbType = properties.getDbType();
        // 插件：乐观锁
         interceptors.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        // 插件：批量更新边界保护（在分页之前添加，确保先检查批量更新限制）
        interceptors.addInnerInterceptor(new BatchUpdateLimitInterceptor(properties));
        // 插件：防全表更新与删除插件
        if (properties.isEnableBlockAttack()) {
            interceptors.addInnerInterceptor(new BlockAttackInnerInterceptor());
        }
        // 插件：切面分页 (warning：如果配置多个插件，切记分页最后添加)
        interceptors.addInnerInterceptor(new PaginationInterceptor(dbType));
        return interceptors;
    }

    /**
     * 设置雪花算法 ID 生成器为 MyBatis-Plus 全局主键生成器
     *
     * @return 配置定制器
     */
    @Bean
    public MybatisPlusPropertiesCustomizer plusPropertiesCustomizer() {
        return plusProperties -> plusProperties.getGlobalConfig().setIdentifierGenerator(_ -> idBuilder.nextId());
    }

}
