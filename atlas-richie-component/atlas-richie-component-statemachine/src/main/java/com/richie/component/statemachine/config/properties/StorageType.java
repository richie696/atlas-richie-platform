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
package com.richie.component.statemachine.config.properties;

/**
 * 存储类型枚举
 */
public enum StorageType {
    /**
     * Redis 存储模式
     * <p>
     * 使用 Redis 作为状态存储，需要 Redis 支持。
     * 支持多实例部署、分布式消费组、Redis Stream 异步持久化等特性。
     * 
     */
    REDIS,

    /**
     * 异步线程存储模式
     * <p>
     * 直接使用异步线程池持久化到数据库，不依赖 Redis。
     * 适合无法使用 Redis 或 Redis 版本过低的场景。
     * 单实例内批量处理，不支持多实例部署。
     * 
     */
    ASYNC_THREAD
}
