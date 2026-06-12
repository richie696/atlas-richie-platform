package com.richie.component.oauth.core.integration;

import com.richie.component.oauth.core.config.OAuth2RedisKey;
import com.richie.component.oauth.core.model.ClientConfig;
import com.richie.component.oauth.core.spi.TokenStore;
import com.richie.component.oauth.core.support.AbstractOAuthCoreRedisIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultTokenStoreIT extends AbstractOAuthCoreRedisIntegrationTest {

    @Autowired
    private TokenStore tokenStore;

    @Test
    void storeAndLoadRefreshToken() {
        ClientConfig config = new ClientConfig();
        config.setRefreshTokenValidDuration(1);

        tokenStore.storeRefreshToken("it:rt:abc123", "client-1", "10.0.0.1", config);

        Map<String, String> loaded = tokenStore.loadRefreshToken("it:rt:abc123");
        assertThat(loaded).isNotNull();
        assertThat(loaded.get("client_id")).isEqualTo("client-1");
        assertThat(loaded.get("ip")).isEqualTo("10.0.0.1");
        assertThat(loaded.get("grant_type")).isEqualTo("client_credentials");
        assertThat(loaded).containsKey("created_at");
    }

    @Test
    void removeRefreshToken() {
        ClientConfig config = new ClientConfig();
        config.setRefreshTokenValidDuration(1);

        tokenStore.storeRefreshToken("it:rt:removeme", "client-2", null, config);
        assertThat(tokenStore.loadRefreshToken("it:rt:removeme")).isNotNull();

        tokenStore.removeRefreshToken("it:rt:removeme");
        assertThat(tokenStore.loadRefreshToken("it:rt:removeme")).isNullOrEmpty();
    }

    @Test
    void addAndCheckBlacklist() {
        tokenStore.addToBlacklist("it:at:banned", 60_000L);

        assertThat(tokenStore.isBlacklisted("it:at:banned")).isTrue();
        assertThat(tokenStore.isBlacklisted("it:at:unknown")).isFalse();
    }

    @Test
    void loadNonExistentRefreshToken() {
        Map<String, String> result = tokenStore.loadRefreshToken("it:rt:nonexistent");
        assertThat(result).isNullOrEmpty();
    }

    @Test
    void bindAndUnbindIp() {
        tokenStore.bindAccessTokenIp("it:at:ipbound", "client-ip", "192.168.1.1", 60_000L);
        tokenStore.removeAccessTokenIpBinding("it:at:ipbound");
    }

    @Test
    void storeAndGetClientRefreshTokenIndex() {
        tokenStore.storeClientRefreshTokenIndex("client-idx", "it:rt:indexed", 60_000L);

        String found = tokenStore.getClientRefreshTokenIndex("client-idx");
        assertThat(found).isEqualTo("it:rt:indexed");
    }

    @Test
    void removeClientRefreshTokenIndex() {
        tokenStore.storeClientRefreshTokenIndex("client-idx-remove", "it:rt:to-remove", 60_000L);
        assertThat(tokenStore.getClientRefreshTokenIndex("client-idx-remove")).isNotNull();

        tokenStore.removeClientRefreshTokenIndex("client-idx-remove");
        assertThat(tokenStore.getClientRefreshTokenIndex("client-idx-remove")).isNull();
    }

    @Test
    void incrementDailyIssueCount() {
        long count1 = tokenStore.incrementDailyIssueCount("client-daily", "20261212", 3600_000L);
        assertThat(count1).isEqualTo(1);

        long count2 = tokenStore.incrementDailyIssueCount("client-daily", "20261212", 3600_000L);
        assertThat(count2).isEqualTo(2);
    }

    @Test
    void incrementAnomalyCounts() {
        long refreshCount = tokenStore.incrementAnomalyRefreshCount("client-anomaly");
        assertThat(refreshCount).isEqualTo(1);

        long rateLimitCount = tokenStore.incrementAnomalyRateLimit("client-anomaly");
        assertThat(rateLimitCount).isEqualTo(1);
    }
}
