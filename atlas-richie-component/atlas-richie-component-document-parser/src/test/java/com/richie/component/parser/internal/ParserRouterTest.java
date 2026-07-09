/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * {@link ParserRouter} 单元测试 — 覆盖 Format → DocumentParser 路由 + 异常路径。
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-08
 */
class ParserRouterTest {

    private TikaDocumentParser tika;
    private FesodDocumentParser fesod;
    private ParserRouter router;

    @BeforeEach
    void setUp() {
        tika = mock(TikaDocumentParser.class);
        fesod = mock(FesodDocumentParser.class);
        router = new ParserRouter(tika, fesod);
    }

    @Test
    @DisplayName("Excel formats should route to FesodDocumentParser")
    void excelRoutesToFesod() {
        assertSame(fesod, router.route(Format.XLSX));
        assertSame(fesod, router.route(Format.XLS));
        assertSame(fesod, router.route(Format.ODS));
    }

    @Test
    @DisplayName("PDF should route to TikaDocumentParser")
    void pdfRoutesToTika() {
        assertSame(tika, router.route(Format.PDF));
    }

    @Test
    @DisplayName("Word formats should route to TikaDocumentParser")
    void wordRoutesToTika() {
        assertSame(tika, router.route(Format.DOCX));
        assertSame(tika, router.route(Format.DOC));
    }

    @Test
    @DisplayName("PowerPoint formats should route to TikaDocumentParser")
    void powerpointRoutesToTika() {
        assertSame(tika, router.route(Format.PPTX));
        assertSame(tika, router.route(Format.PPT));
    }

    @Test
    @DisplayName("OpenDocument formats should route to TikaDocumentParser")
    void openDocumentRoutesToTika() {
        assertSame(tika, router.route(Format.ODT));
        assertSame(tika, router.route(Format.ODP));
    }

    @Test
    @DisplayName("Plain text formats should route to TikaDocumentParser")
    void plainTextRoutesToTika() {
        assertSame(tika, router.route(Format.TXT));
        assertSame(tika, router.route(Format.MD));
    }

    @Test
    @DisplayName("HTML / XML / RTF should route to TikaDocumentParser")
    void structuredTextRoutesToTika() {
        assertSame(tika, router.route(Format.HTML));
        assertSame(tika, router.route(Format.XML));
        assertSame(tika, router.route(Format.RTF));
    }

    @Test
    @DisplayName("UNKNOWN format should throw FormatNotSupportedException")
    void unknownFormatThrows() {
        FormatNotSupportedException ex = assertThrows(
                FormatNotSupportedException.class,
                () -> router.route(Format.UNKNOWN));
        assertEquals("unknown", ex.getDetectedFormat());
        assertNotEmptyMessage(ex);
    }

    @Test
    @DisplayName("null format should throw FormatNotSupportedException")
    void nullFormatThrows() {
        FormatNotSupportedException ex = assertThrows(
                FormatNotSupportedException.class,
                () -> router.route(null));
        assertEquals("null", ex.getDetectedFormat());
        assertNotEmptyMessage(ex);
    }

    private static void assertNotEmptyMessage(FormatNotSupportedException ex) {
        if (ex.getMessage() == null || ex.getMessage().isBlank()) {
            throw new AssertionError("Exception message should not be empty");
        }
    }
}
