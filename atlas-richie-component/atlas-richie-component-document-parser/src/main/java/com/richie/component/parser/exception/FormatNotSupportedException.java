/*
 * Copyright (c) 2026 Richie (https://www.richie696.cn)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.richie.component.parser.exception;

/**
 * 不支持的文档格式异常。
 * <p>
 * 当 FormatDetector 嗅探出的格式不在任何已知 Parser 支持范围时抛出,
 * 携带检测到的格式字符串供业务方排查。
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-08
 */
public class FormatNotSupportedException extends DocumentParseException {

    private final String detectedFormat;

    public FormatNotSupportedException(String detectedFormat, String message) {
        super(message);
        this.detectedFormat = detectedFormat;
    }

    public FormatNotSupportedException(String detectedFormat, String message, Throwable cause) {
        super(message, cause);
        this.detectedFormat = detectedFormat;
    }

    public String getDetectedFormat() {
        return detectedFormat;
    }
}
