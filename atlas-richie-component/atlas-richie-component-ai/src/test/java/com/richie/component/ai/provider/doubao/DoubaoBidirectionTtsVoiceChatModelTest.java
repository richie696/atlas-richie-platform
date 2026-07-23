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
package com.richie.component.ai.provider.doubao;

import com.richie.component.ai.api.voicechat.StsTicket;
import com.richie.component.ai.api.voicechat.VoiceChatConfig;
import com.richie.component.ai.api.voicechat.VoiceConversation;
import com.richie.component.ai.service.VoiceStsService;
import com.richie.component.ai.support.sign.VendorStsContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoubaoBidirectionTtsVoiceChatModelTest {

    @Test
    void vendor_returnsVendor() {
        DoubaoBidirectionTtsVoiceChatModel model = new DoubaoBidirectionTtsVoiceChatModel(new RecordingStsService());
        assertEquals(StsTicket.VENDOR_DOUBAO_OPENSPEECH, model.vendor());
    }

    @Test
    void supportedModels_containsModel() {
        DoubaoBidirectionTtsVoiceChatModel model = new DoubaoBidirectionTtsVoiceChatModel(new RecordingStsService());
        String[] models = model.supportedModels();
        boolean found = false;
        for (String m : models) {
            if ("seed-tts-2.0".equals(m)) { found = true; break; }
        }
        assertTrue(found);
    }

    @Test
    void supports_matchingVendorAndModel_returnsTrue() {
        DoubaoBidirectionTtsVoiceChatModel model = new DoubaoBidirectionTtsVoiceChatModel(new RecordingStsService());
        VoiceChatConfig config = VoiceChatConfig.builder()
                .vendor(StsTicket.VENDOR_DOUBAO_OPENSPEECH).model("seed-tts-2.0").build();
        assertTrue(model.supports(config));
    }

    @Test
    void supports_differentVendor_returnsFalse() {
        DoubaoBidirectionTtsVoiceChatModel model = new DoubaoBidirectionTtsVoiceChatModel(new RecordingStsService());
        VoiceChatConfig config = VoiceChatConfig.builder()
                .vendor(StsTicket.VENDOR_ZHIPU).model("seed-tts-2.0").build();
        assertEquals(false, model.supports(config));
    }

    @Test
    void open_invokesVoiceStsServiceWithCorrectContext() {
        RecordingStsService sts = new RecordingStsService();
        DoubaoBidirectionTtsVoiceChatModel model = new DoubaoBidirectionTtsVoiceChatModel(sts);

        VoiceChatConfig config = VoiceChatConfig.builder()
                .vendor(StsTicket.VENDOR_DOUBAO_OPENSPEECH).model("seed-tts-2.0").build();

        AtomicReference<VoiceConversation> ref = new AtomicReference<>();
        try {
            VoiceConversation conv = model.open(config);
            ref.set(conv);
            VendorStsContext captured = sts.lastContext;
            assertNotNull(captured);
            assertEquals(StsTicket.VENDOR_DOUBAO_OPENSPEECH, captured.getVendor());
            assertEquals("x-api-key", captured.getAuthDomain());
            assertEquals(StsTicket.CAPABILITY_TTS_STREAM, captured.getCapability());
            assertEquals("seed-tts-2.0", captured.getModel());
            assertEquals(DoubaoBidirectionTtsVoiceChatModel.DEFAULT_ENDPOINT, captured.getAttributes().get("endpoint"));
            assertNotNull(conv.events());
        } finally {
            if (ref.get() != null) {
                try { ref.get().close(); } catch (Exception ignore) {}
            }
        }
    }

    @Test
    void open_propagatesStsException() {
        VoiceStsService throwingSts = new ThrowingStsService();
        DoubaoBidirectionTtsVoiceChatModel model = new DoubaoBidirectionTtsVoiceChatModel(throwingSts);
        VoiceChatConfig config = VoiceChatConfig.builder()
                .vendor(StsTicket.VENDOR_DOUBAO_OPENSPEECH).model("seed-tts-2.0").build();
        assertThrows(IllegalStateException.class, () -> model.open(config));
    }

    private static final class RecordingStsService implements VoiceStsService {
        VendorStsContext lastContext;

        @Override
        public StsTicket sign(VendorStsContext ctx) {
            this.lastContext = ctx;
            long now = System.currentTimeMillis();
            return StsTicket.builder()
                    .vendor(ctx.getVendor())
                    .model(ctx.getModel())
                    .capability(ctx.getCapability())
                    .endpoint(ctx.<String>attribute("endpoint"))
                    .issuedAt(now)
                    .expiresAt(now + TimeUnit.HOURS.toMillis(1))
                    .headerMap(Map.of(
                            "X-Api-Key", "fake-key",
                            "X-Api-App-Id", "fake-app-id",
                            "X-Api-Resource-Id", "fake-resource-id",
                            "X-Api-Access-Key", "fake-access-key"))
                    .build();
        }

        @Override public StsTicket sign(String v, String c, String m) { throw new UnsupportedOperationException(); }
        @Override public StsTicket sign(String v, String c, String m, int t, Map<String, Object> a) { throw new UnsupportedOperationException(); }
        @Override public List<String> listRegisteredVendors() { return List.of(); }
        @Override public int signerCount() { return 0; }
    }

    private static final class ThrowingStsService implements VoiceStsService {
        @Override public StsTicket sign(VendorStsContext ctx) { throw new IllegalStateException("simulated STS failure"); }
        @Override public StsTicket sign(String v, String c, String m) { throw new IllegalStateException("simulated STS failure"); }
        @Override public StsTicket sign(String v, String c, String m, int t, Map<String, Object> a) { throw new IllegalStateException("simulated STS failure"); }
        @Override public List<String> listRegisteredVendors() { return List.of(); }
        @Override public int signerCount() { return 0; }
    }
}
