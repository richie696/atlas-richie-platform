package com.richie.component.oauth.dcr.integration;

import com.richie.component.cache.GlobalCache;
import com.richie.component.oauth.core.config.OAuth2RedisKey;
import com.richie.component.oauth.dcr.spi.ClientIdMetadataDocumentResolver;
import com.richie.component.oauth.dcr.support.AbstractOAuthDcrRedisIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultClientIdMetadataDocumentResolverIT extends AbstractOAuthDcrRedisIntegrationTest {

    private static final String CLIENT_ID = "it:client:meta-test";
    private static final String METADATA_URI = "https://metadata.example.com/client.json";
    private static final String CLIENT_ID_NO_URI = "it:client:meta-no-uri";

    @Autowired
    private ClientIdMetadataDocumentResolver resolver;

    @BeforeEach
    void setUp() {
        String key = OAuth2RedisKey.OAUTH2_CLIENT_META.getKey(CLIENT_ID);
        GlobalCache.struct().set(key, Map.of(
                "clientId", CLIENT_ID,
                "metadataUri", METADATA_URI
        ), 60_000L);

        String keyNoUri = OAuth2RedisKey.OAUTH2_CLIENT_META.getKey(CLIENT_ID_NO_URI);
        GlobalCache.struct().set(keyNoUri, Map.of(
                "clientId", CLIENT_ID_NO_URI
        ), 60_000L);
    }

    @Test
    void resolveExistingClientWithMetadataUri() {
        var doc = resolver.resolve(CLIENT_ID, METADATA_URI);
        assertThat(doc).isNotNull();
        assertThat(doc.getClientId()).isEqualTo(CLIENT_ID);
    }

    @Test
    void resolveExistingClientWithNullMetadataUri() {
        var doc = resolver.resolve(CLIENT_ID, null);
        assertThat(doc).isNotNull();
        assertThat(doc.getClientId()).isEqualTo(CLIENT_ID);
    }

    @Test
    void resolveNonExistentClientReturnsNull() {
        var doc = resolver.resolve("it:client:nonexistent", null);
        assertThat(doc).isNull();
    }

    @Test
    void getMetadataUriForNonExistentClient() {
        String uri = resolver.getMetadataUri("it:client:nonexistent");
        assertThat(uri).isNull();
    }
}
