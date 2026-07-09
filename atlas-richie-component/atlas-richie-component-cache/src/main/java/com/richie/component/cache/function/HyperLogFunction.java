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
package com.richie.component.cache.function;

/**
 * HyperLogLog相关API管理器，封装了Redis中HyperLogLog数据结构的常用操作。
 * <p>
 * 主要用于大规模基数统计（如UV、去重计数）等场景，具有极低内存消耗和可接受误差。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-25 17:47:07
 */
public interface HyperLogFunction extends CacheFunction {

    /**
     * 向HyperLogLog添加元素。
     *
     * @param key    HyperLogLog的键
     * @param values 要添加的元素，可变参数
     */
    void pfAdd(String key, Object... values);

    /**
     * 获取HyperLogLog的基数估算值。
     *
     * @param key HyperLogLog的键
     * @return 基数估算值（long类型）
     */
    long pfCount(String key);
}
