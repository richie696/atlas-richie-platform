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
package com.richie.component.ocr.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcrExceptionTest {

    @Test
    void providerUnavailable_npeSafe_whenCauseAndStatusNull() {
        OcrException ex = new OcrException.ProviderUnavailable("vendor-x", null, null);
        assertDoesNotThrow(ex::getMessage);
        assertTrue(ex.getMessage().contains("vendor-x"));
    }

    @Test
    void providerUnavailable_messageIncludesHttpStatus() {
        OcrException ex = new OcrException.ProviderUnavailable(
                "vendor-x", 503, new RuntimeException("upstream timeout"));
        String msg = ex.getMessage();
        assertTrue(msg.contains("503"), "expected 503 in message: " + msg);
        assertTrue(msg.contains("vendor-x"), "expected vendor-x in message: " + msg);
    }

    @Test
    void providerUnavailable_statusNullButCauseGiven() {
        OcrException ex = new OcrException.ProviderUnavailable(
                "vendor-x", null, new RuntimeException("socket reset"));
        String msg = ex.getMessage();
        assertTrue(msg.contains("vendor-x"));
        assertTrue(msg.contains("socket reset"));
    }

    @Test
    void unrecognized_carriesReason() {
        OcrException ex = new OcrException.Unrecognized("vendor-x", "malformed response");
        assertTrue(ex.getMessage().contains("malformed response"));
    }

    @Test
    void configMissing_carriesKey() {
        OcrException ex = new OcrException.ConfigMissing("vendor-x", "api-key");
        assertTrue(ex.getMessage().contains("api-key"));
    }

    @Test
    void vlmTimeout_carriesElapsedAndBudget() {
        OcrException ex = new OcrException.VlmTimeout("vl", 130_000L, 120_000L);
        String msg = ex.getMessage();
        assertTrue(msg.contains("130000") || msg.contains("130,000") || msg.contains("130"));
        assertTrue(msg.contains("120000") || msg.contains("120,000") || msg.contains("120"));
    }

    @Test
    void vlmOutOfMemory_carriesVram() {
        OcrException ex = new OcrException.VlmOutOfMemory("vl", 24576, 16384);
        String msg = ex.getMessage();
        assertTrue(msg.contains("24576") || msg.contains("24576"));
    }

    @Test
    void imageTooLargeForSync_carriesSizes() {
        OcrException ex = new OcrException.ImageTooLargeForSync("vl", 5_000_000L, 1_048_576);
        String msg = ex.getMessage();
        assertTrue(msg.contains("5000000") || msg.contains("5,000,000"));
        assertTrue(msg.contains("1048576") || msg.contains("1,048,576"));
    }

    @Test
    void sidecarUnavailable_carriesEndpoint() {
        OcrException ex = new OcrException.SidecarUnavailable(
                "paddle", "http://localhost:50051", new RuntimeException("connection refused"));
        String msg = ex.getMessage();
        assertTrue(msg.contains("localhost:50051"));
        assertTrue(msg.contains("paddle"));
    }

    @Test
    void allSubclasses_extendBaseOcrException() {
        OcrException[] exceptions = {
                new OcrException.ConfigMissing("v", "k"),
                new OcrException.ProviderUnavailable("v", 500, new RuntimeException()),
                new OcrException.Unrecognized("v", "r"),
                new OcrException.SidecarUnavailable("v", "e", new RuntimeException()),
                new OcrException.VlmTimeout("v", 1, 2),
                new OcrException.VlmOutOfMemory("v", 1, 2),
                new OcrException.ImageTooLargeForSync("v", 1, 2),
        };
        for (OcrException e : exceptions) {
            org.junit.jupiter.api.Assertions.assertTrue(e instanceof OcrException,
                    e.getClass().getSimpleName() + " should be an OcrException");
        }
    }
}