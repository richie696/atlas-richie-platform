/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.oauth.authz;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthorizationServerMetadata 测试")
class AuthorizationServerMetadataTest {

    @Test
    @DisplayName("@Data 注解生成所有字段的 getter 和 setter")
    void dataAnnotation_generatesGettersAndSetters() {
        AuthorizationServerMetadata metadata = new AuthorizationServerMetadata();

        metadata.setIssuer("https://auth.example.com");
        metadata.setAuthorizationEndpoint("https://auth.example.com/authorize");
        metadata.setTokenEndpoint("https://auth.example.com/token");
        metadata.setIntrospectionEndpoint("https://auth.example.com/introspect");
        metadata.setResponseTypesSupported(Arrays.asList("code", "token"));
        metadata.setCodeChallengeMethodsSupported(Arrays.asList("S256"));
        metadata.setGrantTypesSupported(Arrays.asList("authorization_code", "refresh_token"));
        metadata.setScopesSupported(Arrays.asList("read", "write"));

        assertThat(metadata.getIssuer()).isEqualTo("https://auth.example.com");
        assertThat(metadata.getAuthorizationEndpoint()).isEqualTo("https://auth.example.com/authorize");
        assertThat(metadata.getTokenEndpoint()).isEqualTo("https://auth.example.com/token");
        assertThat(metadata.getIntrospectionEndpoint()).isEqualTo("https://auth.example.com/introspect");
        assertThat(metadata.getResponseTypesSupported()).containsExactly("code", "token");
        assertThat(metadata.getCodeChallengeMethodsSupported()).containsExactly("S256");
        assertThat(metadata.getGrantTypesSupported()).containsExactly("authorization_code", "refresh_token");
        assertThat(metadata.getScopesSupported()).containsExactly("read", "write");
    }

    @Test
    @DisplayName("@Builder 注解创建实例正确")
    void builderAnnotation_createsInstanceCorrectly() {
        List<String> responseTypes = Arrays.asList("code", "token");
        List<String> challengeMethods = Arrays.asList("S256");
        List<String> grantTypes = Arrays.asList("authorization_code");
        List<String> scopes = Arrays.asList("read", "write");

        AuthorizationServerMetadata metadata = AuthorizationServerMetadata.builder()
                .issuer("https://auth.example.com")
                .authorizationEndpoint("https://auth.example.com/authorize")
                .tokenEndpoint("https://auth.example.com/token")
                .introspectionEndpoint("https://auth.example.com/introspect")
                .responseTypesSupported(responseTypes)
                .codeChallengeMethodsSupported(challengeMethods)
                .grantTypesSupported(grantTypes)
                .scopesSupported(scopes)
                .build();

        assertThat(metadata.getIssuer()).isEqualTo("https://auth.example.com");
        assertThat(metadata.getAuthorizationEndpoint()).isEqualTo("https://auth.example.com/authorize");
        assertThat(metadata.getTokenEndpoint()).isEqualTo("https://auth.example.com/token");
        assertThat(metadata.getIntrospectionEndpoint()).isEqualTo("https://auth.example.com/introspect");
        assertThat(metadata.getResponseTypesSupported()).isEqualTo(responseTypes);
        assertThat(metadata.getCodeChallengeMethodsSupported()).isEqualTo(challengeMethods);
        assertThat(metadata.getGrantTypesSupported()).isEqualTo(grantTypes);
        assertThat(metadata.getScopesSupported()).isEqualTo(scopes);
    }

    @Test
    @DisplayName("@NoArgsConstructor 生成无参构造函数")
    void noArgsConstructor_createsInstanceWithNoArgs() {
        AuthorizationServerMetadata metadata = new AuthorizationServerMetadata();

        assertThat(metadata).isNotNull();
        assertThat(metadata.getIssuer()).isNull();
        assertThat(metadata.getAuthorizationEndpoint()).isNull();
    }

    @Test
    @DisplayName("@AllArgsConstructor 生成全参构造函数")
    void allArgsConstructor_createsInstanceWithAllArgs() {
        List<String> responseTypes = Arrays.asList("code");
        List<String> challengeMethods = Arrays.asList("S256");
        List<String> grantTypes = Arrays.asList("authorization_code");
        List<String> scopes = Arrays.asList("read");

        AuthorizationServerMetadata metadata = new AuthorizationServerMetadata(
                "https://auth.example.com",
                "https://auth.example.com/authorize",
                "https://auth.example.com/token",
                "https://auth.example.com/introspect",
                responseTypes,
                challengeMethods,
                grantTypes,
                scopes
        );

        assertThat(metadata.getIssuer()).isEqualTo("https://auth.example.com");
        assertThat(metadata.getAuthorizationEndpoint()).isEqualTo("https://auth.example.com/authorize");
        assertThat(metadata.getTokenEndpoint()).isEqualTo("https://auth.example.com/token");
        assertThat(metadata.getIntrospectionEndpoint()).isEqualTo("https://auth.example.com/introspect");
        assertThat(metadata.getResponseTypesSupported()).isEqualTo(responseTypes);
        assertThat(metadata.getCodeChallengeMethodsSupported()).isEqualTo(challengeMethods);
        assertThat(metadata.getGrantTypesSupported()).isEqualTo(grantTypes);
        assertThat(metadata.getScopesSupported()).isEqualTo(scopes);
    }
}
