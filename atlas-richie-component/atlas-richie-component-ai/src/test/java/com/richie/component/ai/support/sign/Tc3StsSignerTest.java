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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Tc3StsSignerTest {

    @Test
    void happyPath_returns_ticket_with_tc3_material_then_signs_headers() {
        Tc3StsSigner signer = new Tc3StsSigner(
                StsTicket.VENDOR_HUNYUAN_TTS,
                "test-secret-id", "test-secret-key",
                "ap-guangzhou", "tts",
                "tts.tencentcloudapi.com");
        VendorStsContext ctx = signer.defaultContext(
                StsTicket.VENDOR_HUNYUAN_TTS, StsTicket.CAPABILITY_TTS_STREAM, "hunyuan-tts");

        StsTicket ticket = signer.sign(ctx);

        assertEquals(StsTicket.VENDOR_HUNYUAN_TTS, ticket.vendor());
        Map<String, String> headers = ticket.asTc3Headers("TextToVoice", "{\"text\":\"hi\"}");
        assertNotNull(headers.get("Authorization"));
        assertTrue(headers.get("Authorization").startsWith("TC3-HMAC-SHA256 Credential=test-secret-id/"));
        assertEquals("TextToVoice", headers.get("X-TC-Action"));
        assertEquals("tts.tencentcloudapi.com", headers.get("Host"));
        assertThrows(UnsupportedOperationException.class, ticket::asBearerHeaders);
    }
}