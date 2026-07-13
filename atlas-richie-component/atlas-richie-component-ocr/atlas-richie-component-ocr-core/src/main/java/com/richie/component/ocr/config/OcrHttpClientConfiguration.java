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
package com.richie.component.ocr.config;

import com.richie.component.http.core.HttpClient;
import com.richie.component.http.jdk.JdkHttpAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient.Builder;
import java.time.Duration;

/**
 * OCR 组件专用 HTTP 客户端配置。
 *
 * <p>创建名为 {@code "ocrHttpClient"} 的独立 {@link HttpClient} Bean，
 * 基于 JDK {@link java.net.http.HttpClient} 实现 ({@link JdkHttpAdapter})，
 * 与业务侧的全局 HttpClient 完全隔离，不依赖 {@code platform.component.http} 配置。
 *
 * <p>所有 vendor OCR Provider（Baidu、Aliyun、Paddle-VL、MinerU 等）均注入此 Bean，
 * 通过 {@code @Qualifier("ocrHttpClient")} 引用。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-11
 */
@Configuration(proxyBeanMethods = false)
public class OcrHttpClientConfiguration {

    /**
     * OCR 模块专用的 JDK HttpClient 实例。
     *
     * <p>默认配置：10s 连接超时、HTTP/2、随 JDK 默认自动管理连接池。
     * 业务侧可通过 {@code ocr-http-client.connect-timeout} 等自定义属性覆盖
     * （需自行扩展 {@code @ConfigurationProperties}）。
     *
     * @return 平台 {@link HttpClient} 门面，包装 JDK {@link java.net.http.HttpClient}
     */
    @Bean("ocrHttpClient")
    public HttpClient ocrHttpClient() {
        var jdkClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        return new JdkHttpAdapter(jdkClient);
    }
}
