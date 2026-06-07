package com.richie.gateway.service.impl;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.GlobalCacheManager;
import com.richie.component.cache.ops.CollectionOps;
import com.richie.component.cache.ops.FieldOps;
import com.richie.context.utils.spring.JwtUtils;
import com.richie.contract.gateway.model.OAuth2Constants;
import com.richie.gateway.constants.GatewayRedisKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2ScopeServiceImplTest {

    private static final String API_CODE = "order.read";
    private static final String PATH_PATTERN = "/api/orders/**";
    private static final String REQUEST_PATH = "/api/orders/123";
    private static final String HTTP_METHOD = "GET";

    @Mock
    private GlobalCacheManager cacheManager;

    @Mock
    private CollectionOps collectionOps;

    @Mock
    private FieldOps fieldOps;

    private OAuth2ScopeServiceImpl service;

    MockedStatic<GlobalCache> globalCacheMockedStatic;
    MockedStatic<JwtUtils> jwtUtilsMockedStatic;

    @BeforeEach
    void setUp() throws Exception {
        injectCacheManager();

        globalCacheMockedStatic = mockStatic(GlobalCache.class);
        globalCacheMockedStatic.when(GlobalCache::collection).thenReturn(collectionOps);
        globalCacheMockedStatic.when(GlobalCache::field).thenReturn(fieldOps);

        // JwtUtils mocking deferred to ExtractScopesFromTokenTests nested class
        // because JwtUtils class has servlet dependencies that fail ByteBuddy

        service = new OAuth2ScopeServiceImpl();
    }

    private void injectCacheManager() throws Exception {
        var field = GlobalCache.class.getDeclaredField("DELEGATE");
        field.setAccessible(true);
        AtomicReference<GlobalCacheManager> ref = (AtomicReference<GlobalCacheManager>) field.get(null);
        ref.set(cacheManager);
    }

    @AfterEach
    void tearDown() {
        if (globalCacheMockedStatic != null) globalCacheMockedStatic.close();
        // jwtUtilsMockedStatic closed by ExtractScopesFromTokenTests if opened
    }

    @Nested
    @DisplayName("getRequiredScopes 路径匹配与 Scope 查询")
    class GetRequiredScopesTests {

        @Test
        @DisplayName("路径为空时返回空列表")
        void getRequiredScopes_blankPath_returnsEmptyList() {
            List<String> result = service.getRequiredScopes("", "GET");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("路径为 null 时返回空列表")
        void getRequiredScopes_nullPath_returnsEmptyList() {
            List<String> result = service.getRequiredScopes(null, "GET");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("缓存中无 API 配置时返回空列表")
        void getRequiredScopes_noApiCodes_returnsEmptyList() {
            when(collectionOps.get(anyString(), eq(String.class))).thenReturn(null);

            List<String> result = service.getRequiredScopes(REQUEST_PATH, HTTP_METHOD);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("缓存中 API 配置为空时返回空列表")
        void getRequiredScopes_emptyApiCodes_returnsEmptyList() {
            when(collectionOps.get(anyString(), eq(String.class))).thenReturn(Set.of());

            List<String> result = service.getRequiredScopes(REQUEST_PATH, HTTP_METHOD);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("路径不匹配任何 API 时返回空列表")
        void getRequiredScopes_noPathMatch_returnsEmptyList() {
            when(collectionOps.get(anyString(), eq(String.class))).thenReturn(Set.of(API_CODE));
            when(fieldOps.getAll(anyString(), eq(String.class))).thenReturn(Map.of(
                    "pathPattern", PATH_PATTERN,
                    "httpMethod", "POST",
                    "enabled", "true",
                    "requireScope", "true"
            ));

            List<String> result = service.getRequiredScopes(REQUEST_PATH, HTTP_METHOD);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("API 已禁用时被跳过")
        void getRequiredScopes_disabledApi_skipped() {
            when(collectionOps.get(anyString(), eq(String.class))).thenReturn(Set.of(API_CODE));
            when(fieldOps.getAll(anyString(), eq(String.class))).thenReturn(Map.of(
                    "pathPattern", PATH_PATTERN,
                    "httpMethod", "GET",
                    "enabled", "false",
                    "requireScope", "true"
            ));

            List<String> result = service.getRequiredScopes(REQUEST_PATH, HTTP_METHOD);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("HTTP 方法不匹配时被跳过")
        void getRequiredScopes_methodMismatch_skipped() {
            when(collectionOps.get(anyString(), eq(String.class))).thenReturn(Set.of(API_CODE));
            when(fieldOps.getAll(anyString(), eq(String.class))).thenReturn(Map.of(
                    "pathPattern", PATH_PATTERN,
                    "httpMethod", "POST",
                    "enabled", "true",
                    "requireScope", "true"
            ));

            List<String> result = service.getRequiredScopes(REQUEST_PATH, HTTP_METHOD);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("requireScope=false 时返回空列表")
        void getRequiredScopes_scopeNotRequired_returnsEmptyList() {
            when(collectionOps.get(anyString(), eq(String.class))).thenReturn(Set.of(API_CODE));
            when(fieldOps.getAll(anyString(), eq(String.class))).thenReturn(Map.of(
                    "pathPattern", PATH_PATTERN,
                    "httpMethod", "GET",
                    "enabled", "true",
                    "requireScope", "false"
            ));

            List<String> result = service.getRequiredScopes(REQUEST_PATH, HTTP_METHOD);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("接口未配置 scope 时返回空列表")
        void getRequiredScopes_noScopesConfigured_returnsEmptyList() {
            when(collectionOps.get(anyString(), eq(String.class))).thenReturn(Set.of(API_CODE));
            when(fieldOps.getAll(anyString(), eq(String.class))).thenAnswer(inv -> {
                String key = inv.getArgument(0, String.class);
                if (key.contains("config")) {
                    return Map.of(
                            "pathPattern", PATH_PATTERN,
                            "httpMethod", "GET",
                            "enabled", "true",
                            "requireScope", "true"
                    );
                }
                return Map.of();
            });

            List<String> result = service.getRequiredScopes(REQUEST_PATH, HTTP_METHOD);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("happy path：匹配成功返回 scope 列表")
        void getRequiredScopes_matched_returnsScopes() {
            when(collectionOps.get(anyString(), eq(String.class))).thenReturn(Set.of(API_CODE));
            when(fieldOps.getAll(contains(API_CODE), eq(String.class))).thenReturn(Map.of(
                    "pathPattern", PATH_PATTERN,
                    "httpMethod", "GET",
                    "enabled", "true",
                    "requireScope", "true"
            ));
            when(collectionOps.get(contains("scopes"), eq(String.class))).thenReturn(Set.of("read", "write"));

            List<String> result = service.getRequiredScopes(REQUEST_PATH, HTTP_METHOD);

            assertThat(result).containsExactlyInAnyOrder("read", "write");
        }

        @Test
        @DisplayName("ALL 方法匹配任意 HTTP 方法")
        void getRequiredScopes_allMethod_matchesAny() {
            when(collectionOps.get(anyString(), eq(String.class))).thenReturn(Set.of(API_CODE));
            when(fieldOps.getAll(anyString(), eq(String.class))).thenReturn(Map.of(
                    "pathPattern", PATH_PATTERN,
                    "httpMethod", "ALL",
                    "enabled", "true",
                    "requireScope", "true"
            ));
            when(collectionOps.get(contains("scopes"), eq(String.class))).thenReturn(Set.of("admin"));

            List<String> result = service.getRequiredScopes(REQUEST_PATH, "DELETE");

            assertThat(result).contains("admin");
        }

        @Test
        @DisplayName("选择最具体的路径匹配")
        void getRequiredScopes_mostSpecificMatch_wins() {
            String keyApiIndex = "gateway:api:index";
            String keyApiV1Config = "gateway:api:api.v1";
            String keyApiV1OrdersConfig = "gateway:api:api.v1.orders";
            String keyApiV1Scopes = "gateway:api:scopes:api.v1";
            String keyApiV1OrdersScopes = "gateway:api:scopes:api.v1.orders";

            when(collectionOps.get(eq(keyApiIndex), eq(String.class))).thenReturn(Set.of("api.v1", "api.v1.orders"));
            when(fieldOps.getAll(eq(keyApiV1Config), eq(String.class))).thenReturn(Map.of(
                    "pathPattern", "/api/**",
                    "httpMethod", "GET",
                    "enabled", "true",
                    "requireScope", "true"
            ));
            when(fieldOps.getAll(eq(keyApiV1OrdersConfig), eq(String.class))).thenReturn(Map.of(
                    "pathPattern", "/api/v1/orders/**",
                    "httpMethod", "GET",
                    "enabled", "true",
                    "requireScope", "true"
            ));
            when(collectionOps.get(contains("scopes"), eq(String.class))).thenReturn(Set.of("orders:read"));

            List<String> result = service.getRequiredScopes("/api/v1/orders/123", "GET");

            assertThat(result).containsExactly("orders:read");
        }
    }

    @Nested
    @DisplayName("verifyScope Scope 验证")
    class VerifyScopeTests {

        @Test
        @DisplayName("requiredScopes 为空时返回 true")
        void verifyScope_nullRequiredScopes_returnsTrue() {
            boolean result = service.verifyScope(Set.of("read"), null);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("requiredScopes 为空列表时返回 true")
        void verifyScope_emptyRequiredScopes_returnsTrue() {
            boolean result = service.verifyScope(Set.of("read"), List.of());
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("tokenScopes 为空时返回 false")
        void verifyScope_nullTokenScopes_returnsFalse() {
            boolean result = service.verifyScope(null, List.of("read"));
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("tokenScopes 为空列表时返回 false")
        void verifyScope_emptyTokenScopes_returnsFalse() {
            boolean result = service.verifyScope(Set.of(), List.of("read"));
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("token 包含所需 scope 时返回 true（OR 逻辑）")
        void verifyScope_tokenContainsRequired_returnsTrue() {
            boolean result = service.verifyScope(Set.of("read", "write"), List.of("read"));
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("token 包含多个所需 scope 时返回 true")
        void verifyScope_tokenContainsMultiple_returnsTrue() {
            boolean result = service.verifyScope(Set.of("read", "write", "delete"), List.of("read", "write"));
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("token 不包含任何所需 scope 时返回 false")
        void verifyScope_noMatch_returnsFalse() {
            boolean result = service.verifyScope(Set.of("read", "write"), List.of("admin"));
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("token 与 requiredScopes 完全不同时返回 false")
        void verifyScope_totallyDifferent_returnsFalse() {
            boolean result = service.verifyScope(Set.of("read"), List.of("write", "delete"));
            assertThat(result).isFalse();
        }
    }

    @Nested
    @Disabled("JwtUtils.getArgument() is static and cannot be mocked due to ByteBuddy failure on servlet classes")
    @DisplayName("extractScopesFromToken JWT Token 解析")
    class ExtractScopesFromTokenTests {

        @BeforeEach
        void setUpJwtMock() {
            jwtUtilsMockedStatic = mockStatic(JwtUtils.class);
        }

        @AfterEach
        void tearDownJwtMock() {
            if (jwtUtilsMockedStatic != null) {
                jwtUtilsMockedStatic.close();
            }
        }

        @Test
        @DisplayName("token 为空时返回空集合")
        void extractScopesFromToken_blankToken_returnsEmptySet() {
            Set<String> result = service.extractScopesFromToken("");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("token 为 null 时返回空集合")
        void extractScopesFromToken_nullToken_returnsEmptySet() {
            Set<String> result = service.extractScopesFromToken(null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("token 不包含点号时返回空集合")
        void extractScopesFromToken_noDot_returnsEmptySet() {
            Set<String> result = service.extractScopesFromToken("no-dot-token");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("scope 声明为空时返回空集合")
        void extractScopesFromToken_emptyScopeClaim_returnsEmptySet() {
            jwtUtilsMockedStatic.when(() -> JwtUtils.getArgument(anyString(), eq(OAuth2Constants.JWT_CLAIM_SCOPE)))
                    .thenReturn("");

            Set<String> result = service.extractScopesFromToken("some.token.here");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("scope 声明为空白时返回空集合")
        void extractScopesFromToken_whitespaceScopeClaim_returnsEmptySet() {
            jwtUtilsMockedStatic.when(() -> JwtUtils.getArgument(anyString(), eq(OAuth2Constants.JWT_CLAIM_SCOPE)))
                    .thenReturn("   ");

            Set<String> result = service.extractScopesFromToken("some.token.here");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("scope 声明为 null 时返回空集合")
        void extractScopesFromToken_nullScopeClaim_returnsEmptySet() {
            jwtUtilsMockedStatic.when(() -> JwtUtils.getArgument(anyString(), eq(OAuth2Constants.JWT_CLAIM_SCOPE)))
                    .thenReturn(null);

            Set<String> result = service.extractScopesFromToken("some.token.here");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("正常 JWT 返回解析后的 scope 集合")
        void extractScopesFromToken_validJwt_returnsScopes() {
            jwtUtilsMockedStatic.when(() -> JwtUtils.getArgument(anyString(), eq(OAuth2Constants.JWT_CLAIM_SCOPE)))
                    .thenReturn("read write admin");

            Set<String> result = service.extractScopesFromToken("valid.jwt.token");

            assertThat(result).containsExactlyInAnyOrder("read", "write", "admin");
        }

        @Test
        @DisplayName("多个空格分隔的 scope 正常解析")
        void extractScopesFromToken_multipleSpaces_parsesCorrectly() {
            jwtUtilsMockedStatic.when(() -> JwtUtils.getArgument(anyString(), eq(OAuth2Constants.JWT_CLAIM_SCOPE)))
                    .thenReturn("read   write    admin");

            Set<String> result = service.extractScopesFromToken("valid.jwt.token");

            assertThat(result).containsExactlyInAnyOrder("read", "write", "admin");
        }

        @Test
        @DisplayName("JWT 解析异常时返回空集合")
        void extractScopesFromToken_jwtException_returnsEmptySet() {
            jwtUtilsMockedStatic.when(() -> JwtUtils.getArgument(anyString(), eq(OAuth2Constants.JWT_CLAIM_SCOPE)))
                    .thenThrow(new RuntimeException("JWT error"));

            Set<String> result = service.extractScopesFromToken("invalid.jwt");

            assertThat(result).isEmpty();
        }
    }
}
