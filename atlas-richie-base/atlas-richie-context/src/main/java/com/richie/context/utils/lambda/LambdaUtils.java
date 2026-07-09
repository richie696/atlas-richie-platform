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
package com.richie.context.utils.lambda;

import com.richie.contract.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * Lambda 工具类，用于从方法引用（如 {@code User::getName}）中解析出实体类的字段名。
 * <p>
 * 典型使用方式：
 * <pre>{@code
 * String field = LambdaUtils.getFieldName(User::getName); // 返回 "name"
 * }</pre>
 * <p>
 * 实现原理：通过 Java 的 {@link SerializedLambda} 机制在运行期获取方法引用的实现方法名，
 * 然后剥离 {@code get} / {@code is} 前缀并将首字母小写，从而得到对应的字段名。
 * 仅适用于标准的 JavaBean getter 方法（{@code getXxx} / {@code isXxx}）。
 *
 * @author richie696
 * @since 2026-06-07
 */
@Slf4j
public final class LambdaUtils {

    private static final String GET_PREFIX = "get";
    private static final String IS_PREFIX = "is";
    private static final String WRITE_REPLACE = "writeReplace";

    private LambdaUtils() {
    }

    /**
     * 从已经反序列化的 {@link SerializedLambda} 中提取字段名。
     *
     * @param column 通过反射调用 {@code writeReplace} 拿到的 SerializedLambda
     * @return 实体字段名（首字母小写）
     * @throws BusinessException 当实现方法名不符合 getter 规范时抛出
     */
    public static String getFieldName(SerializedLambda column) {
        String methodName = column.getImplMethodName();
        String fieldName;
        if (methodName.startsWith(GET_PREFIX) && methodName.length() > GET_PREFIX.length()) {
            fieldName = methodName.substring(GET_PREFIX.length());
        } else if (methodName.startsWith(IS_PREFIX) && methodName.length() > IS_PREFIX.length()) {
            fieldName = methodName.substring(IS_PREFIX.length());
        } else {
            log.error("无法解析字段名：Lambda 实现方法名 {} 不是标准的 getter 格式", methodName);
            throw new BusinessException("LAMBDA_RESOLVE_FAILED",
                    "无法解析字段名：方法 " + methodName + " 不是标准的 getter 格式（应 getXxx / isXxx）");
        }
        return fieldName.substring(0, 1).toLowerCase() + fieldName.substring(1);
    }

    /**
     * 从 {@link Function} 方法引用中提取对应的实体字段名。
     * <p>
     * 内部通过反射调用 {@code writeReplace} 方法拿到 {@link SerializedLambda}，
     * 再委托给 {@link #getFieldName(SerializedLambda)} 完成字段名解析。
     *
     * @param column 实体方法引用，例如 {@code User::getName}
     * @param <T>    实体类型
     * @return 实体字段名（首字母小写）
     * @throws BusinessException 当反射无法获取 SerializedLambda，或方法名不符合 getter 规范时抛出
     */
    public static <T> String getFieldName(Function<T, ?> column) {
        SerializedLambda lambda;
        try {
            Method method = column.getClass().getDeclaredMethod(WRITE_REPLACE);
            method.setAccessible(true);
            Object replaced = method.invoke(column);
            if (!(replaced instanceof SerializedLambda)) {
                log.error("无法解析字段名：writeReplace 返回类型不是 SerializedLambda，实际为 {}",
                        replaced == null ? "null" : replaced.getClass().getName());
                throw new BusinessException("LAMBDA_RESOLVE_FAILED",
                        "无法解析字段名：writeReplace 返回类型不是 SerializedLambda");
            }
            lambda = (SerializedLambda) replaced;
        } catch (ReflectiveOperationException e) {
            log.error("无法解析字段名：反射调用 writeReplace 失败", e);
            throw new BusinessException("LAMBDA_RESOLVE_FAILED", "无法解析字段名：反射调用失败", e);
        }
        return getFieldName(lambda);
    }
}
