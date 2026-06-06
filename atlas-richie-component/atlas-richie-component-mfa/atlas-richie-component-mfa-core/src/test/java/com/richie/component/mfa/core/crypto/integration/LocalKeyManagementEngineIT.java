package com.richie.component.mfa.core.crypto.integration;

import com.richie.component.mfa.core.crypto.provider.LocalKeyManagementEngine;
import com.richie.component.mfa.core.crypto.support.AbstractMfaRedisIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalKeyManagementEngineIT extends AbstractMfaRedisIntegrationTest {

    @Autowired
    private LocalKeyManagementEngine keyManagementEngine;

    @Test
    void storeRetrieveAndDeleteSecret_roundTrip() {
        String reference = keyManagementEngine.storeSecret(null, "user-it-1", "JBSWY3DPEHPK3PXP");

        assertThat(reference).isEqualTo("mfa/user-it-1");
        assertThat(keyManagementEngine.retrieveSecret(reference)).isEqualTo("JBSWY3DPEHPK3PXP");

        keyManagementEngine.deleteSecret(reference);

        assertThatThrownBy(() -> keyManagementEngine.retrieveSecret(reference))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void storeSecret_withTenant_usesTenantScopedPath() {
        String reference = keyManagementEngine.storeSecret("tenant-x", "user-it-2", "SECRETHASH");

        assertThat(reference).isEqualTo("mfa/tenant-x/user-it-2");
        assertThat(keyManagementEngine.retrieveSecret(reference)).isEqualTo("SECRETHASH");
        keyManagementEngine.deleteSecret(reference);
    }
}
