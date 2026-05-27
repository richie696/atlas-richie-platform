package com.richie.component.http.core;

/**
 * HTTP 客户端实现提供方枚举。
 * <p>
 * 对应 {@code platform.component.http.provider} 的可选值。
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
public enum HttpProvider {

    /**
     * OkHttp 客户端（需要导入 atlas-richie-component-okhttp 实现包）
     */
    OKHTTP,

    /**
     * Apache HttpClient 5 客户端（需要导入 atlas-richie-component-httpclient5 实现包）
     */
    HTTP_CLIENT_5,

    /**
     * Spring Web RestClient 客户端
     */
    REST_CLIENT,

    /**
     * JDK HttpClient 客户端（需要导入 atlas-richie-component-http-jdk 实现包）
     */
    JDK
}
