package com.richie.component.search.config.common;

import com.richie.component.search.config.properties.SearchProperties;
import org.apache.http.ssl.SSLContextBuilder;

import javax.net.ssl.SSLContext;
import java.io.File;

/**
 * 搜索引擎配置抽象基类
 *
 * <p>提供各搜索引擎配置的通用方法，包括：
 * <ul>
 *   <li>SSL 配置</li>
 *   <li>通用工具方法</li>
 *   <li>异常处理</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-12-09
 */
public abstract class AbstractSearchAutoConfiguration {

    /**
     * 配置SSL上下文
     *
     * @param sslConfig SSL配置
     * @return 配置好的SSLContext
     * @throws RuntimeException 当SSL配置失败时抛出
     */
    protected SSLContext configureSslContext(SearchProperties.SslConfig sslConfig) {
        try {
            SSLContextBuilder sslBuilder = SSLContextBuilder.create();
            sslBuilder.setProtocol(sslConfig.getProtocol());

            if (sslConfig.getKeystorePath() != null && sslConfig.getKeystorePassword() != null) {
                sslBuilder.loadKeyMaterial(
                        new File(sslConfig.getKeystorePath()),
                        sslConfig.getKeystorePassword().toCharArray(),
                        sslConfig.getKeystorePassword().toCharArray()
                );
            }

            if (sslConfig.getTruststorePath() != null && sslConfig.getTruststorePassword() != null) {
                sslBuilder.loadTrustMaterial(
                        new File(sslConfig.getTruststorePath()),
                        sslConfig.getTruststorePassword().toCharArray()
                );
            }

            if (sslConfig.isTrustAll()) {
                sslBuilder.loadTrustMaterial((_, _) -> true);
            }

            return sslBuilder.build();
        } catch (Exception e) {
            throw new RuntimeException("SSL配置失败", e);
        }
    }

    /**
     * 验证配置参数
     *
     * @param value 配置值
     * @param fieldName 字段名
     * @throws IllegalArgumentException 当配置为空时抛出
     */
    protected void validateConfig(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("%s 未配置或为空".formatted(fieldName));
        }
    }

    /**
     * 创建运行时异常
     *
     * @param message 错误信息
     * @param cause 原因
     * @return RuntimeException
     */
    protected RuntimeException createRuntimeException(String message, Throwable cause) {
        return new RuntimeException(message, cause);
    }
}
