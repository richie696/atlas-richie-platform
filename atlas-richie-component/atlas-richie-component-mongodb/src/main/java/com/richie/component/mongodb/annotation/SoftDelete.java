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
 * 标记实体为软删除行为。
 * <p>
 * 当一个类被 {@code @SoftDelete} 注解时，删除操作会将指定字段设置为 {@code true}，
 * 而非物理移除文档。查询操作会自动过滤已软删除的文档，除非明确绕过。
 * <p>
 * 示例：
 * <pre>
 * &#64;SoftDelete("deleted")
 * public class User {
 *     private Boolean deleted;
 * }
 * </pre>
 *
 * @author Richie
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SoftDelete {

    /**
     * 用于跟踪软删除状态的字段名。
     *
     * @return 字段名，默认为 "deleted"
     */
    String value() default "deleted";
}