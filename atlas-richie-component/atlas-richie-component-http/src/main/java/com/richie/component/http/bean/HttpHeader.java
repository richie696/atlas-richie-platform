package com.richie.component.http.bean;

import jakarta.annotation.Nonnull;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * HTTP请求头
 *
 * @author richie696
 * @version 1.0
 * @since 2025-01-17 09:07:04
 */
public class HttpHeader {

    private final Map<String, String> pairs;

    /**
     * 构造函数
     *
     * @param pairs 请求头键值对
     */
    public HttpHeader(@Nonnull Map<String, String> pairs) {
        this.pairs = Collections.unmodifiableMap(pairs);
    }

    /**
     * 获取请求头键值对
     *
     * @param name 请求头名称
     * @return 返回请求头值
     */
    public String get(String name) {
        return pairs.get(name);
    }

    /**
     * 获取请求头所有的键
     *
     * @return 返回请求头键集合
     */
    public Set<String> names() {
        return pairs.keySet();
    }

    /**
     * 获取请求头键数量
     *
     * @return 返回请求头键数量
     */
    public int size() {
        return pairs.size();
    }

}
