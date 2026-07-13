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
package com.richie.component.ocr.provider;

import com.richie.component.ocr.model.MimeType;
import com.richie.component.ocr.model.OcrImage;
import com.richie.component.ocr.model.OcrOptions;
import com.richie.component.ocr.model.OcrResult;
import com.richie.component.ocr.exception.OcrException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link AbstractOcrProvider} 模板方法行为测试 —— L2 单 vendor 模式。
 *
 * <p>模板方法内不做健康状态跟踪。
 *
 * @author richie696
 * @version 1.0.0
 */
class AbstractOcrProviderTest {

    @Test
    void recognize_success_returnsTranslatedResult() {
        OcrResult expected = new OcrResult("ok", List.of(), 1.0f, Map.of(), 10);
        StubProvider p = new StubProvider(expected, null);

        OcrResult r = p.recognize(
                new OcrImage.Bytes(new byte[]{1}, MimeType.PNG),
                OcrOptions.builder().build());

        assertEquals(expected, r);
    }

    @Test
    void recognize_ocrException_passesThroughUnchanged() {
        OcrException original = new OcrException.Unrecognized("stub", "bad response");
        StubProvider p = new StubProvider(null, original);

        OcrException thrown = assertThrows(OcrException.Unrecognized.class,
                () -> p.recognize(new OcrImage.Bytes(new byte[]{1}, MimeType.PNG),
                        OcrOptions.builder().build()));
        assertEquals(original, thrown, "OcrException must propagate unchanged");
    }

    @Test
    void recognize_genericException_wrapsAsProviderUnavailable() {
        RuntimeException raw = new RuntimeException("io error");
        StubProvider p = new StubProvider(null, raw);

        OcrException.ProviderUnavailable thrown = assertThrows(OcrException.ProviderUnavailable.class,
                () -> p.recognize(new OcrImage.Bytes(new byte[]{1}, MimeType.PNG),
                        OcrOptions.builder().build()));
        assertEquals(raw, thrown.getCause());
    }

    @Test
    void recognize_translationFailure_wrapsAsProviderUnavailable() {
        StubProvider p = new StubProvider(null, null) {
            @Override
            protected Void toProviderRequest(OcrImage image, OcrOptions options) {
                throw new IllegalStateException("malformed request");
            }
        };

        OcrException.ProviderUnavailable thrown = assertThrows(OcrException.ProviderUnavailable.class,
                () -> p.recognize(new OcrImage.Bytes(new byte[]{1}, MimeType.PNG),
                        OcrOptions.builder().build()));
        assertEquals(IllegalStateException.class, thrown.getCause().getClass());
    }

    /**
     * 测试用 Provider: 通过构造器注入要返回的结果或抛出的异常.
     */
    static class StubProvider extends AbstractOcrProvider<Void, OcrResult> {
        private final OcrResult result;
        private final RuntimeException failure;

        StubProvider(OcrResult result, RuntimeException failure) {
            this.result = result;
            this.failure = failure;
        }

        @Override
        protected Void toProviderRequest(OcrImage image, OcrOptions options) {
            return null;
        }

        @Override
        protected OcrResult callProvider(Void request) {
            if (failure != null) throw failure;
            return result;
        }

        @Override
        protected OcrResult fromProviderResponse(OcrResult response) {
            return response;
        }
    }


}
