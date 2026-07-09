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
 * 标记实体为租户作用域，自动根据租户 ID 过滤查询。
 * <p>
 * 当一个类被 {@code @TenantScoped} 注解时，所有查询操作会自动包含基于当前
 * {@link com.richie.component.tenant.context.TenantContextHolder} 的租户 ID 过滤条件。
 * 这确保了租户之间的数据隔离。
 * <p>
 * 示例：
 * <pre>
 * &#64;TenantScoped("tenantId")
 * public class User {
 *     private String tenantId;
 * }
 * </pre>
 *
 * @author Richie
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TenantScoped {

    /**
     * 用于存储租户标识符的字段名。
     *
     * @return 字段名，默认为 "tenantId"
     */
    String value() default "tenantId";
}