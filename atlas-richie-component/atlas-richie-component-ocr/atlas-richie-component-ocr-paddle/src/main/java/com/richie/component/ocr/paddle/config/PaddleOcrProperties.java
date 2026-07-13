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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PaddleOCR vendor-specific configuration properties.
 *
 * <p>Bound to the configuration prefix {@code platform.component.ocr.paddle}.
 *
 * <p>Typical configuration:
 * <pre>
 * platform:
 *   component:
 *     ocr:
 *       vendor: paddle                         # required, activates this paddle module
 *       paddle:
 *         provider-name: paddle-prod           # optional
 *         model-dir: /opt/models/paddleocr     # required
 *         python-path: python3                 # optional
 *         timeout-ms: 60000
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "platform.component.ocr.paddle")
public class PaddleOcrProperties {

    /**
     * PaddleOCR 模型目录，存放检测、方向分类与识别三类模型文件；为空时由 {@code paddleocr} Python 包自动下载至 {@code ~/.paddleocr/}。
     */
    private String modelDir;

    /**
     * Python 解释器可执行文件路径或名称，默认 {@code python3}。
     */
    private String pythonPath = "python3";

    /**
     * 单次识别的超时时间，单位毫秒；PaddleOCR 通常较慢，默认 60 秒。
     */
    private long timeoutMs = 60_000L;
}