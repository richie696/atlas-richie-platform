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
package com.richie.context.common.api;

import com.richie.context.utils.data.Collections;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import org.springframework.util.CollectionUtils;

import java.util.Map;
import java.util.Optional;

/**
 * 请求头上下文持有者类。
 *
 * <p>通过 micrometer {@link ThreadLocalAccessor} 注册，支持跨异步边界自动传播。</p>
 *
 * @author richie696
 * @version 1.0
 * @since 2024-02-23 16:22:59
 */
public class HeaderContextHolder {

    private static final String CONTEXT_KEY = "header-context";
    private static final ThreadLocal<Map<String, String>> CTX = new ThreadLocal<>();

    static {
        ContextRegistry.getInstance().registerThreadLocalAccessor(new ThreadLocalAccessor<Map<String, String>>() {
            @Override public Object key() { return CONTEXT_KEY; }
            @Override public Map<String, String> getValue() { return CTX.get(); }
            @Override public void setValue(Map<String, String> value) { CTX.set(value); }
            @Override public void setValue() { CTX.remove(); }
        });
    }

    private HeaderContextHolder() {
    }

    /**
     * 设置请求头上下文的方法
     * @param map 请求头信息
     */
    public static void setContext(Map<String, String> map) {
        CTX.set(map);
    }

    /**
     * 获取请求头上下文的方法
     * @return 返回请求头上下文
     */
    public static Map<String, String> getContext() {
        return CTX.get();
    }

    /**
     * 清空请求头上下文的方法
     */
    public static void removeContext() {
        CTX.remove();
    }

    /**
     * 获取请求头的方法
     * @param key 请求头的KEY
     * @return 返回请求头的值
     */
    public static String getHeader(String key) {
        if (isEmpty()) {
            return null;
        }
        return CTX.get().get(key);
    }

    /**
     * 判断请求头上下文是否为空的方法
     * @return 返回判断结果
     */
    public static boolean isEmpty() {
        return CollectionUtils.isEmpty(CTX.get());
    }

    /**
     * 判断请求头上下文是否不为空的方法
     * @return 返回判断结果
     */
    public static boolean isNotEmpty() {
        return !isEmpty();
    }

    /**
     * 设置请求头
     * @param key   请求头的KEY
     * @param value 请求头的值
     */
    public static void setHeader(String key, String value) {
        Optional.ofNullable(CTX.get()).ifPresentOrElse(map -> map.put(key, value), () -> CTX.set(Collections.mapOf(key, value)));
    }
}

