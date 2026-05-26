package com.richie.component.http.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * HttpClient5配置
 *
 * @author richie696
 * @version 1.0
 * @since 2026-02-07 19:57:25
 */
@Slf4j
@AutoConfiguration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "platform.component.http", name = "provider", havingValue = "httpclient5")
public class HttpClient5Configuration {

    private final HttpProperties properties;

    /**
     * HttpClient5配置
     *
     * @return 返回HttpClient5实例
     */
    @Bean
    public HttpClient httpClient5() {
        // 连接池管理器
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(properties.getMaxRequests());
        connectionManager.setDefaultMaxPerRoute(properties.getMaxRequestsPerHost());
        TlsConfig.Builder tlsConfigBuilder = TlsConfig.custom()
                .setSupportedProtocols(TLS.V_1_2, TLS.V_1_3);
        connectionManager.setDefaultTlsConfig(tlsConfigBuilder.build());

        // 请求配置
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.of(properties.getConnectTimeout(), properties.getConnectTimeoutTimeUnit()))
                .setResponseTimeout(Timeout.of(properties.getReadTimeout(), properties.getReadTimeoutTimeUnit()))
                .build();

        // 构建HttpClient
        return HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManagerShared(true)
                .build();
    }

}
