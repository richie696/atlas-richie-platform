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
package com.richie.component.http.jdk.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * JDK HttpClient 实现配置。
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
@Data
@ConfigurationProperties(prefix = "platform.component.http.jdk")
public class HttpProperties {

    // ====== 超时 ======

    /** 连接超时（默认：5 秒），覆盖 TCP 握手和 TLS 协商的时间上限。内网建议 3-5s，外网 5-10s。 */
    private Duration connectTimeout = Duration.ofSeconds(5);

    // ====== 协议与重定向 ======

    /** HTTP 协议版本（默认：HTTP_2），可选值：{@code HTTP_1_1} / {@code HTTP_2}。 */
    private HttpClient.Version version = HttpClient.Version.HTTP_2;

    /** 是否自动跟随 3xx 重定向（默认：false）。 */
    private boolean followRedirects = false;

    /** HTTP/2 请求优先级 1~256（默认：16），值越小优先级越高。仅 HTTP/2 生效。 */
    private int priority = 16;

    // ====== 连接池 ======

    /** 空闲连接保持时间（默认：30 秒）。超过此时间未被使用的连接将被关闭。 */
    private Duration keepAliveTime = Duration.ofSeconds(30);

    /** HTTP/2 最大并发流数（默认：100）。仅 HTTP/2 生效。 */
    private int maxConcurrentStreams = 100;

    // ====== 代理（可选） ======

    /** 代理主机（可选）。为空表示不使用代理。 */
    private String proxyHost;

    /** 代理端口（可选）。配合 {@code proxyHost} 使用，默认 80。 */
    private int proxyPort = 80;

    // ====== 执行线程池（可选） ======

    /** 异步回调使用虚拟线程（默认：true）。JDK 21+/25 推荐开启。 */
    private boolean useVirtualThreads = true;

}
