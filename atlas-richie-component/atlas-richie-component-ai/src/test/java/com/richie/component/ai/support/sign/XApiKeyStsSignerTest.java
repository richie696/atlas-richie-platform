/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 */
package com.richie.component.ai.support.sign;

import com.richie.component.ai.api.voicechat.StsTicket;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XApiKeyStsSignerTest {

    @Test
    void happyPath_returns_x_api_key_headers_then_asTc3Headers_throws() {
        XApiKeyStsSigner signer = new XApiKeyStsSigner(
                StsTicket.VENDOR_DOUBAO_OPENSPEECH, "test-api-key",
                "test-app-id", "test-resource-id",
                "wss://openspeech.bytedance.com/api/v3/tts/bidirection");
        VendorStsContext ctx = signer.defaultContext(
                StsTicket.VENDOR_DOUBAO_OPENSPEECH, StsTicket.CAPABILITY_TTS_STREAM, "bidirection-tts");

        StsTicket ticket = signer.sign(ctx);

        assertEquals(StsTicket.VENDOR_DOUBAO_OPENSPEECH, ticket.vendor());
        Map<String, String> headers = ticket.asHeaderMap();
        assertEquals("test-api-key", headers.get("X-Api-Key"));
        assertEquals("test-app-id", headers.get("X-Api-App-Id"));
        assertEquals("test-resource-id", headers.get("X-Api-Resource-Id"));
        assertEquals(4, headers.size(), "X-Api-Key 域返回 X-Api-Key + X-Api-App-Id + X-Api-Resource-Id + X-Api-Access-Key");
        assertThrows(UnsupportedOperationException.class, () -> ticket.asTc3Headers("act", "{}"));
    }
}