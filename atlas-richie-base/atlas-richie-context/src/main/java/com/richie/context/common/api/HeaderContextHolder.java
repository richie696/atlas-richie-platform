package com.richie.context.common.api;

import com.richie.context.utils.data.Collections;
import com.alibaba.ttl.TransmittableThreadLocal;
import org.springframework.util.CollectionUtils;

import java.util.Map;
import java.util.Optional;

/**
 * 请求头上下文持有者类
 *
 * @author richie696
 * @version 1.0
 * @since 2024-02-23 16:22:59
 */
public class HeaderContextHolder {

    private HeaderContextHolder() {
    }

    private static final TransmittableThreadLocal<Map<String, String>> CTX = new TransmittableThreadLocal<>();

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

