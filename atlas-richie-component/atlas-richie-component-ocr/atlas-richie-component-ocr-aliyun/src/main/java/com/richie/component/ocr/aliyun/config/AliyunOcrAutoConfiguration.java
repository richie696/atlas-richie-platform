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
package com.richie.component.ocr.aliyun.config;

import com.richie.component.http.core.HttpClient;
import com.richie.component.ocr.aliyun.provider.AliyunOcrProvider;
import com.richie.component.ocr.config.OcrProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云读光 OCR Provider 的 Spring Boot 自动配置。
 *
 * <p>vendor: {@code aliyun}；API 协议类型: 阿里云读光 HTTP JSON 同步识别接口；
 *
 * <p><b>激活条件</b> (全部放 Bean 上, 不放 class 上):
 * <ul>
 *   <li>主开关 {@code platform.component.ocr.enabled=true} (默认 true)</li>
 *   <li>{@code platform.component.ocr.vendor=aliyun}</li>
 *   <li>业务侧依赖 {@code atlas-richie-component-ocr-aliyun} —— 把本类带到 classpath</li>
 * </ul>
 *
 * <p><b>装配流程</b>:
 * <ol>
 *   <li>绑定 {@link AliyunOcrProperties} (yaml {@code platform.component.ocr.aliyun.*})</li>
 *   <li>通过构造函数直接 {@code new AliyunOcrProvider(props, httpClient)}，typed Properties 构造期注入</li>
 *   <li>把 Provider 暴露为 {@code @Bean} (业务侧可直接注入 aliyun 特定 API)</li>
 * </ol>
 *
 * <p><b>单引擎保证</b>: {@code @ConditionalOnProperty(name="vendor", havingValue="aliyun")}
 * 互斥于其他 vendor 的 autoconfig, 同一部署最多激活一个 Provider。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-10
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AliyunOcrProperties.class)
public class AliyunOcrAutoConfiguration {

    /** 自动配置注册过程使用的日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(AliyunOcrAutoConfiguration.class);

    /**
     * 注册并初始化阿里云读光 OCR Provider Bean。
     * <p>两个 {@code @ConditionalOnProperty} 同时约束: 主开关 enabled=true + vendor=aliyun。
     *
     * @param props 阿里云 OCR 私有配置属性，由 {@code platform.component.ocr.aliyun.*} 绑定得到
     * @param httpClient OCR 专用 HTTP 客户端，用于调用阿里云读光 HTTP JSON 接口
     * @return 已完成配置加载的 {@link AliyunOcrProvider}
     */
    @Bean
    @ConditionalOnProperty(prefix = OcrProperties.PREFIX, name = "enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = OcrProperties.PREFIX, name = "vendor", havingValue = "aliyun")
    @ConditionalOnMissingBean
    public AliyunOcrProvider aliyunOcrProvider(AliyunOcrProperties props,
                                               @Qualifier("ocrHttpClient") HttpClient httpClient) {
        AliyunOcrProvider provider = new AliyunOcrProvider(props, httpClient);
        log.info("[OCR] Aliyun provider activated: name={}, endpoint={}, model={}",
                "aliyun", props.getEndpoint(), props.getVendor().getModel());
        return provider;
    }
}
