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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link McpProtectedResourceMetadata} 单元测试：覆盖 RFC 9728 PRM 模型的 resource
 * 必填校验、所有集合字段的不可变拷贝以及 extensions 透传语义。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpProtectedResourceMetadata 受保护资源元数据")
class McpProtectedResourceMetadataTest {

    private static final URI RESOURCE = URI.create("https://mcp.example/mcp");

    @Nested
    @DisplayName("紧凑构造器校验")
    class CompactConstructor {

        @Test
        @DisplayName("resource 为 null 时抛 NullPointerException")
        void nullResourceRejected() {
            assertThatThrownBy(() -> new McpProtectedResourceMetadata(
                    null, List.of(URI.create("https://idp")), List.of("tools.read"), Map.of()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("resource");
        }

        @Test
        @DisplayName("集合字段为 null 时退化为空不可变集合")
        void nullCollectionsBecomeEmpty() {
            McpProtectedResourceMetadata metadata = new McpProtectedResourceMetadata(
                    RESOURCE, null, null, null);

            assertThat(metadata.authorizationServers()).isEmpty();
            assertThat(metadata.scopesSupported()).isEmpty();
            assertThat(metadata.extensions()).isEmpty();
        }

        @Test
        @DisplayName("authorizationServers 传入可变 List 后变为不可变")
        void authorizationServersAreDefensivelyCopied() {
            List<URI> mutable = new ArrayList<>();
            mutable.add(URI.create("https://idp"));

            McpProtectedResourceMetadata metadata = new McpProtectedResourceMetadata(
                    RESOURCE, mutable, List.of("tools.read"), Map.of());

            assertThatThrownBy(() -> metadata.authorizationServers().add(URI.create("https://evil")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("scopesSupported 传入可变 List 后变为不可变")
        void scopesSupportedAreDefensivelyCopied() {
            List<String> mutable = new ArrayList<>();
            mutable.add("tools.read");

            McpProtectedResourceMetadata metadata = new McpProtectedResourceMetadata(
                    RESOURCE, List.of(), mutable, Map.of());

            assertThatThrownBy(() -> metadata.scopesSupported().add("admin"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("extensions 传入可变 Map 后变为不可变")
        void extensionsAreDefensivelyCopied() {
            Map<String, Object> mutable = new HashMap<>();
            mutable.put("x-custom", "v");

            McpProtectedResourceMetadata metadata = new McpProtectedResourceMetadata(
                    RESOURCE, List.of(), List.of(), mutable);

            assertThatThrownBy(() -> metadata.extensions().put("x-malicious", "v"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("修改源集合不会影响 record 副本")
        void sourceMutationDoesNotLeak() {
            List<URI> redirects = new ArrayList<>();
            redirects.add(URI.create("https://idp"));
            Map<String, Object> extensions = new LinkedHashMap<>();
            extensions.put("x-version", "1");

            McpProtectedResourceMetadata metadata = new McpProtectedResourceMetadata(
                    RESOURCE, redirects, List.of(), extensions);

            redirects.add(URI.create("https://another"));
            extensions.put("x-version", "2");

            assertThat(metadata.authorizationServers())
                    .containsExactly(URI.create("https://idp"));
            assertThat(metadata.extensions()).containsEntry("x-version", "1");
        }
    }
}