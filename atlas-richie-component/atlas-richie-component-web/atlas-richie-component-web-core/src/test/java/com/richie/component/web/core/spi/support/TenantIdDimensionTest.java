/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.web.core.spi.support;

import com.richie.component.web.core.spi.support.MutableWebRequestContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantIdDimensionTest {

    private final TenantIdDimension dim = new TenantIdDimension();

    @Test
    void name_isTenant() {
        assertThat(dim.name()).isEqualTo("tenant");
    }

    @Test
    void headerPresent_returnsTrimmedValue() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .header("X-Tenant-Id", "  acme  ").build();
        assertThat(dim.extract(ctx)).isEqualTo("acme");
    }

    @Test
    void headerMissing_returnsNull() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder().build();
        assertThat(dim.extract(ctx)).isNull();
    }

    @Test
    void headerBlank_returnsNull() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .header("X-Tenant-Id", "   ").build();
        assertThat(dim.extract(ctx)).isNull();
    }

    @Test
    void defaultHeaderIsXTenantId() {
        assertThat(TenantIdDimension.DEFAULT_HEADER).isEqualTo("X-Tenant-Id");
    }
}