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

class AkSkHmacStsSignerTest {

    @Test
    void happyPath_returns_ticket_with_aksk_material_then_signs_headers() {
        AkSkHmacStsSigner signer = new AkSkHmacStsSigner(
                StsTicket.VENDOR_DOUBAO_VIKINGDB,
                "test-ak", "test-sk",
                "cn-north-1", "vikingdb",
                "https://vikingdb.volcengineapi.com");
        VendorStsContext ctx = signer.defaultContext(
                StsTicket.VENDOR_DOUBAO_VIKINGDB, StsTicket.CAPABILITY_VOICE_CHAT, "viking-rerank");

        StsTicket ticket = signer.sign(ctx);

        assertEquals(StsTicket.VENDOR_DOUBAO_VIKINGDB, ticket.vendor());
        Map<String, String> headers = ticket.asSignedHeaders("POST", "/rerank", "{}".getBytes());
        assertNotNull(headers.get("Authorization"));
        assertTrue(headers.get("Authorization").startsWith("HMAC-SHA256 Credential=test-ak/"));
        assertThrows(UnsupportedOperationException.class, ticket::asBearerHeaders);
    }
}