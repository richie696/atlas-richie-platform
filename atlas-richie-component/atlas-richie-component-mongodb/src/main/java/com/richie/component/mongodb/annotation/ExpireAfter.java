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
package com.richie.component.mongodb.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记字段在指定秒数后过期（TTL 索引）。
 * <p>
 * 当字段被 {@code @ExpireAfter} 注解时，MongoDB 会自动在字段值超过指定 TTL 时删除文档。
 * 字段类型应为 {@link java.time.Instant} 或 {@link java.util.Date}。
 * <p>
 * 示例：
 * <pre>
 * public class User {
 *     &#64;ExpireAfter(seconds = 3600)
 *     private Instant resetTokenExpiry;
 * }
 * </pre>
 *
 * @author Richie
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ExpireAfter {

    /**
     * 文档过期前的秒数。
     *
     * @return TTL 秒数
     */
    long seconds();
}