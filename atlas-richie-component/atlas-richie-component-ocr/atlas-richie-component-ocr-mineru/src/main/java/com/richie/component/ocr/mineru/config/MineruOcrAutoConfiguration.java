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
package com.richie.component.ocr.mineru.config;

import com.richie.component.http.core.HttpClient;
import com.richie.component.ocr.config.OcrProperties;
import com.richie.component.ocr.mineru.provider.MineruOcrProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinerU Provider 的 Spring Boot 自动配置（Phase B-2 交付）。
 *
 * <p><b>激活条件</b>:
 * <ul>
 *   <li>主开关 {@code platform.component.ocr.enabled=true} (默认 true)</li>
 *   <li>{@code platform.component.ocr.vendor=mineru}</li>
 *   <li>业务侧依赖 {@code atlas-richie-component-ocr-mineru}</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-11
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MineruOcrProperties.class)
public class MineruOcrAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MineruOcrAutoConfiguration.class);

    /**
     * 在 OCR 主开关启用且当前激活的 vendor 为 {@code mineru} 时，注册 {@link MineruOcrProvider} Bean。
     *
     * <p>触发条件：{@code platform.component.ocr.enabled=true}（默认 true）以及
     * {@code platform.component.ocr.vendor=mineru}。
     *
     * @param props MinerU 配置属性（含服务地址、API Key、超时时间等）
     * @param httpClient 由 {@code OcrCoreAutoConfiguration} 注入的 {@code ocrHttpClient} HTTP 客户端
     * @return 已激活的 {@link MineruOcrProvider} 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = OcrProperties.PREFIX, name = "enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = OcrProperties.PREFIX, name = "vendor",
            havingValue = "mineru")
    public MineruOcrProvider mineruOcrProvider(MineruOcrProperties props,
                                                @Qualifier("ocrHttpClient") HttpClient httpClient) {
        MineruOcrProvider provider = new MineruOcrProvider(props, httpClient);
        log.info("[OCR] MinerU provider activated: name={}, endpoint={}",
                "mineru", props.getEndpoint());
        return provider;
    }
}