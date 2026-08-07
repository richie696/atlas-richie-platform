package cn.richie696.component.oauth.core.support;

import cn.richie696.component.oauth.cache.InMemoryOAuthCache;
import cn.richie696.component.oauth.core.model.ClientConfig;
import cn.richie696.component.oauth.core.spi.TokenStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CacheBackedTokenStoreTest {

    @Test
    void consumedMarkerDetectsRefreshTokenReuseAndPreservesResourceBinding() {
        CacheBackedTokenStore store = new CacheBackedTokenStore(new InMemoryOAuthCache());
        ClientConfig client = ClientConfig.builder()
                .clientId("client-1").refreshTokenValidDuration(1).build();

        store.storeRefreshToken("rt-1", "client-1", "127.0.0.1", client,
                "https://api.example");
        TokenStore.RefreshTokenConsumeResult consumed = store.consumeRefreshToken("rt-1");
        TokenStore.RefreshTokenConsumeResult replayed = store.consumeRefreshToken("rt-1");

        assertThat(consumed.status()).isEqualTo(TokenStore.RefreshTokenConsumeResult.Status.CONSUMED);
        assertThat(consumed.data()).containsEntry("resource", "https://api.example");
        assertThat(replayed.status()).isEqualTo(TokenStore.RefreshTokenConsumeResult.Status.REPLAYED);
        assertThat(replayed.data()).containsEntry("client_id", "client-1");
    }
}
