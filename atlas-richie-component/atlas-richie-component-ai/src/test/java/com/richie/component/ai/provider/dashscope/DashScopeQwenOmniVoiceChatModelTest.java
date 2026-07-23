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
package com.richie.component.ai.provider.dashscope;

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

class DashScopeQwenOmniVoiceChatModelTest {

    @Test
    void vendor_returnsVendor() {
        DashScopeQwenOmniVoiceChatModel model = new DashScopeQwenOmniVoiceChatModel(new RecordingStsService());
        assertEquals(StsTicket.VENDOR_DASHSCOPE, model.vendor());
    }

    @Test
    void supportedModels_containsModel() {
        DashScopeQwenOmniVoiceChatModel model = new DashScopeQwenOmniVoiceChatModel(new RecordingStsService());
        String[] models = model.supportedModels();
        boolean found = false;
        for (String m : models) {
            if ("qwen-omni-turbo-realtime".equals(m)) { found = true; break; }
        }
        assertTrue(found);
    }

    @Test
    void supports_matchingVendorAndModel_returnsTrue() {
        DashScopeQwenOmniVoiceChatModel model = new DashScopeQwenOmniVoiceChatModel(new RecordingStsService());
        VoiceChatConfig config = VoiceChatConfig.builder()
                .vendor(StsTicket.VENDOR_DASHSCOPE).model("qwen-omni-turbo-realtime").build();
        assertTrue(model.supports(config));
    }

    @Test
    void supports_differentVendor_returnsFalse() {
        DashScopeQwenOmniVoiceChatModel model = new DashScopeQwenOmniVoiceChatModel(new RecordingStsService());
        VoiceChatConfig config = VoiceChatConfig.builder()
                .vendor(StsTicket.VENDOR_ZHIPU).model("qwen-omni-turbo-realtime").build();
        assertEquals(false, model.supports(config));
    }

    @Test
    void open_invokesVoiceStsServiceWithCorrectContext() {
        RecordingStsService sts = new RecordingStsService();
        DashScopeQwenOmniVoiceChatModel model = new DashScopeQwenOmniVoiceChatModel(sts);

        VoiceChatConfig config = VoiceChatConfig.builder()
                .vendor(StsTicket.VENDOR_DASHSCOPE).model("qwen-omni-turbo-realtime").build();

        AtomicReference<VoiceConversation> ref = new AtomicReference<>();
        try {
            VoiceConversation conv = model.open(config);
            ref.set(conv);
            VendorStsContext captured = sts.lastContext;
            assertNotNull(captured);
            assertEquals(StsTicket.VENDOR_DASHSCOPE, captured.getVendor());
            assertEquals("bearer", captured.getAuthDomain());
            assertEquals(StsTicket.CAPABILITY_VOICE_CHAT, captured.getCapability());
            assertEquals("qwen-omni-turbo-realtime", captured.getModel());
            assertEquals(DashScopeQwenOmniVoiceChatModel.DEFAULT_ENDPOINT, captured.getAttributes().get("endpoint"));
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
        DashScopeQwenOmniVoiceChatModel model = new DashScopeQwenOmniVoiceChatModel(throwingSts);
        VoiceChatConfig config = VoiceChatConfig.builder()
                .vendor(StsTicket.VENDOR_DASHSCOPE).model("qwen-omni-turbo-realtime").build();
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
                    .bearerHeaders(Map.of("Authorization", "Bearer fake-token-abc123"))
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
