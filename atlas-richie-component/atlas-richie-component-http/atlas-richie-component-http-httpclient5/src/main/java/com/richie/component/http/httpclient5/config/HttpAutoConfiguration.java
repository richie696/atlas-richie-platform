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
package com.richie.component.http.httpclient5.config;

import com.richie.component.http.httpclient5.HttpClient5Adapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * HttpClient5 Provider 自动配置。
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
@Slf4j
@AutoConfiguration
@RequiredArgsConstructor
@EnableConfigurationProperties(HttpProperties.class)
@ConditionalOnProperty(prefix = "platform.component.http", name = "provider", havingValue = "http_client_5")
public class HttpAutoConfiguration {

    private final HttpProperties properties;

    @Bean
    HttpClient5Adapter httpClient(CloseableHttpClient httpClient) {
        return new HttpClient5Adapter(httpClient);
    }

    @Bean
    public CloseableHttpClient httpClient5() {
        // 连接池配置由属性集中驱动，避免硬编码。
        var connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(properties.getMaxTotal());
        connectionManager.setDefaultMaxPerRoute(properties.getDefaultMaxPerRoute());
        connectionManager.setDefaultTlsConfig(TlsConfig.custom()
                .setSupportedProtocols(TLS.V_1_2, TLS.V_1_3).build());

        var requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.of(
                        properties.getConnectionRequestTimeout(),
                        properties.getConnectionRequestTimeoutTimeUnit()))
                .setResponseTimeout(Timeout.of(
                        properties.getResponseTimeout(),
                        properties.getResponseTimeoutTimeUnit()))
                .build();

        var builder = HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig);

        // 返回可复用单例客户端，由 Spring 容器统一管理生命周期。
        return builder.build();
    }

}


