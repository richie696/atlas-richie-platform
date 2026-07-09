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
package com.richie.component.storage.core.impl;

import com.richie.component.storage.core.StorageEngine;
import com.richie.context.common.api.SpringContextHolder;

/**
 * 存储引擎抽象基类，提供客户端获取与销毁的通用逻辑。
 *
 * @param <T> 底层客户端类型（如 Ftp、S3Client 等）
 * @author richie696
 * @since 2023-09-05
 */
public abstract class AbstractDestroyEngine<T> implements StorageEngine {

    /**
     * 手动模式下直注的客户端（绕过 SpringContextHolder）。
     * <p>
     * 自动模式下为 null，走 Spring Bean 查找；
     * 手动模式下由 Provider 创建引擎后主动设置，优先使用。
     */
    private T clientOverride;

    /**
     * 获取存储客户端。
     * <p>
     * 优先返回直注的客户端（手动模式），否则从 Spring 容器查找（自动模式）。
     *
     * @param clientClass 客户端类
     * @return 客户端实例
     */
    protected T getClient(Class<T> clientClass) {
        T override = this.clientOverride;
        if (override != null) {
            return override;
        }
        return SpringContextHolder.getBean(clientClass);
    }

    /**
     * 设置直注客户端（由 Provider 在手动模式下调用）。
     *
     * @param client 客户端实例
     */
    public void setClientOverride(T client) {
        this.clientOverride = client;
    }

    /**
     * 销毁/关闭客户端（子类可覆盖以释放连接等资源）。
     *
     * @param t 客户端实例
     */
    void destroy(T t) {
    }

}
