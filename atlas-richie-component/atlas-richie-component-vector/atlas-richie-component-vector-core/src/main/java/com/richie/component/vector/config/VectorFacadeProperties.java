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
package com.richie.component.vector.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量库门面（{@code VectorOperationsFacade}）配置属性。
 * <p>
 * 用于驱动跨 provider 调度：默认 provider、回退链、单 provider 内的重试次数与退避基线。
 *
 * <h2>配置示例</h2>
 * <pre>{@code
 * platform:
 *   component:
 *     vector:
 *       facade:
 *         default-provider: redisVectorServiceImpl
 *         fallback-chain:
 *           - milvusVectorServiceImpl
 *           - weaviateVectorServiceImpl
 *         max-retries: 2
 *         retry-backoff-millis: 100
 * }</pre>
 *
 * @author richie696
 * @since 2.1.0
 */
@Data
@ConfigurationProperties(prefix = "platform.component.vector.facade")
public class VectorFacadeProperties {

    /**
     * 主 provider bean 名称，默认 {@code redisVectorServiceImpl}。
     * <p>
     * 该名称必须与 {@code Map<String, VectorService>} 中某个 bean 的 key 完全一致。
     */
    private String defaultProvider = "redisVectorServiceImpl";

    /**
     * 回退 provider 列表（按顺序尝试）。
     * <p>
     * 主 provider 的所有重试耗尽后，依次尝试本列表中的 provider，直到成功或全部失败。
     */
    private List<String> fallbackChain = new ArrayList<>();

    /**
     * 单个 provider 的最大重试次数（不含首次调用）。
     * <p>
     * 总尝试次数 = {@code maxRetries + 1}。
     */
    private int maxRetries = 2;

    /**
     * 退避基准毫秒数（指数退避：{@code base, base*2, base*4, ...}）。
     */
    private long retryBackoffMillis = 100L;
}
