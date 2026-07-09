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
package com.richie.component.web.core.spi.support;

import com.richie.component.web.core.config.ratelimit.WebFilterProperties;
import com.richie.component.web.core.spi.KeyDimension;
import com.richie.component.web.core.spi.WebRequestContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompositeKeyResolverTest {

    private static KeyDimension dim(String name, String value) {
        return new KeyDimension() {
            @Override public String name() { return name; }
            @Override public String extract(WebRequestContext ctx) { return value; }
        };
    }

    private static KeyDimension dimNull(String name) {
        return new KeyDimension() {
            @Override public String name() { return name; }
            @Override public String extract(WebRequestContext ctx) { return null; }
        };
    }

    @Test
    void singleDimension_returnsNameColonValue() {
        CompositeKeyResolver resolver = new CompositeKeyResolver(
                List.of(dim("client", "user-1")), "|");
        WebRequestContext ctx = MutableWebRequestContext.builder().build();
        assertThat(resolver.resolve(ctx)).isEqualTo("client:user-1");
    }

    @Test
    void multipleDimensions_joinedInOrder() {
        CompositeKeyResolver resolver = new CompositeKeyResolver(List.of(
                dim("client", "user-1"),
                dim("tenant", "acme"),
                dim("ip", "1.2.3.4"),
                dim("path", "/api/v1/orders")
        ), "|");
        WebRequestContext ctx = MutableWebRequestContext.builder().build();
        assertThat(resolver.resolve(ctx))
                .isEqualTo("client:user-1|tenant:acme|ip:1.2.3.4|path:/api/v1/orders");
    }

    @Test
    void nullDimensionsAreSkipped() {
        CompositeKeyResolver resolver = new CompositeKeyResolver(List.of(
                dim("client", "user-1"),
                dimNull("tenant"),
                dim("ip", "1.2.3.4")
        ), "|");
        WebRequestContext ctx = MutableWebRequestContext.builder().build();
        assertThat(resolver.resolve(ctx)).isEqualTo("client:user-1|ip:1.2.3.4");
    }

    @Test
    void emptyStringDimensionsAreSkipped() {
        CompositeKeyResolver resolver = new CompositeKeyResolver(List.of(
                dim("client", "user-1"),
                dim("tenant", ""),
                dim("ip", "1.2.3.4")
        ), "|");
        WebRequestContext ctx = MutableWebRequestContext.builder().build();
        assertThat(resolver.resolve(ctx)).isEqualTo("client:user-1|ip:1.2.3.4");
    }

    @Test
    void allDimensionsNull_returnsNull() {
        CompositeKeyResolver resolver = new CompositeKeyResolver(List.of(
                dimNull("client"),
                dimNull("tenant"),
                dimNull("ip")
        ), "|");
        WebRequestContext ctx = MutableWebRequestContext.builder().build();
        assertThat(resolver.resolve(ctx)).isNull();
    }

    @Test
    void emptyDimensionsList_returnsNull() {
        CompositeKeyResolver resolver = new CompositeKeyResolver(List.of(), "|");
        WebRequestContext ctx = MutableWebRequestContext.builder().build();
        assertThat(resolver.resolve(ctx)).isNull();
    }

    @Test
    void customSeparator_isRespected() {
        CompositeKeyResolver resolver = new CompositeKeyResolver(List.of(
                dim("client", "user-1"),
                dim("tenant", "acme")
        ), "#");
        WebRequestContext ctx = MutableWebRequestContext.builder().build();
        assertThat(resolver.resolve(ctx)).isEqualTo("client:user-1#tenant:acme");
    }

    @Test
    void constructor_emptySeparatorRejected() {
        assertThatThrownBy(() -> new CompositeKeyResolver(List.of(dim("c", "v")), ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("separator");
    }

    @Test
    void constructor_nullArgsRejected() {
        assertThatThrownBy(() -> new CompositeKeyResolver(null, "|"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CompositeKeyResolver(List.of(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void integrationWithBuiltinDimensions() {
        WebFilterProperties props = new WebFilterProperties();
        List<KeyDimension> builtins = List.of(
                new ClientIdDimension(props),
                new TenantIdDimension(),
                new IpDimension(),
                new PathDimension()
        );
        CompositeKeyResolver resolver = new CompositeKeyResolver(builtins, "|");
        WebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/orders")
                .header("X-Client-Id", "user-1")
                .header("X-Tenant-Id", "acme")
                .header("X-Forwarded-For", "1.2.3.4")
                .build();
        assertThat(resolver.resolve(ctx))
                .isEqualTo("client:user-1|tenant:acme|ip:1.2.3.4|path:/api/v1/orders");
    }
}