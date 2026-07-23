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
package com.richie.component.ai.provider.hunyuan;

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

class HunyuanStreamingAsrVoiceChatModelTest {

    @Test
    void vendor_returnsVendor() {
        HunyuanStreamingAsrVoiceChatModel model = new HunyuanStreamingAsrVoiceChatModel(new RecordingStsService());
        assertEquals(StsTicket.VENDOR_HUNYUAN_STT, model.vendor());
    }

    @Test
    void supportedModels_containsModel() {
        HunyuanStreamingAsrVoiceChatModel model = new HunyuanStreamingAsrVoiceChatModel(new RecordingStsService());
        String[] models = model.supportedModels();
        boolean found = false;
        for (String m : models) {
            if ("16k_zh".equals(m)) { found = true; break; }
        }
        assertTrue(found);
    }

    @Test
    void supports_matchingVendorAndModel_returnsTrue() {
        HunyuanStreamingAsrVoiceChatModel model = new HunyuanStreamingAsrVoiceChatModel(new RecordingStsService());
        VoiceChatConfig config = VoiceChatConfig.builder()
                .vendor(StsTicket.VENDOR_HUNYUAN_STT).model("16k_zh").build();
        assertTrue(model.supports(config));
    }

    @Test
    void supports_differentVendor_returnsFalse() {
        HunyuanStreamingAsrVoiceChatModel model = new HunyuanStreamingAsrVoiceChatModel(new RecordingStsService());
        VoiceChatConfig config = VoiceChatConfig.builder()
                .vendor(StsTicket.VENDOR_DOUBAO_OPENSPEECH).model("16k_zh").build();
        assertEquals(false, model.supports(config));
    }

    @Test
    void open_invokesVoiceStsServiceWithCorrectContext() {
        RecordingStsService sts = new RecordingStsService();
        HunyuanStreamingAsrVoiceChatModel model = new HunyuanStreamingAsrVoiceChatModel(sts);

        VoiceChatConfig config = VoiceChatConfig.builder()
                .vendor(StsTicket.VENDOR_HUNYUAN_STT).model("16k_zh").build();

        AtomicReference<VoiceConversation> ref = new AtomicReference<>();
        try {
            VoiceConversation conv = model.open(config);
            ref.set(conv);
            VendorStsContext captured = sts.lastContext;
            assertNotNull(captured);
            assertEquals(StsTicket.VENDOR_HUNYUAN_STT, captured.getVendor());
            assertEquals("tc3", captured.getAuthDomain());
            assertEquals(StsTicket.CAPABILITY_STT_STREAM, captured.getCapability());
            assertEquals("16k_zh", captured.getModel());
            assertEquals(HunyuanStreamingAsrVoiceChatModel.DEFAULT_ENDPOINT, captured.getAttributes().get("endpoint"));
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
        HunyuanStreamingAsrVoiceChatModel model = new HunyuanStreamingAsrVoiceChatModel(throwingSts);
        VoiceChatConfig config = VoiceChatConfig.builder()
                .vendor(StsTicket.VENDOR_HUNYUAN_STT).model("16k_zh").build();
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
                    .tc3Material(new StsTicket.Tc3Material(
                            "fake-secret-id", "fake-secret-key", "asr", "ap-guangzhou", "asr.tencentcloudapi.com"))
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
