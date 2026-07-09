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

import com.richie.component.storage.enums.StorageEngineEnum;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 存储引擎 JDK 动态代理的共享调用处理器
 * <p>
 * 原先 {@link StorageEngineProxyFactoryBean} 与 {@link StorageEngineRegistry.ProxyHolder}
 * 各自维护一份近乎重复的 {@link InvocationHandler}，本类统一两者的语义：
 * <ul>
 *   <li>Object 方法：toString/hashCode/equals 走身份语义（identity），toString 反映当前 delegate</li>
 *   <li>接口 default 方法：走 {@link InvocationHandler#invokeDefault(Object, Method, Object[])}</li>
 *   <li>抽象方法：从 supplier 读取当前 delegate，delegate 为空时抛 {@link IllegalStateException}</li>
 *   <li>反射调用异常的 {@link InvocationTargetException} 解包为业务异常</li>
 * </ul>
 * delegate 通过 {@link Supplier} 提供，确保调用时读取最新值（{@code volatile} 语义）。
 *
 * @author richie696
 * @version 1.0
 * @since 2024-01-01
 */
public final class StorageEngineInvocationHandler implements InvocationHandler {

    /**
     * 引擎类型描述（用于 toString 和异常信息），可为 null（未指定类型时）
     */
    private final String engineTypeLabel;

    /**
     * 当前 delegate 的延迟提供者（每次 invoke 调用时读取以支持运行时热切换）
     */
    private final Supplier<StorageEngine> delegateSupplier;

    private StorageEngineInvocationHandler(String engineTypeLabel,
                                           Supplier<StorageEngine> delegateSupplier) {
        this.engineTypeLabel = engineTypeLabel;
        this.delegateSupplier = Objects.requireNonNull(delegateSupplier, "delegateSupplier must not be null");
    }

    /**
     * 为指定引擎类型创建 handler（{@link StorageEngineRegistry} 使用）
     */
    public static StorageEngineInvocationHandler forType(StorageEngineEnum engineType,
                                                         Supplier<StorageEngine> delegateSupplier) {
        return new StorageEngineInvocationHandler(
                engineType != null ? engineType.name() : null, delegateSupplier);
    }

    /**
     * 创建未指定类型的 handler（{@link StorageEngineProxyFactoryBean} 与对象存储统一代理使用）
     */
    public static StorageEngineInvocationHandler unnamed(Supplier<StorageEngine> delegateSupplier) {
        return new StorageEngineInvocationHandler(null, delegateSupplier);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Object 类方法（toString/hashCode/equals）走身份语义
        if (method.getDeclaringClass() == Object.class) {
            if ("toString".equals(method.getName())) {
                return formatToString();
            }
            if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(method.getName())) {
                return proxy == (args == null ? null : args[0]);
            }
        }

        // StorageEngine 接口的 default 方法
        if (method.isDefault()) {
            return InvocationHandler.invokeDefault(proxy, method, args);
        }

        // 委托给真实引擎
        StorageEngine currentDelegate = delegateSupplier.get();
        if (currentDelegate == null) {
            throw new IllegalStateException(buildUninitializedMessage());
        }

        try {
            return method.invoke(currentDelegate, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private String formatToString() {
        StorageEngine d = delegateSupplier.get();
        String delegateDesc = d != null ? d.getClass().getSimpleName() : "null";
        return engineTypeLabel != null
                ? "StorageEngineProxy[" + engineTypeLabel + ", delegate=" + delegateDesc + "]"
                : "StorageEngineProxy[delegate=" + delegateDesc + "]";
    }

    private String buildUninitializedMessage() {
        return engineTypeLabel != null
                ? "存储引擎 [" + engineTypeLabel + "] 未初始化，请先通过管理后台或 YAML 配置创建引擎实例"
                : "存储引擎未初始化，请先通过 YAML 配置或管理后台创建引擎实例";
    }
}