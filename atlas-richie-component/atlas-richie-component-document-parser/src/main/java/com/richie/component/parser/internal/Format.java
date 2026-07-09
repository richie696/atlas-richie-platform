/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
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

package com.richie.component.parser.internal;

/**
 * 内部格式枚举,用于 FormatDetector 与 ParserRouter 之间的格式分发。
 * <p>
 * 不对外导出(位于 internal/ 包下),作为实现细节。
 * 业务方无需关心 — 通过 {@link com.richie.component.parser.DocumentReader} 调用即可。
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-08
 */
public enum Format {
    PDF,
    DOCX, DOC,
    XLSX, XLS,
    PPTX, PPT,
    ODT, ODS, ODP,
    RTF,
    TXT, MD,
    HTML, XML,
    UNKNOWN
}
