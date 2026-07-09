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
package com.richie.component.cache.local.manage;

import lombok.Getter;
import lombok.Setter;

/**
 * 带过期时间的值包装，用于本地缓存条目。
 *
 * @param <T> 包装值类型
 * @author richie696
 */
@Getter
public class ExpiryWrapper<T> {

    /** 包装的缓存值 */
    private final T value;

    /** 过期时间戳（毫秒） */
    @Setter
    private long expiryTime;

    /**
     * 构造带过期时间的包装。
     *
     * @param value     缓存值
     * @param expiryTime 过期时间戳（毫秒）
     */
    public ExpiryWrapper(T value, long expiryTime) {
        this.value = value;
        this.expiryTime = expiryTime;
    }

    /**
     * 判断是否已过期。
     *
     * @return 当前时间大于 expiryTime 时返回 true
     */
    public boolean isExpired() {
        return System.currentTimeMillis() > expiryTime;
    }

}
