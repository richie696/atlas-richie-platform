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

class BearerStsSignerTest {

    @Test
    void happyPath_then_asTc3Headers_throws() {
        BearerStsSigner signer = new BearerStsSigner(
                StsTicket.VENDOR_ZHIPU, "test-zhipu-api-key",
                "https://open.bigmodel.cn/api/paas/v4/realtime");
        VendorStsContext ctx = signer.defaultContext(
                StsTicket.VENDOR_ZHIPU, StsTicket.CAPABILITY_VOICE_CHAT, "glm-4-voice");

        StsTicket ticket = signer.sign(ctx);

        assertEquals(StsTicket.VENDOR_ZHIPU, ticket.vendor());
        assertEquals("glm-4-voice", ticket.model());
        assertEquals("https://open.bigmodel.cn/api/paas/v4/realtime", ticket.endpoint());
        assertTrue(ticket.expiresAt() > ticket.issuedAt());
        Map<String, String> headers = ticket.asBearerHeaders();
        assertEquals("Bearer test-zhipu-api-key", headers.get("Authorization"));
        assertThrows(UnsupportedOperationException.class, () -> ticket.asTc3Headers("act", "{}"));
    }
}