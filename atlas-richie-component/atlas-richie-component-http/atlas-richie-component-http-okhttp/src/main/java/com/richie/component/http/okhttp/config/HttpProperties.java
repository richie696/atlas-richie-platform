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
package com.richie.component.http.okhttp.config;

import lombok.Data;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.concurrent.TimeUnit;

/**
 * OkHttp 客户端专用配置。
 * <p>
 * 前缀：{@code platform.component.http.okhttp}
 *
 * <pre>{@code
 * platform:
 *   component:
 *     http:
 *       okhttp:
 *         read-timeout: 10
 *         write-timeout: 10
 *         connect-timeout: 5
 *         max-requests: 200
 * }</pre>
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
@Data
@ConfigurationProperties(prefix = "platform.component.http.okhttp")
public class HttpProperties {

    /** 读取超时（默认：5，单位：秒）。推荐内网 5-10s，外网 10-30s。 */
    private Integer readTimeout = 5;
    /** 读取超时时间单位（默认：SECONDS）。通常无需修改。 */
    private TimeUnit readTimeoutTimeUnit = TimeUnit.SECONDS;

    /** 写入超时（默认：5，单位：秒）。大文件上传建议增大。 */
    private Integer writeTimeout = 5;
    /** 写入超时时间单位（默认：SECONDS）。 */
    private TimeUnit writeTimeoutTimeUnit = TimeUnit.SECONDS;

    /** 连接超时（默认：5，单位：秒）。内网 3-5s，外网 5-10s。 */
    private Integer connectTimeout = 5;
    /** 连接超时时间单位（默认：SECONDS）。 */
    private TimeUnit connectTimeoutTimeUnit = TimeUnit.SECONDS;

    /**
     * 调用超时 — 从请求开始到结束的整体时间上限（默认：15，单位：秒）。
     * <p>
     * 此值应大于 {@code readTimeout + writeTimeout + connectTimeout} 之和，
     * 否则会先于各独立超时触发。一般设为独立超时之和的 3 倍。
     *
     * <pre>{@code
     * # 独立超时：connect=5, read=10, write=10
     * # 建议 call-timeout：30（5+10+10 的 3 倍内）
     * }</pre>
     */
    private Integer callTimeoutTimeout = 15;
    /** 调用超时时间单位（默认：SECONDS）。 */
    private TimeUnit callTimeoutTimeUnit = TimeUnit.SECONDS;

    /**
     * 日志级别（默认：BODY）。
     * <ul>
     *   <li>{@code NONE} — 不输出请求日志</li>
     *   <li>{@code BASIC} — 只输出请求方法和 URL（推荐生产）</li>
     *   <li>{@code HEADERS} — 输出请求方法和 Header（联调）</li>
     *   <li>{@code BODY} — 输出请求体和响应体（开发调试，不要用于生产）</li>
     * </ul>
     */
    private HttpLoggingInterceptor.Level level = HttpLoggingInterceptor.Level.BODY;

    /** 是否启用 HTTP 响应缓存（默认：false）。仅对 GET 请求有效，不常用。 */
    private Boolean enableCache = false;
    /** 缓存文件存储路径（默认：/opt/okhttp3/cache/）。 */
    private String cachePath = "/opt/okhttp3/cache/";
    /** 缓存大小上限，单位 MB（默认：100）。 */
    private Integer cacheSize = 100;

    /** 整体最大并发请求数（默认：250）。根据服务器负载能力调整。 */
    private Integer maxRequests = 250;
    /** 同一 Host 的最大并发请求数（默认：25）。防止对单一服务端过载。 */
    private Integer maxRequestsPerHost = 25;

    /** 空闲连接保持时间（默认：5，单位：分钟）。服务器主动断开前尽量复用。 */
    private Long keepAliveDuration = 5L;
    /** 连接保持时间单位（默认：MINUTES）。 */
    private TimeUnit keepAliveTimeUnit = TimeUnit.MINUTES;

}
