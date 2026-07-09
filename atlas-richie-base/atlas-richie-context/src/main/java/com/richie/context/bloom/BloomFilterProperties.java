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
package com.richie.context.bloom;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 布隆过滤器参数（前缀：{@code platform.component.bloom}）。
 * <p>
 * 控制底层 {@link BloomFilter} 实现的容量 / 误判率。仅 {@link GuavaBloomFilter} 读取本配置；
 * 用户自定义实现可选择性读取。
 *
 * <h2>默认值</h2>
 * <p>{@code expectedInsertions=1_000_000}、{@code falseProbability=0.01} 适用于大多数单体场景。
 * 分布式部署建议使用 Redis bitmap 等共享实现，并在配置中调小 {@code falseProbability}。
 *
 * @author richie696
 * @since 2026-07
 */
@Data
@ConfigurationProperties(prefix = "platform.component.bloom")
public class BloomFilterProperties {

    /**
     * 预期插入元素数量。超过此值后误判率会显著上升，应触发扩容 / 重建。
     */
    private long expectedInsertions = 1_000_000L;

    /**
     * 期望误判率（0~1 之间）。越小越精确，内存占用越大。
     */
    private double falseProbability = 0.01;
}