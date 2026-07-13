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
package com.richie.component.ocr.tencent.config;

import com.richie.component.http.core.HttpClient;
import com.richie.component.ocr.config.OcrProperties;
import com.richie.component.ocr.tencent.provider.TencentOcrProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 腾讯云 OCR Provider 的 Spring Boot 自动配置。
 *
 * <p>vendor: {@code tencent}；API 协议类型: 腾讯云 OCR HTTP JSON 同步识别接口，
 * 使用 TC3-HMAC-SHA256 签名；配置方式: 通过 {@link TencentOcrProperties} 绑定
 * {@code platform.component.ocr.tencent.*}，typed POJO 直接通过构造函数注入 Provider。</p>
 *
 * <p><b>激活条件</b> (全部放 Bean 上, 不放 class 上):
 * <ul>
 *   <li>主开关 {@code platform.component.ocr.enabled=true} (默认 true)</li>
 *   <li>{@code platform.component.ocr.vendor=tencent}</li>
 *   <li>业务侧依赖 {@code atlas-richie-component-ocr-tencent} —— 把本类带到 classpath</li>
 * </ul>
 *
 * <p><b>装配流程</b>:
 * <ol>
 *   <li>绑定 {@link TencentOcrProperties} (yaml {@code platform.component.ocr.tencent.*})</li>
 *   <li>{@code new TencentOcrProvider(props, httpClient)} 构造时直接注入 typed POJO</li>
 *   <li>把 Provider 暴露为 {@code @Bean} (业务侧可直接注入 tencent 特定 API)</li>
 * </ol>
 *
 * <p><b>单引擎保证</b>: {@code @ConditionalOnProperty(name="vendor", havingValue="tencent")}
 * 互斥于其他 vendor 的 autoconfig, 同一部署最多激活一个 Provider。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-11
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TencentOcrProperties.class)
public class TencentOcrAutoConfiguration {

    /** 自动配置注册过程使用的日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(TencentOcrAutoConfiguration.class);

    /**
     * 注册并初始化腾讯云 OCR Provider Bean。
     *
     * <p>激活条件由主开关 {@code platform.component.ocr.enabled=true} 与 vendor 选择
     * {@code platform.component.ocr.vendor=tencent} 共同控制。
     *
     * @param props 腾讯云 OCR 私有配置属性，由 {@code platform.component.ocr.tencent.*} 绑定得到，不能为 {@code null}
     * @param httpClient OCR 专用 HTTP 客户端，用于调用腾讯云 OCR HTTP JSON 接口，不能为 {@code null}
     * @return 已完成配置加载的 {@link TencentOcrProvider}
     */
    @Bean
    @ConditionalOnProperty(prefix = OcrProperties.PREFIX, name = "enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = OcrProperties.PREFIX, name = "vendor", havingValue = "tencent")
    @ConditionalOnMissingBean
    public TencentOcrProvider tencentOcrProvider(TencentOcrProperties props,
                                                 @Qualifier("ocrHttpClient") HttpClient httpClient) {
        TencentOcrProvider provider = new TencentOcrProvider(props, httpClient);
        log.info("[OCR] Tencent provider activated: name={}, endpoint={}, region={}",
                "tencent", props.getEndpoint(), props.getRegion());
        return provider;
    }
}
