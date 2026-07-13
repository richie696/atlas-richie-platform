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
package com.richie.component.ocr.baidu.config;

import com.richie.context.utils.data.JsonUtils;
import com.richie.component.http.core.HttpClient;
import com.richie.component.ocr.baidu.provider.BaiduOcrProvider;
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
 * 百度智能云 OCR Provider 的 Spring Boot 自动配置。
 *
 * <p>vendor: {@code baidu}；API 协议类型: 百度 OCR REST API v2，识别请求使用表单编码，
 * 鉴权请求使用 OAuth2 {@code access_token}；配置方式: 通过 {@link BaiduOcrProperties} 绑定
 * {@code platform.component.ocr.baidu.*}，再转换为 {@code JsonNode} 注入 Provider。</p>
 *
 * <p><b>激活条件</b> (全部放 Bean 上, 不放 class 上):
 * <ul>
 *   <li>主开关 {@code platform.component.ocr.enabled=true} (默认 true)</li>
 *   <li>{@code platform.component.ocr.vendor=baidu}</li>
 *   <li>业务侧依赖 {@code atlas-richie-component-ocr-baidu} —— 把本类带到 classpath</li>
 * </ul>
 *
 * <p><b>装配流程</b>:
 * <ol>
 *   <li>绑定 {@link BaiduOcrProperties} (yaml {@code platform.component.ocr.baidu.*})</li>
 *   <li>{@code JsonUtils.getInstance().toJsonNode(props)} 把 typed Properties 转 JsonNode</li>
 *   <li>new {@link BaiduOcrProvider} → 构造器注入 typed {@code BaiduOcrProperties} POJO</li>
 *   <li>把 Provider 暴露为 {@code @Bean} (业务侧可直接注入 baidu 特定 API)</li>
 * </ol>
 *
 * <p><b>单引擎保证</b>: {@code @ConditionalOnProperty(name="vendor", havingValue="baidu")}
 * 互斥于其他 vendor 的 autoconfig, 同一部署最多激活一个 Provider。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-11
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BaiduOcrProperties.class)
public class BaiduOcrAutoConfiguration {

    /** 自动配置注册过程使用的日志记录器。 */
    private static final Logger log = LoggerFactory.getLogger(BaiduOcrAutoConfiguration.class);

    /**
     * 注册并初始化百度智能云 OCR Provider Bean。
     *
     * <p>激活条件由主开关 {@code platform.component.ocr.enabled=true} 与 vendor 选择
     * {@code platform.component.ocr.vendor=baidu} 共同控制。
     *
     * @param props 百度 OCR 私有配置属性，由 {@code platform.component.ocr.baidu.*} 绑定得到，不能为 {@code null}
     * @param httpClient OCR 专用 HTTP 客户端，用于调用百度 OCR 识别接口和 OAuth2 Token 接口，不能为 {@code null}
     * @return 已完成配置加载的 {@link BaiduOcrProvider}
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = OcrProperties.PREFIX, name = "enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = OcrProperties.PREFIX, name = "vendor",
            havingValue = "baidu")
    public BaiduOcrProvider baiduOcrProvider(BaiduOcrProperties props,
                                              @Qualifier("ocrHttpClient") HttpClient httpClient) {
        return new BaiduOcrProvider(props, httpClient);
    }
}