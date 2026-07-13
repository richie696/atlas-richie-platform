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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Tesseract OCR 私有配置属性。
 *
 * <p>绑定前缀: {@code platform.component.ocr.tesseract}
 *
 * <p>典型配置:
 * <pre>
 * platform:
 *   component:
 *     ocr:
 *       vendor: tesseract                      # 必填, 激活本 tesseract 模块
 *       tesseract:
 *         provider-name: tesseract-prod
 *         tessdata-path: /usr/share/tesseract-ocr/4.00/tessdata
 *         languages:                           # ISO 639-2
 *           - eng
 *           - chi_sim
 *         timeout-ms: 30000
 * </pre>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-11
 */
@Data
@ConfigurationProperties(prefix = "platform.component.ocr.tesseract")
public class TesseractOcrProperties {

    /**
     * Tesseract tessdata 训练数据目录，需包含 {@code eng.traineddata}、{@code chi_sim.traineddata} 等语言包文件。
     */
    private String tessdataPath;

    /**
     * 识别语言列表，元素为 Tesseract 语言简码（如 {@code eng}、{@code chi_sim}），默认 {@code [eng, chi_sim]}。
     * 多个语言会以 {@code eng+chi_sim} 形式拼接至 {@code -l} 参数。
     */
    private List<String> languages = new ArrayList<>(List.of("eng", "chi_sim"));

    /**
     * 单次识别的超时时间，单位毫秒，默认 30 秒。
     */
    private long timeoutMs = 30_000L;

    /**
     * null-safe setter：Lombok {@code @Data} 生成的 {@code setLanguages(List<String>)} 不做 null 检查，
     * 业务侧若在 yaml 中写 {@code languages: null} 会让 Lombok 生成器直接赋值造成 NPE，这里增加保护。
     *
     * @param languages 待设置的语言列表；若为 {@code null} 则使用空列表代替
     */
    public void setLanguages(List<String> languages) {
        this.languages = languages == null ? new ArrayList<>() : languages;
    }
}