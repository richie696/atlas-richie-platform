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
package cn.richie696.component.storage.core;

import cn.richie696.component.storage.enums.StorageEngineEnum;
import cn.richie696.component.storage.support.StorageEngineInvocationHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * {@link StorageEngineInvocationHandler} 共享代理处理器单元测试
 */
@DisplayName("StorageEngineInvocationHandler 测试")
class StorageEngineInvocationHandlerTest {

    /**
     * 创建同时实现 {@link StorageEngine} 与 {@link DirectStorageEngine} 的共享代理：
     * handler 的 delegate supplier 类型是 {@code Supplier<StorageEngine>}，而业务方法
     * （如 existsObject）已拆分到 DirectStorageEngine，所以代理必须覆盖两个接口。
     */
    private DirectStorageEngine newProxy(java.lang.reflect.InvocationHandler handler) {
        return (DirectStorageEngine) Proxy.newProxyInstance(
                StorageEngine.class.getClassLoader(),
                new Class<?>[]{StorageEngine.class, DirectStorageEngine.class},
                handler);
    }

    /** 同时实现两个接口的 delegate mock，保证 existsObject 等 Direct 方法可被 stub。 */
    private StorageEngine newDelegateMock() {
        return mock(StorageEngine.class, withSettings().extraInterfaces(DirectStorageEngine.class));
    }

    @Nested
    @DisplayName("工厂方法")
    class FactoryMethods {

        @Test
        @DisplayName("forType 不允许 delegateSupplier 为 null")
        void forType_shouldRejectNullSupplier() {
            assertThatThrownBy(() -> StorageEngineInvocationHandler.forType(StorageEngineEnum.MINIO, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("unnamed 不允许 delegateSupplier 为 null")
        void unnamed_shouldRejectNullSupplier() {
            assertThatThrownBy(() -> StorageEngineInvocationHandler.unnamed(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("forType 接受 null 引擎类型（降级为 unnamed 模式）")
        void forType_acceptsNullType() {
            StorageEngineInvocationHandler h =
                    StorageEngineInvocationHandler.forType(null, () -> null);
            assertThat(h).isNotNull();
        }
    }

    @Nested
    @DisplayName("Object 方法")
    class ObjectMethods {

        @Test
        @DisplayName("toString: 已绑定 delegate 时包含类型与 delegate 类名")
        void toString_withDelegate() {
            StorageEngine mockDelegate = newDelegateMock();
            DirectStorageEngine proxy = newProxy(
                    StorageEngineInvocationHandler.forType(StorageEngineEnum.MINIO, () -> mockDelegate));

            String str = proxy.toString();

            assertThat(str).contains("MINIO").contains("delegate=");
        }

        @Test
        @DisplayName("toString: 未绑定 delegate 时显示 null")
        void toString_withoutDelegate() {
            DirectStorageEngine proxy = newProxy(
                    StorageEngineInvocationHandler.forType(StorageEngineEnum.FTP, () -> null));

            assertThat(proxy.toString()).contains("FTP").contains("delegate=null");
        }

        @Test
        @DisplayName("toString: unnamed handler 不带类型前缀")
        void toString_unnamed() {
            DirectStorageEngine proxy = newProxy(
                    StorageEngineInvocationHandler.unnamed(() -> null));

            assertThat(proxy.toString()).isEqualTo("StorageEngineProxy[delegate=null]");
        }

        @Test
        @DisplayName("hashCode: 返回 proxy 的 identityHashCode")
        void hashCode_identity() {
            DirectStorageEngine proxy = newProxy(
                    StorageEngineInvocationHandler.unnamed(() -> null));

            assertThat(proxy.hashCode()).isEqualTo(System.identityHashCode(proxy));
        }

        @Test
        @DisplayName("equals: 与自身 identity 相等")
        void equals_identity() {
            DirectStorageEngine proxy = newProxy(
                    StorageEngineInvocationHandler.unnamed(() -> null));

            assertThat(proxy.equals(proxy)).isTrue();
            assertThat(proxy.equals(new Object())).isFalse();
            assertThat(proxy.equals(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("委托与未初始化")
    class DelegateBehavior {

        @Test
        @DisplayName("未初始化时调用业务方法抛 IllegalStateException，含类型标签")
        void invoke_uninitialized_throwsWithType() {
            DirectStorageEngine proxy = newProxy(
                    StorageEngineInvocationHandler.forType(StorageEngineEnum.MINIO, () -> null));

            assertThatThrownBy(() -> proxy.existsObject("any"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("MINIO")
                    .hasMessageContaining("未初始化");
        }

        @Test
        @DisplayName("未初始化时调用业务方法抛 IllegalStateException（unnamed）")
        void invoke_uninitialized_unnamed_throwsGenericMessage() {
            DirectStorageEngine proxy = newProxy(
                    StorageEngineInvocationHandler.unnamed(() -> null));

            assertThatThrownBy(() -> proxy.existsObject("any"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("未初始化");
        }

        @Test
        @DisplayName("委托调用返回 delegate 的真实结果")
        void invoke_delegate() {
            StorageEngine delegate = newDelegateMock();
            when(((DirectStorageEngine) delegate).existsObject("key1")).thenReturn(true);
            DirectStorageEngine proxy = newProxy(
                    StorageEngineInvocationHandler.unnamed(() -> delegate));

            assertThat(proxy.existsObject("key1")).isTrue();
        }

        @Test
        @DisplayName("每次 invoke 重新读取 supplier，支持热切换")
        void invoke_hotSwitch() {
            StorageEngine stub1 = newDelegateMock();
            when(((DirectStorageEngine) stub1).existsObject("a")).thenReturn(true);
            AtomicReference<StorageEngine> ref = new AtomicReference<>(stub1);
            DirectStorageEngine proxy = newProxy(
                    StorageEngineInvocationHandler.forType(StorageEngineEnum.MINIO, ref::get));

            assertThat(proxy.existsObject("a")).isTrue();
            ref.set(null);
            assertThatThrownBy(() -> proxy.existsObject("b"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("InvocationTargetException 解包为业务异常")
        void invoke_unwrapInvocationTargetException() {
            StorageEngine throwing = newDelegateMock();
            doThrow(new IllegalStateException("inner cause"))
                    .when(((DirectStorageEngine) throwing)).existsObject("x");

            DirectStorageEngine proxy = newProxy(
                    StorageEngineInvocationHandler.unnamed(() -> throwing));

            assertThatThrownBy(() -> proxy.existsObject("x"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("inner cause");
        }
    }
}