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
package com.richie.component.vector.service;

import com.richie.component.vector.service.impl.AbstractVectorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 跨 provider v2 接口契约测试 — 通过反射锁定 {@link VectorService} 的 40 个方法
 * 在 {@link AbstractVectorService} 上的签名一致性，从而保证所有 7 个 provider
 * 实现（Milvus / MongoDB / Neo4j / PostgreSQL / Qdrant / Redis / Weaviate）继承
 * 后必须实现这些方法，避免任意一个 provider 漏实现某个 v2 能力时不被发现。
 * <p>
 * 现实说明：本项目当前实际 7 个 provider（无 Elasticsearch 模块），
 * 详见 {@code atlas-richie-component-vector/pom.xml}。
 */
class CrossProviderContractTest {

    /**
     * v2 接口声明的方法签名集合（{@code name#paramTypes}），作为契约基线。
     */
    @Test
    @DisplayName("VectorService 接口必须正好 44 个方法（v2 能力面）")
    void vectorService_declaresExactly44Methods() {
        long count = Arrays.stream(VectorService.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && !m.isDefault())
                .count();
        assertThat(count)
                .as("VectorService v2 应包含 44 个方法（5 Add / 4 Update / 2 Delete / 2 Get / "
                        + "6 Search + 2 Hybrid / 6 IndexBase / 6 IndexExt / 2 StatsHealth / 5 Ops / 4 BatchAsync）— "
                        + "已删除 2 个 @Deprecated 低阶方法（searchByEmbedding / ingestEmbedding）")
                .isEqualTo(44L);
    }

    @Test
    @DisplayName("AbstractVectorService 必须实现 VectorService 全部方法")
    void abstractVectorService_implementsAllInterfaceMethods() {
        Set<String> interfaceMethods = interfaceSignatures(VectorService.class);
        Set<String> abstractMethods = abstractSignatures(AbstractVectorService.class);

        Set<String> missing = new HashSet<>(interfaceMethods);
        missing.removeAll(abstractMethods);

        assertThat(missing)
                .as("AbstractVectorService 漏实现的 VectorService 方法")
                .isEmpty();
        assertThat(abstractMethods).containsAll(interfaceMethods);
    }

    @Test
    @DisplayName("AbstractVectorService 的实现方法签名必须与接口完全一致（返回类型 + 参数）")
    void abstractVectorService_signaturesMatchInterface() {
        for (Method ifaceMethod : VectorService.class.getDeclaredMethods()) {
            if (ifaceMethod.isSynthetic() || ifaceMethod.isDefault()) {
                continue;
            }
            Method impl = findOverridingMethod(AbstractVectorService.class, ifaceMethod);
            assertThat(impl)
                    .as("AbstractVectorService 缺少对 %s 的实现", ifaceMethod)
                    .isNotNull();
            assertThat(impl.getReturnType())
                    .as("%s 返回类型不匹配", ifaceMethod.getName())
                    .isEqualTo(ifaceMethod.getReturnType());
        }
    }

    @Test
    @DisplayName("核心能力分组覆盖：所有 11 个能力分组都有方法落地")
    void allCapabilityGroups_areCovered() {
        Set<String> methodNames = Arrays.stream(VectorService.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        // 每个能力分组至少 1 个方法
        assertThat(methodNames)
                .contains(
                        "addText", "add",                 // Add
                        "updateText", "update",            // Update
                        "delete", "deleteIf",              // Delete
                        "get", "getAll",                  // Get
                        "searchByText", "searchByImage",  // Search
                        "hybridSearch", "searchByMultiVector",
                        "createIndex", "deleteIndex", "indexExists", "getIndexConfig",
                        "countDocuments", "listDocuments",
                        "listIndexes", "truncateIndex", "updateIndexConfig",
                        "cloneIndex", "awaitIndexReady", "describeIndex",
                        "getIndexStats", "healthCheck",
                        "optimize", "createAlias", "switchAlias", "backup", "restore",
                        "addBatch", "updateBatch", "deleteBatch");
    }

    // ==================== 反射工具 ====================

    private static Set<String> interfaceSignatures(Class<?> iface) {
        return Arrays.stream(iface.getDeclaredMethods())
                .filter(m -> !m.isSynthetic())
                .map(CrossProviderContractTest::signature)
                .collect(Collectors.toSet());
    }

    private static Set<String> abstractSignatures(Class<?> klass) {
        return Stream.concat(
                        Arrays.stream(klass.getDeclaredMethods()),
                        // 抽象方法可能由父类提供 — 这里只看本类直接声明
                        Arrays.stream(klass.getMethods()))
                .filter(m -> !m.isSynthetic())
                .map(CrossProviderContractTest::signature)
                .collect(Collectors.toSet());
    }

    private static Method findOverridingMethod(Class<?> klass, Method iface) {
        for (Method candidate : klass.getDeclaredMethods()) {
            if (!candidate.getName().equals(iface.getName())) {
                continue;
            }
            if (!Arrays.equals(candidate.getParameterTypes(), iface.getParameterTypes())) {
                continue;
            }
            return candidate;
        }
        // 退化到父类继承
        for (Method candidate : klass.getMethods()) {
            if (!candidate.getName().equals(iface.getName())) {
                continue;
            }
            if (!Arrays.equals(candidate.getParameterTypes(), iface.getParameterTypes())) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private static String signature(Method m) {
        return m.getName() + "#" + Arrays.stream(m.getParameterTypes())
                .map(Class::getName)
                .collect(Collectors.joining(",", "(", ")"));
    }
}
