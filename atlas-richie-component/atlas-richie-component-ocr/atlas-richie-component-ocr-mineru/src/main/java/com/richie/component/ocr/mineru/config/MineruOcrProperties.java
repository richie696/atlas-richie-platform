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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinerU 私有配置属性（Phase B-2 交付，PDF 结构化路线）。
 *
 * <p>绑定前缀: {@code platform.component.ocr.mineru}
 *
 * <p>典型配置:
 * <pre>
 * platform:
 *   component:
 *     ocr:
 *       vendor: mineru                         # 必填
 *       mineru:
 *         provider-name: mineru-prod
 *         endpoint: http://mineru.internal:8000
 *         api-key: ${MINERU_API_KEY:}
 *         timeout-ms: 180000                    # PDF 大, 3 分钟
 * </pre>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-11
 */
@Data
@ConfigurationProperties(prefix = "platform.component.ocr.mineru")
public class MineruOcrProperties {

    /**
     * MinerU 服务地址（含协议、host 与 port），用于上传与轮询接口，默认内部地址 {@code http://mineru.internal:8000}。
     */
    private String endpoint = "http://mineru.internal:8000";

    /**
     * MinerU 服务访问密钥；为空时表示采用内网匿名访问。
     */
    private String apiKey;

    /**
     * 单次识别（涵盖上传 + 轮询直至完成）的超时时间，单位毫秒；PDF 通常较大，默认 180 秒。
     */
    private long timeoutMs = 180_000L;

}