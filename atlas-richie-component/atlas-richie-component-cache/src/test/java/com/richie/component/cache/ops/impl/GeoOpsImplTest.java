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
package com.richie.component.cache.ops.impl;

import com.richie.component.cache.function.GeoFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeoOpsImplTest {

    @Mock
    private GeoFunction fn;

    @InjectMocks
    private GeoOpsImpl ops;

    @Test
    void add_delegates() {
        ops.add("geo", 116.0, 39.0, "bj");
        verify(fn).addGeo("geo", 116.0, 39.0, "bj");
    }

    @Test
    void distance_delegates() {
        Distance d = new Distance(100, Metrics.KILOMETERS);
        when(fn.geoDist("geo", "a", "b")).thenReturn(d);
        assertThat(ops.distance("geo", "a", "b")).isSameAs(d);
    }
}
