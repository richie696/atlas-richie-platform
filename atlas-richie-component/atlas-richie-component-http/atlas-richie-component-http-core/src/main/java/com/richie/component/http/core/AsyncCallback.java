package com.richie.component.http.core;

import jakarta.annotation.Nullable;
import java.io.IOException;

/**
 * 异步 HTTP 请求回调。
 *
 * @param <T> 响应数据类型
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
public interface AsyncCallback<T> {

    /**
     * 请求成功时回调。
     *
     * @param response 原始 HTTP 响应
     * @param data     反序列化后的响应数据，解析失败时为 {@code null}
     */
    void onResponse(HttpResponse response, @Nullable T data);

    /**
     * 请求失败时回调。
     *
     * @param exception 失败原因（连接超时、DNS 解析失败等）
     */
    void onFailure(IOException exception);

}
