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
import com.richie.component.web.core.spi.support.MutableWebRequestContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIdDimensionTest {

    private static ClientIdDimension dim(String header) {
        WebFilterProperties props = new WebFilterProperties();
        props.setKeyHeader(header);
        return new ClientIdDimension(props);
    }

    @Test
    void name_isClient() {
        assertThat(dim("X-Client-Id").name()).isEqualTo("client");
    }

    @Test
    void headerPresent_returnsTrimmedValue() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .header("X-Client-Id", "  user-42  ").build();
        assertThat(dim("X-Client-Id").extract(ctx)).isEqualTo("user-42");
    }

    @Test
    void headerMissing_returnsNull() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder().build();
        assertThat(dim("X-Client-Id").extract(ctx)).isNull();
    }

    @Test
    void headerBlank_returnsNull() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .header("X-Client-Id", "   ").build();
        assertThat(dim("X-Client-Id").extract(ctx)).isNull();
    }

    @Test
    void customHeaderName_isRespected() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .header("X-User-Id", "alice").build();
        assertThat(dim("X-User-Id").extract(ctx)).isEqualTo("alice");
    }
}