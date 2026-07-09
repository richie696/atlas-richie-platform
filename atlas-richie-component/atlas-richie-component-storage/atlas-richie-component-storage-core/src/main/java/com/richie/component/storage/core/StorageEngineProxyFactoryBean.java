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

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.FactoryBean;

import java.lang.reflect.Proxy;

/**
 * StorageEngine JDK 动态代理 FactoryBean
 * <p>
 * 通过 JDK 动态代理生成 StorageEngine 接口的代理实例，
 * 所有方法调用委托给当前的 delegate（可运行时热替换）。
 * <p>
 * 业务代码注入 StorageEngine 类型时，实际获得此代理对象（@Primary），
 * 确保每次调用都使用最新的引擎实例。
 *
 * @author richie696
 * @version 1.0
 * @since 2024-01-01
 */
@Getter
@Slf4j
public class StorageEngineProxyFactoryBean implements FactoryBean<StorageEngine> {

    /**
     *  获取当前委托的引擎实例
     */
    private volatile StorageEngine delegate;

    @Override
    public StorageEngine getObject() {
        return (StorageEngine) Proxy.newProxyInstance(
                StorageEngine.class.getClassLoader(),
                new Class<?>[]{StorageEngine.class},
                StorageEngineInvocationHandler.unnamed(() -> this.delegate)
        );
    }

    @Override
    public Class<?> getObjectType() {
        return StorageEngine.class;
    }

    /**
     * 设置委托的真实引擎（由 Registry 在初始化/切换时调用）
     */
    public void setDelegate(StorageEngine delegate) {
        StorageEngine old = this.delegate;
        this.delegate = delegate;
        log.info("StorageEngine delegate 已更新: {} -> {}",
                old != null ? old.getClass().getSimpleName() : "null",
                delegate != null ? delegate.getClass().getSimpleName() : "null");
    }

    /**
     * 检查是否已绑定引擎
     */
    public boolean isAvailable() {
        return delegate != null;
    }
}