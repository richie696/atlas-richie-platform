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
package com.richie.component.ocr.provider;

import com.richie.component.ocr.model.OcrImage;
import com.richie.component.ocr.model.OcrOptions;
import com.richie.component.ocr.model.OcrResult;

/**
 * Provider 抽象 —— L2 单 vendor 配置模式。
 *
 * <p>SPI 契约 (L2):
 * <ul>
 *   <li>运行时只允许激活一个 vendor —— 业务侧直接 {@code @Autowired AliyunOcrProvider} 等具体 vendor Bean
 *       （由各 vendor {@code *AutoConfiguration} 自我装配）。无 Registry/Engine 多 vendor 编排层</li>
 *   <li>vendor 私有字段全部下沉到各 vendor Maven 模块, 不进主组件接口</li>
 *   <li>vendor 配置由 vendor 模块 typed {@code *Properties} POJO 通过 {@code @ConfigurationProperties}
 *       + Spring 标准 relaxed binding 注入 (kebab-case yaml ↔ camel-case field)</li>
 *   <li>横切关注点 (限流/熔断/重试) 由调用方在编排层组合</li>
 * </ul>
 *
 * <p>本组件仅提供同步 {@link #recognize} 入口。业务侧如需异步，自行编排
 * {@code CompletableFuture.supplyAsync(() -> provider.recognize(img, opt), executor)}。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-10
 */
public interface OcrProvider {

    /**
     * 同步识别。
     *
     * <p>vendor 通过 {@link AbstractOcrProvider} 继承 template method pattern 实现
     * （{@code toProviderRequest → callProvider → fromProviderResponse}）。
     */
    OcrResult recognize(OcrImage image, OcrOptions options);
}
