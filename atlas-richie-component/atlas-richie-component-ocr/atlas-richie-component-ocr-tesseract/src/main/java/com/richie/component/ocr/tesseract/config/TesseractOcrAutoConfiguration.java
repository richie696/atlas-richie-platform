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
package com.richie.component.ocr.tesseract.config;

import com.richie.component.ocr.config.OcrProperties;
import com.richie.component.ocr.tesseract.provider.TesseractOcrProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tesseract OCR Provider 的 Spring Boot 自动配置（Phase B 交付）。
 *
 * <p><b>激活条件</b>:
 * <ul>
 *   <li>主开关 {@code platform.component.ocr.enabled=true} (默认 true)</li>
 *   <li>{@code platform.component.ocr.vendor=tesseract}</li>
 *   <li>业务侧依赖 {@code atlas-richie-component-ocr-tesseract}</li>
 * </ul>
 *
 * <p>当前 {@link TesseractOcrProvider} 是 skeleton; Phase B 真实实现后无需修改本配置类。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-11
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TesseractOcrProperties.class)
public class TesseractOcrAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TesseractOcrAutoConfiguration.class);

    /**
     * 在 OCR 主开关启用且当前激活的 vendor 为 {@code tesseract} 时，注册 {@link TesseractOcrProvider} Bean。
     *
     * <p>触发条件：{@code platform.component.ocr.enabled=true}（默认 true）以及
     * {@code platform.component.ocr.vendor=tesseract}。
     *
     * @param props Tesseract 配置属性（含 tessdata 路径、语言列表、超时时间等）
     * @return 已激活的 {@link TesseractOcrProvider} 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = OcrProperties.PREFIX, name = "enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = OcrProperties.PREFIX, name = "vendor",
            havingValue = "tesseract")
    public TesseractOcrProvider tesseractOcrProvider(TesseractOcrProperties props) {
        TesseractOcrProvider provider = new TesseractOcrProvider(props);
        log.info("[OCR] Tesseract provider activated: name={}, tessdata={}, langs={}",
                "tesseract", props.getTessdataPath(), props.getLanguages());
        return provider;
    }
}