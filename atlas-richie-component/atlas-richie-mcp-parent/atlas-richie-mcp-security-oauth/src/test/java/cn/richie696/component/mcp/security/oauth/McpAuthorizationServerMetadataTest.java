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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link McpAuthorizationServerMetadata} 单元测试：聚焦 RFC 8414 / OIDC AS metadata record
 * 的非空校验、不可变集合语义与可选字段容错。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpAuthorizationServerMetadata 授权服务器元数据")
class McpAuthorizationServerMetadataTest {

    @Nested
    @DisplayName("构造期校验")
    class CompactConstructor {

        @Test
        @DisplayName("issuer 为 null 时抛出 NullPointerException")
        void nullIssuerRejected() {
            assertThatThrownBy(() -> new McpAuthorizationServerMetadata(
                    null,
                    URI.create("https://issuer.example/authorize"),
                    URI.create("https://issuer.example/token"),
                    null,
                    null, null, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("issuer");
        }

        @Test
        @DisplayName("合法入参构造后所有字段均可读取")
        void happyPathFieldsAreAccessible() {
            McpAuthorizationServerMetadata metadata = new McpAuthorizationServerMetadata(
                    URI.create("https://issuer.example"),
                    URI.create("https://issuer.example/authorize"),
                    URI.create("https://issuer.example/token"),
                    URI.create("https://issuer.example/register"),
                    List.of("code"),
                    List.of("authorization_code", "refresh_token"),
                    List.of("S256"));

            assertThat(metadata.issuer()).isEqualTo(URI.create("https://issuer.example"));
            assertThat(metadata.authorizationEndpoint()).isEqualTo(URI.create("https://issuer.example/authorize"));
            assertThat(metadata.tokenEndpoint()).isEqualTo(URI.create("https://issuer.example/token"));
            assertThat(metadata.registrationEndpoint()).isEqualTo(URI.create("https://issuer.example/register"));
            assertThat(metadata.responseTypesSupported()).containsExactly("code");
            assertThat(metadata.grantTypesSupported()).containsExactly("authorization_code", "refresh_token");
            assertThat(metadata.codeChallengeMethodsSupported()).containsExactly("S256");
        }
    }

    @Nested
    @DisplayName("集合归一化")
    class CollectionNormalization {

        @Test
        @DisplayName("列表为 null 时退化为空 List.of()")
        void nullListsBecomeEmpty() {
            McpAuthorizationServerMetadata metadata = new McpAuthorizationServerMetadata(
                    URI.create("https://issuer.example"), null, null, null, null, null, null);

            assertThat(metadata.responseTypesSupported()).isEmpty();
            assertThat(metadata.grantTypesSupported()).isEmpty();
            assertThat(metadata.codeChallengeMethodsSupported()).isEmpty();
        }

        @Test
        @DisplayName("传入可变 ArrayList 后会得到不可变拷贝")
        void mutableListIsDefensivelyCopied() {
            List<String> responseTypes = new ArrayList<>();
            responseTypes.add("code");
            List<String> grants = new ArrayList<>();
            grants.add("authorization_code");
            List<String> methods = new ArrayList<>();
            methods.add("S256");

            McpAuthorizationServerMetadata metadata = new McpAuthorizationServerMetadata(
                    URI.create("https://issuer.example"),
                    URI.create("https://issuer.example/authorize"),
                    URI.create("https://issuer.example/token"),
                    null,
                    responseTypes, grants, methods);

            assertThatThrownBy(() -> metadata.responseTypesSupported().add("token"))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> metadata.grantTypesSupported().add("client_credentials"))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> metadata.codeChallengeMethodsSupported().add("plain"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("后续修改源列表不会影响已构造的 record")
        void sourceListMutationDoesNotLeak() {
            List<String> responseTypes = new ArrayList<>();
            responseTypes.add("code");
            McpAuthorizationServerMetadata metadata = new McpAuthorizationServerMetadata(
                    URI.create("https://issuer.example"), null, null, null,
                    responseTypes, null, null);

            responseTypes.add("token");

            assertThat(metadata.responseTypesSupported()).containsExactly("code");
        }
    }
}