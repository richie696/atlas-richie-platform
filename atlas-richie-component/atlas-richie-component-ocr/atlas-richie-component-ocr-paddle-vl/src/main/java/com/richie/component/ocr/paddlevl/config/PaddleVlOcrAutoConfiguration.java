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
package com.richie.component.ocr.paddlevl.config;

import com.richie.component.http.core.HttpClient;
import com.richie.component.ocr.config.OcrProperties;
import com.richie.component.ocr.paddlevl.provider.PaddleVlOcrProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * PaddleOCR-VL Provider 的 Spring Boot 自动配置（Phase B-2 交付，VLM 路线）。
 *
 * <p><b>激活条件</b>:
 * <ul>
 *   <li>主开关 {@code platform.component.ocr.enabled=true} (默认 true)</li>
 *   <li>{@code platform.component.ocr.vendor=paddle-vl} <b>注意 yaml 写 {@code paddle-vl}（带连字符）</b></li>
 *   <li>业务侧依赖 {@code atlas-richie-component-ocr-paddle-vl}</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-11
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PaddleVlOcrProperties.class)
public class PaddleVlOcrAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PaddleVlOcrAutoConfiguration.class);

    /**
     * 在 OCR 主开关启用且当前激活的 vendor 为 {@code paddle-vl} 时，注册 {@link PaddleVlOcrProvider} Bean。
     *
     * <p>触发条件：{@code platform.component.ocr.enabled=true}（默认 true）以及
     * {@code platform.component.ocr.vendor=paddle-vl}（注意 yaml 中使用连字符）。
     *
     * @param props PaddleOCR-VL 配置属性（含 gRPC 端点、GPU 槽数、超时时间等）
     * @param httpClient 由 {@code OcrCoreAutoConfiguration} 注入的 {@code ocrHttpClient} HTTP 客户端
     * @return 已激活的 {@link PaddleVlOcrProvider} 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = OcrProperties.PREFIX, name = "enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = OcrProperties.PREFIX, name = "vendor",
            havingValue = "paddle-vl")
    public PaddleVlOcrProvider paddleVlOcrProvider(PaddleVlOcrProperties props,
                                                   @Qualifier("ocrHttpClient") HttpClient httpClient) {
        PaddleVlOcrProvider provider = new PaddleVlOcrProvider(props, httpClient);
        log.info("[OCR] Paddle-VL provider activated (Phase B-2 skeleton): name={}, grpc={}, gpu-pool={}",
                "paddle-vl", props.getGrpcEndpoint(), props.getGpuPool());
        return provider;
    }
}