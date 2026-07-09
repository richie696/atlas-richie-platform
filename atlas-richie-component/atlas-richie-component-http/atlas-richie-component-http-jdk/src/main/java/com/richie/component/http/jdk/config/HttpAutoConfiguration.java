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
package com.richie.component.http.jdk.config;

import com.richie.component.http.core.HttpCoreProperties;
import com.richie.component.http.jdk.JdkHttpAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.Executors;

/**
 * JDK HttpClient Provider 自动配置。
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(HttpProperties.class)
@ConditionalOnProperty(prefix = "platform.component.http", name = "provider", havingValue = "jdk")
public class HttpAutoConfiguration {

    private final HttpProperties properties;
    private final HttpCoreProperties coreProperties;

    public HttpAutoConfiguration(HttpProperties properties, HttpCoreProperties coreProperties) {
        this.properties = properties;
        this.coreProperties = coreProperties;
    }

    @Bean
    public JdkHttpAdapter httpClient() {
        // 核心客户端参数统一由配置项驱动，便于跨环境调优。
        var builder = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .version(properties.getVersion())
                .followRedirects(properties.isFollowRedirects()
                        ? HttpClient.Redirect.NORMAL
                        : HttpClient.Redirect.NEVER)
                .priority(properties.getPriority());

        if (properties.isUseVirtualThreads()) {
            builder.executor(Executors.newVirtualThreadPerTaskExecutor());
        }

        var proxyHost = properties.getProxyHost();
        if (proxyHost != null && !proxyHost.isBlank()) {
            builder.proxy(ProxySelector.of(
                    new InetSocketAddress(proxyHost, properties.getProxyPort())));
        }

        if (!coreProperties.isStrictSsl()) {
            log.warn("⚠️ Insecure mode: SSL verification disabled — do NOT use in production");
            // 仅在明确关闭 strictSsl 时启用“信任所有证书”。
            builder.sslContext(createTrustAllContext());
            var params = new SSLParameters();
            params.setEndpointIdentificationAlgorithm(null);
            builder.sslParameters(params);
        }

        return new JdkHttpAdapter(builder.build());
    }

    private static SSLContext createTrustAllContext() {
        try {
            var ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{
                    new X509TrustManager() {
                        @Override public void checkClientTrusted(X509Certificate[] chain, String a) {}
                        @Override public void checkServerTrusted(X509Certificate[] chain, String a) {}
                        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    }
            }, new SecureRandom());
            return ctx;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }

}
