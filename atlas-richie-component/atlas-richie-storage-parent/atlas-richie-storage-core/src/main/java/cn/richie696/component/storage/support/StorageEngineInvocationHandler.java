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
package cn.richie696.component.storage.support;

import cn.richie696.component.storage.core.StorageEngine;
import cn.richie696.component.storage.core.StorageResponseNormalizer;
import cn.richie696.component.storage.enums.StorageEngineEnum;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

/**
 * 存储引擎 JDK 动态代理的共享调用处理器
 * <p>
 * 原先 {@link cn.richie696.component.storage.config.StorageEngineProxyFactoryBean} 与
 * {@link cn.richie696.component.storage.config.StorageEngineRegistry.ProxyHolder}
 * 各自维护一份近乎重复的 {@link InvocationHandler}，本类统一两者的语义：
 * <ul>
 *   <li>Object 方法：toString/hashCode/equals 走身份语义（identity），toString 反映当前 delegate</li>
 *   <li>接口 default 方法：通过真实 delegate 做动态分派，优先命中 Provider 覆写；未覆写时仍执行接口默认实现</li>
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
    private final Supplier<StorageResponseNormalizer> responseNormalizerSupplier;
    private final Lock invocationLock;

    private StorageEngineInvocationHandler(String engineTypeLabel,
                                           Supplier<StorageEngine> delegateSupplier,
                                           Supplier<StorageResponseNormalizer> responseNormalizerSupplier,
                                           Lock invocationLock) {
        this.engineTypeLabel = engineTypeLabel;
        this.delegateSupplier = Objects.requireNonNull(delegateSupplier, "delegateSupplier must not be null");
        this.responseNormalizerSupplier = Objects.requireNonNull(responseNormalizerSupplier, "responseNormalizerSupplier must not be null");
        this.invocationLock = invocationLock;
    }

    /**
     * 为指定引擎类型创建 handler（{@link cn.richie696.component.storage.config.StorageEngineRegistry} 使用）
     */
    public static StorageEngineInvocationHandler forType(StorageEngineEnum engineType,
                                                         Supplier<StorageEngine> delegateSupplier) {
        return forType(engineType, delegateSupplier, () -> StorageResponseNormalizer.standard());
    }

    public static StorageEngineInvocationHandler forType(StorageEngineEnum engineType,
                                                         Supplier<StorageEngine> delegateSupplier,
                                                         Supplier<StorageResponseNormalizer> responseNormalizerSupplier) {
        return new StorageEngineInvocationHandler(
                engineType != null ? engineType.name() : null, delegateSupplier, responseNormalizerSupplier, null);
    }

    public static StorageEngineInvocationHandler forType(StorageEngineEnum engineType,
                                                         Supplier<StorageEngine> delegateSupplier,
                                                         Supplier<StorageResponseNormalizer> responseNormalizerSupplier,
                                                         Lock invocationLock) {
        return new StorageEngineInvocationHandler(
                engineType != null ? engineType.name() : null,
                delegateSupplier,
                responseNormalizerSupplier,
                Objects.requireNonNull(invocationLock, "invocationLock must not be null"));
    }

    /**
     * 创建未指定类型的 handler（{@link cn.richie696.component.storage.config.StorageEngineProxyFactoryBean} 与对象存储统一代理使用）
     */
    public static StorageEngineInvocationHandler unnamed(Supplier<StorageEngine> delegateSupplier) {
        return unnamed(delegateSupplier, () -> StorageResponseNormalizer.standard());
    }

    public static StorageEngineInvocationHandler unnamed(Supplier<StorageEngine> delegateSupplier,
                                                         Supplier<StorageResponseNormalizer> responseNormalizerSupplier) {
        return new StorageEngineInvocationHandler(null, delegateSupplier, responseNormalizerSupplier, null);
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

        if (invocationLock != null) {
            invocationLock.lock();
        }
        try {
            // delegate 的读取和整个业务调用共享同一把读锁。运行期刷新在释放旧引擎前
            // 获取写锁，因此已经进入旧 delegate 的请求一定先执行完毕。
            StorageEngine currentDelegate = delegateSupplier.get();
            if (currentDelegate == null) {
                throw new IllegalStateException(buildUninitializedMessage());
            }
            // 通过真实 delegate 做动态分派。对象存储的统一代理同时暴露
            // DirectStorageEngine，而直传策略的 request 重载在接口中有 fallback
            // default；直接反射调用 delegate 才能优先命中 COS/OSS 等 Provider 的
            // 覆写实现，未覆写时 JVM 会自然执行接口 default。
            Object response = method.invoke(currentDelegate, args);
            StorageResponseNormalizer normalizer = responseNormalizerSupplier.get();
            return normalizer == null ? response : normalizer.normalize(response);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        } finally {
            if (invocationLock != null) {
                invocationLock.unlock();
            }
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
