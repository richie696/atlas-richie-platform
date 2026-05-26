package com.richie.component.http.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.concurrent.Executors;

/**
 * OkHttp 专用自动配置类
 *
 * <p>负责 OkHttp 相关的所有 Bean 配置，包括：
 * <ul>
 *   <li>OkHttpClient 配置</li>
 *   <li>SSL 配置</li>
 *   <li>连接池配置</li>
 *   <li>超时配置</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-12-09
 */
@Slf4j
@AutoConfiguration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "platform.component.http", name = "provider", havingValue = "okhttp", matchIfMissing = true)
public class OkhttpConfiguration {

    private final HttpProperties properties;

    /**
     * OkHttpClient配置
     *
     * @return 返回OkHttpClient实例
     */
    @Bean("httpComponent")
    public OkHttpClient okHttpClient() {
        TrustManager[] trustManagers = trustManagers();
        final SSLSocketFactory sslSocketFactory;
        try {
            final var sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustManagers, new java.security.SecureRandom());
            sslSocketFactory = sslContext.getSocketFactory();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e.getMessage());
        }
        var dispatcher = new Dispatcher(Executors.newCachedThreadPool());
        dispatcher.setMaxRequests(properties.getMaxRequests());
        dispatcher.setMaxRequestsPerHost(properties.getMaxRequestsPerHost());
        var httpsBuilder = new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustManagers[0])
                .readTimeout(properties.getReadTimeout(), properties.getReadTimeoutTimeUnit())
                .writeTimeout(properties.getWriteTimeout(), properties.getWriteTimeoutTimeUnit())
                .connectTimeout(properties.getConnectTimeout(), properties.getConnectTimeoutTimeUnit())
                .callTimeout(properties.getCallTimeoutTimeout(), properties.getCallTimeoutTimeUnit())
                .addInterceptor(new HttpLoggingInterceptor(HttpLoggingInterceptor.Logger.DEFAULT)
                        .setLevel(properties.getLevel()));
        // 是否启用OkHttp3的缓存
        if (properties.getEnableCache()) {
            httpsBuilder.setCache$okhttp(
                    new okhttp3.Cache(
                            new File(properties.getCachePath()),
                            (long) properties.getCacheSize() * 1024 * 1024
                    )
            );
        }
        httpsBuilder.setHostnameVerifier$okhttp((_, _) -> true);
        httpsBuilder.setConnectionPool$okhttp(
                new ConnectionPool(
                        Runtime.getRuntime().availableProcessors(),
                        properties.getKeepAliveDuration(),
                        properties.getKeepAliveTimeUnit()
                )
        );
        return httpsBuilder.build();
    }

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

}
