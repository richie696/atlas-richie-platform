/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.statemachine.config;

import com.richie.component.statemachine.config.properties.RedisStreamConfig;
import com.richie.component.statemachine.config.properties.RulesEngineConfig;
import com.richie.component.statemachine.config.properties.DbPersistenceMode;
import com.richie.component.statemachine.config.properties.StorageType;
import com.richie.component.statemachine.config.properties.ThreadPoolConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 状态机配置属性
 * <p>
 * Spring Boot 配置属性类，用于绑定 application.yml 中的状态机配置。
 * 支持规则引擎、状态机、存储、表达式等各个方面的配置。
 *
 * <p>
 * 注意：此类使用 Lombok {@code @Data} 注解，构造函数由 Lombok 自动生成。
 *
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "platform.component.statemachine")
public class StateMachineProperties {

    /**
     * 是否启用状态机组件
     * <p>
     * 默认为 true，设置为 false 可以禁用状态机组件。
     *
     */
    private boolean enabled = true;

    /**
     * 规则引擎配置
     * <p>
     * 配置 Easy Rules 引擎的行为，包括规则执行策略、优先级、超时等。
     *
     */
    private RulesEngineConfig rulesEngine = new RulesEngineConfig();

    /**
     * 存储类型
     * <p>
     * 选择状态机的存储方式：
     * <ul>
     *   <li>REDIS：使用 Redis 作为状态存储（需要 Redis 支持，支持多实例部署）</li>
     *   <li>ASYNC_DB：直接使用异步线程池持久化到数据库【默认】（不依赖 Redis，单实例内批量处理）</li>
     * </ul>
     *
     */
    private StorageType storageType = StorageType.ASYNC_THREAD;

    /**
     * 数据库持久化模式
     * <p>
     * ASYNC：迁转成功后异步持久化到数据库（默认）
     * SYNC：迁转返回前同步持久化到数据库
     */
    private DbPersistenceMode dbPersistenceMode = DbPersistenceMode.ASYNC;

    /**
     * 是否启用严格模式
     * <p>
     * 启用后若检测到状态机级 SYNC 被状态级 ASYNC 覆盖，将视为非法配置。
     */
    private boolean strictPersistenceMode = true;

    /**
     * 同步持久化失败重试次数
     */
    private int syncRetryTimes = 2;

    /**
     * 同步持久化重试间隔（毫秒）
     */
    private long syncRetryIntervalMs = 100L;

    /**
     * 是否启用状态历史记录
     * <p>
     * 设置为 true 时，每次状态转换都会记录历史记录。
     *
     */
    private boolean enableHistory = true;

    /**
     * 是否启用状态变更事件
     * <p>
     * 设置为 true 时，每次状态转换都会发布 Spring 事件。
     *
     */
    private boolean enableEvents = true;

    /**
     * 状态机配置路径
     * <p>
     * 状态机配置文件（YAML/JSON）的加载路径，支持 classpath 和文件系统路径。
     * 支持 Ant 风格模式，如 classpath:statemachine\/**\/*.yml
     *
     */
    private String configPath = "classpath:statemachine/";

    /**
     * Redis 存储配置实例
     * <p>
     * 仅在 storageType=REDIS 时使用。
     *
     */
    private RedisStreamConfig redisStream = new RedisStreamConfig();

    /**
     * 异步数据库持久化配置实例
     * <p>
     * 仅在 storageType=ASYNC_DB 时使用。
     *
     */
    private ThreadPoolConfig threadPool = new ThreadPoolConfig();

}
