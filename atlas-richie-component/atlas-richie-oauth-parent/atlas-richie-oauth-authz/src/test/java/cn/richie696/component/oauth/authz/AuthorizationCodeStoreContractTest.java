package cn.richie696.component.oauth.authz;

import cn.richie696.component.oauth.authz.spi.AuthorizationCodeStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationCodeStoreContractTest {
    @Test
    void defaultOverloadsAndAtomicConsumePreserveLegacyImplementations() {
        LegacyStore store = new LegacyStore();
        store.storeAuthorizationCode("code", "client", "https://callback", "challenge", "S256",
                List.of("read"), "user", "nonce", 600L);
        assertThat(store.consume("code").status()).isEqualTo(AuthorizationCodeStore.AuthorizationCodeConsumeResult.Status.CONSUMED);
        assertThat(store.consume("missing").status()).isEqualTo(AuthorizationCodeStore.AuthorizationCodeConsumeResult.Status.NOT_FOUND);
        assertThat(AuthorizationCodeStore.AuthorizationCodeConsumeResult.consumed(null).data()).isEmpty();
        assertThat(AuthorizationCodeStore.AuthorizationCodeConsumeResult.notFound().data()).isEmpty();
    }

    private static final class LegacyStore implements AuthorizationCodeStore {
        private Map<String, String> data;

        @Override
        public void storeAuthorizationCode(String code, String clientId, String redirectUri,
                                            String codeChallenge, String codeChallengeMethod,
                                            List<String> scopes, String userId, long ttlSeconds) {
            data = Map.of("clientId", clientId);
        }

        @Override
        public Map<String, String> loadAuthorizationCode(String code) {
            return data;
        }

        @Override
        public void consumeAuthorizationCode(String code) {
            data = null;
        }
    }
}
