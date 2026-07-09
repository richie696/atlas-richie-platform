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
package com.richie.component.desensitize.core.annotation;

import com.richie.component.desensitize.core.model.MaskScene;
import com.richie.component.desensitize.core.model.MaskType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记敏感字段，供 API 序列化与安全日志序列化使用。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Sensitive {

    /**
     * 指定字段脱敏类型。
     *
     * @return 脱敏类型
     */
    MaskType type();

    /**
     * 指定生效场景，默认用于 API 返回与日志场景。
     *
     * @return 生效场景数组
     */
    MaskScene[] scenes() default {MaskScene.API_RESPONSE, MaskScene.LOG};

    /**
     * 自定义策略名称（预留扩展）。
     *
     * @return 自定义策略标识
     */
    String customStrategy() default "";
}
