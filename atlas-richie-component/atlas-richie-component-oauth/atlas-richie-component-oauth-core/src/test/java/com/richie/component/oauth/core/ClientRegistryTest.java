package com.richie.component.oauth.core;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.ops.FieldOps;
import com.richie.component.cache.ops.KeyOps;
import com.richie.component.cache.ops.StructOps;
import com.richie.component.oauth.core.model.ClientConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientRegistryTest {

    @Mock
    private FieldOps fieldOps;
    @Mock
    private KeyOps keyOps;
    @Mock
    private StructOps structOps;

    private ClientRegistry clientRegistry;

    @BeforeEach
    void setUp() {
        clientRegistry = new ClientRegistry();
    }

    @Test
    void getClientConfig_singleField_whenClientExists_returnsValue() {
        String clientId = "client-123";
        String clientName = "Test Client";

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::field).thenReturn(fieldOps);
            when(fieldOps.get(anyString(), eq("clientName"), eq(Object.class))).thenReturn(clientName);

            String result = clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_NAME);

            assertThat(result).isEqualTo(clientName);
        }
    }

    @Test
    void getClientConfig_singleField_whenClientNotExists_returnsNull() {
        String clientId = "nonexistent-client";

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::field).thenReturn(fieldOps);
            when(fieldOps.get(anyString(), eq("enabled"), eq(Object.class))).thenReturn(null);

            Boolean result = clientRegistry.getClientConfig(clientId, ClientConfig.Field.ENABLED);

            assertThat(result).isNull();
        }
    }

    @Test
    void getClientConfig_singleField_whenBlankClientId_returnsNull() {
        Object result = clientRegistry.getClientConfig("", ClientConfig.Field.ENABLED);
        assertThat(result).isNull();

        result = clientRegistry.getClientConfig(null, ClientConfig.Field.ENABLED);
        assertThat(result).isNull();
    }

    @Test
    void getClientConfig_twoFields_whenBothExist_returnsMap() {
        String clientId = "client-123";
        String clientName = "Test Client";
        Boolean enabled = true;

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::field).thenReturn(fieldOps);
            when(fieldOps.get(anyString(), eq("clientName"), eq(Object.class))).thenReturn(clientName);
            when(fieldOps.get(anyString(), eq("enabled"), eq(Object.class))).thenReturn(enabled);

            Map<ClientConfig.Field, Object> result = clientRegistry.getClientConfig(
                    clientId, ClientConfig.Field.CLIENT_NAME, ClientConfig.Field.ENABLED);

            assertThat(result).isNotNull();
            assertThat(result).hasSize(2);
            assertThat(result.get(ClientConfig.Field.CLIENT_NAME)).isEqualTo(clientName);
            assertThat(result.get(ClientConfig.Field.ENABLED)).isEqualTo(enabled);
        }
    }

    @Test
    void getClientConfig_twoFields_whenOneExists_returnsMapWithOneEntry() {
        String clientId = "client-123";

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::field).thenReturn(fieldOps);
            when(fieldOps.get(anyString(), eq("clientName"), eq(Object.class))).thenReturn(null);
            when(fieldOps.get(anyString(), eq("enabled"), eq(Object.class))).thenReturn(true);

            Map<ClientConfig.Field, Object> result = clientRegistry.getClientConfig(
                    clientId, ClientConfig.Field.CLIENT_NAME, ClientConfig.Field.ENABLED);

            assertThat(result).isNotNull();
            assertThat(result).hasSize(1);
            assertThat(result.get(ClientConfig.Field.ENABLED)).isEqualTo(true);
            assertThat(result).doesNotContainKey(ClientConfig.Field.CLIENT_NAME);
        }
    }

    @Test
    void getClientConfig_twoFields_whenBlankClientId_returnsNull() {
        Map<ClientConfig.Field, Object> result = clientRegistry.getClientConfig(
                "", ClientConfig.Field.CLIENT_NAME, ClientConfig.Field.ENABLED);
        assertThat(result).isNull();
    }

    @Test
    void isClientValid_whenEnabled_returnsTrue() {
        String clientId = "client-123";

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::field).thenReturn(fieldOps);
            when(fieldOps.get(anyString(), eq("enabled"), eq(Object.class))).thenReturn(true);

            boolean result = clientRegistry.isClientValid(clientId);

            assertThat(result).isTrue();
        }
    }

    @Test
    void isClientValid_whenDisabled_returnsFalse() {
        String clientId = "client-123";

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::field).thenReturn(fieldOps);
            when(fieldOps.get(anyString(), eq("enabled"), eq(Object.class))).thenReturn(false);

            boolean result = clientRegistry.isClientValid(clientId);

            assertThat(result).isFalse();
        }
    }

    @Test
    void isClientValid_whenClientNotExists_returnsFalse() {
        String clientId = "nonexistent-client";

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::field).thenReturn(fieldOps);
            when(fieldOps.get(anyString(), eq("enabled"), eq(Object.class))).thenReturn(null);

            boolean result = clientRegistry.isClientValid(clientId);

            assertThat(result).isFalse();
        }
    }

    @Test
    void verifyClientSecret_whenMatching_returnsTrue() {
        String clientId = "client-123";
        String clientSecret = "secret-abc";

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::field).thenReturn(fieldOps);
            when(fieldOps.get(anyString(), eq("clientSecret"), eq(Object.class))).thenReturn(clientSecret);
            when(fieldOps.get(anyString(), eq("enabled"), eq(Object.class))).thenReturn(true);

            boolean result = clientRegistry.verifyClientSecret(clientId, clientSecret);

            assertThat(result).isTrue();
        }
    }

    @Test
    void verifyClientSecret_whenNotMatching_returnsFalse() {
        String clientId = "client-123";
        String storedSecret = "secret-abc";
        String providedSecret = "wrong-secret";

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::field).thenReturn(fieldOps);
            when(fieldOps.get(anyString(), eq("clientSecret"), eq(Object.class))).thenReturn(storedSecret);
            when(fieldOps.get(anyString(), eq("enabled"), eq(Object.class))).thenReturn(true);

            boolean result = clientRegistry.verifyClientSecret(clientId, providedSecret);

            assertThat(result).isFalse();
        }
    }

    @Test
    void verifyClientSecret_whenClientNotExists_returnsFalse() {
        String clientId = "nonexistent-client";
        String clientSecret = "secret-abc";

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::field).thenReturn(fieldOps);
            when(fieldOps.get(anyString(), eq("clientSecret"), eq(Object.class))).thenReturn(null);

            boolean result = clientRegistry.verifyClientSecret(clientId, clientSecret);

            assertThat(result).isFalse();
        }
    }

    @Test
    void verifyClientSecret_whenClientDisabled_returnsFalse() {
        String clientId = "client-123";
        String clientSecret = "secret-abc";

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::field).thenReturn(fieldOps);
            when(fieldOps.get(anyString(), eq("clientSecret"), eq(Object.class))).thenReturn(clientSecret);
            when(fieldOps.get(anyString(), eq("enabled"), eq(Object.class))).thenReturn(false);

            boolean result = clientRegistry.verifyClientSecret(clientId, clientSecret);

            assertThat(result).isFalse();
        }
    }

    @Test
    void verifyClientSecret_whenBlankParams_returnsFalse() {
        assertThat(clientRegistry.verifyClientSecret("", "secret")).isFalse();
        assertThat(clientRegistry.verifyClientSecret(null, "secret")).isFalse();
        assertThat(clientRegistry.verifyClientSecret("client", "")).isFalse();
        assertThat(clientRegistry.verifyClientSecret("client", null)).isFalse();
    }

    @Test
    void registerTestClient_whenValidClientName_createsAndStoresClient() {
        String clientName = "Test Client";

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::struct).thenReturn(structOps);
            cache.when(GlobalCache::key).thenReturn(keyOps);
            when(keyOps.hasKey(anyString())).thenReturn(false);

            ClientConfig result = clientRegistry.registerTestClient(clientName);

            assertThat(result).isNotNull();
            assertThat(result.getClientId()).startsWith("client-");
            assertThat(result.getClientSecret()).isNotBlank();
            assertThat(result.getClientName()).isEqualTo(clientName);
            assertThat(result.getEnabled()).isTrue();
            verify(structOps).set(anyString(), any(ClientConfig.class), anyLong());
        }
    }

    @Test
    void registerTestClient_whenBlankClientName_throwsException() {
        assertThatThrownBy(() -> clientRegistry.registerTestClient(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clientName");

        assertThatThrownBy(() -> clientRegistry.registerTestClient(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clientName");
    }
}
