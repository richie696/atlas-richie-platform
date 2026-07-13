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
package com.richie.component.ocr.paddle.config;

import com.richie.component.ocr.config.OcrProperties;
import com.richie.component.ocr.paddle.provider.PaddleOcrProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * PaddleOCR Provider 的 Spring Boot 自动配置（Phase B 交付）。
 *
 * <p><b>激活条件</b>:
 * <ul>
 *   <li>主开关 {@code platform.component.ocr.enabled=true}（默认 true）</li>
 *   <li>{@code platform.component.ocr.vendor=paddle}</li>
 *   <li>业务侧依赖 {@code atlas-richie-component-ocr-paddle}</li>
 * </ul>
 *
 * <p>当前 {@link PaddleOcrProvider} 是 skeleton; Phase B 真实实现（子进程或 sidecar gRPC）落地后本配置类无需修改。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PaddleOcrProperties.class)
public class PaddleOcrAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PaddleOcrAutoConfiguration.class);

    /**
     * 在 OCR 主开关启用且当前激活的 vendor 为 {@code paddle} 时，注册 {@link PaddleOcrProvider} Bean。
     *
     * <p>触发条件：{@code platform.component.ocr.enabled=true}（默认 true）以及
     * {@code platform.component.ocr.vendor=paddle}。
     *
     * @param props PaddleOCR 配置属性
     * @return 已激活的 {@link PaddleOcrProvider} 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = OcrProperties.PREFIX, name = "enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = OcrProperties.PREFIX, name = "vendor",
            havingValue = "paddle")
    public PaddleOcrProvider paddleOcrProvider(PaddleOcrProperties props) {
        PaddleOcrProvider provider = new PaddleOcrProvider(props);
        log.info("[OCR] Paddle provider activated: name={}, model-dir={}",
                "paddle", props.getModelDir());
        return provider;
    }
}