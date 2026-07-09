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

/**
 * 存储引擎提供者 SPI
 * <p>
 * 各引擎模块（minio、s3、ftp 等）实现此接口，提供从 StorageProperties 创建引擎的能力。
 * 注册为 Spring Bean 后由 Registry 自动发现。
 *
 * @author richie696
 * @version 1.0
 * @since 2024-01-01
 */
public interface StorageEngineProvider {

    /**
     * 支持的引擎类型
     */
    StorageEngineEnum supportedEngineType();

    /**
     * 根据配置创建存储引擎实例
     * <p>
     * Provider 从 StorageProperties 中提取自身需要的子配置（如 ObjectConfig、FtpConfig）。
     *
     * @param properties 存储配置属性
     * @return 新创建的引擎实例
     */
    StorageEngine create(StorageProperties properties);

    /**
     * 引擎创建后的初始化回调（手动模式下替代 @PostConstruct）
     * <p>
     * 自动模式下由 Spring Bean 生命周期触发 @PostConstruct；
     * 手动模式下引擎不经 Spring 容器，需由 Provider 主动调用完成初始化。
     *
     * @param engine 刚创建的引擎实例
     */
    default void afterPropertiesSet(StorageEngine engine) {
        // 默认空实现，子类可覆盖（如 MinIO 的桶探测）
    }

    /**
     * 销毁引擎时清理 Provider 自行创建的资源（连接池、客户端等）
     *
     * @param engine 待销毁的引擎实例
     */
    default void destroy(StorageEngine engine) {
        // 默认空实现，子类可覆盖
    }

    /**
     * 校验配置参数是否完整
     *
     * @param properties 待校验的配置
     * @throws IllegalArgumentException 参数不完整时抛出
     */
    default void validate(StorageProperties properties) {
        // 默认空实现，子类可覆盖
    }

    /**
     * 判断当前 Provider 是否支持给定的引擎类
     * <p>
     * 用于根据已创建的引擎实例反查其类型（替代脆弱的类名匹配）。
     * 默认实现始终返回 true 表示支持所有类，调用方需要按 {@link #supportedEngineType()}
     * 进一步过滤。各 Provider 应重写此方法返回精确匹配，避免歧义。
     *
     * @param engineClass 引擎实例的 Class 对象
     * @return true 表示当前 Provider 能创建该类型的引擎
     */
    default boolean supports(Class<? extends StorageEngine> engineClass) {
        return true;
    }
}
