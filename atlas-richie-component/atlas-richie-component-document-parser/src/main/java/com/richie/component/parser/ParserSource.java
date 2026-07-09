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

package com.richie.component.parser;

import java.io.File;
import java.io.InputStream;
import java.net.URL;

/**
 * 解析输入源 — sealed 类型,支持 File / InputStream / URL 三种形态。
 * <p>
 * sealed 设计保证所有可能的输入源在编译期可知,
 * 替换或新增形态(如 byte[])时,所有实现类都会收到强制检查。
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-08
 */
public sealed interface ParserSource
        permits ParserSource.FileSource,
                ParserSource.StreamSource,
                ParserSource.UrlSource {

    /**
     * 源名称提示,用于日志与错误消息。
     */
    String nameHint();

    /**
     * 本地文件源。
     */
    record FileSource(File file) implements ParserSource {
        public FileSource {
            if (file == null) {
                throw new IllegalArgumentException("file must not be null");
            }
        }

        @Override
        public String nameHint() {
            return file.getName();
        }
    }

    /**
     * 输入流源(调用方负责流的生命周期)。
     *
     * @param in       输入流
     * @param nameHint 文件名提示(用于扩展名嗅探,例如 "report.docx")
     */
    record StreamSource(InputStream in, String nameHint) implements ParserSource {
        public StreamSource {
            if (in == null) {
                throw new IllegalArgumentException("in must not be null");
            }
            if (nameHint == null || nameHint.isBlank()) {
                nameHint = "stream";
            }
        }
    }

    /**
     * HTTPS URL 源(配合 {@link UrlFetchPolicy} 三道防线)。
     */
    record UrlSource(URL url, UrlFetchPolicy policy) implements ParserSource {
        public UrlSource {
            if (url == null) {
                throw new IllegalArgumentException("url must not be null");
            }
            if (policy == null) {
                policy = UrlFetchPolicy.defaults();
            }
        }

        @Override
        public String nameHint() {
            return url.toString();
        }
    }
}
