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
package com.richie.component.cache.ops;

/**
 * 分布式限流API管理器，封装了基于Redis的滑动窗口限流算法。
 * <p>
 * 适用于接口防刷、限流、突发流量控制等场景。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-25 17:49:40
 */
public interface LimiterOps {

    /**
     * 滑动窗口限流，判断是否允许通过。
     *
     * @param key            限流标识键
     * @param maxCount       窗口内最大请求数
     * @param windowSeconds  窗口时间（秒）
     * @return true表示允许通过，false表示被限流
     */
    boolean tryAcquire(String key, int maxCount, int windowSeconds);
}
