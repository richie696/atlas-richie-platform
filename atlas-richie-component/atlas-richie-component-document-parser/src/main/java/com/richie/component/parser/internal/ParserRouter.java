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

import com.richie.component.parser.DocumentParser;
import com.richie.component.parser.exception.FormatNotSupportedException;

/**
 * 解析路由器 — 根据 {@link Format} 分发到对应的 {@link DocumentParser} 实现。
 * <p>
 * <b>路由规则</b>(基于用户拍板的设计原则):
 * <ul>
 *   <li>Excel (.xlsx / .xls / .ods) → {@link FesodDocumentParser} (Apache Fesod)</li>
 *   <li>其余所有可解析格式 → {@link TikaDocumentParser} (Apache Tika)</li>
 * </ul>
 * TXT / Markdown 暂走 {@link TikaDocumentParser},fast-path 由后续 phase 接入。
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-08
 */
public final class ParserRouter {

    private final TikaDocumentParser tikaParser;
    private final FesodDocumentParser fesodParser;

    public ParserRouter(TikaDocumentParser tikaParser, FesodDocumentParser fesodParser) {
        this.tikaParser = tikaParser;
        this.fesodParser = fesodParser;
    }

    /**
     * 根据格式分发到对应 parser。
     *
     * @param format 嗅探出的内部 Format
     * @return 对应的 DocumentParser
     * @throws FormatNotSupportedException 不支持的格式
     */
    public DocumentParser route(Format format) {
        if (format == null) {
            throw new FormatNotSupportedException("null", "Format is null");
        }
        return switch (format) {
            case XLSX, XLS, ODS -> fesodParser;
            case PDF, DOCX, DOC, PPTX, PPT, ODT, ODP,
                 RTF, TXT, MD, HTML, XML -> tikaParser;
            case UNKNOWN ->
                    throw new FormatNotSupportedException(
                            "unknown",
                            "Document format could not be detected. "
                                    + "Provide a file with a recognized extension or content type."
                    );
        };
    }
}
