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

package com.richie.component.parser.internal;

import com.richie.component.parser.exception.DocumentParseException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 格式嗅探器 — 基于 Tika magic bytes 检测真实格式(三道防线之内容层)。
 * <p>
 * 自动 mark/reset 保护调用方流,嗅探后流仍可正常读取。
 * 同时提供基于扩展名的快速识别(在无法嗅探流时作为 hint)。
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-08
 */
public final class FormatDetector {

    private static final MimeTypes MIME_TYPES = MimeTypes.getDefaultMimeTypes();

    private static final int SNIFF_BUFFER_SIZE = 8192;

    private FormatDetector() {
    }

    /**
     * 嗅探 InputStream 真实格式(读取前 8KB 即可)。
     * <p>
     * 自动处理 mark/reset:如果流不支持 mark,会包一层 {@link BufferedInputStream};
     * 嗅探完成后调用 {@link InputStream#reset()} 还原读取位置。
     *
     * @param stream   待嗅探的输入流
     * @param nameHint 文件名提示(辅助嗅探,例如 "report.docx")
     * @return Tika MediaType;无法识别时返回 {@link MediaType#OCTET_STREAM}
     */
    public static MediaType detect(InputStream stream, String nameHint) {
        if (stream == null) {
            throw new IllegalArgumentException("stream must not be null");
        }
        boolean needBuffer = !stream.markSupported();
        InputStream sniff = needBuffer ? new BufferedInputStream(stream) : stream;
        Metadata metadata = new Metadata();
        if (nameHint != null && !nameHint.isBlank()) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, nameHint);
        }
        try {
            sniff.mark(SNIFF_BUFFER_SIZE);
            return MIME_TYPES.detect(sniff, metadata);
        } catch (IOException e) {
            throw new DocumentParseException(
                    "Format detection failed for source: " + nameHint, e);
        } finally {
            try {
                sniff.reset();
            } catch (IOException ignored) {
                // reset 失败也不影响嗅探结果,业务方流的剩余内容可能不完整
            }
        }
    }

    /**
     * 将 Tika MediaType 映射到内部 {@link Format} 枚举。
     */
    public static Format toFormat(MediaType mediaType) {
        if (mediaType == null) {
            return Format.UNKNOWN;
        }
        String base = mediaType.getType() + "/" + mediaType.getSubtype();
        return switch (base) {
            case "application/pdf" -> Format.PDF;
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> Format.DOCX;
            case "application/msword" -> Format.DOC;
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> Format.XLSX;
            case "application/vnd.ms-excel" -> Format.XLS;
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> Format.PPTX;
            case "application/vnd.ms-powerpoint" -> Format.PPT;
            case "application/vnd.oasis.opendocument.text" -> Format.ODT;
            case "application/vnd.oasis.opendocument.spreadsheet" -> Format.ODS;
            case "application/vnd.oasis.opendocument.presentation" -> Format.ODP;
            case "application/rtf", "text/rtf" -> Format.RTF;
            case "text/plain" -> Format.TXT;
            case "text/markdown", "text/x-markdown" -> Format.MD;
            case "text/html" -> Format.HTML;
            case "text/xml", "application/xml" -> Format.XML;
            default -> Format.UNKNOWN;
        };
    }

    /**
     * 基于文件名后缀的快速识别(在内容嗅探之前可作为 hint)。
     *
     * @param nameHint 文件名(例如 "report.docx")
     * @return 内部 Format;无法识别时返回 {@link Format#UNKNOWN}
     */
    public static Format fromExtension(String nameHint) {
        if (nameHint == null) {
            return Format.UNKNOWN;
        }
        String lower = nameHint.toLowerCase();
        if (lower.endsWith(".pdf")) return Format.PDF;
        if (lower.endsWith(".docx")) return Format.DOCX;
        if (lower.endsWith(".doc")) return Format.DOC;
        if (lower.endsWith(".xlsx")) return Format.XLSX;
        if (lower.endsWith(".xls")) return Format.XLS;
        if (lower.endsWith(".pptx")) return Format.PPTX;
        if (lower.endsWith(".ppt")) return Format.PPT;
        if (lower.endsWith(".odt")) return Format.ODT;
        if (lower.endsWith(".ods")) return Format.ODS;
        if (lower.endsWith(".odp")) return Format.ODP;
        if (lower.endsWith(".rtf")) return Format.RTF;
        if (lower.endsWith(".txt")) return Format.TXT;
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) return Format.MD;
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return Format.HTML;
        if (lower.endsWith(".xml")) return Format.XML;
        return Format.UNKNOWN;
    }

    /**
     * 综合嗅探:优先内容嗅探,无法识别时回退到扩展名。
     */
    public static Format detectFormat(InputStream stream, String nameHint) {
        MediaType mediaType = detect(stream, nameHint);
        Format format = toFormat(mediaType);
        if (format == Format.UNKNOWN) {
            format = fromExtension(nameHint);
        }
        return format;
    }
}
