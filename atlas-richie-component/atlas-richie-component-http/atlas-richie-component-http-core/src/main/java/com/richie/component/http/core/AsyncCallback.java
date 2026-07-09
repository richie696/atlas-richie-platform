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
