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

class AppCodeStsSignerTest {

    @Test
    void happyPath_returns_appcode_headers_then_asTc3Headers_throws() {
        AppCodeStsSigner signer = new AppCodeStsSigner(
                StsTicket.VENDOR_PANGU, "test-app-code-abc123",
                "https://pangu.apigw.com/v1/realtime");
        VendorStsContext ctx = signer.defaultContext(
                StsTicket.VENDOR_PANGU, StsTicket.CAPABILITY_VOICE_CHAT, "pangu-realtime");

        StsTicket ticket = signer.sign(ctx);

        assertEquals(StsTicket.VENDOR_PANGU, ticket.vendor());
        Map<String, String> headers = ticket.asAppCodeHeaders();
        assertEquals("test-app-code-abc123", headers.get("X-Apig-AppCode"));
        assertThrows(UnsupportedOperationException.class, () -> ticket.asTc3Headers("act", "{}"));
    }
}