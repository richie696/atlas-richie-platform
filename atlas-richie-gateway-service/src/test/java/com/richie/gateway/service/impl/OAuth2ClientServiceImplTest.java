package com.richie.gateway.service.impl;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.GlobalCacheManager;
import com.richie.component.cache.ops.FieldOps;
import com.richie.component.cache.ops.StructOps;
import com.richie.gateway.service.OAuth2ClientService;
import com.richie.gateway.vo.ThirdPartyClientConfigVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import tools.jackson.core.type.TypeReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2ClientServiceImplTest {

    private static final String CLIENT_ID = "client-20250607-001";
    private static final String CLIENT_SECRET = "test-secret-12345";

    @Mock
    private GlobalCacheManager cacheManager;

    @Mock
    private FieldOps fieldOps;

    @Mock
    private StructOps structOps;

    @Mock
    private com.richie.component.cache.ops.KeyOps keyOps;

    private OAuth2ClientServiceImpl service;

    private MockedStatic<GlobalCache> globalCacheMockedStatic;

    @BeforeEach
    void setUp() throws Exception {
        injectCacheManager();

        globalCacheMockedStatic = mockStatic(GlobalCache.class);
        globalCacheMockedStatic.when(GlobalCache::key).thenReturn(keyOps);
        globalCacheMockedStatic.when(GlobalCache::field).thenReturn(fieldOps);
        globalCacheMockedStatic.when(GlobalCache::struct).thenReturn(structOps);

        service = new OAuth2ClientServiceImpl();
    }

    private void injectCacheManager() throws Exception {
        var field = GlobalCache.class.getDeclaredField("DELEGATE");
        field.setAccessible(true);
        AtomicReference<GlobalCacheManager> ref = (AtomicReference<GlobalCacheManager>) field.get(null);
        ref.set(cacheManager);
    }

    @AfterEach
    void tearDown() {
        if (globalCacheMockedStatic != null) {
            globalCacheMockedStatic.close();
        }
    }

    @Nested
    @DisplayName("getClientConfig 查询客户端配置")
    class GetClientConfigTests {

        @Test
        @DisplayName("单字段查询：返回配置的字段值")
        void getClientConfig_singleField_returnsValue() {
            when(fieldOps.get(anyString(), eq("clientId"), eq(Object.class)))
                    .thenReturn(CLIENT_ID);

            Object result = service.getClientConfig(CLIENT_ID, ThirdPartyClientConfigVO.Field.CLIENT_ID);

            assertThat(result).isEqualTo(CLIENT_ID);
        }

        @Test
        @DisplayName("单字段查询：字段不存在时返回 null")
        void getClientConfig_singleField_notFound() {
            when(fieldOps.get(anyString(), anyString(), eq(Object.class))).thenReturn(null);

            Object result = service.getClientConfig(CLIENT_ID, ThirdPartyClientConfigVO.Field.CLIENT_ID);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("单字段查询：clientId 为空时返回 null")
        void getClientConfig_blankClientId_returnsNull() {
            Object result = service.getClientConfig("  ", ThirdPartyClientConfigVO.Field.CLIENT_ID);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("单字段查询：field 为空时返回 null")
        void getClientConfig_nullFields_returnsNull() {
            Object result = service.getClientConfig(CLIENT_ID, (ThirdPartyClientConfigVO.Field[]) null);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("批量字段查询：返回完整配置 Map")
        @SuppressWarnings("unchecked")
        void getClientConfig_multipleFields_returnsMap() {
            when(fieldOps.get(anyString(), (java.util.Collection<String>) any(), any(TypeReference.class))).thenReturn(List.of());

            when(fieldOps.get(anyString(), anyString(), eq(Object.class))).thenReturn(null);

            Map<ThirdPartyClientConfigVO.Field, Object> result = service.getClientConfig(CLIENT_ID,
                    ThirdPartyClientConfigVO.Field.CLIENT_ID,
                    ThirdPartyClientConfigVO.Field.CLIENT_SECRET);

            assertThat(result).hasSize(2);
            assertThat(result.get(ThirdPartyClientConfigVO.Field.CLIENT_ID)).isNull();
            assertThat(result.get(ThirdPartyClientConfigVO.Field.CLIENT_SECRET)).isNull();
        }
    }

    @Nested
    @DisplayName("isClientValid 客户端有效性判断")
    class IsClientValidTests {

        @Test
        @DisplayName("enabled=true 时返回 true")
        void isClientValid_enabled_returnsTrue() {
            when(fieldOps.get(anyString(), eq("enabled"), eq(Object.class))).thenReturn("true");

            boolean result = service.isClientValid(CLIENT_ID);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("enabled=false 时返回 false")
        void isClientValid_disabled_returnsFalse() {
            when(fieldOps.get(anyString(), eq("enabled"), eq(Object.class))).thenReturn("false");

            boolean result = service.isClientValid(CLIENT_ID);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("enabled 字段不存在时返回 false")
        void isClientValid_noEnabledField_returnsFalse() {
            when(fieldOps.get(anyString(), eq("enabled"), eq(Object.class))).thenReturn(null);

            boolean result = service.isClientValid(CLIENT_ID);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("verifyClientSecret 客户端密钥验证")
    class VerifyClientSecretTests {

        @Test
        @DisplayName("密钥匹配时返回 true")
        void verifyClientSecret_matching_returnsTrue() {
            when(fieldOps.get(anyString(), eq("clientSecret"), eq(Object.class))).thenReturn(CLIENT_SECRET);
            when(fieldOps.get(anyString(), eq("enabled"), eq(Object.class))).thenReturn("true");

            boolean result = service.verifyClientSecret(CLIENT_ID, CLIENT_SECRET);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("密钥不匹配时返回 false")
        void verifyClientSecret_wrongSecret_returnsFalse() {
            when(fieldOps.get(anyString(), eq("clientSecret"), eq(Object.class))).thenReturn(CLIENT_SECRET);
            when(fieldOps.get(anyString(), eq("enabled"), eq(Object.class))).thenReturn("true");

            boolean result = service.verifyClientSecret(CLIENT_ID, "wrong-secret");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("客户端不存在时返回 false")
        void verifyClientSecret_clientNotFound_returnsFalse() {
            when(fieldOps.get(anyString(), eq("clientSecret"), eq(Object.class))).thenReturn(null);

            boolean result = service.verifyClientSecret(CLIENT_ID, CLIENT_SECRET);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("客户端已禁用时返回 false")
        void verifyClientSecret_disabledClient_returnsFalse() {
            when(fieldOps.get(anyString(), eq("clientSecret"), eq(Object.class))).thenReturn(CLIENT_SECRET);
            when(fieldOps.get(anyString(), eq("enabled"), eq(Object.class))).thenReturn("false");

            boolean result = service.verifyClientSecret(CLIENT_ID, CLIENT_SECRET);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("clientId 为空时返回 false")
        void verifyClientSecret_blankClientId_returnsFalse() {
            boolean result = service.verifyClientSecret("", CLIENT_SECRET);
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("clientSecret 为空时返回 false")
        void verifyClientSecret_blankSecret_returnsFalse() {
            boolean result = service.verifyClientSecret(CLIENT_ID, "");
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("registerTestClient 注册测试客户端")
    class RegisterTestClientTests {

        @Test
        @DisplayName("正常注册测试客户端成功")
        void registerTestClient_validClientName_returnsConfig() {
            when(keyOps.hasKey(anyString())).thenReturn(false);
            doNothing().when(structOps).set(anyString(), any(), anyLong());

            ThirdPartyClientConfigVO result = service.registerTestClient("test-app");

            assertThat(result).isNotNull();
            assertThat(result.getClientId()).isNotBlank();
            assertThat(result.getClientSecret()).isNotBlank();
            assertThat(result.getClientName()).isEqualTo("test-app");
            assertThat(result.getEnabled()).isTrue();
        }

        @Test
        @DisplayName("clientName 为空时抛出 IllegalArgumentException")
        void registerTestClient_blankClientName_throws() {
            assertThatThrownBy(() -> service.registerTestClient(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("clientName 不能为空");
        }

        @Test
        @DisplayName("clientName 为 null 时抛出 IllegalArgumentException")
        void registerTestClient_nullClientName_throws() {
            assertThatThrownBy(() -> service.registerTestClient(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("clientName 不能为空");
        }
    }
}
