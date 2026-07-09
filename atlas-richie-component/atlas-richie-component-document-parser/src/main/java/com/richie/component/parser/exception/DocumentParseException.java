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
 * 文档解析失败异常基类。
 * <p>
 * 所有解析相关的异常都应继承本类,业务方可统一 {@code catch (DocumentParseException)}
 * 处理所有解析失败场景,并按需 catch 具体子类(如 {@link ImageOnlyPdfException})
 * 实现精细 fallback。
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-08
 */
public class DocumentParseException extends RuntimeException {

    public DocumentParseException(String message) {
        super(message);
    }

    public DocumentParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
