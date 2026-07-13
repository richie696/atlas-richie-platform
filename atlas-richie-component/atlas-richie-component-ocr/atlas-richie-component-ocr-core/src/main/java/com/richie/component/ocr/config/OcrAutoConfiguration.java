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
package com.richie.component.ocr.config;

import com.richie.component.ocr.config.OcrProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * OCR 组件核心 Spring Boot 自动配置 —— L2 单 vendor 模式, 不暴露 Registry/Engine/Tracker bean。
 *
 * <p>L2 行为契约:
 * <ul>
 *   <li>无 {@code ocrProviderRegistry}、{@code ocrEngine} bean —— 业务侧直接
 *       {@code @Autowired AliyunOcrProvider} 等具体 vendor bean，通过同步 {@code recognize()} 获取结果</li>
 *   <li>vendor 选择由各 {@code *AutoConfiguration} 的
 *       {@code @ConditionalOnProperty(prefix=OcrProperties.PREFIX, name="vendor", havingValue="<vendor>")}
 *       控制——同一配置同时仅有一个 vendor 装配生效</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-10
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OcrProperties.class)
@ConditionalOnProperty(prefix = OcrProperties.PREFIX, name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class OcrAutoConfiguration {
}
