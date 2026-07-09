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
package com.richie.component.cache.redis.enums;

/**
 * 主题类型
 *
 * @author richie696
 * @version 1.0
 * @since 2024-08-12 22:50:06
 */
public enum TopicTypeEnum {
    /**
     * <p>1vs1主题
     * <ul>
     *     <li>用于精确订阅特定的频道</li>
     *     <li>只能订阅一个具体的频道</li>
     *     <li>适用于只需要订阅单个频道的场景</li>
     * </ul>
     */
    CHANNEL,
    /**
     * <p>1vsN主题
     * <ul>
     *     <li>用于基于模式的订阅</li>
     *     <li>允许使用通配符(如:*)来匹配多个频道的场景</li>
     *     <li>适用于需要订阅一组相关频道的场景</li>
     * </ul>
     */
    PATTERN
}
