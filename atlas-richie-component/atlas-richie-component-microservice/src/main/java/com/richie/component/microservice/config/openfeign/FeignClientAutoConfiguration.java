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
package com.richie.component.microservice.config.openfeign;

import com.richie.component.microservice.config.properties.FeignClientOkhttpProperties;
import com.richie.component.microservice.config.properties.OkhttpExtension;
import com.richie.component.microservice.interceptor.FeignClientRequestInterceptor;
import feign.Feign;
import feign.Logger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.FeignClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.concurrent.Executors;

/**
 * OpenFeign OkHttp 自动配置类
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-09 15:32:00
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({Feign.class, OkHttpClient.class})
@ConditionalOnProperty(prefix = "spring.cloud.openfeign.okhttp", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({FeignClientOkhttpProperties.class, OkhttpExtension.class})
@ComponentScan("com.richie.component.microservice")
@RequiredArgsConstructor
public class FeignClientAutoConfiguration {

    /** Feign OkHttp 扩展配置（超时、连接池、SSL、日志等） */
    private final FeignClientOkhttpProperties properties;

    /**
     * 确保 Spring Cloud OpenFeign 使用 OkHttp 客户端。
     *
     * @return FeignClientProperties 默认配置（Logger.Level.BASIC）
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean
    public FeignClientProperties feignClientProperties() {
        var properties = new FeignClientProperties();
        var defaultConfig = new FeignClientProperties.FeignClientConfiguration();
        defaultConfig.setLoggerLevel(Logger.Level.BASIC);
        properties.getConfig().put("default", defaultConfig);
        return properties;
    }

    /**
     * 自定义 OkHttpClient.Builder，覆盖 Spring Cloud OpenFeign 的默认配置。
     *
     * @return 配置好超时、连接池、SSL、日志拦截器等的 Builder
     */
    @Bean
    @ConditionalOnMissingBean
    public OkHttpClient.Builder okHttpClientBuilder() {
        log.info("Initializing custom OkHttpClient.Builder with platform configuration");
        TrustManager[] trustManagers = trustManagers();

        var dispatcher = new Dispatcher(Executors.newCachedThreadPool());
        dispatcher.setMaxRequests(properties.getMaxConnections());
        dispatcher.setMaxRequestsPerHost(properties.getMaxConnectionsPerRoute());

        var builder = new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .readTimeout(properties.getOkHttp().getReadTimeout())
                .writeTimeout(properties.getOkHttp().getWriteTimeout())
                .connectTimeout(properties.getOkHttp().getConnectTimeout())
                .callTimeout(properties.getOkHttp().getCallTimeout())
                .addInterceptor(new HttpLoggingInterceptor(HttpLoggingInterceptor.Logger.DEFAULT)
                        .setLevel(properties.getOkHttp().getLevel()));

        // 处理 SSL 配置
        if (properties.getOkHttp().getInsecureTrustAll()) {
            log.warn("Insecure SSL trust all is enabled - only use in development/test environments");
            try {
                final var sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, trustManagers, new java.security.SecureRandom());
                final var sslSocketFactory = sslContext.getSocketFactory();
                builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustManagers[0]);
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                log.warn("Failed to configure SSL context for insecure trust all: {}", e.getMessage());
            }
        }

        // 主机名校验
        if (!properties.getOkHttp().getHostnameVerification()) {
            builder.hostnameVerifier((hostnameVerifier, sslSession) -> true);
        }

        // 缓存配置
        if (properties.getOkHttp().getEnableCache()) {
            builder.cache(new okhttp3.Cache(
                    new File(properties.getOkHttp().getCachePath()),
                    properties.getOkHttp().getCacheSize() * 1024 * 1024
            ));
        }

        // 连接池配置
        builder.connectionPool(new ConnectionPool(
                Runtime.getRuntime().availableProcessors(),
                properties.getTimeToLive(),
                properties.getTimeToLiveUnit()
        ));

        return builder;
    }

    /**
     * 创建信任所有证书的 TrustManager 数组（仅用于 insecureTrustAll 场景）。
     *
     * @return X509TrustManager 数组
     */
    private TrustManager[] trustManagers() {
        return new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[]{};
                    }
                }
        };
    }

    /**
     * Feign 请求拦截器 Bean，用于透传请求头（语言、时区、租户等）。
     *
     * @return FeignClientRequestInterceptor 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public FeignClientRequestInterceptor feignClientRequestInterceptor() {
        return new FeignClientRequestInterceptor();
    }

}
