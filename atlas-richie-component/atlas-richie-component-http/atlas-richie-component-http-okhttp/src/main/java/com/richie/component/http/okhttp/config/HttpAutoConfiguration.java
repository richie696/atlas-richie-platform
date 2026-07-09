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

import com.richie.component.http.core.HttpCoreProperties;
import com.richie.component.http.okhttp.OkHttpAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.net.ssl.*;
import java.io.File;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.Executors;

/**
 * OkHttp Provider 自动配置。
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
@Slf4j
@AutoConfiguration
@RequiredArgsConstructor
@EnableConfigurationProperties(HttpProperties.class)
@ConditionalOnProperty(prefix = "platform.component.http", name = "provider", havingValue = "okhttp")
public class HttpAutoConfiguration {

    private final HttpProperties properties;
    private final HttpCoreProperties coreProperties;

    @Bean
    OkHttpAdapter httpClient(OkHttpClient okHttpClient) {
        return new OkHttpAdapter(okHttpClient);
    }

    @Bean("httpComponent")
    public OkHttpClient okHttpClient() {
        // Dispatcher 控制全局/单主机并发阈值。
        var dispatcher = new Dispatcher(Executors.newCachedThreadPool());
        dispatcher.setMaxRequests(properties.getMaxRequests());
        dispatcher.setMaxRequestsPerHost(properties.getMaxRequestsPerHost());

        var builder = new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .readTimeout(properties.getReadTimeout(), properties.getReadTimeoutTimeUnit())
                .writeTimeout(properties.getWriteTimeout(), properties.getWriteTimeoutTimeUnit())
                .connectTimeout(properties.getConnectTimeout(), properties.getConnectTimeoutTimeUnit())
                .callTimeout(properties.getCallTimeoutTimeout(), properties.getCallTimeoutTimeUnit())
                .addInterceptor(new HttpLoggingInterceptor(HttpLoggingInterceptor.Logger.DEFAULT)
                        .setLevel(properties.getLevel()));

        // SSL: strictSsl=true（默认）→ 平台标准 CA 验证, false → 跳过所有校验
        if (!coreProperties.isStrictSsl()) {
            log.warn("⚠️ Insecure mode: skipping all certificate verification — do NOT use in production");
            var trustAll = trustAllCerts();
            try {
                var sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustAll, new SecureRandom());
                builder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAll[0]);
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                throw new RuntimeException(e);
            }
            builder.hostnameVerifier((_, _) -> true);
        }

        if (properties.getEnableCache()) {
            // 缓存默认仅对 GET 生效，用于降低重复请求开销。
            builder.cache(new okhttp3.Cache(
                    new File(properties.getCachePath()),
                    (long) properties.getCacheSize() * 1024 * 1024));
        }

        builder.connectionPool(new ConnectionPool(
                Runtime.getRuntime().availableProcessors(),
                properties.getKeepAliveDuration(),
                properties.getKeepAliveTimeUnit()));

        return builder.build();
    }

    private static TrustManager[] trustAllCerts() {
        return new TrustManager[]{
                new X509TrustManager() {
                    @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                    @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }
        };
    }

}
