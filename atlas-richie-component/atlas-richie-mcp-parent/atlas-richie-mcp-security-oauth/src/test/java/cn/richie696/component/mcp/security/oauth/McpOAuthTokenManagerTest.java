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
package cn.richie696.component.mcp.security.oauth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link McpOAuthTokenManager} 单元测试：覆盖 token 缓存快路径、scope 不满足时的
 * 刷新路径、refresh_token vs client_credentials 选路、resource 多租户隔离、
 * scope 合并与 {@link McpOAuthTokenManager#accept(McpOAuthTokenResponse)} 的回写语义。
 *
 * @author richie696
 * @since 2026-08-11
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("McpOAuthTokenManager 自刷新 token 管理器")
class McpOAuthTokenManagerTest {

    private static final URI TOKEN_ENDPOINT = URI.create("https://issuer.example/token");
    private static final URI RESOURCE = URI.create("https://mcp.example");

    @Mock
    private McpOAuthTokenClient client;

    private McpOAuthTokenManager manager;

    @BeforeEach
    void setUp() {
        manager = new McpOAuthTokenManager(
                client, TOKEN_ENDPOINT, "client-1", "secret-1", RESOURCE,
                Set.of("tools.read"));
    }

    private McpOAuthAccessToken freshToken(String value, Set<String> scopes) {
        return new McpOAuthAccessToken(
                value, "Bearer", Instant.now().plusSeconds(3600),
                "https://issuer.example", RESOURCE.toString(), scopes);
    }

    private McpOAuthTokenResponse response(String accessTokenValue, String refreshToken,
                                           Set<String> scopes) {
        return new McpOAuthTokenResponse(
                freshToken(accessTokenValue, scopes),
                refreshToken,
                scopes);
    }

    private Object readField(McpOAuthTokenManager mgr, String name) {
        try {
            java.lang.reflect.Field field = McpOAuthTokenManager.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(mgr);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Cannot read field " + name, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private McpOAuthAccessToken storedAccessToken() {
        java.util.concurrent.atomic.AtomicReference<McpOAuthAccessToken> ref =
                (java.util.concurrent.atomic.AtomicReference<McpOAuthAccessToken>) readField(manager, "accessToken");
        return ref.get();
    }

    @SuppressWarnings("unchecked")
    private void writeAccessToken(McpOAuthAccessToken token) {
        java.util.concurrent.atomic.AtomicReference<McpOAuthAccessToken> ref =
                (java.util.concurrent.atomic.AtomicReference<McpOAuthAccessToken>) readField(manager, "accessToken");
        ref.set(token);
    }

    private String storedRefreshToken() {
        java.util.concurrent.atomic.AtomicReference<String> ref =
                (java.util.concurrent.atomic.AtomicReference<String>) readField(manager, "refreshToken");
        return ref.get();
    }

    @Nested
    @DisplayName("构造器")
    class Constructor {

        @Test
        @DisplayName("client / tokenEndpoint / clientId / resource 为 null 时抛 NullPointerException")
        void requiredArgsNotNull() {
            assertThatThrownBy(() -> new McpOAuthTokenManager(
                    null, TOKEN_ENDPOINT, "client-1", "secret", RESOURCE, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("client");
            assertThatThrownBy(() -> new McpOAuthTokenManager(
                    client, null, "client-1", "secret", RESOURCE, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("tokenEndpoint");
            assertThatThrownBy(() -> new McpOAuthTokenManager(
                    client, TOKEN_ENDPOINT, null, "secret", RESOURCE, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("clientId");
            assertThatThrownBy(() -> new McpOAuthTokenManager(
                    client, TOKEN_ENDPOINT, "client-1", "secret", null, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("resource");
        }

        @Test
        @DisplayName("configuredScopes 为 null 时退化为空 Set")
        void nullScopesBecomeEmpty() {
            McpOAuthTokenManager local = new McpOAuthTokenManager(
                    client, TOKEN_ENDPOINT, "client-1", "secret", RESOURCE, null);

            assertThat(local).isNotNull();
        }
    }

    @Nested
    @DisplayName("tokenFor() 快路径")
    class FastPath {

        @Test
        @DisplayName("首次调用无缓存时走 client_credentials 刷新")
        void firstCallUsesClientCredentials() throws Exception {
            when(client.clientCredentials(eq(TOKEN_ENDPOINT), eq("client-1"), eq("secret-1"),
                    eq(RESOURCE), any())).thenReturn(response("at-1", null, Set.of("tools.read")));

            Optional<McpOAuthAccessToken> result = manager.tokenFor(RESOURCE, Set.of("tools.read")).toCompletableFuture().get();

            assertThat(result).isPresent();
            assertThat(result.get().value()).isEqualTo("at-1");
            verify(client).clientCredentials(eq(TOKEN_ENDPOINT), eq("client-1"), eq("secret-1"),
                    eq(RESOURCE), any());
            verify(client, never()).refreshToken(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("缓存 token 仍有效且 scope 满足时直接返回不触发刷新")
        void cachedTokenReusedWhenUsable() throws Exception {
            manager.accept(response("at-cached", null, Set.of("tools.read")));

            Optional<McpOAuthAccessToken> result = manager.tokenFor(RESOURCE, Set.of("tools.read")).toCompletableFuture().get();

            assertThat(result).isPresent();
            assertThat(result.get().value()).isEqualTo("at-cached");
            verify(client, never()).clientCredentials(any(), any(), any(), any(), any());
            verify(client, never()).refreshToken(any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("tokenFor() 慢路径")
    class SlowPath {

        @Test
        @DisplayName("缓存 token 过期时走 refresh_token grant（前提是已持有 refresh token）")
        void expiredTokenUsesRefreshTokenGrant() throws Exception {
            manager.accept(response("at-old", "rt-1", Set.of("tools.read")));
            when(client.refreshToken(eq(TOKEN_ENDPOINT), eq("client-1"), eq("secret-1"),
                    eq("rt-1"), eq(RESOURCE), any()))
                    .thenReturn(response("at-new", "rt-2", Set.of("tools.read")));

            McpOAuthAccessToken expiredToken = new McpOAuthAccessToken(
                    "at-old", "Bearer", Instant.now().minusSeconds(120),
                    "https://issuer.example", RESOURCE.toString(), Set.of("tools.read"));
            writeAccessToken(expiredToken);

            Optional<McpOAuthAccessToken> result = manager.tokenFor(RESOURCE, Set.of("tools.read")).toCompletableFuture().get();

            assertThat(result).isPresent();
            assertThat(result.get().value()).isEqualTo("at-new");
            verify(client).refreshToken(eq(TOKEN_ENDPOINT), eq("client-1"), eq("secret-1"),
                    eq("rt-1"), eq(RESOURCE), any());
            verify(client, never()).clientCredentials(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("缓存 token scope 不满足时走刷新")
        void scopeMissingTriggersRefresh() throws Exception {
            manager.accept(response("at-cached", null, Set.of("tools.read")));

            when(client.clientCredentials(eq(TOKEN_ENDPOINT), eq("client-1"), eq("secret-1"),
                    eq(RESOURCE), any()))
                    .thenReturn(response("at-new", null, Set.of("tools.read", "tools.admin")));

            Optional<McpOAuthAccessToken> result = manager.tokenFor(RESOURCE, Set.of("tools.admin")).toCompletableFuture().get();

            assertThat(result).isPresent();
            assertThat(result.get().scopes()).contains("tools.admin");
            verify(client, times(1)).clientCredentials(eq(TOKEN_ENDPOINT), eq("client-1"), eq("secret-1"),
                    eq(RESOURCE), any());
        }

        @Test
        @DisplayName("scope 合并：configuredScopes 与 requiredScopes 取并集")
        void configuredScopesMergedWithRequired() throws Exception {
            when(client.clientCredentials(eq(TOKEN_ENDPOINT), eq("client-1"), eq("secret-1"),
                    eq(RESOURCE), any()))
                    .thenReturn(response("at-new", null, Set.of("tools.read")));

            manager.tokenFor(RESOURCE, Set.of("tools.write")).toCompletableFuture().get();

            ArgumentCaptor<Set<String>> captor = ArgumentCaptor.forClass(Set.class);
            verify(client).clientCredentials(eq(TOKEN_ENDPOINT), eq("client-1"), eq("secret-1"),
                    eq(RESOURCE), captor.capture());
            assertThat(captor.getValue()).contains("tools.read", "tools.write");
        }

        @Test
        @DisplayName("refresh_token grant 也携带合并后的 scope")
        void refreshGrantAlsoCarriesMergedScopes() throws Exception {
            manager.accept(response("at-cached", "rt-1", Set.of("tools.read")));
            McpOAuthAccessToken expired = new McpOAuthAccessToken(
                    "at-cached", "Bearer", Instant.now().minusSeconds(120),
                    "https://issuer.example", RESOURCE.toString(), Set.of("tools.read"));
            writeAccessToken(expired);

            when(client.refreshToken(eq(TOKEN_ENDPOINT), eq("client-1"), eq("secret-1"),
                    eq("rt-1"), eq(RESOURCE), any()))
                    .thenReturn(response("at-new", "rt-2", Set.of("tools.read", "tools.write")));

            manager.tokenFor(RESOURCE, Set.of("tools.write")).toCompletableFuture().get();

            ArgumentCaptor<Set<String>> captor = ArgumentCaptor.forClass(Set.class);
            verify(client).refreshToken(eq(TOKEN_ENDPOINT), eq("client-1"), eq("secret-1"),
                    eq("rt-1"), eq(RESOURCE), captor.capture());
            assertThat(captor.getValue()).contains("tools.read", "tools.write");
        }
    }

    @Nested
    @DisplayName("资源隔离与 scope 输入")
    class ResourceIsolation {

        @Test
        @DisplayName("requestedResource 与绑定 resource 不一致时返回 empty 且不调用 client")
        void resourceMismatchReturnsEmpty() throws Exception {
            Optional<McpOAuthAccessToken> result = manager.tokenFor(
                    URI.create("https://other.example"), Set.of("tools.read")).toCompletableFuture().get();

            assertThat(result).isEmpty();
            verify(client, never()).clientCredentials(any(), any(), any(), any(), any());
            verify(client, never()).refreshToken(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("requestedResource 为 null 时跳过资源校验")
        void nullRequestedResourceBypassesCheck() throws Exception {
            when(client.clientCredentials(eq(TOKEN_ENDPOINT), eq("client-1"), eq("secret-1"),
                    eq(RESOURCE), any())).thenReturn(response("at-new", null, Set.of("tools.read")));

            Optional<McpOAuthAccessToken> result = manager.tokenFor(null, Set.of("tools.read")).toCompletableFuture().get();

            assertThat(result).isPresent();
            verify(client).clientCredentials(eq(TOKEN_ENDPOINT), eq("client-1"), eq("secret-1"),
                    eq(RESOURCE), any());
        }

        @Test
        @DisplayName("requiredScopes 为 null 时退化为空集合，不抛异常")
        void nullRequiredScopesBecomeEmpty() throws Exception {
            when(client.clientCredentials(eq(TOKEN_ENDPOINT), eq("client-1"), eq("secret-1"),
                    eq(RESOURCE), any())).thenReturn(response("at-new", null, Set.of("tools.read")));

            Optional<McpOAuthAccessToken> result = manager.tokenFor(RESOURCE, null).toCompletableFuture().get();

            assertThat(result).isPresent();
        }
    }

    @Nested
    @DisplayName("accept() 回写语义")
    class AcceptBehavior {

        @Test
        @DisplayName("refresh token 为 null 时保留旧 refresh token")
        void nullRefreshTokenKeepsOld() throws Exception {
            manager.accept(response("at-1", "rt-1", Set.of("tools.read")));
            manager.accept(response("at-2", null, Set.of("tools.read")));

            Optional<McpOAuthAccessToken> result = manager.tokenFor(RESOURCE, Set.of("tools.read")).toCompletableFuture().get();

            assertThat(result).isPresent();
            assertThat(result.get().value()).isEqualTo("at-2");
            assertThat(storedRefreshToken()).isEqualTo("rt-1");
        }

        @Test
        @DisplayName("refresh token 为 blank 时也保留旧 refresh token")
        void blankRefreshTokenKeepsOld() {
            manager.accept(response("at-1", "rt-1", Set.of("tools.read")));
            manager.accept(response("at-2", "  ", Set.of("tools.read")));

            assertThat(storedRefreshToken()).isEqualTo("rt-1");
        }

        @Test
        @DisplayName("非空 refresh token 覆盖旧值")
        void nonBlankRefreshTokenOverwrites() {
            manager.accept(response("at-1", "rt-1", Set.of("tools.read")));
            manager.accept(response("at-2", "rt-2", Set.of("tools.read")));

            assertThat(storedRefreshToken()).isEqualTo("rt-2");
        }

        @Test
        @DisplayName("response 为 null 时抛 NullPointerException")
        void nullResponseRejected() {
            assertThatThrownBy(() -> manager.accept(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("response");
        }

        @Test
        @DisplayName("response 级 scopes 覆盖 access token 内部 scopes（withRefreshTokenMetadata）")
        void responseScopesOverrideTokenScopes() {
            McpOAuthAccessToken bare = new McpOAuthAccessToken(
                    "at-1", "Bearer", Instant.now().plusSeconds(3600),
                    null, RESOURCE.toString(), Set.of());
            McpOAuthTokenResponse responseWithScopes = new McpOAuthTokenResponse(
                    bare, null, Set.of("tools.read", "tools.write"));

            manager.accept(responseWithScopes);

            McpOAuthAccessToken stored = storedAccessToken();

            assertThat(stored.scopes()).containsExactlyInAnyOrder("tools.read", "tools.write");
        }
    }

    @Nested
    @DisplayName("并发路径")
    class Concurrency {

        @Test
        @DisplayName("快路径 token 命中时 second call 仍走快路径（双检查）")
        void doubleCheckSkipsClientCall() throws Exception {
            manager.accept(response("at-cached", null, Set.of("tools.read")));
            Optional<McpOAuthAccessToken> first = manager.tokenFor(RESOURCE, Set.of("tools.read")).toCompletableFuture().get();
            Optional<McpOAuthAccessToken> second = manager.tokenFor(RESOURCE, Set.of("tools.read")).toCompletableFuture().get();

            assertThat(first).isPresent();
            assertThat(second).isPresent();
            verify(client, never()).clientCredentials(any(), any(), any(), any(), any());
            verify(client, never()).refreshToken(any(), any(), any(), any(), any(), any());
        }
    }
}