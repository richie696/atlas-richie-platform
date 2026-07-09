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
package com.richie.component.cache.config;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 布隆过滤器配置类
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-26 14:11:11
 */
@Data
@Accessors(chain = true)
public class BloomFilterConfig {

    /** 是否启用布隆过滤器 */
    private boolean enable = false;
    /** 布隆过滤器实现类型 */
    private Type type = Type.REDISSON;
    /** 布隆过滤器数据保存路径 */
    private String key = "platform:cache:bloom:global";
    /**
     * 预期插入数量
     * <p>
     * 说明：布隆过滤器预计会存储的最大元素数量。该值应略大于实际最大数据量，不能太小，否则误判率会上升。
     * <br>配置建议：
     * <ul>
     *   <li>如有1000万用户，建议设为1200万~1500万。</li>
     *   <li>如有100万商品，建议设为120万~150万。</li>
     *   <li>数据量会持续增长时，建议定期重建布隆过滤器。</li>
     * </ul>
     * <br>内存消耗：
     * <ul>
     *   <li>内存消耗（bit）≈ -n × ln(p) / (ln2)^2，其中n为expectedInsertions，p为falseProbability。</li>
     *   <li>如n=1,000,000，p=0.001，约需1.8MB内存。</li>
     *   <li>如n=10,000,000，p=0.001，约需18MB内存。</li>
     * </ul>
     */
    private long expectedInsertions = 10_000_000L;
    /**
     * 误判率（false positive probability）
     * <p>
     * 说明：布隆过滤器允许的最大误判概率（即"明明没存过，但contains返回true"的概率）。
     * <br>配置建议：
     * <ul>
     *   <li>常用值：0.01（1%）、0.001（0.1%）、0.0001（0.01%）</li>
     *   <li>大部分业务建议0.001，安全风控等对误判极度敏感场景可设为0.0001</li>
     *   <li>误判率越低，内存消耗越大，需要权衡</li>
     * </ul>
     * <br>内存消耗：
     * <ul>
     *   <li>误判率越低，所需内存越多</li>
     * </ul>
     */
    private double falseProbability = 0.001;

    /** 布隆过滤器实现类型 */
    public enum Type {
        /** Redisson */
        REDISSON,
        /** Guava */
        GUAVA
    }
}
