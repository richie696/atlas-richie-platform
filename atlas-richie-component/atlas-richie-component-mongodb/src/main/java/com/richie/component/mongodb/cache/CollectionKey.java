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
package com.richie.component.mongodb.cache;

/**
 * 对象集合枚举
 *
 * @author richie696
 * @version 1.0
 * @since 2025-01-15 09:44:57
 */
public interface CollectionKey {

    /**
     * 返回集合键名（用于 ObjectCache 的集合标识）。
     *
     * @return 集合键字符串
     */
    String getKey();

}
