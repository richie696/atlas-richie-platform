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

class IpDimensionTest {

    private final IpDimension dim = new IpDimension();

    @Test
    void name_isIp() {
        assertThat(dim.name()).isEqualTo("ip");
    }

    @Test
    void xffPresent_singleIp_returnsIt() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .header("X-Forwarded-For", "1.2.3.4").build();
        assertThat(dim.extract(ctx)).isEqualTo("1.2.3.4");
    }

    @Test
    void xffPresent_multipleIps_returnsLeftmost() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .header("X-Forwarded-For", "1.2.3.4, 10.0.0.1, 192.168.1.1").build();
        assertThat(dim.extract(ctx)).isEqualTo("1.2.3.4");
    }

    @Test
    void xffMissing_fallsBackToXRealIp() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .header("X-Real-IP", "5.6.7.8").build();
        assertThat(dim.extract(ctx)).isEqualTo("5.6.7.8");
    }

    @Test
    void xffEmpty_fallsBackToXRealIp() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .header("X-Forwarded-For", "   ")
                .header("X-Real-IP", "5.6.7.8").build();
        assertThat(dim.extract(ctx)).isEqualTo("5.6.7.8");
    }

    @Test
    void allHeadersMissing_returnsNull() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder().build();
        assertThat(dim.extract(ctx)).isNull();
    }

    @Test
    void leftmostIsBlank_returnsNull() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .header("X-Forwarded-For", "  , 10.0.0.1").build();
        assertThat(dim.extract(ctx)).isNull();
    }
}