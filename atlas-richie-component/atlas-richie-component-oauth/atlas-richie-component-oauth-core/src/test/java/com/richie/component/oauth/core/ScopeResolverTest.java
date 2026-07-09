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
package com.richie.component.oauth.core;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.ops.CollectionOps;
import com.richie.component.cache.ops.FieldOps;
import com.richie.context.utils.spring.JwtUtils;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScopeResolverTest {

    @Mock
    private CollectionOps collectionOps;
    @Mock
    private FieldOps fieldOps;

    private ScopeResolver scopeResolver;

    @BeforeEach
    void setUp() {
        scopeResolver = new ScopeResolver();
    }

    @Test
    void getRequiredScopes_whenPathMatches_returnsScopes() {
        String path = "/api/users/123";
        String method = "GET";

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::collection).thenReturn(collectionOps);
            cache.when(GlobalCache::field).thenReturn(fieldOps);

            Set<String> apiCodes = Set.of("api-user", "api-order");
            when(collectionOps.get(anyString(), eq(String.class))).thenReturn(apiCodes);

            Map<String, String> apiConfig = Map.of(
                    "pathPattern", "/api/users/**",
                    "httpMethod", "GET",
                    "enabled", "true",
                    "requireScope", "true"
            );
            when(fieldOps.getAll(anyString(), eq(String.class))).thenReturn(apiConfig);

            Set<String> scopes = Set.of("user:read", "user:write");
            when(collectionOps.get(anyString(), eq(String.class))).thenReturn(scopes);

            List<String> result = scopeResolver.getRequiredScopes(path, method);

            assertThat(result).isNotNull();
            assertThat(result).containsExactlyInAnyOrder("user:read", "user:write");
        }
    }

    @Test
    void getRequiredScopes_whenNoMatch_returnsEmptyList() {
        String path = "/unknown/endpoint";
        String method = "GET";

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::collection).thenReturn(collectionOps);
            cache.when(GlobalCache::field).thenReturn(fieldOps);

            Set<String> apiCodes = Set.of("api-user");
            when(collectionOps.get(anyString(), eq(String.class))).thenReturn(apiCodes);

            Map<String, String> apiConfig = Map.of(
                    "pathPattern", "/api/users/**",
                    "httpMethod", "GET",
                    "enabled", "true"
            );
            when(fieldOps.getAll(anyString(), eq(String.class))).thenReturn(apiConfig);

            List<String> result = scopeResolver.getRequiredScopes(path, method);

            assertThat(result).isEmpty();
        }
    }

    @Test
    void getRequiredScopes_whenNoApiCodes_returnsEmptyList() {
        String path = "/api/users";
        String method = "GET";

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::collection).thenReturn(collectionOps);
            when(collectionOps.get(anyString(), eq(String.class))).thenReturn(null);

            List<String> result = scopeResolver.getRequiredScopes(path, method);

            assertThat(result).isEmpty();
        }
    }

    @Test
    void getRequiredScopes_whenRequireScopeFalse_returnsEmptyList() {
        String path = "/api/public/data";
        String method = "GET";

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::collection).thenReturn(collectionOps);
            cache.when(GlobalCache::field).thenReturn(fieldOps);

            Set<String> apiCodes = Set.of("api-public");
            when(collectionOps.get(anyString(), eq(String.class))).thenReturn(apiCodes);

            Map<String, String> apiConfig = Map.of(
                    "pathPattern", "/api/public/**",
                    "httpMethod", "GET",
                    "enabled", "true",
                    "requireScope", "false"
            );
            when(fieldOps.getAll(anyString(), eq(String.class))).thenReturn(apiConfig);

            List<String> result = scopeResolver.getRequiredScopes(path, method);

            assertThat(result).isEmpty();
        }
    }

    @Test
    void getRequiredScopes_whenMethodMismatch_returnsEmptyList() {
        String path = "/api/users";
        String method = "DELETE";

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::collection).thenReturn(collectionOps);
            cache.when(GlobalCache::field).thenReturn(fieldOps);

            Set<String> apiCodes = Set.of("api-user");
            when(collectionOps.get(anyString(), eq(String.class))).thenReturn(apiCodes);

            Map<String, String> apiConfig = Map.of(
                    "pathPattern", "/api/users",
                    "httpMethod", "GET",
                    "enabled", "true"
            );
            when(fieldOps.getAll(anyString(), eq(String.class))).thenReturn(apiConfig);

            List<String> result = scopeResolver.getRequiredScopes(path, method);

            assertThat(result).isEmpty();
        }
    }

    @Test
    void getRequiredScopes_whenApiDisabled_returnsEmptyList() {
        String path = "/api/users";
        String method = "GET";

        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::collection).thenReturn(collectionOps);
            cache.when(GlobalCache::field).thenReturn(fieldOps);

            Set<String> apiCodes = Set.of("api-user");
            when(collectionOps.get(anyString(), eq(String.class))).thenReturn(apiCodes);

            Map<String, String> apiConfig = Map.of(
                    "pathPattern", "/api/users",
                    "httpMethod", "GET",
                    "enabled", "false"
            );
            when(fieldOps.getAll(anyString(), eq(String.class))).thenReturn(apiConfig);

            List<String> result = scopeResolver.getRequiredScopes(path, method);

            assertThat(result).isEmpty();
        }
    }

    @Test
    void getRequiredScopes_whenBlankPath_returnsEmptyList() {
        List<String> result = scopeResolver.getRequiredScopes("", "GET");
        assertThat(result).isEmpty();

        result = scopeResolver.getRequiredScopes(null, "GET");
        assertThat(result).isEmpty();
    }

    @Test
    void verifyScope_whenTokenHasRequiredScope_returnsTrue() {
        Set<String> tokenScopes = Set.of("read", "write", "delete");
        List<String> requiredScopes = List.of("read");

        boolean result = scopeResolver.verifyScope(tokenScopes, requiredScopes);

        assertThat(result).isTrue();
    }

    @Test
    void verifyScope_whenTokenMissingRequiredScope_returnsFalse() {
        Set<String> tokenScopes = Set.of("read", "write");
        List<String> requiredScopes = List.of("delete");

        boolean result = scopeResolver.verifyScope(tokenScopes, requiredScopes);

        assertThat(result).isFalse();
    }

    @Test
    void verifyScope_whenNoRequiredScopes_returnsTrue() {
        Set<String> tokenScopes = Set.of("read", "write");
        List<String> requiredScopes = List.of();

        boolean result = scopeResolver.verifyScope(tokenScopes, requiredScopes);

        assertThat(result).isTrue();
    }

    @Test
    void verifyScope_whenNullRequiredScopes_returnsTrue() {
        Set<String> tokenScopes = Set.of("read", "write");

        boolean result = scopeResolver.verifyScope(tokenScopes, null);

        assertThat(result).isTrue();
    }

    @Test
    void verifyScope_whenNoTokenScopes_returnsFalse() {
        Set<String> tokenScopes = Set.of();
        List<String> requiredScopes = List.of("read");

        boolean result = scopeResolver.verifyScope(tokenScopes, requiredScopes);

        assertThat(result).isFalse();
    }

    @Test
    void verifyScope_whenNullTokenScopes_returnsFalse() {
        List<String> requiredScopes = List.of("read");

        boolean result = scopeResolver.verifyScope(null, requiredScopes);

        assertThat(result).isFalse();
    }

    @Test
    void extractScopesFromToken_whenValidJwt_returnsScopes() {
        String accessToken = "valid.jwt.token";

        try (MockedStatic<JwtUtils> jwtUtils = mockStatic(JwtUtils.class)) {
            jwtUtils.when(() -> JwtUtils.getArgument(accessToken, "scope"))
                    .thenReturn("read write delete");

            Set<String> result = scopeResolver.extractScopesFromToken(accessToken);

            assertThat(result).containsExactlyInAnyOrder("read", "write", "delete");
        }
    }

    @Test
    void extractScopesFromToken_whenNoScopeClaim_returnsEmptySet() {
        String accessToken = "valid.jwt.token";

        try (MockedStatic<JwtUtils> jwtUtils = mockStatic(JwtUtils.class)) {
            jwtUtils.when(() -> JwtUtils.getArgument(accessToken, "scope")).thenReturn(null);

            Set<String> result = scopeResolver.extractScopesFromToken(accessToken);

            assertThat(result).isEmpty();
        }
    }

    @Test
    void extractScopesFromToken_whenMalformedJwt_returnsEmptySet() {
        String accessToken = "not-a-valid-jwt";

        try (MockedStatic<JwtUtils> jwtUtils = mockStatic(JwtUtils.class)) {
            jwtUtils.when(() -> JwtUtils.getArgument(anyString(), eq("scope")))
                    .thenThrow(new RuntimeException("Invalid JWT"));

            Set<String> result = scopeResolver.extractScopesFromToken(accessToken);

            assertThat(result).isEmpty();
        }
    }

    @Test
    void extractScopesFromToken_whenBlankToken_returnsEmptySet() {
        assertThat(scopeResolver.extractScopesFromToken("")).isEmpty();
        assertThat(scopeResolver.extractScopesFromToken(null)).isEmpty();
    }

    @Test
    void extractScopesFromToken_whenNoDotInToken_returnsEmptySet() {
        String accessToken = "no-dots-here";

        Set<String> result = scopeResolver.extractScopesFromToken(accessToken);

        assertThat(result).isEmpty();
    }
}
