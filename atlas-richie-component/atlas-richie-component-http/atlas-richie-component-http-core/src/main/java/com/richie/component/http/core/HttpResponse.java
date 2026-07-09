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
package com.richie.component.http.core;

import com.richie.context.utils.data.JsonUtils;
import tools.jackson.core.type.TypeReference;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * HTTP 响应封装。
 * <p>
 * 提供状态码、响应头、响应体（byte[] / String / 反序列化对象）的统一访问方式。
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
public class HttpResponse {

    private final int statusCode;
    private final Map<String, List<String>> headers;
    private final byte[] body;
    private final InputStream bodyStream;

    HttpResponse(int statusCode, Map<String, List<String>> headers, byte[] body, InputStream bodyStream) {
        this.statusCode = statusCode;
        this.headers = headers;
        this.body = body;
        this.bodyStream = bodyStream;
    }

    /** 创建字节数组响应。 */
    public static HttpResponse of(int code, Map<String, List<String>> headers, byte[] body) {
        return new HttpResponse(code, headers, body, null);
    }

    /** 创建流式响应（用于大文件下载）。 */
    public static HttpResponse of(int code, Map<String, List<String>> headers, InputStream stream) {
        return new HttpResponse(code, headers, null, stream);
    }

    /** HTTP 状态码。 */
    public int statusCode() { return statusCode; }

    /** 响应头（name → values）。 */
    public Map<String, List<String>> headers() { return headers; }

    /** 状态码是否在 2xx 范围内。 */
    public boolean isSuccessful() { return statusCode >= 200 && statusCode < 300; }

    /** 响应体原始字节数组（流式响应时可能为 {@code null}）。 */
    public byte[] body() { return body; }

    /** 响应体输入流（大文件下载场景）。 */
    public InputStream bodyStream() { return bodyStream; }

    /** 将响应体按 UTF-8 解码为字符串。 */
    public String bodyAsString() {
        return body != null ? new String(body) : null;
    }

    /** 将响应体反序列化为指定类型。 */
    public <T> T bodyAs(Class<T> type) {
        return body != null ? JsonUtils.getInstance().deserializePayload(body, type) : null;
    }

    /** 将响应体反序列化为泛型类型。 */
    public <T> T bodyAs(TypeReference<T> typeRef) {
        return body != null ? JsonUtils.getInstance().deserializePayload(body, typeRef) : null;
    }

}
