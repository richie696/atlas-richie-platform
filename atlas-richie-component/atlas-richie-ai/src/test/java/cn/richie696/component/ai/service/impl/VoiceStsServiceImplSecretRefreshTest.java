/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.ai.service.impl;

import cn.richie696.component.ai.api.voicechat.StsTicket;
import cn.richie696.component.ai.support.sign.StsSigner;
import cn.richie696.component.ai.support.sign.VendorStsContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VoiceStsServiceImplSecretRefreshTest {

    @Test
    void signerGenerationSwitchesAtomicallyAndCanRollback() {
        VoiceStsServiceImpl service = new VoiceStsServiceImpl(List.of(signer("old")));
        var prepared = service.prepareSecretRefresh(List.of(signer("new")));

        assertThat(service.listRegisteredVendors()).containsExactly("old");
        prepared.commit();
        assertThat(service.listRegisteredVendors()).containsExactly("new");
        prepared.rollback();
        assertThat(service.listRegisteredVendors()).containsExactly("old");
    }

    private StsSigner signer(String vendor) {
        return new StsSigner() {
            @Override
            public String vendor() {
                return vendor;
            }

            @Override
            public String[] supportedAuthDomains() {
                return new String[]{"bearer"};
            }

            @Override
            public StsTicket sign(VendorStsContext ctx) {
                throw new UnsupportedOperationException("not needed by this test");
            }
        };
    }
}
